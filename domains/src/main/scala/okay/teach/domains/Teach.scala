package okay.teach.domains

import okay.effects.json.Json
import okay.llm.{HttpBackend, Llm}
import okay.scala3.*
import okay.teach.{Book, Case, Domain, DomainSpec, Lesson, Meanings, Reader, Ref, Sealed, Teacher, Vote}
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
      |  teacher-train <out> <epochs> <decisions.jsonl>…   a small teacher taught the 35B's decisions (T7)
      |  teacher-measure <adapter|-> <decisions.jsonl>…   its agreement with the 35B
      |  merge <book.txt> <a> <b> <out.txt> [cases.jsonl out.jsonl]  two sections one meaning
      |  contrast <book.txt> <run> [pairs] [n]           confused pairs (on questions apart) taught by contrast
      |  measure <book.txt|domain|spec.json> <sealed.tsv|-> <adapter|a,b,…|-> <cases.jsonl> [out]  first + in two; several vote
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
    else if args(0) == "teacher-train" then
      SmallTeacher.train(Path.of(args(1)), args(2).toInt, args.drop(3).toSeq.map(Path.of(_)))
    else if args(0) == "teacher-measure" then
      SmallTeacher.measure(Option.when(args(1) != "-")(Path.of(args(1))), args.drop(2).toSeq.map(Path.of(_)))
    else if args(0) == "merge" then merge(Path.of(args(1)), args(2), args(3), Path.of(args(4)),
      Option.when(args.length > 6)(Path.of(args(5)) -> Path.of(args(6))))
    else if args(0) == "contrast" then
      contrast(Path.of(args(1)), Path.of(args(2)), args.lift(3).fold(6)(_.toInt), args.lift(4).fold(2)(_.toInt))
    else if args(0) == "measure" then measure(args(1), args(2), args(3), Path.of(args(4)), args.lift(5))
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
      else if args(1).endsWith(".txt") then BookLoop.load(Path.of(args(1)))._1
      else domains.getOrElse(args(1), sys.error(s"no domain ${args(1)}; known: ${domains.keys.mkString(", ")}"))
    args(0) match
      case "check" => check(t, Path.of(args(2)))
      case "write" => val _ = write(t, args(2).toInt, Path.of(args(3)))
      case "train" =>
        // OKAY_TEACH_BALANCE=1: each meaning cut to the median count (Balance, §7.11)
        val all = cases(t, Path.of(args(2)))
        val used = if sys.env.get("OKAY_TEACH_BALANCE").contains("1") then okay.teach.Balance.toMedian(all) else all
        OnGpu.train(t.domain, t.data, used, args.lift(5).getOrElse(model),
        Path.of(args(3)), args.lift(4).fold(2)(_.toInt),
        seed = 5 + sys.env.get("OKAY_TEACH_SEED").fold(0)(_.toInt))
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
          run(Llm.on(teacher)(gpu.on(okay.teach.Teaching.drive[Lesson + Llm](start,
            okay.teach.Teaching.recorded(okay.teach.Teaching.byModel, decisions(out, "35B"))))))
        else gpu.run(okay.teach.Teaching.drive[Lesson](start,
          okay.teach.Teaching.recorded(okay.teach.Teaching.standard, decisions(out, "standard"))))
        Files.writeString(out.resolve("sigma.txt"), end.said + "\n\nhistory:\n" + end.history.mkString("\n"))
        println(end.said)
      case "calibrate" =>
        calibrate(t, Path.of(args(2)), Path.of(args(3)), Path.of(args(4)), args.lift(5).fold(0.05)(_.toDouble))
      case "all" => all(t, args(2).toInt, args(3).toInt, Path.of(args(4)),
        Path.of(args.lift(5).getOrElse(s"runs/${args(1)}")))
      case other => sys.error(s"no step $other")

  private val model = "mlx-community/Qwen3-0.6B-4bit"

  /** each decision of a teacher as a line of `<out>/decisions.jsonl`: the state as the teacher read it, the actions
   * allowed, the one chosen, and by whom (SPRINT T7: a small teacher is taught on these) */
  def decisions(out: Path, by: String): (okay.teach.Sigma, okay.teach.Action) => Unit = (s, a) =>
    import okay.teach.{Action, TeacherSpace}
    val line = Json.obj("by" -> Json.Str(by), "state" -> Json.Str(s.said),
      "allowed" -> Json.Arr(TeacherSpace.allowed(s).map(x => Json.Str(Action.said(x)))),
      "chosen" -> Json.Str(Action.said(a))).render + "\n"
    Files.createDirectories(out)
    Files.writeString(out.resolve("decisions.jsonl"), line, java.nio.file.StandardOpenOption.CREATE,
      java.nio.file.StandardOpenOption.APPEND)

  def write[D, T](t: Taught[D, T], n: Int, out: Path, seed: Long = 0): (Int, Int) =
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      // WRITER_MODEL: who writes the questions, apart from who drives (TEACHER_MODEL) — T21
      sys.env.get("WRITER_MODEL").getOrElse(env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit")))
    val written = t.meanings.zipWithIndex.map { (m, i) =>
      // (isolated: `write` runs inside a lesson's handler too, a run of its own — okay-scala's NestedRun otherwise)
      val w = runIsolated(Llm.on(teacher)(Teacher.write[D, T, Llm](t.domain, t.data, m, n, t.about, seed + i.toLong)))
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
    // a book can be repaired by merging two sections (T19): each merge writes `<out>/book.m<k>.txt` and its sealed set
    Files.createDirectories(dir)
    var current = file
    var merges = 0
    val merging = (a: String, b: String) =>
      val book = Book.parse(Files.readString(current))
      def id(w: String) = book.sections.find(s => s"topic: ${s.title}" == w).map(_.id)
        .getOrElse(sys.error(s"no section $w"))
      val (ia, ib) = (id(a), id(b))
      val merged = book.merge(ia, ib).getOrElse(sys.error(s"cannot merge $a and $b"))
      merges += 1
      val next = dir.resolve(s"book.m$merges.txt")
      Files.writeString(next, merged.render + "\n")
      current = next
      val (t2, sealed2) = BookLoop.load(next)
      val title = merged.section(s"$ia+$ib").get.title
      (t2, sealed2, (w: String) => if w == a || w == b then s"topic: $title" else w)
    val gpu = LessonOnGpu(t, name, dir, Map("held" -> sealedFile), merging = Some(merging))
    // 30 operations: a lesson that asks more twice and votes needs ~22 (ch. 6 with a 4B writer stopped at 20 one
    // step short of a release that passed)
    val start = okay.teach.Sigma(name, Sealed("held"), canMerge = true, budget = 30)
    val end = if driver == "model" then
      val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
        env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
      run(Llm.on(teacher)(gpu.on(okay.teach.Teaching.drive[Lesson + Llm](start,
            okay.teach.Teaching.recorded(okay.teach.Teaching.byModel, decisions(dir, "35B"))))))
    else if driver.startsWith("small:") then
      // the small teacher drives (T7); the questions are still written by the 35B over HTTP (Ask)
      val (choose, close) = SmallTeacher.chooser(Path.of(driver.stripPrefix("small:")))
      try gpu.run(okay.teach.Teaching.drive[Lesson](start,
          okay.teach.Teaching.recorded(choose, decisions(dir, "small")))) finally close()
    else gpu.run(okay.teach.Teaching.drive[Lesson](start,
          okay.teach.Teaching.recorded(okay.teach.Teaching.standard, decisions(dir, "standard"))))
    Files.writeString(dir.resolve("sigma.txt"), end.said + "\n\nhistory:\n" + end.history.mkString("\n"))
    println(end.said)

  /**
   * `contrast <book.txt> <run> [pairs] [n]` — THE CONTRAST LESSON, AN EXPERIMENT (SPRINT T17): a released run's adapter
   * reads questions the teacher writes APART (`valid.jsonl`, other seeds, never trained on); the meanings it confuses
   * there (never on the sealed set) get contrast questions — about A, easily taken for B (`Domain.contrast`); a
   * control gets as many plain questions of the same meanings. Two adapters trained (cases + contrast, cases +
   * control) and both read on the sealed set: counts only.
   */
  def contrast(file: Path, run: Path, pairs: Int, n: Int): Unit =
    val (t, sealedFile) = BookLoop.load(file)
    contrastOn(t, sealedFile, run, pairs, n)

  private def contrastOn[D, T](t: Taught[D, T], sealedFile: Path, run: Path, pairs: Int, n: Int): Unit =
    val teacher = HttpBackend(env("TEACHER_URL", "http://127.0.0.1:8090/v1"),
      env("TEACHER_MODEL", "mlx-community/Qwen3.6-35B-A3B-4bit"))
    def rows(cs: Vector[Case[T]]) = cs.map(c => Json.obj("language" -> Json.Str(c.language),
      "text" -> Json.Str(c.text), "meant" -> Json.Str(t.show(c.meant))).render)
    def written(m: T, k: Int, seed: Long, other: Option[T]) =
      runIsolated(Llm.on(teacher)(Teacher.write[D, T, Llm](t.domain, t.data, m, k, t.about, seed,
        other.map(t.show)))).kept
    def once(p: Path)(make: => Vector[Case[T]]): Vector[Case[T]] =
      if !Files.exists(p) then Files.write(p, rows(make).asJava)
      cases(t, p)
    def readAll(adapter: Path, cs: Vector[Case[T]]) =
      val gpu = OnGpu.Gpu(model, Some(adapter))
      try OnGpu.read(t.domain, t.data, cs, gpu) finally gpu.close()
    def train(name: String, cs: Vector[Case[T]]): Path =
      val dir = run.resolve(name)
      if !Files.exists(dir.resolve("reader.adapter.safetensors")) then
        OnGpu.train(t.domain, t.data, cs, model, dir, 2)
      dir.resolve("reader.adapter.safetensors")
    val trained = cases(t, run.resolve("cases.jsonl"))
    val adapter = run.resolve(s"adapter-${trained.size}/reader.adapter.safetensors")
    val sealedCases = cases(t, sealedFile)
    val valid = once(run.resolve("valid.jsonl"))(t.meanings.zipWithIndex.flatMap((m, i) => written(m, 2, 7000L + i,
      None)))
    val seen = readAll(adapter, valid)
    val confused = seen.filterNot(_.right).flatMap(r => r.meaning.map(r.c.meant -> _)).groupBy(identity)
      .map((k, v) => k -> v.size).toVector.filter(_._2 >= 2).sortBy(-_._2).take(pairs)
    println(s"valid: ${valid.size} questions apart, right ${seen.count(_.right)}; confused there: " +
      confused.map { case ((a, b), k) => s"${t.show(a)} as ${t.show(b)} $k" }.mkString(", "))
    // BOTH SIDES of each pair (§7.10: one side only shifted the reader toward it — ch. 3 fell from 71 to 39): A in
    // contrast with B and B with A; the control as many plain questions of both
    val sides = confused.map(_._1).flatMap((a, b) => Vector(a -> b, b -> a)).distinct
    val contrastCases = once(run.resolve("contrast-both.jsonl"))(sides.zipWithIndex.flatMap { case ((a, b), i) =>
      written(a, n, 8000L + i, Some(b)) })
    val controlCases = once(run.resolve("control-both.jsonl"))(sides.zipWithIndex.flatMap { case ((a, _), i) =>
      written(a, n, 9000L + i, None) })
    println(s"contrast ${contrastCases.size}, control ${controlCases.size} questions")
    for (name, extra) <- Vector("adapter-contrast-both" -> contrastCases, "adapter-control-both" -> controlCases) do
      val reads = readAll(train(name, trained ++ extra), sealedCases)
      println(s"$name (${trained.size} + ${extra.size}): sealed right ${reads.count(_.right)} of ${reads.size}, " +
        s"in two ${reads.count(_.inTwo)}")
    val before = readAll(adapter, sealedCases)
    println(s"before (${trained.size}): sealed right ${before.count(_.right)}, in two ${before.count(_.inTwo)}")

  /**
   * `merge <book.txt> <a> <b> <out.txt> [<cases.jsonl> <out-cases.jsonl>]` — A REPAIR OF THE SPACE (SPRINT T18): the
   * book with sections a and b one meaning (`Book.merge`), and the teacher's cases relabelled to it — every other step
   * (train, measure, book) then runs on the merged book unchanged
   */
  def merge(file: Path, a: String, b: String, out: Path, cases: Option[(Path, Path)]): Unit =
    val book = Book.parse(Files.readString(file))
    val merged = book.merge(a, b).getOrElse(sys.error(s"no sections $a and $b in $file"))
    Files.writeString(out, merged.render + "\n")
    val title = merged.sections.find(_.id == s"$a+$b").get.title
    val old = Set(a, b).flatMap(book.section).map(s => s"topic: ${s.title}")
    cases.foreach { (in, to) =>
      val rows = Files.readAllLines(in).asScala.toVector.filterNot(_.isBlank).map { l =>
        val j = Json.fromText(l)
        val meant = j("meant").string
        Json.obj("language" -> j("language"), "text" -> j("text"),
          "meant" -> Json.Str(if old(meant) then s"topic: $title" else meant)).render }
      Files.write(to, rows.asJava) }
    println(s"$out: ${merged.sections.size} sections ($a+$b: $title)")
  /**
   * `measure <book.txt|domain|spec.json> <sealed.tsv|-> <adapter|-> <cases.jsonl> [out]` — A MEASURE ALONE (SPRINT
   * T16): an adapter already made, read plain on the sealed set (`-` for a book's own), and the baseline learned from
   * its cases: counts only, right first and right among the first two; a line each in `out`'s trace
   */
  def measure(what: String, sealedArg: String, adapter: String, casesFile: Path, out: Option[String]): Unit =
    val (t, sealedFile) =
      if what.endsWith(".txt") then BookLoop.load(Path.of(what))
      else ((if what.endsWith(".json") then fromSpec(Path.of(what)) else domains(what)), Path.of(sealedArg))
    val held = if sealedArg == "-" then sealedFile else Path.of(sealedArg)
    if adapter.contains(',') then vote(t, held, adapter.split(',').toVector.map(Path.of(_)))
    else
      val gpu = LessonOnGpu(t, name(what), Path.of(out.getOrElse("runs/measure")), Map("held" -> held))
      val on = Sealed("held")
      val program = for
        taught <- Lesson.measure[Lesson](on, Option.when(adapter != "-")(Ref(adapter)), false)
        words <- Lesson.baseline[Lesson](on, Ref(casesFile.toString))
      yield Vector(taught, words)
      gpu.run(program).foreach(m => println(m.said))

  /** several adapters (`a,b,c`) read the sealed set and vote (Vote, specs/teach.md §7.8): counts only */
  private def vote[D, T](t: Taught[D, T], held: Path, adapters: Vector[Path]): Unit =
    val sealedCases = cases(t, held)
    val reads = adapters.map { a =>
      val gpu = OnGpu.Gpu(model, Some(a))
      try OnGpu.read(t.domain, t.data, sealedCases, gpu) finally gpu.close() }
    reads.zip(adapters).foreach((rs, a) =>
      println(s"  ${a.getParent.getParent.getFileName}/${a.getParent.getFileName}: right ${rs.count(_.right)}, in two ${rs.count(_.inTwo)}"))
    val voted = sealedCases.indices.map(i => Vote.of(reads.map(rs => (rs(i).meaning, rs(i).second))))
    val right = sealedCases.indices.count(i => voted(i).first.contains(sealedCases(i).meant))
    val two = sealedCases.indices.count(i => voted(i).inTwo(sealedCases(i).meant))
    val all = sealedCases.indices.filter(i => voted(i).votes == 2 * adapters.size)
    println(s"voted by ${adapters.size}: right $right of ${sealedCases.size}, in two $two; all agreed on " +
      s"${all.size}, right ${all.count(i => voted(i).first.contains(sealedCases(i).meant))}")
