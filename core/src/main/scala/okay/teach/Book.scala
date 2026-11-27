package okay.teach

/** a section of a book: its id ("2.3"), its title as printed, its text (never its questions) */
final case class Section(id: String, title: String, text: String)

/**
 * A BOOK OF THE BOOK LOOP (specs/book-loop.md): sections, each `# <id> <title>`, its text, its own questions as
 * `?? ` lines; `#! ` lines are comments. `parse` keeps the questions OUT of the sections' texts — the teacher reads
 * the texts, the questions are the sealed set (theory L1), labelled by the section they are printed under.
 */
final case class Book(sections: Vector[Section], questions: Vector[(String, String)]):
  def section(id: String): Option[Section] = sections.find(_.id == id)

object Book:
  def parse(text: String): Book =
    val Head = """# (\S+)\s+(.*)""".r
    var sections = Vector.empty[Section]
    var questions = Vector.empty[(String, String)]
    var current: Option[(String, String)] = None
    val body = StringBuilder()
    def close(): Unit = current.foreach((id, title) => sections :+= Section(id, title, body.toString.trim))
    for line <- text.linesIterator do
      if line.startsWith("#! ") then ()
      else line match
        case Head(id, title) => close(); current = Some((id, title.trim)); body.clear()
        case q if q.startsWith("?? ") => current.foreach((id, _) => questions :+= (id -> q.drop(3).trim))
        case other => if current.nonEmpty then body.append(other).append('\n')
    close()
    Book(sections, questions.filter(_._2.nonEmpty))
