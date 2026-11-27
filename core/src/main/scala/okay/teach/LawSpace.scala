package okay.teach

import okay.frames.{Choice, Hole, Space}
import okay.teach.equations.{Expr, Law, Quantity, Rational, Theory, Variable}

/** THE WORDS A LAW IS WRITTEN IN: the variables declared, the constants a law may use, how deep its right side */
final case class LawWords(variables: Vector[Variable], constants: Vector[Rational] = Vector(Rational(2),
    Rational(1) / Rational(2)), depth: Int = 5):
  val names: Vector[String] = variables.map(_.name)
  val dims: Map[String, okay.teach.equations.Dim] = variables.map(v => v.name -> v.dim).toMap

/**
 * A LAW AS A SPACE OF CHOICES (specs/equations.md §4; theory L5): the left variable, then the right side as a tree
 * in prefix order — each node an operation (+ − × ÷, a square, a reciprocal, a root), a variable or a constant,
 * operations only above the depth bound. Only a law whose sides have one dimension closes. The words are `x = expr`,
 * parsed back by the engine's parser.
 */
object LawSpace extends Space[LawWords, Law]:
  def name: String = "law"
  private val binary = Vector("+", "-", "*", "/")
  private val unary = Vector("^2", "^-1", "sqrt")

  /** the depths of the slots still open after a prefix of nodes (none: the tree is complete); None: not a tree */
  private def open(w: LawWords, nodes: Vector[String]): Option[List[Int]] =
    nodes.foldLeft(Option(List(1))) { (acc, n) =>
      acc.flatMap {
        case d :: rest =>
          if binary.contains(n) then Option.when(d < w.depth)(List(d + 1, d + 1) ++ rest)
          else if unary.contains(n) then Option.when(d < w.depth)((d + 1) :: rest)
          else Some(rest)
        case Nil => None } }

  private def leaves(w: LawWords): Vector[String] = w.names ++ w.constants.map(_.show())

  def next(w: LawWords, text: String, chosen: Vector[Choice]): Option[Hole] =
    if chosen.isEmpty then Some(Hole("left", w.names))
    else open(w, chosen.drop(1).map(_.option)).flatMap(_.headOption).map { d =>
      val n = chosen.size
      Hole(s"node $n", (if d < w.depth then binary ++ unary else Vector.empty) ++ leaves(w)) }

  private def tree(w: LawWords, nodes: List[String]): Option[(Expr, List[String])] = nodes match
    case Nil => None
    case n :: rest =>
      if binary.contains(n) then
        for (a, r1) <- tree(w, rest); (b, r2) <- tree(w, r1) yield ((n match
          case "+" => Expr.Add(a, b)
          case "-" => Expr.Sub(a, b)
          case "*" => Expr.Mul(a, b)
          case _ => Expr.Div(a, b)), r2)
      else if unary.contains(n) then
        tree(w, rest).map((a, r) => ((n match
          case "^2" => Expr.Pow(a, 2)
          case "^-1" => Expr.Pow(a, -1)
          case _ => Expr.Sqrt(a)), r))
      else if w.names.contains(n) then Some((Expr.Var(n), rest))
      else w.constants.find(_.show() == n).map(c => (Expr.Num(c), rest))

  def assemble(w: LawWords, chosen: Vector[Choice]): Option[Law] =
    for
      left <- chosen.headOption.map(_.option).filter(w.names.contains)
      (right, rest) <- tree(w, chosen.drop(1).map(_.option).toList)
      if rest.isEmpty
      law = Law("law", left, right)
      if Theory(w.variables, Vector(law)).check.isRight
    yield law

  private def prefix(e: Expr): Option[Vector[String]] = e match
    case Expr.Var(n) => Some(Vector(n))
    case Expr.Num(v) => Some(Vector(v.show()))
    case Expr.Add(a, b) => for x <- prefix(a); y <- prefix(b) yield "+" +: (x ++ y)
    case Expr.Sub(a, b) => for x <- prefix(a); y <- prefix(b) yield "-" +: (x ++ y)
    case Expr.Mul(a, b) => for x <- prefix(a); y <- prefix(b) yield "*" +: (x ++ y)
    case Expr.Div(a, b) => for x <- prefix(a); y <- prefix(b) yield "/" +: (x ++ y)
    case Expr.Pow(a, 2) => prefix(a).map("^2" +: _)
    case Expr.Pow(a, -1) => prefix(a).map("^-1" +: _)
    case Expr.Sqrt(a) => prefix(a).map("sqrt" +: _)
    case _ => None

  def path(w: LawWords, l: Law): Vector[Choice] =
    val nodes = prefix(l.right).getOrElse(Vector("?"))
    Choice("left", l.left) +: nodes.zipWithIndex.map((n, i) => Choice(s"node ${i + 1}", n))

  def say(w: LawWords, l: Law, language: String): String = s"${l.left} = ${l.right}"

  def parse(w: LawWords, words: String, language: String): Option[Law] =
    words.split("=", 2) match
      case Array(x, e) => Expr.parse(e).toOption.map(r => Law("law", x.trim, r))
      case _ => None

/** a worked example of a book: what is given, what is asked, the book's answer to `figures` significant figures */
final case class Worked(number: Int, givens: Map[String, Quantity], asked: String, answer: Quantity, figures: Int = 3):
  /** right when the theory's answer agrees with the book's to its printed figures */
  def agrees(got: Quantity): Boolean =
    got.dim == answer.dim && {
      val a = answer.value.toBigDecimal; val g = got.value.toBigDecimal
      (g - a).abs <= a.abs * BigDecimal(10).pow(1 - figures) / 2 + BigDecimal("1e-12") }
