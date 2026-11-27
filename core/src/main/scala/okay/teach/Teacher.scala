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

  private val names = Map("en" -> "English", "pl" -> "Polish", "ru" -> "Russian", "uk" -> "Ukrainian",
    "de" -> "German", "fr" -> "French", "es" -> "Spanish")

  /**
   * THE TEACHER'S TASK, ONE LANGUAGE (SPRINT T10): the domain, the meaning as the domain tells a teacher of it
   * (`Domain.about`: may include the answer, marked as the teacher's only), and `n` questions in one language as a
   * person who does NOT know the answer writes them — never quoting the answer's facts, numbers only where the person
   * would write them. One a line, nothing else. (T1's questions quoted the rule's figures; asked for all languages at
   * once, the teacher often wrote one a language.)
   */
  def task[D, T](domain: Domain[D, T], data: D, meant: T, n: Int, about: String, language: String): String =
    s"""$about
       |
       |${domain.about(data, meant)}
       |
       |Write $n different messages in ${names.getOrElse(language, language)} that people who need exactly this would
       |write — people who do NOT know the answer: they ask about their own situation, in everyday words, short or
       |long, with or without detail, the way real people write to a forum or a chat. Do not quote the answer, its
       |figures or its wording; write a number only where the person would (their own salary, age, dates). One message
       |a line, nothing else: no numbering, no language labels, no quotes.""".stripMargin

  /** the lines of a reply: numbering, bullets, quotes and a leading language label stripped, empty lines dropped */
  def parse(reply: String): Vector[String] =
    reply.linesIterator.map(_.trim
      .replaceAll("""^(\d+[.)]|[-*•])\s*""", "")
      .replaceAll("""(?i)^(en|pl|ru|uk|english|polish|russian|ukrainian)\s*:\s*""", "")
      .stripPrefix("\"").stripSuffix("\"").trim).filter(_.nonEmpty).toVector.distinct

  def write[D, T, R[_]](domain: Domain[D, T], data: D, meant: T, n: Int, about: String, seed: Long = 0)(
      using In[Llm, R]): Written[T] ! R =
    domain.languages.zipWithIndex.foldLeft(pure[Written[T], R](Written(meant, Vector.empty, Vector.empty))) {
      case (acc, (lang, i)) => acc.flatMap { sofar =>
        val ask = Ask(Vector(message("user", task(domain, data, meant, n, about, lang))), maxTokens = 120 * n + 600,
          sampling = Sampling(temperature = Some(0.9), seed = Some(seed * 10 + i)))
        Llm.complete[R](ask).map { done =>
          val asked = parse(done.reply.content).take(n)
          val judged = asked.map(text => (text, Laws.reach(domain.space)(data, text, meant)))
          sofar.copy(kept = sofar.kept ++ judged.collect { case (text, None) => Case(lang, text, meant) },
            refused = sofar.refused ++ judged.collect { case (text, Some(why)) => text -> why },
            reply = sofar.reply + s"--- $lang\n${done.reply.content}\n", byStop = sofar.byStop && done.byStop)
        } } }

  /** every meaning written for, in turn */
  def writeAll[D, T, R[_]](domain: Domain[D, T], data: D, meanings: Vector[T], n: Int, about: String)(
      using In[Llm, R]): Vector[Written[T]] ! R =
    meanings.zipWithIndex.foldLeft(pure[Vector[Written[T]], R](Vector.empty)) { case (acc, (m, i)) =>
      acc.flatMap(done => write(domain, data, m, n, about, i.toLong).map(done :+ _)) }
