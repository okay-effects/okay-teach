package okay.teach.equations

/** a variable of a theory: its name, what it is, its dimension */
final case class Variable(name: String, about: String, dim: Dim)

/** a law: `name: x = expr` */
final case class Law(name: String, left: String, right: Expr):
  def vars: Vector[String] = (left +: right.vars).distinct
  /** how often a variable occurs in the law (both sides) */
  def occurrences(x: String): Int = (if left == x then 1 else 0) + right.vars.count(_ == x)
  /** the law solved for `x` (it must occur once) */
  def solved(x: String): Either[String, Expr] =
    if occurrences(x) != 1 then Left(s"$x occurs ${occurrences(x)} times in $name")
    else if left == x then Right(right)
    else Expr.isolate(right, Expr.Var(left), x)
  override def toString: String = s"$name: $left = $right"

/** one step of a derivation: the law used, the variable found, the expression and its value */
final case class Step(law: String, found: String, by: Expr, value: Rational, inexact: Boolean):
  def said: String = s"$law: $found = $by = ${value.show()}${if inexact then " (rounded)" else ""}"

/**
 * A THEORY (specs/equations.md §2): variables and laws — data, written by a teacher, judged by a book. Well-formed
 * when every law's variables are declared and its sides have one dimension (checked by `check`, the first law).
 */
final case class Theory(variables: Vector[Variable], laws: Vector[Law]):
  val dims: Map[String, Dim] = variables.map(v => v.name -> v.dim).toMap

  def check: Either[String, Unit] =
    laws.foldLeft[Either[String, Unit]](Right(())) { (acc, l) =>
      acc.flatMap(_ =>
        for
          left <- dims.get(l.left).toRight(s"${l.name}: ${l.left} is not declared")
          right <- Expr.dim(l.right, dims).left.map(e => s"${l.name}: $e")
          _ <- if left == right then Right(()) else Left(s"${l.name}: $left on the left, $right on the right")
        yield ()) }

  /**
   * THE SOLVER (specs/equations.md §3): forward chaining from the givens to the asked — a law with all its variables
   * known but one, occurring once, gives that one — with the derivation; or what could not be found
   */
  def solve(givens: Map[String, Quantity], asked: String): Either[String, (Quantity, Vector[Step])] =
    val wrong = givens.collect { case (n, q) if !dims.get(n).contains(q.dim) =>
      s"$n is given in ${q.dim}, declared ${dims.get(n).fold("nowhere")(_.toString)}" }
    if wrong.nonEmpty then Left(wrong.mkString("; "))
    else
      var known = givens.view.mapValues(_.value).toMap
      var steps = Vector.empty[Step]
      var moved = true
      while !known.contains(asked) && moved do
        moved = false
        for l <- laws if !known.contains(asked) do
          l.vars.filterNot(known.contains) match
            case Vector(x) if l.occurrences(x) == 1 =>
              for e <- l.solved(x); vi <- Expr.eval(e, known) do
                val (v, inexact) = vi
                known += x -> v; steps :+= Step(l.name, x, e, v, inexact); moved = true
            case _ => ()
      known.get(asked).map(v => (Quantity(v, dims(asked)), steps))
        .toRight(s"$asked cannot be found from ${givens.keys.toVector.sorted.mkString(", ")}: no law leaves it the " +
          "only unknown")

object Theory:
  /**
   * The text form: `variable v: speed [m/s]` and `law speed: v = s / t`, one a line; `#` a comment.
   */
  def parse(text: String): Either[String, Theory] =
    val V = """variable\s+(\w+)\s*:\s*(.*?)\s*\[(.*)\]\s*""".r
    val L = """law\s+([\w-]+)\s*:\s*(\w+)\s*=\s*(.+)""".r
    text.linesIterator.map(_.trim).filterNot(l => l.isEmpty || l.startsWith("#"))
      .foldLeft[Either[String, Theory]](Right(Theory(Vector.empty, Vector.empty))) { (acc, l) =>
        acc.flatMap(t => l match
          case V(n, about, unit) =>
            Units.parse(unit).toRight(s"no unit $unit").map(u => t.copy(variables = t.variables :+ Variable(n, about,
              u.dim)))
          case L(n, x, e) => Expr.parse(e).map(r => t.copy(laws = t.laws :+ Law(n, x, r)))
          case other => Left(s"not a line of a theory: $other")) }
      .flatMap(t => t.check.map(_ => t))
