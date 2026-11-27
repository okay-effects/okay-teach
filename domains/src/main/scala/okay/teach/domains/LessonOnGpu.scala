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

  private def note(op: String, result: String): Unit =
    val line = Json.obj("at" -> Json.Str(java.time.Instant.now.toString), "domain" -> Json.Str(name),
      "op" -> Json.Str(op), "result" -> Json.Str(result)).render + "\n"
    Files.writeString(trace, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    println(s"  $op → $result")

  private def answer[X](op: Lesson[X]): X =
    val result: X = op match
      case Lesson.Ask(n) =>
        val cases = out.resolve("cases.jsonl")
        val (kept, refused) = if Files.exists(cases) then (Teach.cases(t, cases).size, 0)
          else Teach.write(t, n, cases)
        Asked(Ref(cases.toString), kept, refused)
      case Lesson.Train(cases, epochs) =>
        val dir = out.resolve("adapter")
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
          Bar.choose(reads.map(r => (r.margin, r.right)), epsilon))
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
