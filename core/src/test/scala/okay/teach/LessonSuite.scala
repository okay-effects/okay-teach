package okay.teach

import okay.scala3.*

/** THE LESSON ON FAKES: the standard lesson's operations in order, the gate, and the sealed set only measured */
class LessonSuite extends munit.FunSuite:
  private def m(what: String, right: Int, wrong: Int) =
    Measured(what, right, 200, Map.empty, Some(Bar.Chosen(1.0, right + wrong, wrong, 200)))

  /** a handler that records each operation and answers by the given measures */
  private def run(measures: Map[(Boolean, Boolean), Measured], words: Measured = m("b", 100, 60))
      : (Lesson.Outcome, Vector[String]) =
    var seen = Vector.empty[String]
    val out = okay.scala3.run(handle(Lesson.standard[Lesson](Sealed("held"), 8, 2),
      Handler.tail[Lesson, Pure, Lesson.Outcome, Lesson.Outcome](pure)([X] => (op: Lesson[X]) =>
        seen :+= op.toString
        op match
          case Lesson.Ask(n) => Asked(Ref("cases"), 10 * n, 1)
          case Lesson.Train(c, e, _) => Ref(s"adapter of ${c.id} × $e")
          case Lesson.Measure(_, a, c) => measures((a.nonEmpty, c))
          case Lesson.Baseline(_, _) => words
          case Lesson.Release(a, _) => Ref(s"released ${a.id}")
          case Lesson.Check(_) => Checked(0, 0, Vector.empty)
          case Lesson.Merge(_, _) => Asked(Ref("merged"), 0, 0)
          case Lesson.Uncovered(_) => ())))
    (out, seen)

  test("the standard lesson: ask, train, four measures on the sealed set, release when the gate passes"):
    val (out, seen) = run(Map((false, false) -> m("u", 16, 84), (false, true) -> m("uc", 30, 70),
      (true, false) -> m("t", 150, 10), (true, true) -> m("tc", 140, 12)))
    assertEquals(seen.take(2), Vector("Ask(8)", "Train(Ref(cases),2,1)"))
    assertEquals(seen.count(_.startsWith("Measure(Sealed(held)")), 4)
    assertEquals(out.released, Some(Ref("released adapter of cases × 2")))

  test("the gate refuses a taught reader with more right but more silent wrong"):
    val (out, seen) = run(Map((false, false) -> m("u", 60, 5), (false, true) -> m("uc", 50, 5),
      (true, false) -> m("t", 70, 20), (true, true) -> m("tc", 65, 30)))
    assertEquals(out.released, None)
    assert(!seen.exists(_.startsWith("Release")))


  test("the inseparable pairs: confused both ways, often enough among the pair's questions apart (T19)"):
    val reads = Vector.fill(6)(("g", "g")) ++ Vector.fill(5)(("a", "g")) ++ Vector.fill(4)(("a", "a")) ++
      Vector.fill(9)(("p", "p")) ++ Vector.fill(2)(("p", "v")) ++ Vector.fill(8)(("v", "v"))
    assertEquals(Checked.pairs(reads), Vector(("a", "g", 5)))

  test("L7 clearly: beating the baseline by more than chance (z >= 1.645)"):
    def m(r: Int, n: Int) = Measured("x", r, n, Map.empty, None)
    assert(!Lesson.clearly(m(39, 82), m(35, 82)), "chapter 4's first vote")
    assert(Lesson.clearly(m(58, 61), m(51, 61)), "chapter 5")
    assert(Lesson.clearly(m(141, 192), m(115, 192)), "tax rules")
    assert(Lesson.clearly(m(83, 92), m(64, 92)))
  test("the gate refuses a taught reader the baseline beats (L7), however it compares with the untaught"):
    val good = Map((false, false) -> m("u", 16, 84), (false, true) -> m("uc", 30, 70),
      (true, false) -> m("t", 150, 10), (true, true) -> m("tc", 140, 12))
    assertEquals(run(good, words = m("b", 170, 30))._1.released, None)
    assert(run(good, words = m("b", 120, 80))._1.released.nonEmpty)
