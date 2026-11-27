package okay.teach.equations

/** AN EXACT NUMBER: a fraction of two integers in lowest terms, the denominator positive (specs/equations.md §1) */
final case class Rational private (n: BigInt, d: BigInt):
  def +(o: Rational): Rational = Rational(n * o.d + o.n * d, d * o.d)
  def -(o: Rational): Rational = Rational(n * o.d - o.n * d, d * o.d)
  def *(o: Rational): Rational = Rational(n * o.n, d * o.d)
  def /(o: Rational): Rational = Rational(n * o.d, d * o.n)
  def unary_- : Rational = Rational(-n, d)
  def pow(k: Int): Rational = if k >= 0 then Rational(n.pow(k), d.pow(k)) else Rational(d.pow(-k), n.pow(-k))
  def isZero: Boolean = n == 0
  def signum: Int = n.signum
  def toBigDecimal: BigDecimal = BigDecimal(n) / BigDecimal(d)
  override def toString: String = if d == 1 then n.toString else s"$n/$d"
  /** as a person reads it: an integer, else a decimal to `digits` significant figures */
  def show(digits: Int = 6): String =
    if d == 1 then n.toString
    else toBigDecimal.round(new java.math.MathContext(digits)).bigDecimal.stripTrailingZeros.toPlainString

object Rational:
  def apply(n: BigInt, d: BigInt = 1): Rational =
    require(d != 0, "a zero denominator")
    val g = n.gcd(d).max(1)
    val s = if d < 0 then -1 else 1
    new Rational(s * n / g, s * d / g)
  def apply(i: Int): Rational = apply(BigInt(i))
  val zero: Rational = apply(0)
  val one: Rational = apply(1)
  /** a decimal as written ("12.5", "1e3", "0,25"), exactly */
  def parse(s: String): Option[Rational] =
    scala.util.Try(BigDecimal(s.trim.replace(',', '.'))).toOption.map(of)
  def of(b: BigDecimal): Rational =
    val u = b.bigDecimal.stripTrailingZeros
    if u.scale <= 0 then apply(BigInt(u.toBigIntegerExact)) else apply(BigInt(u.unscaledValue), BigInt(10).pow(u.scale))

/** A DIMENSION: integer exponents over the SI base — length, mass, time, current, temperature, amount, luminosity */
final case class Dim(e: Vector[Int]):
  def *(o: Dim): Dim = Dim(e.zip(o.e).map(_ + _))
  def /(o: Dim): Dim = Dim(e.zip(o.e).map(_ - _))
  def pow(k: Int): Dim = Dim(e.map(_ * k))
  /** the square root, when every exponent is even */
  def sqrt: Option[Dim] = Option.when(e.forall(_ % 2 == 0))(Dim(e.map(_ / 2)))
  def isNone: Boolean = e.forall(_ == 0)
  override def toString: String =
    val names = Vector("m", "kg", "s", "A", "K", "mol", "cd")
    val parts = names.zip(e).filter(_._2 != 0).map((u, k) => if k == 1 then u else s"$u^$k")
    if parts.isEmpty then "1" else parts.mkString("·")

object Dim:
  val none: Dim = Dim(Vector.fill(7)(0))
  private def base(i: Int) = Dim(Vector.tabulate(7)(j => if i == j then 1 else 0))
  val length: Dim = base(0); val mass: Dim = base(1); val time: Dim = base(2); val current: Dim = base(3)
  val temperature: Dim = base(4); val amount: Dim = base(5); val luminosity: Dim = base(6)

/** A UNIT: its dimension and its scale to the coherent SI unit (exact) */
final case class SiUnit(name: String, dim: Dim, scale: Rational)

/**
 * THE UNITS' CATALOG (specs/equations.md §1): the SI base and derived units, the prefixes, common non-SI units; and
 * unit expressions written with `*`, `/`, `^` ("m/s^2", "kg*m/s^2", "km/h").
 */
