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
    val blank = scoring match
      case Scoring.ContentFree(b) => Some(b)
      case Scoring.Plain => None
    for
      prompt <- Read.prompt[R](domain.system(data), domain.user(data, text))
      empty <- blank.fold(pure[Option[Array[Int]], R](None))(b =>
        Read.prompt[R](domain.system(data), domain.user(data, b)).map(Some(_)))
      newline <- Read.encode[R]("\n").map(_.head)
      read <- step(domain, newline, prompt, empty, Reading(domain.space, data, text))
    yield read

  private def step[D, T, R[_]](domain: Domain[D, T], newline: Int, context: Array[Int], empty: Option[Array[Int]],
      r: Reading[D, T])(using In[Read, R]): Reading[D, T] ! R =
    r.hole match
      case Some(h) if h.options.nonEmpty =>
        for
          tokens <- Read.each[String, Array[Int], R](h.options)(o => Read.encode[R](domain.line(h, o)))
          lines = tokens.map(_ :+ newline)
          next <- r.forced match
            case Some(only) => pure[Reading[D, T], R](r.chose(only))
            case None =>
              for
                scores <- Read.score[R](context, lines)
                prior <- empty.fold(pure[Option[Vector[Double]], R](None))(e => Read.score[R](e, lines).map(Some(_)))
              yield r.weighed(prior.fold(scores)(p => scores.zip(p).map(_ - _)))
          chosen = h.options.indexOf(next.chosen.last.option)
          rest <- step(domain, newline, context ++ lines(chosen), empty.map(_ ++ lines(chosen)), next)
        yield rest
      case _ => pure(r)

  /** the lines a meaning is written in: its path, each choice as the domain writes it */
  def lines[D, T](domain: Domain[D, T], data: D, text: String, meant: T): Vector[String] =
    val path = domain.space.path(data, meant)
    path.indices.toVector.flatMap(i =>
      domain.space.next(data, text, path.take(i)).map(h => domain.line(h, path(i).option)))

  /** the choices of a reading, for a log */
  def said(chosen: Vector[Choice]): String = chosen.map(c => s"${c.hole}=${c.option}").mkString(", ")
