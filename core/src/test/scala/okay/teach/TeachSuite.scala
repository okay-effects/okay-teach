package okay.teach

import okay.datalog.{Parse, Program}
import okay.frames.{Choice, Hole, Laws, Space}
import okay.llm.read.Read
import okay.scala3.*

class TeachSuite extends munit.FunSuite:
  private val v = Vocabulary(Map("parent" -> 2, "anc" -> 2, "person" -> 1, "dead" -> 1), Vector("adam"))
  private def rule(s: String) = Parse.program(s).toOption.get.rules.head

  test("the rule space: rules reached choice by choice, closed to themselves, said and parsed back"):
    val rules = Vector("anc(X, Y) :- parent(X, Y).", "anc(A, C) :- parent(A, B), anc(B, C).",
      "person(X) :- parent(X, _).", "anc(adam, X) :- person(X), not dead(X).", "anc(X, Y) :- parent(X, Y), X != Y.")
    for s <- rules do
      val r = RuleSpace.canonical(rule(s))
      assertEquals(Laws.reach(RuleSpace)(v, "", r), None, s)
      assertEquals(Laws.roundTrip(RuleSpace)(v, r, Seq("en")), None, s)

  test("the rule space refuses what the vocabulary does not have, and an unsafe rule does not close"):
    assert(Laws.reach(RuleSpace)(v, "", rule("child(X, Y) :- parent(Y, X).")).nonEmpty)
    assert(Laws.reach(RuleSpace)(v, "", rule("anc(X, Y) :- parent(X, Y, Z).")).nonEmpty)
    val unsafe = Vector("anc", "X", "Y", "1", "atom", "person", "X").zip(Vector("head", "head 1", "head 2", "body",
      "kind 1", "pred 1", "arg 1.1")).map((o, h) => Choice(h, o))
    assertEquals(RuleSpace.close(v, "", unsafe), None)

  test("the bar: the least that keeps (wrong + 1) / (answered + 1) within epsilon, from the strictest down"):
    val rs = (1 to 40).map(i => (i.toDouble, true)) ++ Vector((0.5, false), (0.4, false), (0.3, true))
    val b = Bar.choose(rs, 0.05).get
    // at 0.5: 41 answered, 1 wrong, 2 / 42 ≤ 0.05 holds; at 0.4: 3 / 43 does not — the sequence stops there
    assertEquals(b.bar, 0.5); assertEquals(b.answered, 41); assertEquals(b.wrong, 1)
    // one wrong at margin 20: at 20 the bound fails (2 / 22), and a fixed sequence stops at its first failure — the
    // lower bars that would hold again (2 / 41) are not tried: trying them would be choosing the bar by the data
    val one = Bar.choose((1 to 40).map(i => (i.toDouble, i != 20)), 0.05).get
    assertEquals(one.bar, 21.0); assertEquals(one.wrong, 0)
    assertEquals(Bar.choose(Vector((1.0, false)), 0.05), None)

  test("the teacher's lines: numbering, bullets, quotes and a language label stripped, one message a line"):
    val got = Teacher.parse("1. How much tax do I pay?\n2) \"Ile podatku?\"\n- en: Do I pay at all?\n\n• one more")
    assertEquals(got, Vector("How much tax do I pay?", "Ile podatku?", "Do I pay at all?", "one more"))

  test("a chooser's number: the last one in the reply, within the options"):
    assertEquals(Choose.number("I think 2, no: 3", 4), Some(3))
    assertEquals(Choose.number("7", 4), None)
    assertEquals(Choose.number("none", 4), None)

  // A SPACE OF ONE HOLE AND A FAKE MODEL that prefers the first option for itself, and the right one by the text
  private object Pick extends Space[Unit, String]:
    val all = Vector("alpha", "beta", "gamma")
    def name = "pick"
    def next(u: Unit, t: String, c: Vector[Choice]) = Option.when(c.isEmpty)(Hole("pick", all))
    def assemble(u: Unit, c: Vector[Choice]) = c.headOption.map(_.option)
    def path(u: Unit, t: String) = Vector(Choice("pick", t))
    def say(u: Unit, t: String, l: String) = t
    def parse(u: Unit, w: String, l: String) = Option.when(all.contains(w))(w)
  private object PickDomain extends Domain[Unit, String]:
    def space = Pick
    def system(u: Unit) = "pick"

  /** the prompt's text as ids (one id a char); the score of an option: 2 for the first listed (a model's bias), +1
   * when the text names it — so plain reading always picks the first, the calibrated one what the text names */
  private def fake[A](p: A ! Read): A =
    var texts = Map.empty[Int, String]
    def ids(s: String): Array[Int] = { val k = texts.size + 1000; texts += k -> s; Array(k) }
    Read.run(p, new okay.llm.read.Reading:
      def answer[X](op: Read[X]): X = op match
        case Read.Prompt(_, user) => ids(user).asInstanceOf[X]
        case Read.Encode(s) => ids(s).asInstanceOf[X]
        case Read.Score(context, options) =>
          val text = context.headOption.flatMap(texts.get).getOrElse("")
          options.map(o => texts.getOrElse(o.head, "")).map(o =>
            (if o == "alpha" then 2.0 else 0.0) + (if text.contains(o) then 1.0 else 0.0)).asInstanceOf[X]
        case _ => sys.error(s"not faked: $op"))

  test("content-free calibration takes away what a model prefers for itself"):
    def read(t: String, s: Reader.Scoring) = fake(Reader.read[Unit, String, Read](PickDomain, (), t, s)).meaning
    assertEquals(read("I want gamma", Reader.Scoring.Plain), Some("alpha"))
    assertEquals(read("I want gamma", Reader.Scoring.ContentFree()), Some("gamma"))
    assertEquals(read("beta please", Reader.Scoring.ContentFree()), Some("beta"))

  test("the lines a meaning is written in"):
    assertEquals(Reader.lines(PickDomain, (), "gamma", "gamma"), Vector("gamma"))
    assertEquals(RuleSpace.say(v, rule("anc(A, B) :- parent(A, B)."), "en"), "anc(X, Y) :- parent(X, Y).")
    assert(Program(Vector(rule("anc(X, Y) :- parent(X, Y)."))).rules.nonEmpty)

  test("a plan's lines: the rules described, the predicates it declares"):
    val reply = "predicate: chain/2 — a chain of premisses\n- predicate: `retinend/1` — kept\npredicate: Bad/2\n" +
      "rule: chain holds when a premiss links X to Y\nRule: a term is a retinend when …\nnoise"
    assertEquals(RuleTeacher.declared(reply), Map("chain" -> 2, "retinend" -> 1))
    assertEquals(RuleTeacher.plan(reply).size, 2)