object Units:
  import Dim.*
  private def r(n: Int, d: Int = 1) = Rational(BigInt(n), BigInt(d))
  private val newton = mass * length / time.pow(2)
  private val joule = newton * length
  private val named: Vector[SiUnit] = Vector(
    SiUnit("m", length, r(1)), SiUnit("g", mass, r(1, 1000)), SiUnit("s", time, r(1)), SiUnit("A", current, r(1)),
    SiUnit("K", temperature, r(1)), SiUnit("mol", amount, r(1)), SiUnit("cd", luminosity, r(1)),
    SiUnit("N", newton, r(1)), SiUnit("J", joule, r(1)), SiUnit("W", joule / time, r(1)),
    SiUnit("Pa", newton / length.pow(2), r(1)), SiUnit("Hz", none / time, r(1)), SiUnit("C", current * time, r(1)),
    SiUnit("V", joule / (current * time), r(1)), SiUnit("Ω", joule / (current * time) / current, r(1)),
    SiUnit("min", time, r(60)), SiUnit("h", time, r(3600)), SiUnit("l", length.pow(3), r(1, 1000)),
    SiUnit("t", mass, r(1000)), SiUnit("Wh", joule, r(3600)))
  private val prefixes: Vector[(String, Rational)] = Vector("G" -> r(1000000000), "M" -> r(1000000), "k" -> r(1000),
    "c" -> r(1, 100), "m" -> r(1, 1000), "µ" -> r(1, 1000000), "u" -> r(1, 1000000), "n" -> r(1, 1000000000))
  private val prefixed = Set("m", "g", "s", "N", "J", "W", "Pa", "Hz", "C", "V", "Ω", "A", "Wh", "l", "mol")

  /** one unit by name, with a prefix where the unit takes one (`kg` is a prefixed `g`) */
  def one(name: String): Option[SiUnit] =
    named.find(_.name == name).orElse(prefixes.collectFirst {
      case (p, k) if name.startsWith(p) && prefixed(name.drop(p.length)) =>
        named.find(_.name == name.drop(p.length)).map(u => SiUnit(name, u.dim, u.scale * k)) }.flatten)

  /** a unit expression: factors joined by `*` or `·`, divided by `/`, each with an optional `^k` ("1" alone: none) */
  def parse(text: String): Option[SiUnit] =
    val t = text.trim
    if t.isEmpty || t == "1" then Some(SiUnit("1", Dim.none, Rational.one))
    else
      val pieces = "[*·/]|[^*·/]+".r.findAllIn(t.replace(" ", "")).toVector
      pieces.foldLeft(Option((Dim.none, Rational.one, 1))) { case (acc, p) =>
        acc.flatMap { (dim, scale, sign) =>
          if p == "/" then Some((dim, scale, -1))
          else if p == "*" || p == "·" then Some((dim, scale, 1))
          else
            val (name, k) = p.split('^') match
              case Array(n, e) => (n, e.toIntOption)
              case Array(n) => (n, Some(1))
              case _ => ("", None)
            for e <- k; u <- one(name) yield (dim * u.dim.pow(sign * e), scale * u.scale.pow(sign * e), sign)
        } }.map((d, s, _) => SiUnit(t, d, s))

/** A QUANTITY: an exact value in coherent SI units, and its dimension */
final case class Quantity(value: Rational, dim: Dim):
  def in(u: SiUnit): Option[Rational] = Option.when(u.dim == dim)(value / u.scale)

object Quantity:
  /** "100 km/h", "9.81 m/s^2", "3,5 kg" */
  def parse(text: String): Option[Quantity] =
    val t = text.trim
    val i = t.indexWhere(c => !(c.isDigit || c == '.' || c == ',' || c == '-' || c == 'e' || c == 'E'))
    val (num, unit) = if i < 0 then (t, "") else (t.take(i), t.drop(i))
    for v <- Rational.parse(num); u <- Units.parse(unit) yield Quantity(v * u.scale, u.dim)
