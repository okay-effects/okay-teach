package okay.teach

import okay.frames.Laws
import okay.teach.equations.{Expr, Law, Quantity, Theory}

/** THE LAW SPACE AND ITS JUDGE: kinematics' laws are reached by choosing, a law of mixed dimensions does not close,
 * the judge agrees with a book's rounded answers */
class LawSpaceSuite extends munit.FunSuite:
  private val theory = Theory.parse(
    """variable v: final speed [m/s]
      |variable v0: initial speed [m/s]
      |variable a: acceleration [m/s^2]
      |variable t: time [s]
      |variable s: distance [m]
      |law speed: v = v0 + a * t
      |law distance: s = v0 * t + a * t^2 / 2""".stripMargin).fold(e => fail(e), identity)
  // (distance: + → / → * → ^2 → t is five deep; a bound of 4 refuses it, as it should)
  private val words = LawWords(theory.variables, depth = 5)
  private def q(s: String) = Quantity.parse(s).get

  test("the laws of a chapter are reached by choosing, and said and parsed back"):
    for l <- theory.laws do
      val law = l.copy(name = "law")
      assertEquals(Laws.reach(LawSpace)(words, "", law), None, l.toString)
      assertEquals(Laws.roundTrip(LawSpace)(words, law, Seq("en")), None, l.toString)

  test("a law whose sides differ in dimension does not close; an operation past the depth is not offered"):
    val bad = Law("law", "v", Expr.Var("s"))
    assertEquals(LawSpace.close(words, "", LawSpace.path(words, bad)), None)
    val shallow = LawWords(theory.variables, depth = 1)
    assert(!LawSpace.next(shallow, "", Vector(okay.frames.Choice("left", "v"))).get.options.contains("+"))

  test("the judge: right to the book's figures, and what failed in words"):
    val book = Vector(Worked(1, Map("v0" -> q("0 m/s"), "a" -> q("9.8 m/s^2"), "t" -> q("3 s")), "v", q("29.4 m/s")),
      Worked(2, Map("v0" -> q("5 m/s"), "a" -> q("2 m/s^2"), "t" -> q("3 s")), "s", q("24.0 m")),
      Worked(3, Map("t" -> q("2 s")), "s", q("1 m")))
    val v = LawTeacher.judge(theory, book)
    assertEquals((v.passed, v.of), (2, 3))
    assert(v.told.contains("example 3"), v.told)

  test("a plan's variables and laws are read; a unit outside the catalog is dropped"):
    val reply = "variable: v — final speed [m/s]\n- variable: `t` — time [s]\nvariable: z — zork [furlong]\n" +
      "law: the final speed is the initial speed plus acceleration times time"
    assertEquals(LawTeacher.variables(reply).map(_.name), Vector("v", "t"))
    assertEquals(LawTeacher.laws(reply).size, 1)
