package okay.teach.domains

import okay.effects.json.Json
import okay.llm.{HttpBackend, Llm}
import okay.scala3.*
import okay.teach.{Case, Domain, Reader, Teacher}
import okay.teach.mlx.OnGpu

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** a domain as the command knows it: its data, the meanings a teacher writes for, what it is about, a meaning as a
 * string and back (the files' form) */
final case class Taught[D, T](domain: Domain[D, T], data: D, meanings: Vector[T], about: String,
    show: T => String, read: String => T)

/**
 * THE COMMAND (specs/teach.md §6): `sbt "domains/runMain okay.teach.domains.Teach <step> …"`
 *
 *  - `write <domain> <n> <out.jsonl>` — the teacher (TEACHER_URL, TEACHER_MODEL; the local 35B by default) writes n
 *    questions a language for each meaning; the kept (reached by the space) written as cases, the refused counted
 *  - `train <domain> <cases.jsonl> <out dir> [epochs] [model]` — a LoRA on the cases (Qwen3-0.6B by default)
 *  - `read <domain> <cases> <adapter|-> <plain|calibrated> [epsilon] [model]` — the reader on cases (a jsonl of
 *    `write`, or a tsv of meaning, language, text): right by language, and the bar epsilon allows
 */
object Teach:
  val domains: Map[String, Taught[?, ?]] = Map(
    "tax-rules" -> Taught[Unit, String](TaxRules, (), okay.tax.Rules.all.map(_.id), TaxRules.about, identity,
      identity))

  private def env(k: String, d: String) = sys.env.getOrElse(k, d)

  def cases[D, T](t: Taught[D, T], file: Path): Vector[Case[T]] =
    val lines = Files.readAllLines(file).asScala.toVector.filterNot(l => l.isBlank || l.startsWith("#"))
    if file.toString.endsWith(".tsv") then lines.map(_.split('\t')).map(a => Case(a(1), a(2), t.read(a(0))))
    else lines.map(Json.fromText).map(j => Case(j("language").string, j("text").string, t.read(j("meant").string)))

  def main(args: Array[String]): Unit =
    val t = domains.getOrElse(args(1), sys.error(s"no domain ${args(1)}; known: ${domains.keys.mkString(", ")}"))
    args(0) match
      case "write" => write(t, args(2).toInt, Path.of(args(3)))
      case "train" => OnGpu.train(t.domain, t.data, cases(t, Path.of(args(2))), args.lift(5).getOrElse(model),
        Path.of(args(3)), args.lift(4).fold(2)(_.toInt))
      case "read" => read(t, Path.of(args(2)), args(3), args(4), args.lift(5).fold(0.05)(_.toDouble),
        args.lift(6).getOrElse(model))
      case other => sys.error(s"no step $other")

  private val model = "mlx-community/Qwen3-0.6B-4bit"

  def write[D, T](t: Taught[D, T], n: Int, out: Path): Unit =
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
    val written = t.meanings.zipWithIndex.map { (m, i) =>
      val w = run(Llm.on(teacher)(Teacher.write[D, T, Llm](t.domain, t.data, m, n, t.about, i.toLong)))
      println(s"  ${t.show(m)}: kept ${w.kept.size}, refused ${w.refused.size}")
      w }
    val rows = written.flatMap(_.kept).map(c => Json.obj("language" -> Json.Str(c.language),
      "text" -> Json.Str(c.text), "meant" -> Json.Str(t.show(c.meant))).render)
    Files.write(out, rows.asJava)
    println(s"$out: ${rows.size} cases kept of ${rows.size + written.map(_.refused.size).sum} written")

  def read[D, T](t: Taught[D, T], file: Path, adapter: String, scoring: String, epsilon: Double, repo: String): Unit =
    val cs = cases(t, file)
    val gpu = OnGpu.Gpu(repo, Option.when(adapter != "-")(Path.of(adapter)))
    val s = if scoring == "calibrated" then Reader.Scoring.ContentFree() else Reader.Scoring.Plain
    val t0 = System.nanoTime()
    val reads = OnGpu.read(t.domain, t.data, cs, gpu, s)
    gpu.close()
    reads.filterNot(_.right).foreach(r =>
      println(s"    [${r.c.language}] ${r.c.text} → ${r.meaning.map(t.show)} (${t.show(r.c.meant)})"))
    println(f"$repo + $adapter, $scoring, ${(System.nanoTime() - t0) / 1e9 / math.max(1, cs.size)}%.2f s a text")
    println(OnGpu.said(reads, epsilon))
