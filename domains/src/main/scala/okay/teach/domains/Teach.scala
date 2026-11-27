package okay.teach.domains

import okay.effects.json.Json
import okay.llm.{HttpBackend, Llm}
import okay.scala3.*
import okay.teach.{Case, Domain, Lesson, Reader, Sealed, Teacher}
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
 *  - `lesson <domain> <n> <epochs> <sealed.tsv> [out]` — the standard Lesson (an effect, theory §2): the same line,
 *    the sealed set measured by aggregates only, the gate, a released competence, the trace in `<out>/trace.jsonl`
 *  - `drive <domain> <sealed.tsv> [standard|model] [out]` — the lesson driven by a teacher choosing each operation
 *    among those the laws allow (theory §7): the fixed policy, or the teacher model (TEACHER_URL); Σ in `sigma.txt`
 *  - `all <domain> <n> <epochs> <held-out> [out]` — the whole line, written, trained and read (below)
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
    if args(0) == "rules" then
      rules(args.lift(1).fold(10)(_.toInt), args.lift(2).fold(4)(_.toInt), Path.of(args(3)))
    else main1(args)

  private def main1(args: Array[String]): Unit =
    val t = domains.getOrElse(args(1), sys.error(s"no domain ${args(1)}; known: ${domains.keys.mkString(", ")}"))
    args(0) match
      case "write" => write(t, args(2).toInt, Path.of(args(3)))
      case "train" => OnGpu.train(t.domain, t.data, cases(t, Path.of(args(2))), args.lift(5).getOrElse(model),
        Path.of(args(3)), args.lift(4).fold(2)(_.toInt))
      case "read" => read(t, Path.of(args(2)), args(3), args(4), args.lift(5).fold(0.05)(_.toDouble),
        args.lift(6).getOrElse(model))
      case "lesson" =>
        val out = Path.of(args.lift(5).getOrElse(s"runs/${args(1)}"))
        val o = LessonOnGpu(t, args(1), out, Map("held" -> Path.of(args(4)))).run(
          Lesson.standard[Lesson](Sealed("held"), args(2).toInt, args(3).toInt))
        println(s"released: ${o.released.fold("none (the gate did not pass)")(_.id)}")
      case "drive" =>
        val out = Path.of(args.lift(4).getOrElse(s"runs/${args(1)}-driven"))
        val gpu = LessonOnGpu(t, args(1), out, Map("held" -> Path.of(args(2))))
        val start = okay.teach.Sigma(args(1), Sealed("held"))
        val end = if args.lift(3).contains("model") then
          val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
            env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
          run(Llm.on(teacher)(gpu.on(okay.teach.Teaching.drive[Lesson + Llm](start, okay.teach.Teaching.byModel))))
        else gpu.run(okay.teach.Teaching.drive[Lesson](start, okay.teach.Teaching.standard))
        Files.writeString(out.resolve("sigma.txt"), end.said + "\n\nhistory:\n" + end.history.mkString("\n"))
        println(end.said)
      case "all" => all(t, args(2).toInt, args(3).toInt, Path.of(args(4)),
        Path.of(args.lift(5).getOrElse(s"runs/${args(1)}")))
      case other => sys.error(s"no step $other")

  private val model = "mlx-community/Qwen3-0.6B-4bit"

  def write[D, T](t: Taught[D, T], n: Int, out: Path): (Int, Int) =
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
    (rows.size, written.map(_.refused.size).sum)

  def read[D, T](t: Taught[D, T], file: Path, adapter: String, scoring: String, epsilon: Double, repo: String): Unit =
    val cs = cases(t, file)
    val gpu = OnGpu.Gpu(repo, Option.when(adapter != "-")(Path.of(adapter)))
    val s = if scoring == "calibrated" then Reader.Scoring.ContentFree() else Reader.Scoring.Plain
    val t0 = System.nanoTime()
    val reads = OnGpu.read(t.domain, t.data, cs, gpu, s)
    gpu.close()
    // the texts misread only when asked (SHOW_TEXTS=1): a sealed set's texts are never shown to a teacher (theory L1)
    if sys.env.get("SHOW_TEXTS").contains("1") then reads.filterNot(_.right).foreach(r =>
      println(s"    [${r.c.language}] ${r.c.text} → ${r.meaning.map(t.show)} (${t.show(r.c.meant)})"))
    println(f"$repo + $adapter, $scoring, ${(System.nanoTime() - t0) / 1e9 / math.max(1, cs.size)}%.2f s a text")
    println(OnGpu.said(reads, epsilon))

  /**
   * `rules <train> <rounds> <out dir>` — the teacher writes Carroll's rules by choosing (`RuleTeacher`), judged on the
   * first `train` consistent exercises; each round's plan, rules and verdict written to the directory; the best
   * program measured on the held out
   */
  def rules(train: Int, rounds: Int, out: Path): Unit =
    Files.createDirectories(out)
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
    val (trainSet, held) = CarrollRules.exercises(train)
    val t0 = System.nanoTime()
    val done = run(Llm.on(teacher)(okay.teach.RuleTeacher.teach[Llm](CarrollRules.lesson, CarrollRules.vocabulary,
      CarrollRules.judge(trainSet), rounds)))
    done.zipWithIndex.foreach { (r, i) =>
      Files.writeString(out.resolve(s"round-${i + 1}.txt"), (Vector(s"passed ${r.passed} of ${r.of}", "",
        "plan:") ++ r.plan ++ Vector("", "rules:") ++ r.rules.map(_.toString) ++ Vector("", "not built:") ++
        r.unread ++ Vector("", s"told: ${r.told}")).mkString("\n"))
      println(s"  round ${i + 1}: ${r.plan.size} described, ${r.rules.size} built, passed ${r.passed} of ${r.of}") }
    okay.teach.RuleTeacher.best(done).foreach { p =>
      Files.writeString(out.resolve("best.dl"), p.toString)
      println(s"the best on the held out: ${CarrollRules.scored(p, held).said}") }
    println(f"${(System.nanoTime() - t0) / 1e9}%.0f s")

  /**
   * `all <domain> <n> <epochs> <held-out> [out]` — the whole line: the teacher's questions (`<out>/cases.jsonl`), a
   * LoRA on them (`<out>/adapter`), then the held out read four ways — untaught and taught, plain and calibrated
   */
  def all[D, T](t: Taught[D, T], n: Int, epochs: Int, held: Path, out: Path): Unit =
    Files.createDirectories(out)
    val cases = out.resolve("cases.jsonl")
    if !Files.exists(cases) then { val _ = write(t, n, cases) }
    val adapter = out.resolve("adapter")
    if !Files.exists(adapter.resolve("reader.adapter.safetensors")) then
      OnGpu.train(t.domain, t.data, Teach.cases(t, cases), model, adapter, epochs)
    for a <- Vector("-", adapter.resolve("reader.adapter.safetensors").toString); s <- Vector("plain", "calibrated") do
      println(s"== ${if a == "-" then "untaught" else "taught"}, $s")
      read(t, held, a, s, 0.05, model)
