package okay.teach

import okay.llm.{Ask, Llm, Sampling}
import okay.scala3.*
import okay.teach.equations.{Law, Theory, Units, Variable}

/**
 * A THEORY OF EQUATIONS WRITTEN BY CHOOSING, JUDGED (specs/equations.md §4) — `RuleTeacher`'s method for the
 * equations engine: (1) PLAN: the teacher declares the variables a chapter needs ("variable: v — final speed [m/s]",
 * the unit read by the engine's catalog) and lists the laws in words; (2) LAWS: each built by `Choose` in
 * `LawSpace` — it cannot write a law whose sides differ in dimension; (3) JUDGE: the theory solves the training
 * worked examples; what failed goes back in words; rounds until all pass, the best kept.
 */
object LawTeacher:
  final case class Round(variables: Vector[Variable], plan: Vector[String], laws: Vector[Law], unread: Vector[String],
      passed: Int, of: Int, told: String)

  /** a theory judged on worked examples: how many agree, and in words what did not */
  def judge(t: Theory, examples: Vector[Worked]): RuleTeacher.Verdict =
    val results = examples.map(e => e -> t.solve(e.givens, e.asked))
    val failed = results.collect {
      case (e, Left(why)) => s"example ${e.number}: $why"
      case (e, Right((got, _))) if !e.agrees(got) =>
        s"example ${e.number}: the book says ${e.answer.value.show()} for ${e.asked}, the laws gave ${got.value.show()}" }
    RuleTeacher.Verdict(examples.size - failed.size, examples.size, failed.take(3).mkString("; "))

  def planTask(lesson: String, known: Vector[Variable], feedback: Option[String]): String =
    s"""$lesson
       |
       |${if known.isEmpty then "" else "Variables already declared: " +
         known.map(v => s"${v.name} (${v.about})").mkString(", ")}
       |Declare each variable the laws need, one a line: "variable: name — what it is [unit]" (a short lowercase
       |name, an SI unit such as m, s, m/s, m/s^2, kg, N, J). Then list the laws, each in plain words naming its
       |variables, one a line beginning "law: ". No formulas in symbols.
       |${feedback.fold("")(f => s"\nThe last theory failed the book's worked examples: $f\nChange the laws.")}"""
      .stripMargin

  /** the variables a plan declares, with units the catalog reads */
  def variables(reply: String): Vector[Variable] =
    val one = """\s*-?\s*[Vv]ariable:\s*`?([a-z][a-z0-9_]*)`?\s*[—-]\s*(.*?)\s*\[(.+)\]\s*""".r
    reply.linesIterator.collect { case one(n, about, unit) => (n, about, Units.parse(unit)) }
      .collect { case (n, about, Some(u)) => Variable(n, about, u.dim) }.toVector.distinctBy(_.name)

  def laws(reply: String): Vector[String] =
    reply.linesIterator.map(_.trim.stripPrefix("- ")).collect {
      case l if l.toLowerCase.startsWith("law:") => l.drop(4).trim }.filter(_.nonEmpty).toVector

  def lawTask(lesson: String): String =
    s"""$lesson
       |
       |Build the law described below as an equation, part by part: the variable on its left, then the right side
       |as a tree — each part an operation (+, -, *, /, ^2 a square, ^-1 a reciprocal, sqrt), a variable or a
       |constant, an operation's operands following it.""".stripMargin

  def round[R[_]](lesson: String, known: Vector[Variable], examples: Vector[Worked], feedback: Option[String],
      seed: Long)(using In[Llm, R]): Round ! R =
    val ask = Ask(Vector(Teacher.message("user", planTask(lesson, known, feedback))), maxTokens = 3000,
      sampling = Sampling(temperature = Some(0.3), seed = Some(seed)))
    for
      reply <- Llm.complete[R](ask).map(_.reply.content)
      vars = (known ++ variables(reply)).distinctBy(_.name)
      described = laws(reply)
      built <- described.foldLeft(pure[Vector[(String, Option[Law])], R](Vector.empty))((acc, d) =>
        acc.flatMap(done => Choose.read[LawWords, Law, R](LawSpace, LawWords(vars), d, lawTask(lesson))
          .map(r => done :+ (d -> r.meaning))))
    yield
      val made = built.flatMap(_._2).distinct.zipWithIndex.map((l, i) => l.copy(name = s"law${i + 1}"))
      val verdict = judge(Theory(vars, made), examples)
      Round(vars, described, made, built.collect { case (d, None) => d }, verdict.passed, verdict.of, verdict.told)

  def teach[R[_]](lesson: String, known: Vector[Variable], examples: Vector[Worked], rounds: Int)(
      using In[Llm, R]): Vector[Round] ! R =
    def go(done: Vector[Round]): Vector[Round] ! R =
      if done.size >= rounds || done.lastOption.exists(r => r.passed == r.of) then pure(done)
      else round(lesson, known, examples, done.lastOption.map(_.told), done.size.toLong).flatMap(r => go(done :+ r))
    go(Vector.empty)
