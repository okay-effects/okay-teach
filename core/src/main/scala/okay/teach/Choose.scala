package okay.teach

import okay.frames.{Hole, Reading, Space}
import okay.llm.{Ask, Llm, Sampling}
import okay.scala3.*

/**
 * ANY MODEL READS ANY SPACE BY CHOOSING (specs/teach.md §5): a model that only generates text (a teacher behind
 * `Llm`, no access to its scores) reads a space hole by hole, each time asked to pick one of the numbered options —
 * the answer a number, parsed strictly. It cannot write what is not an option, so a big model that fails at writing
 * a formal text whole (Datalog rules: 0 of 16 for the local 35B, okay-study §4) is asked only what it can do —
 * choose. The chosen option scores 1, the others 0 (margin 1); an answer that is no option scores all 0 (the first
 * taken with margin 0: a doubt, never a silent answer).
 */
object Choose:
  /** the question of one hole: the task, the text, the choices so far, the options numbered */
  def question[D, T](r: Reading[D, T], h: Hole, task: String): String =
    val sofar = if r.chosen.isEmpty then "(none yet)" else r.chosen.map(c => s"${c.hole} = ${c.option}").mkString("\n")
    s"""$task
       |
       |${r.text}
       |
       |Chosen so far:
       |$sofar
       |
       |Now choose ${h.name}, one of:
       |${h.options.zipWithIndex.map((o, i) => s"${i + 1}. $o").mkString("\n")}
       |
       |Answer with the number alone.""".stripMargin

  /** the number an answer gives, when it is one of 1 … n (the last number in the reply: a model may think first) */
  def number(reply: String, n: Int): Option[Int] =
    "\\d+".r.findAllIn(reply).toVector.lastOption.flatMap(_.toIntOption).filter(k => k >= 1 && k <= n)

  def read[D, T, R[_]](space: Space[D, T], data: D, text: String, task: String, maxTokens: Int = 64)(
      using In[Llm, R]): Reading[D, T] ! R =
    def step(r: Reading[D, T]): Reading[D, T] ! R = r.hole match
      case Some(h) if h.options.nonEmpty =>
        val next: Reading[D, T] ! R = r.forced match
          case Some(only) => pure(r.chose(only))
          case None =>
            val ask = Ask(Vector(Teacher.message("user", question(r, h, task))), maxTokens = maxTokens,
              sampling = Sampling(temperature = Some(0.0)))
            Llm.complete[R](ask).map(done => number(done.reply.content, h.options.size)
              .fold(r.weighed(Vector.fill(h.options.size)(0.0)))(k =>
                r.weighed(Vector.tabulate(h.options.size)(i => if i == k - 1 then 1.0 else 0.0))))
        next.flatMap(step)
      case _ => pure(r)
    step(Reading(space, data, text))
