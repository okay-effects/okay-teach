package okay.teach

import okay.scala3.*

/** a thing a lesson made or was given — cases, an adapter, a released competence — by its id (a path to a handler) */
final case class Ref(id: String)

/**
 * THE SEALED SET, BY ID ONLY (theory L1): a lesson program holds it, passes it to `Measure`, and can do nothing else
 * with it — no operation returns its texts. The handler of `Measure` alone opens it.
 */
final case class Sealed(id: String)

/** questions asked of the teacher: the cases kept (by the space's laws) and how many were refused */
final case class Asked(cases: Ref, kept: Int, refused: Int)

/** a measure as a lesson sees it: aggregates only — right of all, by language, and the bar at the lesson's ε */
final case class Measured(what: String, right: Int, of: Int, byLanguage: Map[String, (Int, Int)],
    bar: Option[Bar.Chosen]):
  def said: String =
    val langs = byLanguage.toVector.sortBy(_._1).map { case (l, (r, n)) => s"$l $r/$n" }.mkString(", ")
    s"$what: right $right of $of ($langs); ${bar.fold("no bar holds")(_.said)}"

/**
 * THE LESSON AS AN EFFECT (theory §2): each operation a decision of the teacher, each handler a tool. A lesson is a
 * program over it; who chooses the operations (this file's `standard`, a person, a teacher model) is not the
 * handler's business, and what the handlers do (the 35B, MLX, files) is not the program's.
 */
enum Lesson[X]:
  /** the teacher writes `n` questions a language for every meaning; the space's laws keep or refuse each */
  case Ask(n: Int) extends Lesson[Asked]
  /** an adapter trained on the cases */
  case Train(cases: Ref, epochs: Int) extends Lesson[Ref]
  /** the reader (an adapter or none; plain or calibrated) on the sealed set: aggregates only */
  case Measure(on: Sealed, adapter: Option[Ref], calibrated: Boolean) extends Lesson[Measured]
  /** the competence released: the adapter and the measure it was released on */
  case Release(adapter: Ref, measured: Measured) extends Lesson[Ref]
  /** no engine of the catalog covers the domain, and what is missing */
  case Uncovered(why: String) extends Lesson[Unit]

object Lesson:
  def ask[R[_]](n: Int)(using In[Lesson, R]): Asked ! R = perform[Lesson](Ask(n))
  def train[R[_]](cases: Ref, epochs: Int)(using In[Lesson, R]): Ref ! R = perform[Lesson](Train(cases, epochs))
  def measure[R[_]](on: Sealed, adapter: Option[Ref], calibrated: Boolean)(using In[Lesson, R]): Measured ! R =
    perform[Lesson](Measure(on, adapter, calibrated))
  def release[R[_]](adapter: Ref, m: Measured)(using In[Lesson, R]): Ref ! R = perform[Lesson](Release(adapter, m))
  def uncovered[R[_]](why: String)(using In[Lesson, R]): Unit ! R = perform[Lesson](Uncovered(why))

  /** what a lesson came to: its questions, its measures, the competence released if the gate passed */
  final case class Outcome(asked: Asked, measures: Vector[Measured], released: Option[Ref])

  /**
   * THE GATE (theory L4): a candidate replaces the baseline only when it has more right and, at its bar, no more
   * wrong among the answered than the baseline at its own (a baseline with no bar counts all it got wrong).
   */
  def passes(candidate: Measured, baseline: Measured): Boolean =
    def wrong(m: Measured) = m.bar.fold(m.of - m.right)(_.wrong)
    candidate.right > baseline.right && wrong(candidate) <= wrong(baseline)

  /**
   * THE STANDARD LESSON (theory §2; `Teach all`): ask, train, measure the reader four ways — untaught and taught,
   * plain and calibrated — and release the best taught one if it passes the gate against the best untaught
   */
  def standard[R[_]](on: Sealed, n: Int, epochs: Int)(using In[Lesson, R]): Outcome ! R =
    val ways = Vector(false, true)
    def each[A](xs: Vector[A])(f: A => Measured ! R): Vector[Measured] ! R =
      xs.foldLeft(pure[Vector[Measured], R](Vector.empty))((acc, x) => acc.flatMap(done => f(x).map(done :+ _)))
    for
      asked <- ask(n)
      adapter <- train(asked.cases, epochs)
      untaught <- each(ways)(c => measure(on, None, c))
      taught <- each(ways)(c => measure(on, Some(adapter), c))
      best = taught.maxBy(_.right)
      released <- if passes(best, untaught.maxBy(_.right)) then release(adapter, best).map(Some(_))
        else pure[Option[Ref], R](None)
    yield Outcome(asked, untaught ++ taught, released)
