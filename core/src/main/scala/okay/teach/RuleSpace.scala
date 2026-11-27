package okay.teach

import okay.datalog.{Atom, Check, Literal, Parse, Program, Rule, Term}
import okay.frames.{Choice, Hole, Space}

/** THE WORDS A RULE IS WRITTEN IN: the predicates with their arities, the constants a rule may name, how long a
 * body may be and how many variables a rule may use */
final case class Vocabulary(predicates: Map[String, Int], constants: Vector[String] = Vector.empty,
    maxBody: Int = 3, maxVars: Int = 4):
  val variables: Vector[String] = Vector("X", "Y", "Z", "W", "V", "U").take(maxVars)
  val names: Vector[String] = predicates.keys.toVector.sorted

/**
 * A DATALOG RULE AS A SPACE OF CHOICES (specs/teach.md §5): a rule of okay-datalog, reached hole by hole — the
 * head's predicate, its arguments, the body's length, then each literal's kind (an atom, its negation, `=`, `!=`),
 * its predicate and its arguments — every option from the vocabulary: a variable or a constant, a known predicate
 * of its known arity. The term is the rule; only a safe rule (okay-datalog `Check.safety`) closes. The words are
 * the rule's own text, parsed back by okay-datalog, its variables renamed in order of appearance (`canonical`).
 */
object RuleSpace extends Space[Vocabulary, Rule]:
  def name: String = "datalog rule"
  private val kinds = Vector("atom", "not", "=", "!=")

  /** the holes of a rule so far: the walk of `next` over the choices, kept apart so `path` writes the same */
  def next(v: Vocabulary, text: String, chosen: Vector[Choice]): Option[Hole] =
    val o = chosen.map(_.option)
    val terms = v.variables ++ v.constants
    if o.isEmpty then Some(Hole("head", v.names))
    else if !v.predicates.contains(o(0)) then None
    else
      val headArity = v.predicates(o(0))
      if o.size <= headArity then Some(Hole(s"head ${o.size}", terms))
      else if o.size == headArity + 1 then Some(Hole("body", (1 to v.maxBody).map(_.toString).toVector))
      else
        // walk the literals chosen so far: each a kind, then a predicate and its arguments, or two terms
        val size = o(headArity + 1).toIntOption.getOrElse(0)
        var i = headArity + 2
        var lit = 1
        var hole: Option[Hole] = None
        while hole.isEmpty && lit <= size do
          if i >= o.size then hole = Some(Hole(s"kind $lit", kinds))
          else
            val kind = o(i)
            if kind == "atom" || kind == "not" then
              if i + 1 >= o.size then hole = Some(Hole(s"pred $lit", v.names))
              else
                val arity = v.predicates.getOrElse(o(i + 1), -1)
                val taken = o.size - (i + 2)
                if arity < 0 then lit = size + 1
                else if taken < arity then hole = Some(Hole(s"arg $lit.${taken + 1}", terms))
                else { i += 2 + arity; lit += 1 }
            else
              val taken = o.size - (i + 1)
              if taken < 2 then hole = Some(Hole(s"${if taken == 0 then "left" else "right"} $lit", terms))
              else { i += 3; lit += 1 }
        hole

  private def term(v: Vocabulary, s: String): Term = if v.variables.contains(s) then Term.Var(s) else Term.Const(s)

  def assemble(v: Vocabulary, chosen: Vector[Choice]): Option[Rule] =
    val o = chosen.map(_.option)
    for
      headArity <- o.headOption.flatMap(v.predicates.get)
      size <- o.lift(headArity + 1).flatMap(_.toIntOption)
      head = Atom(o(0), o.slice(1, 1 + headArity).map(term(v, _)))
      body <- literals(v, o.drop(headArity + 2), size)
      rule = Rule(head, body)
      if Check.safety(Program(Vector(rule))).isRight
    yield rule

  private def literals(v: Vocabulary, o: Vector[String], size: Int): Option[Vector[Literal]] =
    if size == 0 then Option.when(o.isEmpty)(Vector.empty)
    else o.headOption.flatMap {
      case k @ ("atom" | "not") =>
        o.lift(1).flatMap(v.predicates.get).flatMap { arity =>
          val atom = Atom(o(1), o.slice(2, 2 + arity).map(term(v, _)))
          literals(v, o.drop(2 + arity), size - 1).map((if k == "atom" then Literal.Pos(atom)
            else Literal.Neg(atom)) +: _) }
      case op @ ("=" | "!=") if o.size >= 3 =>
        literals(v, o.drop(3), size - 1).map(Literal.Cmp(op, term(v, o(1)), term(v, o(2))) +: _)
      case _ => None }

  def path(v: Vocabulary, r: Rule): Vector[Choice] =
    val c = canonical(r)
    val head = Vector("head" -> c.head.pred) ++ c.head.args.zipWithIndex.map((t, i) => s"head ${i + 1}" -> show(t))
    val body = Vector("body" -> c.body.size.toString) ++ c.body.zipWithIndex.flatMap { (l, j) =>
      val n = j + 1
      l match
        case Literal.Pos(a) => Vector(s"kind $n" -> "atom", s"pred $n" -> a.pred) ++ args(n, a)
        case Literal.Neg(a) => Vector(s"kind $n" -> "not", s"pred $n" -> a.pred) ++ args(n, a)
        case Literal.Cmp(op, l, r) => Vector(s"kind $n" -> op, s"left $n" -> show(l), s"right $n" -> show(r)) }
    (head ++ body).map(Choice(_, _))

  private def args(n: Int, a: Atom) = a.args.zipWithIndex.map((t, i) => s"arg $n.${i + 1}" -> show(t))
  private def show(t: Term): String = t match
    case Term.Var(x) => x
    case Term.Const(x) => x

  /** the rule with its variables renamed X, Y, Z, … in order of appearance (head first, then the body) */
  def canonical(r: Rule): Rule =
    val order = (r.head.vars ++ r.body.flatMap(_.vars)).distinct
    val names = order.zip(Vector("X", "Y", "Z", "W", "V", "U") ++ (1 to 99).map(i => s"V$i")).toMap
    def t(x: Term) = x match
      case Term.Var(n) => Term.Var(names(n))
      case c => c
    def a(x: Atom) = Atom(x.pred, x.args.map(t))
    Rule(a(r.head), r.body.map {
      case Literal.Pos(x) => Literal.Pos(a(x))
      case Literal.Neg(x) => Literal.Neg(a(x))
      case Literal.Cmp(op, l, rr) => Literal.Cmp(op, t(l), t(rr)) })

  def say(v: Vocabulary, r: Rule, language: String): String = canonical(r).toString

  def parse(v: Vocabulary, words: String, language: String): Option[Rule] =
    Parse.program(words).toOption.collect { case Program(Vector(r)) => canonical(r) }
