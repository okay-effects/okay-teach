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
  /** the languages questions are asked in */
  def languages: Vector[String] = Vector("en", "pl", "ru", "uk")

/** a text and the meaning it means: what a teacher writes, a training example, a held-out case */
final case class Case[T](language: String, text: String, meant: T)
