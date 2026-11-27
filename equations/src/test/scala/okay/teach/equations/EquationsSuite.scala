package okay.teach.equations

/** THE ENGINE'S LAWS (specs/equations.md §7) */
class EquationsSuite extends munit.FunSuite:
  private def q(s: String) = Quantity.parse(s).getOrElse(fail(s"not a quantity: $s"))
  private val kinematics = Theory.parse(
    """variable v: final speed [m/s]
      |variable v0: initial speed [m/s]
      |variable a: acceleration [m/s^2]
      |variable t: time [s]
      |variable s: distance [m]
      |law speed: v = v0 + a * t
      |law distance: s = v0 * t + a * t^2 / 2
      |law average: s = (v0 + v) * t / 2""".stripMargin).fold(e => fail(e), identity)

  test("rationals are exact; units convert exactly: km/h → m/s is 5/18"):
    assertEquals(Rational.parse("0.1").get + Rational.parse("0.2").get, Rational.parse("0.3").get)
    assertEquals(Units.parse("km/h").get.scale, Rational(5) / Rational(18))
    assertEquals(Units.parse("kg*m/s^2").get.dim, Units.parse("N").get.dim)
    assertEquals(q("100 km/h").in(Units.parse("m/s").get), Some(Rational(250) / Rational(9)))
    assertEquals(q("3 kWh").value, Rational(10800000))

  test("dimensions: a law with sides of different dimensions is refused, and so is a sum of them"):
    assert(Theory.parse("variable v: speed [m/s]\nvariable s: distance [m]\nlaw bad: v = s").isLeft)
    assert(Theory.parse("variable v: speed [m/s]\nvariable s: distance [m]\nvariable t: time [s]\nlaw bad: v = s + t")
      .left.exists(_.contains("adds")))

  test("inversion: every law solved for each variable occurring once gives it back"):
    val values = Map("v0" -> Rational(3), "a" -> Rational(2), "t" -> Rational(5), "v" -> Rational(13),
      "s" -> Rational(40))
    for l <- kinematics.laws; x <- l.vars if l.occurrences(x) == 1 do
      val e = l.solved(x).fold(err => fail(err), identity)
      assertEquals(Expr.eval(e, values - x).map(_._1), Right(values(x)), s"${l.name} for $x: $e")

  test("the solver chains laws and says how: v0, a, t → v → s; and a square root, exactly when it can"):
    val (v, steps) = kinematics.solve(Map("v0" -> q("3 m/s"), "a" -> q("2 m/s^2"), "t" -> q("5 s")), "v")
      .fold(e => fail(e), identity)
    assertEquals(v.value, Rational(13)); assertEquals(steps.map(_.law), Vector("speed"))
    val (s, _) = kinematics.solve(Map("v0" -> q("0 m/s"), "a" -> q("2 m/s^2"), "t" -> q("4 s")), "s")
      .fold(e => fail(e), identity)
    assertEquals(s.value, Rational(16))
    // the time to cover 16 m from rest at 2 m/s²: t occurs twice in `distance`, so the chain goes another way
    val falling = Theory.parse("variable h: height [m]\nvariable g: gravity [m/s^2]\nvariable v: speed [m/s]\n" +
      "law drop: v = sqrt(2 * g * h)").fold(e => fail(e), identity)
    val (speed, st) = falling.solve(Map("g" -> q("8 m/s^2"), "h" -> q("4 m")), "v").fold(e => fail(e), identity)
    assertEquals(speed.value, Rational(8)); assert(!st.head.inexact)
    val (h, _) = falling.solve(Map("g" -> q("9.81 m/s^2"), "v" -> q("10 m/s")), "h").fold(e => fail(e), identity)
    assertEquals(h.value, Rational(100) / Rational.parse("19.62").get)

  test("what cannot be found says what is missing; a quantity of the wrong dimension is refused"):
    assert(kinematics.solve(Map("t" -> q("5 s")), "s").left.exists(_.contains("cannot be found")))
    assert(kinematics.solve(Map("t" -> q("5 m")), "s").left.exists(_.contains("declared")))
