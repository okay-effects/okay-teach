package okay.teach

import okay.frames.Laws
import okay.scala3.*

/** THE TEACHER'S SPACE AND THE DRIVEN LESSON: the laws as preconditions, a fixed policy's trace, a bad chooser
 * stopped by the laws, every allowed action reachable */
class TeachingSuite extends munit.FunSuite:
  private def m(what: String, right: Int, wrong: Int) =
    Measured(what, right, 100, Map.empty, Some(Bar.Chosen(1.0, right + wrong, wrong, 100)))
  private val start = Sigma("tax-rules", Sealed("held"))

  /** a lesson's operations answered by fakes: taught readers better than untaught */
  private def run(choose: Teaching.Chooser[Lesson]): Sigma =
    okay.scala3.run(handle(Teaching.drive[Lesson](start, choose),
      Handler.tail[Lesson, Pure, Sigma, Sigma](pure)([X] => (op: Lesson[X]) => op match
        case Lesson.Ask(n) => Asked(Ref("cases"), 25 * 4 * n, 3)
        case Lesson.Train(_, _) => Ref("adapter")
        case Lesson.Measure(_, a, c) => if a.isEmpty then m("u", if c then 30 else 16, 70) else m("t", 150, 8)
        case Lesson.Baseline(_, _) => m("b", 100, 100)
        case Lesson.Release(a, _) => Ref(s"released ${a.id}")
        case Lesson.Uncovered(_) => ())))

  test("the laws as preconditions: what each state allows"):
    assertEquals(TeacherSpace.allowed(start).collect { case a: Action.Ask => a }.size, 3)
    assert(!TeacherSpace.allowed(start).exists(_.isInstanceOf[Action.Train]), "no training without questions")
    assert(!TeacherSpace.allowed(start).contains(Action.Release), "no release without a measure that passes")
    assert(!TeacherSpace.allowed(start).contains(Action.Measure(true, false)), "no taught measure untrained")
    assertEquals(TeacherSpace.allowed(start.copy(budget = 0)), Vector(Action.Stop))

  test("the fixed policy: ask, train, four measures, the baseline, release, stop — the trace in Σ"):
    val s = run(Teaching.standard[Lesson])
    assertEquals(s.history.size, 9)
    assertEquals(s.released, Some(Ref("released adapter")))
    assertEquals(s.measures.map(_.what), Vector("untaught, plain", "untaught, calibrated", "taught, plain",
      "taught, calibrated", Teaching.baselineWhat))

  test("a chooser that asks for what the laws do not allow is stopped, not obeyed"):
    val s = run(_ => pure(Action.Release))
    assertEquals(s.history, Vector(Action.said(Action.Stop)))
    assertEquals(s.released, None)

  test("every allowed action is reached by choosing, and said back"):
    for st <- Vector(start, start.copy(asked = Some(Asked(Ref("c"), 10, 0)))); a <- TeacherSpace.allowed(st) do
      assertEquals(Laws.reach(TeacherSpace)(st, st.said, a), None)
      assertEquals(Laws.roundTrip(TeacherSpace)(st, a, Seq("en")), None)
