package okay.teach

import okay.frames.{Choice, Hole, Space}
import okay.scala3.*

/**
 * THE LESSON'S STATE, Σ (theory §2, §7): what exists so far — the questions asked, the adapter, the measures — and
 * the budget left. What a teacher sees before choosing the next operation; never a sealed text (theory L1).
 */
final case class Sigma(domain: String, sealedSet: Sealed, asked: Option[Asked] = None, adapter: Option[Ref] = None,
    measures: Vector[Measured] = Vector.empty, released: Option[Ref] = None, budget: Int = 12,
    history: Vector[String] = Vector.empty, trainedOn: Int = 0,
    earlier: Vector[(Int, Int, Int)] = Vector.empty, seeds: Int = 1, checked: Option[Checked] = None,
    canMerge: Boolean = false, merged: Vector[(String, String)] = Vector.empty):
  def measured(what: String): Option[Measured] = measures.find(_.what == what)
  /** the state in words, for a teacher model */
  def said: String =
    Vector(s"Domain: $domain.",
      asked.fold("No training questions yet.")(a => s"Training questions: ${a.kept} kept, ${a.refused} refused."),
      adapter.fold("No adapter trained yet.")(_ => s"An adapter is trained, on $trainedOn of the questions." +
        (if seeds > 1 then s" ($seeds seeds, read as a vote)" else "")),
      if merged.isEmpty then "" else "Merged into one meaning: " + merged.map((a, b) => s"$a + $b").mkString(", ") + ".",
      checked.fold("")(c => s"The adapter checked: ${c.said}."),
      if measures.isEmpty then "Nothing measured yet." else "Measured on the sealed set:\n" +
        measures.map("  " + _.said).mkString("\n"),
      if earlier.isEmpty then "" else "Earlier adapters (questions → taught right, the baseline right): " +
        earlier.map((n, t, b) => s"$n → $t vs $b").mkString(", ") + ".",
      released.fold("Nothing released. " + Sigma.toRelease(this))(_ => "A competence is released."),
      s"Operations left in the budget: $budget.").filter(_.nonEmpty).mkString("\n")

/** an operation a teacher may choose, with its arguments (theory §2), as the option it is offered by */
enum Action:
  case Ask(n: Int)
  case Train(epochs: Int, seeds: Int = 1)
  case Check
  case Merge(a: String, b: String)
  case Measure(taught: Boolean, calibrated: Boolean)
  case MeasureBaseline
  case Release
  case Stop

object Action:
  def said(a: Action): String = a match
    case Ask(n) => s"ask the teacher for $n questions a language for every meaning"
    case Train(e, k) => s"train an adapter on the questions, $e epochs" + (if k > 1 then s", $k seeds voting" else "")
    case Check => "check the adapter on questions the teacher writes apart (finds meanings it cannot split)"
    case Merge(a, b) => s"merge $a and $b into one meaning (the reader cannot split them; train again after)"
    case Measure(t, c) => s"measure the ${if t then "taught" else "untaught"} reader, ${if c then "calibrated" else
      "plain"}, on the sealed set"
    case MeasureBaseline => "measure the baseline (a word search learned from the training questions) on the sealed set"
    case Release => "release the best taught reader (if it passes the gate)"
    case Stop => "stop: the lesson is done or cannot do better"
  def what(taught: Boolean, calibrated: Boolean): String =
    s"${if taught then "taught" else "untaught"}, ${if calibrated then "calibrated" else "plain"}"

/**
 * THE TEACHER'S SPACE (theory §7, level L1): one hole, `action`, its options the operations Σ allows — the laws of
 * the process as preconditions: ask only before there are questions (or to ask more after training), train only on
 * questions, measure what is not yet measured, release only what passes the gate (L4) and only once, stop always.
 * A teacher cannot choose an operation out of order; it can only choose badly, and the trace shows it.
 */
