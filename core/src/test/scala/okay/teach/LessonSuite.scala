package okay.teach

import okay.scala3.*

/** THE LESSON ON FAKES: the standard lesson's operations in order, the gate, and the sealed set only measured */
class LessonSuite extends munit.FunSuite:
  private def m(what: String, right: Int, wrong: Int) =
    Measured(what, right, 100, Map.empty, Some(Bar.Chosen(1.0, right + wrong, wrong, 100)))

  /** a handler that records each operation and answers by the given measures */
  private def run(measures: Map[(Boolean, Boolean), Measured]): (Lesson.Outcome, Vector[String]) =
    var seen = Vector.empty[String]
    val out = okay.scala3.run(handle(Lesson.standard[Lesson](Sealed("held"), 8, 2),
      Handler.tail[Lesson, Pure, Lesson.Outcome, Lesson.Outcome](pure)([X] => (op: Lesson[X]) =>
        seen :+= op.toString
        op match
          case Lesson.Ask(n) => Asked(Ref("cases"), 10 * n, 1)
          case Lesson.Train(c, e) => Ref(s"adapter of ${c.id} × $e")
          case Lesson.Measure(_, a, c) => measures((a.nonEmpty, c))
          case Lesson.Release(a, _) => Ref(s"released ${a.id}")
          case Lesson.Uncovered(_) => ())))
    (out, seen)

  test("the standard lesson: ask, train, four measures on the sealed set, release when the gate passes"):
    val (out, seen) = run(Map((false, false) -> m("u", 16, 84), (false, true) -> m("uc", 30, 70),
      (true, false) -> m("t", 150, 10), (true, true) -> m("tc", 140, 12)))
    assertEquals(seen.take(2), Vector("Ask(8)", "Train(Ref(cases),2)"))
    assertEquals(seen.count(_.startsWith("Measure(Sealed(held)")), 4)
    assertEquals(out.released, Some(Ref("released adapter of cases × 2")))

  test("the gate refuses a taught reader with more right but more silent wrong"):
    val (out, seen) = run(Map((false, false) -> m("u", 60, 5), (false, true) -> m("uc", 50, 5),
      (true, false) -> m("t", 70, 20), (true, true) -> m("tc", 65, 30)))
    assertEquals(out.released, None)
    assert(!seen.exists(_.startsWith("Release")))
