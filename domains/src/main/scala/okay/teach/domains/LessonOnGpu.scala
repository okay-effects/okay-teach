package okay.teach.domains

import okay.effects.json.Json
import okay.scala3.*
import okay.teach.{Asked, Bar, Case, Checked, Lesson, Lint, Measured, Reader, Ref, Vote}
import okay.teach.mlx.OnGpu

import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import scala.jdk.CollectionConverters.*

/**
 * THE LESSON'S HANDLER ON THIS MAC (theory §2): `Ask` by the teacher over HTTP (`Teach.write`), `Train` and `Measure`
 * on MLX (`OnGpu`), `Release` a competence directory (theory §8). The sealed sets are files only this handler maps
 * from their ids, and `Measure` returns their aggregates alone (theory L1). Every operation and its result is
 * appended to `<out>/trace.jsonl` — a lesson's trace, the teacher model's data (theory §7).
 *
 * SINCE T19: `Train` with seeds makes several adapters, one Ref of their ids joined by commas, and `Measure` reads
 * them as a vote (`Vote`, margin = the first meaning's share of the votes). `Check` reads the questions the teacher
 * writes apart (`valid.jsonl`, never trained on) and finds the inseparable pairs (`Checked.pairs`). `Merge` makes
 * two meanings one through `merging` (a domain that can be repaired so: a book), relabelling the cases, the
 * questions apart and, through the new domain, the sealed set.
 */
