package okay.teach.domains

import okay.effects.json.Json
import okay.llm.{HttpBackend, Llm}
import okay.scala3.*
import okay.teach.{Case, Domain, DomainSpec, Lesson, Meanings, Reader, Sealed, Teacher}
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
 *  - `laws [rounds] [out]` — kinematics' laws written by choosing (OpenStax §2.5), judged, the held out measured
 *  - `calibrate <domain> <adapter> <calibration.tsv> <report.tsv> [epsilon]` — the bar chosen on one set, its
 *    answered and wrong reported on another (theory L2)
 *  - `all <domain> <n> <epochs> <held-out> [out]` — the whole line, written, trained and read (below)
 *  - `read <domain> <cases> <adapter|-> <plain|calibrated> [epsilon] [model]` — the reader on cases (a jsonl of
 *    `write`, or a tsv of meaning, language, text): right by language, and the bar epsilon allows
 */
object Teach:
  val domains: Map[String, Taught[?, ?]] = Map(
    "tax-rules" -> Taught[Unit, String](TaxRules, (), okay.tax.Rules.all.map(_.id), TaxRules.topic, identity,
      identity))

  private def env(k: String, d: String) = sys.env.getOrElse(k, d)

  def cases[D, T](t: Taught[D, T], file: Path): Vector[Case[T]] =
    val lines = Files.readAllLines(file).asScala.toVector.filterNot(l => l.isBlank || l.startsWith("#"))
    if file.toString.endsWith(".tsv") then lines.map(_.split('\t')).map(a => Case(a(1), a(2), t.read(a(0))))
    else lines.map(Json.fromText).map(j => Case(j("language").string, j("text").string, t.read(j("meant").string)))

  val usage: String =
    """okay-teach <step> …  (bin/okay-teach; paths from the checkout's root; skills/teach-a-model says when)
      |  mcp                                                the model's door: these steps as MCP tools over stdio
      |  book <book.txt> [out] [model|standard]               the book loop: sections → a lesson the model drives
      |  check <spec.json> <file.tsv>                       a spec and a question file checked: no GPU
      |  lesson <domain|spec.json> <n> <epochs> <sealed.tsv> [out]
      |  drive <domain|spec.json> <sealed.tsv> [standard|model] [out]
      |  write <domain|spec.json> <n> <out.jsonl>
      |  train <domain|spec.json> <cases.jsonl> <out dir> [epochs] [model]
      |  read <domain|spec.json> <cases> <adapter|-> <plain|calibrated> [epsilon] [model]
      |  calibrate <domain|spec.json> <adapter> <calibration.tsv> <report.tsv> [epsilon]
      |  all <domain|spec.json> <n> <epochs> <held-out> [out]
      |  rules [train] [rounds] <out>      laws [rounds] [out]""".stripMargin

  def main(args: Array[String]): Unit =
    if args.isEmpty || args.length < 2 && args(0) != "mcp" then println(usage)
    else if args(0) == "mcp" then McpDoor.serve()
    else if args(0) == "book" then book(Path.of(args(1)), args.lift(2), args.lift(3).getOrElse("model"))
    else if args(0) == "laws" then
      laws(args.lift(1).fold(4)(_.toInt), Path.of(args.lift(2).getOrElse("runs/kinematics")))
    else if args(0) == "rules" then
      rules(args.lift(1).fold(10)(_.toInt), args.lift(2).fold(4)(_.toInt), Path.of(args(3)))
    else main1(args)

  /**
   * A DOMAIN AS A JSON SPEC (specs/domain-spec.md): `<name>.json`, its meanings from `<name>.meanings.txt` (one a
   * line, in the spec's words) or enumerated when every hole is an enum; a sealed set's first column is a meaning's
   * words. What a model teaching by the skill uses: no Scala
   */
  def fromSpec(file: Path): Taught[DomainSpec.Data, DomainSpec.Meaning] =
    val spec = DomainSpec.fromJson(Json.fromText(Files.readString(file))).fold(e => sys.error(s"$file: $e"), identity)
    val listed = Path.of(file.toString.stripSuffix(".json") + ".meanings.txt")
    val meanings = (if Files.exists(listed) then Meanings.listed(spec, Files.readAllLines(listed).asScala.toVector)
      else Meanings.enumerated(spec)).fold(e => sys.error(e), identity)
    val space = spec.domain.space
    Taught(spec.domain, Map.empty, meanings, spec.system, m => space.say(Map.empty, m, "en"),
      w => space.parse(Map.empty, w, "en").getOrElse(sys.error(s"not a meaning of ${spec.name}: $w")))

  private def main1(args: Array[String]): Unit =
    val t: Taught[?, ?] = if args(1).endsWith(".json") then fromSpec(Path.of(args(1)))
      else domains.getOrElse(args(1), sys.error(s"no domain ${args(1)}; known: ${domains.keys.mkString(", ")}"))
    args(0) match
      case "check" => check(t, Path.of(args(2)))
      case "write" => val _ = write(t, args(2).toInt, Path.of(args(3)))
      case "train" => OnGpu.train(t.domain, t.data, cases(t, Path.of(args(2))), args.lift(5).getOrElse(model),
        Path.of(args(3)), args.lift(4).fold(2)(_.toInt))
      case "read" => read(t, Path.of(args(2)), args(3), args(4), args.lift(5).fold(0.05)(_.toDouble),
        args.lift(6).getOrElse(model))
      case "lesson" =>
        val out = Path.of(args.lift(5).getOrElse(s"runs/${args(1)}"))
        val o = LessonOnGpu(t, name(args(1)), out, Map("held" -> Path.of(args(4)))).run(
          Lesson.standard[Lesson](Sealed("held"), args(2).toInt, args(3).toInt))
        println(s"released: ${o.released.fold("none (the gate did not pass)")(_.id)}")
      case "drive" =>
        val out = Path.of(args.lift(4).getOrElse(s"runs/${args(1)}-driven"))
        val gpu = LessonOnGpu(t, name(args(1)), out, Map("held" -> Path.of(args(2))))
        val start = okay.teach.Sigma(name(args(1)), Sealed("held"))
        val end = if args.lift(3).contains("model") then
          val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
            env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
          run(Llm.on(teacher)(gpu.on(okay.teach.Teaching.drive[Lesson + Llm](start, okay.teach.Teaching.byModel))))
        else gpu.run(okay.teach.Teaching.drive[Lesson](start, okay.teach.Teaching.standard))
        Files.writeString(out.resolve("sigma.txt"), end.said + "\n\nhistory:\n" + end.history.mkString("\n"))
        println(end.said)
      case "calibrate" =>
        calibrate(t, Path.of(args(2)), Path.of(args(3)), Path.of(args(4)), args.lift(5).fold(0.05)(_.toDouble))
      case "all" => all(t, args(2).toInt, args(3).toInt, Path.of(args(4)),
        Path.of(args.lift(5).getOrElse(s"runs/${args(1)}")))
      case other => sys.error(s"no step $other")

  private val model = "mlx-community/Qwen3-0.6B-4bit"

  def write[D, T](t: Taught[D, T], n: Int, out: Path): (Int, Int) =
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
    val written = t.meanings.zipWithIndex.map { (m, i) =>
      // (isolated: `write` runs inside a lesson's handler too, a run of its own — okay-scala's NestedRun otherwise)
      val w = runIsolated(Llm.on(teacher)(Teacher.write[D, T, Llm](t.domain, t.data, m, n, t.about, i.toLong)))
      println(s"  ${t.show(m)}: kept ${w.kept.size}, refused ${w.refused.size}" +
        (if w.byStop then "" else " (the reply hit the token limit)"))
      // the teacher's raw replies kept beside the cases: a short count is diagnosed from them
      Files.writeString(out.resolveSibling(out.getFileName.toString + ".replies.txt"),
        s"=== ${t.show(m)}\n${w.reply}\n", java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND)
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

  /**
   * `laws [rounds] [out]` — the teacher writes kinematics' laws by choosing (`LawTeacher`), judged on four of OpenStax
   * §2.5's worked examples; each round's variables, plan, laws and verdict written; the best measured on the four
   * held out (`scripts/fetch-openstax.zsh` first)
   */
  def laws(rounds: Int, out: Path): Unit =
    Files.createDirectories(out)
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
    val t0 = System.nanoTime()
    val done = run(Llm.on(teacher)(okay.teach.LawTeacher.teach[Llm](Kinematics.lesson, Kinematics.variables,
      Kinematics.train, rounds)))
    done.zipWithIndex.foreach { (r, i) =>
      Files.writeString(out.resolve(s"round-${i + 1}.txt"), (Vector(s"passed ${r.passed} of ${r.of}", "", "plan:") ++
        r.plan ++ Vector("", "laws:") ++ r.laws.map(_.toString) ++ Vector("", "not built:") ++ r.unread ++
        Vector("", s"told: ${r.told}")).mkString("\n"))
      println(s"  round ${i + 1}: ${r.plan.size} described, ${r.laws.size} built, passed ${r.passed} of ${r.of}") }
    done.maxByOption(_.passed).foreach { r =>
      val theory = okay.teach.equations.Theory(r.variables, r.laws)
      Files.writeString(out.resolve("best.txt"), r.laws.mkString("\n"))
      val v = okay.teach.LawTeacher.judge(theory, Kinematics.held)
      println(s"the best on the held out: ${v.passed} of ${v.of}") }
    println(f"${(System.nanoTime() - t0) / 1e9}%.0f s")

  /**
   * `calibrate <domain> <adapter> <calibration.tsv> <report.tsv> [epsilon]` — the bar chosen on one set and reported
   * on another (theory L2: calibration and sealed disjoint): on the report set, how many the reader answers above the
   * bar and how many of those are wrong. Only counts are printed
   */
  def calibrate[D, T](t: Taught[D, T], adapter: Path, calibration: Path, report: Path, epsilon: Double): Unit =
    val gpu = OnGpu.Gpu(model, Some(adapter))
    val (cal, rep) = try (OnGpu.read(t.domain, t.data, cases(t, calibration), gpu),
      OnGpu.read(t.domain, t.data, cases(t, report), gpu)) finally gpu.close()
    println(s"calibration: ${OnGpu.said(cal, epsilon)}")
    okay.teach.Bar.choose(cal.map(r => (r.margin, r.right)), epsilon) match
      case None => println("no bar holds on the calibration set")
      case Some(b) =>
        val answered = rep.filter(_.margin >= b.bar)
        println(f"report: right ${rep.count(_.right)} of ${rep.size}; at the bar ${b.bar}%.3f answered " +
          s"${answered.size} of ${rep.size}, wrong ${answered.count(!_.right)}")

  /** a domain's name: a spec's own name (not its path), else the registered one */
  def name(arg: String): String =
    if arg.endsWith(".json") then
      DomainSpec.fromJson(Json.fromText(Files.readString(Path.of(arg)))).fold(_ => arg, _.name)
    else arg

  /**
   * `check <spec.json> <file.tsv>` — before any lesson, no GPU: every row's meaning read in the spec's words (an
   * option's key or its shown text), and each question checked to reach its meaning (the spec offers what the meaning
   * needs, e.g. the numbers it writes). Counts only, and the first rows that fail — never use it to read a SEALED
   * file's texts (theory L1): check the spec on rows you write for the purpose
   */
  def check[D, T](t: Taught[D, T], file: Path): Unit =
    val rows = Files.readAllLines(file).asScala.toVector.filterNot(l => l.isBlank || l.startsWith("#"))
      .map(_.split('\t'))
    val read = rows.map(a => a -> scala.util.Try(t.read(a(0))).toOption)
    val unread = read.collect { case (a, None) => a(0) }
    val unreached = read.collect { case (a, Some(m)) if okay.frames.Laws.reach(t.domain.space)(t.data, a(2), m)
      .nonEmpty => a(0) }
    println(s"${rows.size} rows: ${rows.size - unread.size - unreached.size} fine, ${unread.size} meanings not read, " +
      s"${unreached.size} not reached")
    (unread.take(3) ++ unreached.take(3)).foreach(m => println(s"  $m"))

  /**
   * `book <book.txt> [out] [model|standard]` — THE BOOK LOOP (specs/book-loop.md): the book's sections become the
   * domain, its own questions the sealed set (written beside it, never read by the teacher), and the lesson is driven
   * by the local model (`model`, the default) or the fixed policy; Σ, the trace and the competence in `out`
   */
  def book(file: Path, out: Option[String], driver: String): Unit =
    val (t, sealedFile) = BookLoop.load(file)
    val name = t.domain.asInstanceOf[BookDomain].name
    val dir = Path.of(out.getOrElse(s"runs/$name"))
    println(s"$name: ${t.meanings.size} sections, sealed ${Files.readAllLines(sealedFile).size} questions " +
      s"($sealedFile), driven by $driver")
    val gpu = LessonOnGpu(t, name, dir, Map("held" -> sealedFile))
    val start = okay.teach.Sigma(name, Sealed("held"))
    val end = if driver == "model" then
      val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
        env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
      run(Llm.on(teacher)(gpu.on(okay.teach.Teaching.drive[Lesson + Llm](start, okay.teach.Teaching.byModel))))
    else gpu.run(okay.teach.Teaching.drive[Lesson](start, okay.teach.Teaching.standard))
    Files.writeString(dir.resolve("sigma.txt"), end.said + "\n\nhistory:\n" + end.history.mkString("\n"))
    println(end.said)
