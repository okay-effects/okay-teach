package okay.teach.equations

/** AN EXPRESSION OF A LAW (specs/equations.md §2): variables, exact constants, + − × ÷, integer powers, sqrt */
enum Expr:
  case Var(name: String)
  case Num(value: Rational)
  case Add(a: Expr, b: Expr)
  case Sub(a: Expr, b: Expr)
  case Mul(a: Expr, b: Expr)
  case Div(a: Expr, b: Expr)
  case Pow(a: Expr, k: Int)
  case Sqrt(a: Expr)

  def vars: Vector[String] = this match
    case Var(n) => Vector(n)
    case Num(_) => Vector.empty
    case Add(a, b) => a.vars ++ b.vars
    case Sub(a, b) => a.vars ++ b.vars
    case Mul(a, b) => a.vars ++ b.vars
    case Div(a, b) => a.vars ++ b.vars
    case Pow(a, _) => a.vars
    case Sqrt(a) => a.vars

  override def toString: String = this match
    case Var(n) => n
    case Num(v) => v.show()
    case Add(a, b) => s"($a + $b)"
    case Sub(a, b) => s"($a - $b)"
    case Mul(a, b) => s"$a * $b"
    case Div(a, b) => s"$a / $b"
    case Pow(a, k) => s"$a^$k"
    case Sqrt(a) => s"sqrt($a)"

