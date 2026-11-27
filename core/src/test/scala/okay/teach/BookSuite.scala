package okay.teach

/** THE BOOK'S FORM: sections with their texts, their questions kept out of the texts and labelled */
class BookSuite extends munit.FunSuite:

  test("two sections merged into one meaning, the questions relabelled; rendered and parsed back (T18)"):
    val b = Book.parse("# 3.2 Graphical\nDraw arrows.\n?? Draw it?\n# 3.3 Analytical\nUse sines.\n?? Sine it?\n" +
      "# 3.4 Projectiles\nThrow.\n?? Throw it?")
    val m = b.merge("3.2", "3.3").get
    assertEquals(m.sections.map(_.id), Vector("3.2+3.3", "3.4"))
    assertEquals(m.sections.head.title, "Graphical / Analytical")
    assertEquals(m.questions.map(_._1), Vector("3.2+3.3", "3.2+3.3", "3.4"))
    assertEquals(Book.parse(m.render), m)
    assertEquals(b.merge("3.2", "9.9"), None)
  test("sections, texts without the questions, the questions labelled by their section, comments skipped"):
    val b = Book.parse("#! a comment\n# 2.1 Displacement\nDisplacement is the change in position.\n?? What is it?\n" +
      "# 2.2 Vectors\nA vector has a direction.\n?? Is speed a vector?\n?? Give an example.\n")
    assertEquals(b.sections.map(_.id), Vector("2.1", "2.2"))
    assertEquals(b.sections.map(_.title), Vector("Displacement", "Vectors"))
    assert(!b.sections.exists(_.text.contains("??")))
    assertEquals(b.questions, Vector("2.1" -> "What is it?", "2.2" -> "Is speed a vector?", "2.2" -> "Give an example."))