final class LessonOnGpu[D, T](t0: Taught[D, T], name: String, out: Path, sealedFiles: Map[String, Path],
    epsilon: Double = 0.05, repo: String = "mlx-community/Qwen3-0.6B-4bit",
    // a run's seed (OKAY_TEACH_SEED, default 0 = the runs recorded): the teacher's questions and the LoRA's start
    seed: Long = sys.env.get("OKAY_TEACH_SEED").fold(0L)(_.toLong),
    // two meanings (in words) made one: the merged domain, its sealed set, the old meaning words to the new
    merging: Option[(String, String) => (Taught[D, T], Path, String => String)] = None):
  Files.createDirectories(out)
  private val trace = out.resolve("trace.jsonl")
  private var asks = 0
  private var t = t0
  private var sealedNow = sealedFiles
  private var casesFile = out.resolve("cases.jsonl")
  // the questions apart, relabelled to `valid.m<k>.jsonl` by each merge (the first one kept as written)
  private var validFile = out.resolve("valid.jsonl")
  private var generation = 0

  private def note(op: String, result: String): Unit =
    val line = Json.obj("at" -> Json.Str(java.time.Instant.now.toString), "domain" -> Json.Str(name),
      "op" -> Json.Str(op), "result" -> Json.Str(result)).render + "\n"
    Files.writeString(trace, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    println(s"  $op → $result")

  // each sealed set checked before it measures (Lint): its problems in the trace, counts only
  private def lint(): Unit =
    sealedNow.foreach((id, f) => Lint.cases(Teach.cases(t, f), t.show).foreach(p => note(s"Lint($id)", p)))
  lint()

  private def sealedFile(id: String): Path = sealedNow.getOrElse(id, sys.error(s"no sealed set $id"))

  /** the cases read by one adapter (none: the untaught reader) or by several voting */
  private def readAll(adapter: Option[Ref], cs: Vector[Case[T]], calibrated: Boolean): Vector[OnGpu.Read1[T]] =
    val scoring = if calibrated then Reader.Scoring.ContentFree() else Reader.Scoring.Plain
    def one(a: Option[Path]) =
      val gpu = OnGpu.Gpu(repo, a)
      try OnGpu.read(t.domain, t.data, cs, gpu, scoring) finally gpu.close()
    adapter.map(_.id.split(',').toVector.map(Path.of(_))) match
      case None => one(None)
      case Some(Vector(a)) => one(Some(a))
      case Some(as) =>
        val each = as.map(a => one(Some(a)))
        cs.indices.toVector.map { i =>
          val v = Vote.of(each.map(rs => (rs(i).meaning, rs(i).second)))
          OnGpu.Read1(cs(i), v.first, v.votes.toDouble / math.max(1, v.of), v.second) }

  /** a file of cases with its meanings renamed (a merge) */
  private def relabel(from: Path, to: Path, words: String => String): Unit =
    val rows = Files.readAllLines(from).asScala.toVector.filterNot(_.isBlank).map { l =>
      val j = Json.fromText(l)
      Json.obj("language" -> j("language"), "text" -> j("text"), "meant" -> Json.Str(words(j("meant").string))).render }
    Files.write(to, rows.asJava)

  private def answer[X](op: Lesson[X]): X =
    val result: X = op match
      case Lesson.Ask(n) =>
        // the first ask of a run that has questions resumes them; a later ask (the corpus repaired) writes MORE, with
        // other seeds, appended — the teacher does not repeat itself
        asks += 1
        val refused =
          if Files.exists(casesFile) && asks == 1 then 0
          else
            val more = out.resolve(s"cases.$asks.jsonl")
            val (_, r) = Teach.write(t, n, more, seed = 1000L * asks + 100000L * seed)
            Files.write(casesFile, Files.readAllLines(more), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            r
        Asked(Ref(casesFile.toString), Teach.cases(t, casesFile).size, refused)
      case Lesson.Train(cases, epochs, seeds) =>
        // an adapter for each size of the corpus (and each space after a merge); seed i > 0 a sibling of seed 0
        val cs = Teach.cases(t, Path.of(cases.id))
        val tag = (if generation == 0 then "" else s"m$generation-") + cs.size + (if epochs == 2 then "" else
          s"-e$epochs")
        val ids = (0 until seeds).map { i =>
          val dir = out.resolve(s"adapter-$tag" + (if i == 0 then "" else s"-s$i"))
          if !Files.exists(dir.resolve("reader.adapter.safetensors")) then
            OnGpu.train(t.domain, t.data, cs, repo, dir, epochs, seed = 5 + seed.toInt + i)
          dir.resolve("reader.adapter.safetensors").toString }
        Ref(ids.mkString(","))
      case Lesson.Measure(on, adapter, calibrated) =>
        val reads = readAll(adapter, Teach.cases(t, sealedFile(on.id)), calibrated)
        val what = (if adapter.isEmpty then "untaught" else "taught") + ", " +
          (if calibrated then "calibrated" else "plain")
        // the readings' margins and outcomes, no text (L1): a bar is studied from them without the GPU
        Files.write(out.resolve(s"margins.${what.replace(", ", "-")}.tsv"),
          reads.map(r => f"${r.margin}%.4f\t${r.right}\t${r.inTwo}\t${t.show(r.c.meant)}\t" +
            r.meaning.fold("nothing")(t.show) + "\t" + r.second.fold("nothing")(t.show)).asJava)
        Measured(what,
          reads.count(_.right), reads.size,
          reads.groupBy(_.c.language).map((l, rs) => l -> (rs.count(_.right), rs.size)),
          Bar.choose(reads.map(r => (r.margin, r.right)), epsilon),
          reads.filterNot(_.right).groupBy(r => (t.show(r.c.meant), r.meaning.fold("nothing")(t.show)))
            .map((k, rs) => k -> rs.size),
          inTwo = Some(reads.count(_.inTwo)),
          pair = Bar.pair(reads.map(r => (r.margin, r.right, r.inTwo)), epsilon))
      case Lesson.Check(adapter) =>
        // the teacher's questions APART: 2 a passage, other seeds, never trained on — never the sealed set (L1)
        if !Files.exists(validFile) then
          val _ = Teach.write(t, 2, validFile, seed = 7000L + 100000L * seed)
        val reads = readAll(Some(adapter), Teach.cases(t, validFile), calibrated = false)
        Checked(reads.count(_.right), reads.size,
          Checked.pairs(reads.map(r => (t.show(r.c.meant), r.meaning.fold("nothing")(t.show)))))
      case Lesson.Merge(a, b) =>
        val merge = merging.getOrElse(sys.error(s"$name cannot merge meanings"))
        val (merged, sealedMerged, words) = merge(a, b)
        generation += 1
        val next = out.resolve(s"cases.m$generation.jsonl")
        relabel(casesFile, next, words)
        if Files.exists(validFile) then
          val v = out.resolve(s"valid.m$generation.jsonl")
          relabel(validFile, v, words)
          validFile = v
        t = merged
        sealedNow = sealedNow.map((id, _) => id -> sealedMerged)
        casesFile = next
        lint()
        Asked(Ref(casesFile.toString), Teach.cases(t, casesFile).size, 0)
      case Lesson.Baseline(on, cases) =>
        // the word search learned from the training cases alone, read on the sealed set: counts only (L1, L7)
        val ranked = okay.teach.Baseline.ranked(Teach.cases(t, Path.of(cases.id)))
        val sealedCases = Teach.cases(t, sealedFile(on.id))
        val firsts = sealedCases.map(c => c -> ranked(c.text).take(2))
        val right = firsts.map((c, r) => c -> r.headOption.contains(c.meant))
        Measured("baseline (words)", right.count(_._2), right.size,
          right.groupBy(_._1.language).map((l, rs) => l -> (rs.count(_._2), rs.size)), None,
          inTwo = Some(firsts.count((c, r) => r.contains(c.meant))))
      case Lesson.Release(adapter, measured) =>
        // one adapter, or several read as a vote (`vote.json` says how many)
        val dir = out.resolve("competence")
        val ids = adapter.id.split(',').toVector
        ids.zipWithIndex.foreach { (id, i) =>
          val to = dir.resolve(if i == 0 then "adapter" else s"adapter-$i")
          Files.createDirectories(to)
          Files.copy(Path.of(id), to.resolve("reader.adapter.safetensors"), StandardCopyOption.REPLACE_EXISTING) }
        Files.writeString(dir.resolve("vote.json"), Json.obj("adapters" -> Json.Num(ids.size)).render)
        Files.writeString(dir.resolve("bar.json"), Json.obj("epsilon" -> Json.Num(epsilon),
          "measured" -> Json.Str(measured.said)).render)
        Files.writeString(dir.resolve("provenance.json"), Json.obj("domain" -> Json.Str(name),
          "base" -> Json.Str(repo), "trace" -> Json.Str(trace.toString)).render)
        Ref(dir.toString)
      case Lesson.Uncovered(_) => ()
    note(op.toString, result match
      case m: Measured => m.said
      case c: Checked => c.said
      case other => other.toString)
    result

  def run[A](p: A ! Lesson): A =
    okay.scala3.run(handle(p, Handler.tail[Lesson, Pure, A, A](pure)([X] => (op: Lesson[X]) => answer(op))))

  /** the Lesson handled inside a program of other effects too (a teacher model's `Llm`) */
  def on[R[_], Rest[_], A](p: A ! R)(using Split[Lesson, R, Rest]): A ! Rest =
    handle(p, Handler.tail[Lesson, Rest, A, A](pure)([X] => (op: Lesson[X]) => answer(op)))
