package okay.teach.domains

import java.nio.file.{Files, Path}

/** THE BOOK LOOP ON A REAL BOOK (OpenStax ch. 2, fetched by scripts/fetch-openstax-chapter.zsh; skipped if absent):
 * every section a meaning, every question sealed and labelled — and no question leaks into a text the teacher reads */
class BookLoopSuite extends munit.FunSuite:
  private val file = Path.of("books/openstax-cp2e-ch2.txt")

  test("the book's sections are the meanings, its questions the sealed set, none of them in the teacher's texts"):
    assume(Files.exists(file), "books/openstax-cp2e-ch2.txt not fetched")
    val (t, sealedFile) = BookLoop.load(file)
    val book = t.domain.asInstanceOf[BookDomain].book
    assertEquals(t.meanings.size, 8)
    assert(book.questions.size >= 90, book.questions.size.toString)
    // whole questions: no formula fragment ("80 m /s") taken for a question, none twice (the fetch, specs/teach.md §7.6)
    assertEquals(book.questions.map(_._2).filter(_.trim.length < 20), Vector.empty)
    assertEquals(book.questions.size, book.questions.map(_._2).distinct.size)
    val texts = book.sections.map(_.text.replaceAll("\\s+", " "))
    val leaked = book.questions.map(_._2.replaceAll("\\s+", " ").take(60)).filter(q => q.length > 30 &&
      texts.exists(_.contains(q)))
    assertEquals(leaked, Vector.empty, "a sealed question appears in a section's text")
    val rows = Teach.cases(t, sealedFile)
    assertEquals(rows.size, book.questions.size)
    for m <- t.meanings do assertEquals(t.read(t.show(m)), m)

  test("each section cut into 1 to 8 passages, none holding a sealed question"):
    assume(Files.exists(file), "books/openstax-cp2e-ch2.txt not fetched")
    val (t, _) = BookLoop.load(file)
    val d = t.domain.asInstanceOf[BookDomain]
    val counts = t.meanings.map(m => d.passages(Map.empty, m))
    println(s"  passages by section: ${counts.map(_.size).mkString(", ")}")
    assert(counts.forall(p => p.nonEmpty && p.size <= 8))
    val questions = d.book.questions.map(_._2.replaceAll("\\s+", " ").take(60)).filter(_.length > 30)
    assert(!counts.flatten.exists(p => questions.exists(q => p.replaceAll("\\s+", " ").contains(q))))
