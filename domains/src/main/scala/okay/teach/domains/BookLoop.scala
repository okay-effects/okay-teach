package okay.teach.domains

import okay.frames.{Hole, Space}
import okay.teach.{Book, Domain, DomainSpec, HoleKind, HoleSpec}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/**
 * THE BOOK LOOP'S DOMAIN (specs/book-loop.md): a question → the one section of the book it is about. The spec is
 * built from the book itself (one enum hole `topic`, an option a section, shown as its printed title); the teacher
 * is told the section's TEXT (`about`, its own reading — never the book's questions, which `Book.parse` keeps out);
 * the sealed set is the book's own questions, each labelled by the section it is printed under.
 */
final class BookDomain(val name: String, val book: Book, maxText: Int = 12000, size: Int = 2000, most: Int = 8)
    extends Domain[DomainSpec.Data, DomainSpec.Meaning]:
  val spec: DomainSpec = DomainSpec(name,
    "You read a question into the one section of a textbook it is about, a line: topic: TITLE.\n" +
      book.sections.map(s => s"topic: ${s.title}").mkString("\n"),
    Vector(HoleSpec("topic", HoleKind.Enum(book.sections.map(_.id), book.sections.map(s => s.id -> s.title).toMap))),
    languages = Vector("en"))
  private val inner = spec.domain
  def space: Space[DomainSpec.Data, DomainSpec.Meaning] = inner.space
  def system(data: DomainSpec.Data): String = inner.system(data)
  override def line(hole: Hole, option: String): String = inner.line(hole, option)
  override def languages: Vector[String] = spec.languages
  override def about(data: DomainSpec.Data, meant: DomainSpec.Meaning): String =
    meant.collectFirst { case ("topic", id) => id }.flatMap(book.section).fold(meant.toString)(s =>
      s"The section the question is about: ${s.id} ${s.title}. Its text, for you only — the person asking has not " +
        s"read it and does not quote it:\n${s.text.take(maxText)}")

  /** the section cut into passages of about `size` characters at paragraph ends, at most `most` — a teacher shown a
   * whole section asks generic questions; a book's own ask about its specifics (SPRINT T15) */
  override def passages(data: DomainSpec.Data, meant: DomainSpec.Meaning): Vector[String] =
    meant.collectFirst { case ("topic", id) => id }.flatMap(book.section).fold(Vector(about(data, meant))) { s =>
      val paragraphs = s.text.split("\n\\s*\n").toVector.map(_.trim).filter(_.length > 40)
      val chunks = paragraphs.foldLeft(Vector.empty[String])((acc, p) =>
        if acc.nonEmpty && acc.last.length + p.length < size then acc.init :+ (acc.last + "\n\n" + p) else acc :+ p)
      val picked = if chunks.size <= most then chunks
        else (0 until most).map(i => chunks(i * chunks.size / most)).toVector
      picked.map(c => s"The section: ${s.id} ${s.title}. A passage of it, for you only — the person asking has not " +
        s"read it and does not quote it:\n${c.take(maxText)}") }

  /** a book is asked as a student and its own exercises ask: conceptual questions and problems with numbers */
  override def asking(n: Int, language: String): String =
    s"Write $n different questions in $language that a student of this book, or the book's own end-of-section " +
      "exercises, would ask about what this passage teaches: conceptual questions (why, what if, compare, give an " +
      "example) and problems with numbers of their own. Do not quote the passage, do not name its section or its " +
      "terms' definitions word for word; vary the length and the situation."

object BookLoop:
  /** the book's domain as the command knows it, and its sealed set written beside the book (`<book>.sealed.tsv`) */
  def load(file: Path): (Taught[DomainSpec.Data, DomainSpec.Meaning], Path) =
    val book = Book.parse(Files.readString(file))
    val name = file.getFileName.toString.stripSuffix(".txt")
    val d = BookDomain(name, book)
    val space = d.space
    val taught = Taught[DomainSpec.Data, DomainSpec.Meaning](d, Map.empty,
      book.sections.map(s => Vector("topic" -> s.id)), s"Questions people ask about the topics of the book $name.",
      m => space.say(Map.empty, m, "en"),
      w => space.parse(Map.empty, w, "en").getOrElse(sys.error(s"not a section of $name: $w")))
    val sealedFile = Path.of(file.toString.stripSuffix(".txt") + ".sealed.tsv")
    Files.write(sealedFile, book.questions.map((id, q) => s"topic: $id\ten\t${q.replace('\t', ' ')}").asJava)
    (taught, sealedFile)
