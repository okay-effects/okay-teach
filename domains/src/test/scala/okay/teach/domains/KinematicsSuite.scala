package okay.teach.domains

import okay.teach.{LawSpace, LawTeacher, LawWords}
import okay.frames.Laws

/** THE JUDGE AND THE SPACE ARE FAIR (as for Carroll): the laws a person writes from the section are reached by
 * choosing, and they pass all eight worked examples — so a teacher's score is the teacher's */
class KinematicsSuite extends munit.FunSuite:
  test("the reference laws pass the four examples to judge and the four held out"):
    assertEquals(LawTeacher.judge(Kinematics.reference, Kinematics.train), okay.teach.RuleTeacher.Verdict(4, 4, ""))
    assertEquals(LawTeacher.judge(Kinematics.reference, Kinematics.held), okay.teach.RuleTeacher.Verdict(4, 4, ""))

  test("every reference law is reached by choosing over the lesson's variables"):
    val words = LawWords(Kinematics.variables)
    for l <- Kinematics.reference.laws do
      assertEquals(Laws.reach(LawSpace)(words, "", l.copy(name = "law")), None, l.toString)

  test("the variables the lesson names are the reference's"):
    assertEquals(Kinematics.variables.map(v => v.name -> v.dim),
      Kinematics.reference.variables.map(v => v.name -> v.dim))
