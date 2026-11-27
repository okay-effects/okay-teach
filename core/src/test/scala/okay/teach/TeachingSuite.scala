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

  test("the state says what a release still needs (the 35B stopped short of the baseline without being told)"):
    val s = start.copy(asked = Some(Asked(Ref("c"), 10, 0)), adapter = Some(Ref("a")),
      measures = Vector(m("untaught, plain", 19, 37), m("taught, plain", 52, 4)))
    assert(s.said.contains("A release needs first: the baseline measured."), s.said)
    val ready = s.copy(measures = s.measures :+ m(Teaching.baselineWhat, 22, 34))
    assert(ready.said.contains("A release is possible now."), ready.said)

  test("a failed gate lets the teacher ask more and train again; a new adapter is measured again"):
    val failed = start.copy(asked = Some(Asked(Ref("c"), 32, 0)), adapter = Some(Ref("a")), trainedOn = 32,
      measures = Vector(m("untaught, plain", 44, 90), m("taught, plain", 50, 84), m(Teaching.baselineWhat, 53, 81)))
    assert(TeacherSpace.best(failed).isEmpty)
    assert(TeacherSpace.allowed(failed).contains(Action.Ask(16)), "ask more after a failed gate")
    assert(!TeacherSpace.allowed(failed).exists(_.isInstanceOf[Action.Train]), "nothing new to train on")
    val more = failed.copy(asked = Some(Asked(Ref("c"), 160, 0)))
    assert(TeacherSpace.allowed(more).contains(Action.Train(2)), "train again on the grown corpus")
    assert(!TeacherSpace.allowed(more).exists(_.isInstanceOf[Action.Ask]), "no asking again before training on the new")
    assert(failed.said.contains("more questions and training again may help"), failed.said)
    // driven: the trained-again adapter's measures replace the old ones
    var trainings = 0
    val s = okay.scala3.run(handle(Teaching.drive[Lesson](more, st => pure(
        if TeacherSpace.allowed(st).contains(Action.Train(2)) then Action.Train(2) else Action.Stop)),
      Handler.tail[Lesson, Pure, Sigma, Sigma](pure)([X] => (op: Lesson[X]) => op match
        case Lesson.Train(_, _) => trainings += 1; Ref("a2")
        case _ => sys.error(s"not expected: $op"))))
    assertEquals(trainings, 1)
    assertEquals(s.trainedOn, 160)
    assertEquals(s.measures.map(_.what), Vector("untaught, plain"))
    // the last adapter's numbers kept as the trend, and said
    assertEquals(s.earlier, Vector((32, 50, 53)))
    assert(s.said.contains("32 → 50 vs 53"), s.said)