object TeacherSpace extends Space[Sigma, Action]:
  def name: String = "teacher"

  def allowed(s: Sigma): Vector[Action] =
    if s.budget <= 0 || s.released.nonEmpty then Vector(Action.Stop)
    else
      // ask first; or ask MORE when the taught reader is measured and does not pass the gate (the corpus repaired)
      // (once an adapter: the questions asked since must be trained on before asking again — run c asked twice)
      val failed = s.adapter.nonEmpty && s.measures.exists(_.what.startsWith("taught")) &&
        s.measured(Teaching.baselineWhat).nonEmpty && best(s).isEmpty && s.asked.forall(_.kept == s.trainedOn)
      val ask = if s.asked.isEmpty then Vector(4, 8, 16).map(Action.Ask(_))
        else if failed then Vector(8, 16).map(Action.Ask(_)) else Vector.empty
      // train when there are questions the adapter was not trained on
      // 2 or 3 epochs: one under-trains (the book loop, 2026-11-27: 880 questions × 1 epoch → 58 of 134; 352 × 2 → 80)
      // several seeds voting (§7.8, §7.11: one adapter swings ±10 by seed) — also again on the same questions, once
      val train = if s.asked.exists(_.kept > s.trainedOn) then
          Vector(Action.Train(2), Action.Train(3), Action.Train(2, 3), Action.Train(3, 3))
        else if s.adapter.nonEmpty && s.seeds == 1 && s.measures.exists(_.what.startsWith("taught")) then
          Vector(Action.Train(2, 3))
        else Vector.empty
      // check on questions apart once an adapter; merge a pair the check found (a repair of the space, F1)
      val check = Option.when(s.adapter.nonEmpty && s.checked.isEmpty)(Action.Check).toVector
      val merge = if !s.canMerge then Vector.empty
        else s.checked.toVector.flatMap(_.pairs).map((a, b, _) => Action.Merge(a, b))
      val measure = for t <- Vector(false, true) if !t || s.adapter.nonEmpty; c <- Vector(false, true)
        if s.measured(Action.what(t, c)).isEmpty yield Action.Measure(t, c)
      val baseline = Option.when(s.asked.exists(_.kept > 0) && s.measured(Teaching.baselineWhat).isEmpty)(
        Action.MeasureBaseline).toVector
      // L8: released only once checked on questions apart (the 35B released ch. 3 at 71 without looking; the check
      // would have found the pair whose merge gives 83)
      val release = Option.when(best(s).nonEmpty && s.checked.nonEmpty)(Action.Release).toVector
      ask ++ train ++ check ++ merge ++ measure ++ baseline ++ release :+ Action.Stop

  /** the best taught measure that passes the gate against the best untaught (L4) and the baseline (L7) */
  def best(s: Sigma): Option[Measured] =
    val taught = s.measures.filter(_.what.startsWith("taught"))
    val untaught = s.measures.filter(_.what.startsWith("untaught"))
    for t <- taught.maxByOption(_.right); u <- untaught.maxByOption(_.right); b <- s.measured(Teaching.baselineWhat)
      if Lesson.passes(t, u) && Lesson.passes(t, b) yield t

  def next(s: Sigma, text: String, chosen: Vector[Choice]): Option[Hole] =
    Option.when(chosen.isEmpty)(Hole("action", allowed(s).map(Action.said)))
  def assemble(s: Sigma, chosen: Vector[Choice]): Option[Action] =
    chosen.headOption.flatMap(c => allowed(s).find(Action.said(_) == c.option))
  def path(s: Sigma, a: Action): Vector[Choice] = Vector(Choice("action", Action.said(a)))
  def say(s: Sigma, a: Action, language: String): String = Action.said(a)
  def parse(s: Sigma, words: String, language: String): Option[Action] = allowed(s).find(Action.said(_) == words)

/**
 * A LESSON DRIVEN BY A TEACHER (theory §7): Σ → the teacher chooses an action among those allowed → the Lesson's
 * operation → Σ again, until it stops or the budget is spent. Who chooses is a parameter: a model (`byModel`, any
 * `Llm` reading the teacher's space by `Choose`) or a fixed policy (`standard`). Each step is recorded in Σ's history:
 * the trace a teacher model learns from.
 */
