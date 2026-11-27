package okay.teach

import okay.datalog.{Program, Rule}
import okay.llm.{Ask, Llm, Sampling}
import okay.scala3.*

/**
 * A THEORY WRITTEN BY CHOICES, JUDGED (specs/teach.md §5): what a big model cannot write whole, it builds in
 * steps it can do. (1) PLAN: from the lesson and the vocabulary, the rules wanted, each in plain words, one a line —
 * prose, which a model writes well. (2) RULES: each described rule read in `RuleSpace` by `Choose` — the model
 * picks a head, arguments, literals among the vocabulary's options; it cannot misspell a predicate or leave a rule
 * unsafe. (3) JUDGE: the program run on the book's worked exercises (the caller's `judge`: how many pass, and in
 * words what failed); a failure goes back as words to the plan, and the rules are chosen again — at most `rounds`
 * times, the best program by passed kept.
 */
object RuleTeacher:
  /** one round: the plan's lines, the rules chosen (a description none closed for is left out), the judge's word */
  final case class Round(plan: Vector[String], rules: Vector[Rule], unread: Vector[String], passed: Int, of: Int,
      told: String)
  /** what a judge says of a program: passed of all, and in words what failed (empty when all passed) */
  final case class Verdict(passed: Int, of: Int, told: String)

  def planTask(lesson: String, v: Vocabulary, feedback: Option[String]): String =
    val preds = v.names.map(p => s"$p/${v.predicates(p)}").mkString(", ")
    s"""$lesson
       |
       |The predicates (name/arity): $preds
       |${if v.constants.isEmpty then "" else s"The constants: ${v.constants.mkString(", ")}"}
       |
       |List the Datalog rules this lesson needs, each in plain words naming its predicates — what follows from
       |what. One rule a line, beginning "rule: ". No code.
       |${feedback.fold("")(f => s"\nThe last theory failed the book's exercises: $f\nChange the rules so they " +
         "pass.")}"""
      .stripMargin

  /** the described rules of a plan reply */
  def plan(reply: String): Vector[String] =
    reply.linesIterator.map(_.trim.stripPrefix("- ")).collect {
      case l if l.toLowerCase.startsWith("rule:") => l.drop(5).trim }.filter(_.nonEmpty).toVector

  def ruleTask(lesson: String): String =
    s"""$lesson
       |
       |Build the Datalog rule described below, part by part: its head, the head's arguments (variables X, Y, Z …
       |or constants), how many literals its body has, then each literal. A variable of the head must appear in a
       |positive literal of the body.""".stripMargin

  def round[R[_]](lesson: String, v: Vocabulary, feedback: Option[String], judge: Program => Verdict, seed: Long)(
      using In[Llm, R]): Round ! R =
    val ask = Ask(Vector(Teacher.message("user", planTask(lesson, v, feedback))), maxTokens = 3000,
      sampling = Sampling(temperature = Some(0.3), seed = Some(seed)))
    for
      planned <- Llm.complete[R](ask).map(d => plan(d.reply.content))
      read <- Read.each(planned)(d => Choose.read[Vocabulary, Rule, R](RuleSpace, v, d, ruleTask(lesson)).map(d -> _))
    yield
      val rules = read.flatMap(_._2.meaning).distinct
      val verdict = judge(Program(rules))
      Round(planned, rules, read.collect { case (d, r) if r.meaning.isEmpty => d }, verdict.passed, verdict.of,
        verdict.told)

  /** rounds until all pass or `rounds` are spent; each after the first told what failed */
  def teach[R[_]](lesson: String, v: Vocabulary, judge: Program => Verdict, rounds: Int)(
      using In[Llm, R]): Vector[Round] ! R =
    def go(done: Vector[Round]): Vector[Round] ! R =
      if done.size >= rounds || done.lastOption.exists(r => r.passed == r.of) then pure(done)
      else
        val told = done.lastOption.map(r => r.told + (if r.unread.isEmpty then "" else
          s" These described rules could not be built from the predicates: ${r.unread.mkString("; ")}."))
        round(lesson, v, told, judge, done.size.toLong).flatMap(r => go(done :+ r))
    go(Vector.empty)

  /** the best round's program, by passed */
  def best(rounds: Vector[Round]): Option[Program] = rounds.maxByOption(_.passed).map(r => Program(r.rules))

  /** a sequence of programs, as `Read.each` does for `Read` — kept here so the teacher needs only `Llm` */
  private object Read:
    def each[A, B, R[_]](xs: Vector[A])(f: A => B ! R): Vector[B] ! R =
      xs.foldLeft(pure[Vector[B], R](Vector.empty))((acc, x) => acc.flatMap(done => f(x).map(done :+ _)))
