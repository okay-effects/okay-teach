package okay.teach

import okay.frames.{Hole, Space}

/**
 * A DOMAIN TO TEACH (specs/teach.md §1): a space of meanings (okay-frames) and how a reader is asked about it — the
 * system prompt, the user's message for a text over the data, and each choice written as a line of the reader's
 * answer. One definition serves the reading (`Reader`), the teacher's questions (`Teacher`), the training examples
 * (`Examples`) and the laws.
 */
trait Domain[D, T]:
  def space: Space[D, T]
  /** what the reader is told: the language of its lines */
  def system(data: D): String
  /** the user's message: the data in words, then the text */
  def user(@annotation.unused data: D, text: String): String = text
  /** a choice as a line of the answer (the option itself by default; "label: option" where holes are slots) */
  def line(@annotation.unused hole: Hole, option: String): String = option
  /** what a teacher is told of a meaning (its canonical words by default; a domain may add what only the teacher
   * should know, marked so: the answer behind a question, never to be quoted) */
  def about(data: D, meant: T): String = s"The meaning: ${space.say(data, meant, "en")}"
  /** what the teacher is told, one piece at a time: n questions are asked of each (default: `about` alone; a book
   * gives its section passage by passage, so the questions cover all of it — SPRINT T15) */
  def passages(data: D, meant: T): Vector[String] = Vector(about(data, meant))
  /** who asks, and how: the teacher's instruction for n questions in a language (default: people who do NOT know
   * the answer, in everyday words; a book: a student and the book's own exercises) */
  def asking(n: Int, language: String): String =
    s"Write $n different messages in $language that people who need exactly this would write — people who do NOT " +
      "know the answer: they ask about their own situation, in everyday words, short or long, with or without " +
      "detail, the way real people write to a forum or a chat. Do not quote the answer, its figures or its wording; " +
      "write a number only where the person would (their own salary, age, dates)."
  /** the languages questions are asked in */
  def languages: Vector[String] = Vector("en", "pl", "ru", "uk")

/** a text and the meaning it means: what a teacher writes, a training example, a held-out case */
final case class Case[T](language: String, text: String, meant: T)