object Expr:
  /** an expression as written: `s / t`, `v0 + a * t`, `0.5 * a * t^2`, `sqrt(2 * g * h)` */
  def parse(text: String): Either[String, Expr] =
    val tokens = """\d+(?:[.,]\d+)?|[A-Za-z_][A-Za-z0-9_]*|[-+*/^()]""".r.findAllIn(text).toVector
    var i = 0
    def peek = tokens.lift(i)
    def take() = { val t = tokens(i); i += 1; t }
    def sum(): Expr =
      var e = product()
      while peek.exists(t => t == "+" || t == "-") do
        val op = take(); val b = product(); e = if op == "+" then Add(e, b) else Sub(e, b)
      e
    def product(): Expr =
      var e = power()
      while peek.exists(t => t == "*" || t == "/") do
        val op = take(); val b = power(); e = if op == "*" then Mul(e, b) else Div(e, b)
      e
    def power(): Expr =
      val b = atom()
      if peek.contains("^") then
        take()
        val neg = peek.contains("-") && { take(); true }
        val k = take().toIntOption.getOrElse(sys.error("a power needs an integer"))
        Pow(b, if neg then -k else k)
      else b
    def atom(): Expr = take() match
      case "(" => val e = sum(); if take() != ")" then sys.error("a ( without )"); e
      case "-" => Sub(Num(Rational.zero), atom())
      case "sqrt" => if take() != "(" then sys.error("sqrt needs ("); val e = sum(); take(); Sqrt(e)
      case t if t.head.isDigit => Num(Rational.parse(t).get)
      case t => Var(t)
    scala.util.Try { val e = sum(); if i != tokens.size then sys.error(s"left over: ${tokens.drop(i).mkString}"); e }
      .toEither.left.map(_.getMessage)

  /** the value, and whether a step was inexact (a square root of a non-square) */
  def eval(e: Expr, values: Map[String, Rational]): Either[String, (Rational, Boolean)] = e match
    case Var(n) => values.get(n).map((_, false)).toRight(s"$n is not known")
    case Num(v) => Right((v, false))
    case Add(a, b) => two(a, b, values)(_ + _)
    case Sub(a, b) => two(a, b, values)(_ - _)
    case Mul(a, b) => two(a, b, values)(_ * _)
    case Div(a, b) =>
      for xi <- eval(a, values); yj <- eval(b, values)
        r <- if yj._1.isZero then Left(s"a division by zero in $e") else Right(xi._1 / yj._1)
      yield (r, xi._2 || yj._2)
    case Pow(a, k) =>
      eval(a, values).flatMap((x, i) => if x.isZero && k < 0 then Left(s"zero to a negative power in $e")
        else Right((x.pow(k), i)))
    case Sqrt(a) => eval(a, values).flatMap((x, i) => sqrt(x).map((r, exact) => (r, i || !exact)))

  private def two(a: Expr, b: Expr, values: Map[String, Rational])(f: (Rational, Rational) => Rational) =
    for xi <- eval(a, values); yj <- eval(b, values) yield (f(xi._1, yj._1), xi._2 || yj._2)

  /** the integer square root (Newton's method on BigInt: the same on the JVM and in Scala.js) */
  def isqrt(n: BigInt): BigInt =
    if n < 2 then n
    else
      var x = BigInt(1) << ((n.bitLength + 1) / 2)
      var y = (x + n / x) / 2
      while y < x do { x = y; y = (x + n / x) / 2 }
      x

  /** the square root: exact when numerator and denominator are squares, else to 1e-30 */
  def sqrt(x: Rational): Either[String, (Rational, Boolean)] =
    if x.signum < 0 then Left(s"the square root of a negative number ($x)")
    else
      val (a, b) = (isqrt(x.n), isqrt(x.d))
      if a * a == x.n && b * b == x.d then Right((Rational(a, b), true))
      else
        val scale = BigInt(10).pow(30)
        Right((Rational(isqrt(x.n * x.d * scale * scale), x.d * scale), false))

  /** the dimension of an expression, given the variables'; a sum of different dimensions is refused */
  def dim(e: Expr, dims: Map[String, Dim]): Either[String, Dim] = e match
    case Var(n) => dims.get(n).toRight(s"$n has no dimension declared")
    case Num(_) => Right(Dim.none)
    case Add(a, b) => same(a, b, dims, "+")
    case Sub(a, b) => same(a, b, dims, "-")
    case Mul(a, b) => for x <- dim(a, dims); y <- dim(b, dims) yield x * y
    case Div(a, b) => for x <- dim(a, dims); y <- dim(b, dims) yield x / y
    case Pow(a, k) => dim(a, dims).map(_.pow(k))
    case Sqrt(a) => dim(a, dims).flatMap(x => x.sqrt.toRight(s"the square root of $x in $e"))

  private def same(a: Expr, b: Expr, dims: Map[String, Dim], op: String) =
    for x <- dim(a, dims); y <- dim(b, dims)
      r <- if x == y then Right(x) else Left(s"$a $op $b adds $x to $y")
    yield r

  /**
   * THE EQUATION `side = other` SOLVED FOR `x`, occurring once in `side` (specs/equations.md §2): inverted along the
   * path to it — + ↔ −, × ↔ ÷, a square ↔ its (nonnegative) root, a reciprocal ↔ a reciprocal
   */
  def isolate(side: Expr, other: Expr, x: String): Either[String, Expr] =
    def has(e: Expr) = e.vars.contains(x)
    side match
      case Var(n) if n == x => Right(other)
      case Add(a, b) => if has(a) then isolate(a, Sub(other, b), x) else isolate(b, Sub(other, a), x)
      case Sub(a, b) => if has(a) then isolate(a, Add(other, b), x) else isolate(b, Sub(a, other), x)
      case Mul(a, b) => if has(a) then isolate(a, Div(other, b), x) else isolate(b, Div(other, a), x)
      case Div(a, b) => if has(a) then isolate(a, Mul(other, b), x) else isolate(b, Div(a, other), x)
      case Pow(a, 2) => isolate(a, Sqrt(other), x)
      case Pow(a, 1) => isolate(a, other, x)
      case Pow(a, -1) => isolate(a, Div(Num(Rational.one), other), x)
      case Sqrt(a) => isolate(a, Pow(other, 2), x)
      case _ => Left(s"$x cannot be isolated in $side")
