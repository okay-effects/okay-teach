package okay.teach

import okay.frames.{Choice, Reading}
import okay.llm.read.Read
import okay.scala3.*

/**
 * THE READER (specs/teach.md §2): a text read in a domain's space by a language model — okay-frames' `Reading`, each
 * hole's options written as the domain's lines and weighed after the prompt and the lines chosen before
 * (`Read.score`), the best chosen; a hole of one option taken with no forward.
 *
 * CALIBRATED (`Scoring.ContentFree`, after Zhao et al. 2021, "Calibrate before use"): a model weighing options it
 * was not trained on prefers some for themselves — the first listed, the short, the fluent (okay-tax §6.2: a 0.6B
 * chose the first rule 114 times of 192). Each option's score is taken less its score after a text with no content
 * (the same prompt, the same lines before, the text `blank`): what is left is what the text itself adds.
 */
object Reader:
  enum Scoring:
    case Plain
    case ContentFree(blank: String = "N/A")

  def read[D, T, R[_]](domain: Domain[D, T], data: D, text: String, scoring: Scoring = Scoring.Plain)(
      using In[Read, R]): Reading[D, T] ! R =
    readTwo(domain, data, text, scoring, second = false).map(_.first)

  /**
   * TWO READINGS (SPRINT T16): the best, and — when `second` — the runner-up: at the least sure choice its second
   * option taken, the holes after it read again. Where meanings overlap (a book's general section and its
   * neighbours) the meant is often the runner-up; a competence below its bar answers with both.
   */
  final case class Two[D, T](first: Reading[D, T], second: Option[Reading[D, T]]):
    def meanings: Vector[T] = (first.meaning.toVector ++ second.flatMap(_.meaning)).distinct

  def readTwo[D, T, R[_]](domain: Domain[D, T], data: D, text: String, scoring: Scoring = Scoring.Plain,
      second: Boolean = true)(using In[Read, R]): Two[D, T] ! R =
    val blank = scoring match
      case Scoring.ContentFree(b) => Some(b)
      case Scoring.Plain => None
    for
      prompt <- Read.prompt[R](domain.system(data), domain.user(data, text))
      empty <- blank.fold(pure[Option[Array[Int]], R](None))(b =>
        Read.prompt[R](domain.system(data), domain.user(data, b)).map(Some(_)))
      newline <- Read.encode[R]("\n").map(_.head)
      read <- step(domain, newline, prompt, empty, Reading(domain.space, data, text), Vector.empty)
      (first, alts) = read
      // the least sure choice that had a runner-up
      at = first.margins.indices.filter(alts(_).nonEmpty).minByOption(first.margins(_))
      other <- at.filter(_ => second).fold(pure[Option[Reading[D, T]], R](None)) { i =>
        val start = Reading(domain.space, data, text, first.chosen.take(i), first.margins.take(i))
        val holes = start.chosen.indices.flatMap(k =>
          domain.space.next(data, text, start.chosen.take(k)).map(h => domain.line(h, start.chosen(k).option)))
        for
          before <- Read.each[String, Array[Int], R](holes.toVector)(l => Read.encode[R](l))
          lines = before.flatMap(_ :+ newline)
          next = start.chose(alts(i).get)
          h = start.hole.get
          tokens <- Read.encode[R](domain.line(h, alts(i).get)).map(_ :+ newline)
          rest <- step(domain, newline, prompt ++ lines ++ tokens, empty.map(_ ++ lines ++ tokens), next,
            Vector.empty)
        yield Option.when(rest._1.meaning.nonEmpty)(rest._1)
      }
    yield Two(first, other)

  private def step[D, T, R[_]](domain: Domain[D, T], newline: Int, context: Array[Int], empty: Option[Array[Int]],
      r: Reading[D, T], alts: Vector[Option[String]])(
      using In[Read, R]): (Reading[D, T], Vector[Option[String]]) ! R =
    r.hole match
      case Some(h) if h.options.nonEmpty =>
        for
          tokens <- Read.each[String, Array[Int], R](h.options)(o => Read.encode[R](domain.line(h, o)))
          lines = tokens.map(_ :+ newline)
          next <- r.forced match
            case Some(only) => pure[(Reading[D, T], Option[String]), R]((r.chose(only), None))
            case None =>
              for
                scores <- Read.score[R](context, lines)
                prior <- empty.fold(pure[Option[Vector[Double]], R](None))(e => Read.score[R](e, lines).map(Some(_)))
                weighed = prior.fold(scores)(p => scores.zip(p).map(_ - _))
                best = weighed.indices.foldLeft(0)((b, i) => if weighed(i) > weighed(b) then i else b)
                runnerUp = weighed.indices.filter(_ != best).maxByOption(weighed(_)).map(h.options(_))
              yield (r.weighed(weighed), runnerUp)
          chosen = h.options.indexOf(next._1.chosen.last.option)
          rest <- step(domain, newline, context ++ lines(chosen), empty.map(_ ++ lines(chosen)), next._1,
            alts :+ next._2)
        yield rest
      case _ => pure((r, alts))

  /** the lines a meaning is written in: its path, each choice as the domain writes it */
  def lines[D, T](domain: Domain[D, T], data: D, text: String, meant: T): Vector[String] =
    val path = domain.space.path(data, meant)
    path.indices.toVector.flatMap(i =>
      domain.space.next(data, text, path.take(i)).map(h => domain.line(h, path(i).option)))

  /** the choices of a reading, for a log */
  def said(chosen: Vector[Choice]): String = chosen.map(c => s"${c.hole}=${c.option}").mkString(", ")
