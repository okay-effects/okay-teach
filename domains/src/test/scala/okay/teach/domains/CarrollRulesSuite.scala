package okay.teach.domains

import okay.datalog.Parse
import okay.frames.Laws
import okay.teach.RuleSpace

/** THE JUDGE AND THE SPACE ARE FAIR: the reference rules (a Claude agent's, okay-study) can be built by choosing,
 * and the judge passes them on every exercise — so a teacher's score here is the teacher's, not the tool's */
class CarrollRulesSuite extends munit.FunSuite:
  private val reference = Parse.program(CarrollRules.forms.rules).fold(e => fail(e), identity)

  test("every reference rule is reached choice by choice, with the predicates it uses declared"):
    val used = reference.rules.flatMap(r => r.head +: r.body.collect {
      case okay.datalog.Literal.Pos(a) => a
      case okay.datalog.Literal.Neg(a) => a }).map(a => a.pred -> a.arity).toMap
    val v = CarrollRules.vocabulary.copy(predicates = used ++ CarrollRules.vocabulary.predicates)
    for r <- reference.rules do
      assertEquals(Laws.reach(RuleSpace)(v, "", RuleSpace.canonical(r)), None, r.toString)

  test("the judge passes the reference: 10 of 10 to train on, 16 of 16 held out"):
    val (train, held) = CarrollRules.exercises(10)
    assertEquals(CarrollRules.judge(train)(reference), okay.teach.RuleTeacher.Verdict(10, 10, ""))
    assertEquals(CarrollRules.scored(reference, held).said, "16 of 16")

  test("the judge tells what failed, in words"):
    val (train, _) = CarrollRules.exercises(10)
    val v = CarrollRules.judge(train)(okay.datalog.Program(Vector.empty))
    assertEquals(v.passed, 0)
    assert(v.told.contains("the book says"), v.told)