object Teaching:
  type Chooser[R[_]] = Sigma => Action ! R
  val baselineWhat = "baseline (words)"

  def drive[R[_]](start: Sigma, choose: Chooser[R])(using In[Lesson, R]): Sigma ! R =
    def step(s: Sigma): Sigma ! R =
      choose(s).flatMap { a =>
        val chosen = if TeacherSpace.allowed(s).contains(a) then a else Action.Stop
        val spent = s.copy(budget = s.budget - 1, history = s.history :+ Action.said(chosen))
        chosen match
          case Action.Stop => pure(spent)
          case Action.Ask(n) => Lesson.ask(n).flatMap(x => step(spent.copy(asked = Some(x))))
          case Action.Train(e, k) =>
            // a new adapter: the taught measures and the baseline (learned from the questions) are taken again;
            // the last adapter's numbers kept as the trend, so a teacher sees when more questions stop helping
            val last = for t <- spent.measures.filter(_.what.startsWith("taught")).maxByOption(_.right)
              b <- spent.measured(baselineWhat) yield (spent.trainedOn, t.right, b.right)
            Lesson.train(s.asked.get.cases, e, k).flatMap(x => step(spent.copy(adapter = Some(x),
              trainedOn = s.asked.get.kept, earlier = spent.earlier ++ last, seeds = k, checked = None,
              measures = spent.measures.filter(_.what.startsWith("untaught")))))
          case Action.Check => Lesson.check(s.adapter.get).flatMap(c => step(spent.copy(checked = Some(c))))
          case Action.Merge(a, b) =>
            // a new space: the questions relabelled, everything measured or trained before is of the old one
            Lesson.merge(a, b).flatMap(x => step(spent.copy(asked = Some(x), adapter = None, trainedOn = 0, seeds = 1,
              measures = Vector.empty, checked = None, earlier = Vector.empty, merged = spent.merged :+ (a -> b))))
          case Action.Measure(t, c) =>
            Lesson.measure(s.sealedSet, if t then s.adapter else None, c).flatMap(m =>
              step(spent.copy(measures = spent.measures :+ m.copy(what = Action.what(t, c)))))
          case Action.MeasureBaseline =>
            Lesson.baseline(s.sealedSet, s.asked.get.cases).flatMap(m =>
              step(spent.copy(measures = spent.measures :+ m.copy(what = baselineWhat))))
          case Action.Release =>
            Lesson.release(s.adapter.get, TeacherSpace.best(s).get).flatMap(x => step(spent.copy(released = Some(x))))
      }
    step(start)

  /** the fixed policy: the standard lesson's order (ask 8, train 2, the four measures, release, stop) */
  def standard[R[_]]: Chooser[R] = s =>
    val order = Vector(Action.Ask(8), Action.Train(2), Action.Measure(false, false), Action.Measure(false, true),
      Action.Measure(true, false), Action.Measure(true, true), Action.MeasureBaseline, Action.Check, Action.Release,
      Action.Stop)
    pure(order.find(TeacherSpace.allowed(s).contains).getOrElse(Action.Stop))

  val task: String =
    "You teach a small language model to read questions of a domain: you ask a teacher for training questions, " +
      "train an adapter, measure readers on a sealed set you never see, and release a reader only when it is " +
      "better than the untaught one and the baseline; when it is not, you may ask for more questions and train " +
      "again — and when more questions have stopped helping (see the earlier adapters), stop. " +
      "An adapter can be checked on questions written apart: when it cannot split two meanings, merge them and train " +
      "again; several seeds voting are steadier than one. " +
      "Here is the state of the lesson. Choose the next operation."

  /** a model chooses (any `Llm`, by numbered choices — `Choose`); an answer that is no option stops the lesson */
  def byModel[R[_]](using In[okay.llm.Llm, R]): Chooser[R] = s =>
    Choose.read[Sigma, Action, R](TeacherSpace, s, s.said, task).map(_.meaning.getOrElse(Action.Stop))

object Sigma:
  /** what a release still needs, in words: the measures the gate compares (L4, L7) and the taught reader beating them
   * (the 35B driving its first lesson stopped short of it: the state never said what release needed) */
  def toRelease(s: Sigma): String =
    val missing = Vector(
      Option.when(s.adapter.isEmpty)("an adapter trained"),
      Option.when(!s.measures.exists(_.what.startsWith("taught")))("a taught reader measured"),
      Option.when(!s.measures.exists(_.what.startsWith("untaught")))("an untaught reader measured"),
      Option.when(s.measured(Teaching.baselineWhat).isEmpty)("the baseline measured")).flatten
    if missing.nonEmpty then s"A release needs first: ${missing.mkString(", ")}."
    else if TeacherSpace.best(s).isEmpty then
      "A release needs the best taught reader to beat the untaught one and the baseline: it does not yet — more " +
        "questions and training again may help."
    else if s.checked.isEmpty then "A release needs first: the adapter checked on questions apart (L8)."
    else if s.checked.exists(_.pairs.nonEmpty) && s.canMerge then
      "A release is possible now; but the check found meanings the reader cannot split — merging them and training " +
        "again (several seeds) raised a book chapter from 74 to 83 of 92."
    else "A release is possible now."
