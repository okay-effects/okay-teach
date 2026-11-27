package okay.teach

import okay.effects.json.Json
import okay.frames.Laws
import okay.llm.{Ask, Llm, Sampling}
import okay.scala3.*

/**
 * THE TEACHER'S QUESTIONS (specs/teach.md §3): for a meaning, said in its canonical words, a teacher (any model
 * behind `Llm`) writes how people would ask for it, in each of the domain's languages. A question is KEPT only when
 * the space offers its meaning for it step by step (okay-frames `Laws.reach`): a question that does not write a
 * number the meaning needs, or names a value the space does not offer for it, would teach the reader what it cannot
 * choose. The kept are training texts; the held-out questions are never written here.
 */
object Teacher:
  /** what one meaning gave: the questions kept, and those refused with why */
  final case class Written[T](meant: T, kept: Vector[Case[T]], refused: Vector[(String, String)],
      reply: String = "",
      byStop: Boolean = true)

  def message(role: String, text: String): Json = Json.obj("role" -> Json.Str(role), "content" -> Json.Str(text))

  /** the teacher's task: the domain, the meaning in words, `n` questions a language, one a line "<lang>: <text>" */
  def task[D, T](domain: Domain[D, T], data: D, meant: T, n: Int, about: String): String =
    val words = domain.space.say(data, meant, "en")
    s"""$about
       |
       |The meaning: $words
       |
       |Write $n different questions or requests a person could write that mean exactly this, in each of these
       |languages: ${domain.languages.mkString(", ")}. Vary the words, the order, the length and the register, as real
       |people do; keep every number the meaning needs written in the question. One per line, as
       |"<language code>: <text>", nothing else.""".stripMargin

  /** the lines "<lang>: <text>" of a reply, in the domain's languages */
  def parse(reply: String, languages: Vector[String]): Vector[(String, String)] =
    reply.linesIterator.map(_.trim.stripPrefix("- ")).flatMap { l =>
      val i = l.indexOf(':')
      Option.when(i > 0)((l.take(i).trim.toLowerCase, l.drop(i + 1).trim.stripPrefix("\"").stripSuffix("\"")))
        .filter((lang, text) => languages.contains(lang) && text.nonEmpty)
    }.toVector.distinct

  def write[D, T, R[_]](domain: Domain[D, T], data: D, meant: T, n: Int, about: String, seed: Long = 0)(
      using In[Llm, R]): Written[T] ! R =
    val ask = Ask(Vector(message("user", task(domain, data, meant, n, about))), maxTokens = 400 * n + 400,
      sampling = Sampling(temperature = Some(0.8), seed = Some(seed)))
    Llm.complete[R](ask).map { done =>
      val asked = parse(done.reply.content, domain.languages)
      val judged = asked.map((lang, text) => (lang, text, Laws.reach(domain.space)(data, text, meant)))
      Written(meant, judged.collect { case (lang, text, None) => Case(lang, text, meant) },
        judged.collect { case (_, text, Some(why)) => text -> why }).copy(reply = done.reply.content,
        byStop = done.byStop)
    }

  /** every meaning written for, in turn */
  def writeAll[D, T, R[_]](domain: Domain[D, T], data: D, meanings: Vector[T], n: Int, about: String)(
      using In[Llm, R]): Vector[Written[T]] ! R =
    meanings.zipWithIndex.foldLeft(pure[Vector[Written[T]], R](Vector.empty)) { case (acc, (m, i)) =>
      acc.flatMap(done => write(domain, data, m, n, about, i.toLong).map(done :+ _)) }
