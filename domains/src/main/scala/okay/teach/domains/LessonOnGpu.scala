package okay.teach.domains

import okay.effects.json.Json
import okay.scala3.*
import okay.teach.{Asked, Bar, Lesson, Measured, Reader, Ref}
import okay.teach.mlx.OnGpu

import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}

/**
 * THE LESSON'S HANDLER ON THIS MAC (theory §2): `Ask` by the teacher over HTTP (`Teach.write`), `Train` and `Measure`
 * on MLX (`OnGpu`), `Release` a competence directory (theory §8). The sealed sets are files only this handler maps
 * from their ids, and `Measure` returns their aggregates alone (theory L1). Every operation and its result is
 * appended to `<out>/trace.jsonl` — a lesson's trace, the teacher model's data (theory §7).
 */
final class LessonOnGpu[D, T](t: Taught[D, T], name: String, out: Path, sealedFiles: Map[String, Path],
    epsilon: Double = 0.05, repo: String = "mlx-community/Qwen3-0.6B-4bit"):
  Files.createDirectories(out)
  private val trace = out.resolve("trace.jsonl")
  private var asks = 0

  private def note(op: String, result: String): Unit =
    val line = Json.obj("at" -> Json.Str(java.time.Instant.now.toString), "domain" -> Json.Str(name),
      "op" -> Json.Str(op), "result" -> Json.Str(result)).render + "\n"
    Files.writeString(trace, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    println(s"  $op → $result")

  private def answer[X](op: Lesson[X]): X =
    val result: X = op match
      case Lesson.Ask(n) =>
        // the first ask of a run that has questions resumes them; a later ask (the corpus repaired) writes MORE, with
        // other seeds, appended — the teacher does not repeat itself
        val cases = out.resolve("cases.jsonl")
        asks += 1
        val refused =
          if Files.exists(cases) && asks == 1 then 0
          else
            val more = out.resolve(s"cases.$asks.jsonl")
            val (_, r) = Teach.write(t, n, more, seed = 1000L * asks)
            Files.write(cases, Files.readAllLines(more), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            r
        Asked(Ref(cases.toString), Teach.cases(t, cases).size, refused)
      case Lesson.Train(cases, epochs) =>
        // an adapter for each size of the corpus: training again after more questions makes a new one
        val dir = out.resolve(s"adapter-${Teach.cases(t, Path.of(cases.id)).size}")
        if !Files.exists(dir.resolve("reader.adapter.safetensors")) then
          OnGpu.train(t.domain, t.data, Teach.cases(t, Path.of(cases.id)), repo, dir, epochs)
        Ref(dir.resolve("reader.adapter.safetensors").toString)
      case Lesson.Measure(on, adapter, calibrated) =>
        val file = sealedFiles.getOrElse(on.id, sys.error(s"no sealed set ${on.id}"))
        val gpu = OnGpu.Gpu(repo, adapter.map(a => Path.of(a.id)))
        val reads = try OnGpu.read(t.domain, t.data, Teach.cases(t, file), gpu,
          if calibrated then Reader.Scoring.ContentFree() else Reader.Scoring.Plain) finally gpu.close()
        val what = (if adapter.isEmpty then "untaught" else "taught") + ", " +
          (if calibrated then "calibrated" else "plain")
        Measured(what,
          reads.count(_.right), reads.size,
          reads.groupBy(_.c.language).map((l, rs) => l -> (rs.count(_.right), rs.size)),
          Bar.choose(reads.map(r => (r.margin, r.right)), epsilon),
          reads.filterNot(_.right).groupBy(r => (t.show(r.c.meant), r.meaning.fold("nothing")(t.show)))
            .map((k, rs) => k -> rs.size))
      case Lesson.Baseline(on, cases) =>
        // the word search learned from the training cases alone, read on the sealed set: counts only (L1, L7)
        val file = sealedFiles.getOrElse(on.id, sys.error(s"no sealed set ${on.id}"))
        val read = okay.teach.Baseline.words(Teach.cases(t, Path.of(cases.id)))
        val sealedCases = Teach.cases(t, file)
        val right = sealedCases.map(c => c -> read(c.text).contains(c.meant))
        Measured("baseline (words)", right.count(_._2), right.size,
          right.groupBy(_._1.language).map((l, rs) => l -> (rs.count(_._2), rs.size)), None)
      case Lesson.Release(adapter, measured) =>
        val dir = out.resolve("competence")
        Files.createDirectories(dir.resolve("adapter"))
        Files.copy(Path.of(adapter.id), dir.resolve("adapter/reader.adapter.safetensors"),
          StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(dir.resolve("bar.json"), Json.obj("epsilon" -> Json.Num(epsilon),
          "measured" -> Json.Str(measured.said)).render)
        Files.writeString(dir.resolve("provenance.json"), Json.obj("domain" -> Json.Str(name),
          "base" -> Json.Str(repo), "trace" -> Json.Str(trace.toString)).render)
        Ref(dir.toString)
      case Lesson.Uncovered(_) => ()
    note(op.toString, result match
      case m: Measured => m.said
      case other => other.toString)
    result

  def run[A](p: A ! Lesson): A =
    okay.scala3.run(handle(p, Handler.tail[Lesson, Pure, A, A](pure)([X] => (op: Lesson[X]) => answer(op))))

  /** the Lesson handled inside a program of other effects too (a teacher model's `Llm`) */
  def on[R[_], Rest[_], A](p: A ! R)(using Split[Lesson, R, Rest]): A ! Rest =
    handle(p, Handler.tail[Lesson, Rest, A, A](pure)([X] => (op: Lesson[X]) => answer(op)))
