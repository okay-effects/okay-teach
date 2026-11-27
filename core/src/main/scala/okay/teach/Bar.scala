package okay.teach

/**
 * THE BAR THAT KEEPS WRONG ANSWERS RARE (specs/teach.md §4; okay-desk specs/guarantees.md, the understanding's
 * guarantee): readings of calibration texts, each its margin and whether it was right; a reading answers when its
 * margin is at least the bar, else it asks. The bar is chosen by fixed-sequence testing (Learn then Test, Angelopoulos
 * et al. 2021): bars from the strictest down, each kept while the wrong among the answered stay within `epsilon` by
 * the finite-sample bound (wrong + 1) / (answered + 1) ≤ epsilon; the last kept is the bar. Valid for texts drawn
 * like the calibration's — never for others (a new domain, a new kind of question needs its own calibration).
 */
object Bar:
  final case class Chosen(bar: Double, answered: Int, wrong: Int, total: Int):
    def said: String = f"bar $bar%.3f: answered $answered of $total, wrong $wrong"

  def choose(results: Seq[(Double, Boolean)], epsilon: Double): Option[Chosen] =
    val bars = (results.map(_._1).distinct.sorted(using Ordering.Double.TotalOrdering).reverse :+
      Double.NegativeInfinity).filterNot(_.isNaN)
    def at(bar: Double): Chosen =
      val answered = results.filter(_._1 >= bar)
      Chosen(bar, answered.size, answered.count(!_._2), results.size)
    def holds(c: Chosen): Boolean = (c.wrong + 1).toDouble / (c.answered + 1) <= epsilon
    // (the sequence begins where the bound can hold at all — at least 1 / epsilon answered: fixed before any test)
    val least = math.ceil(1 / epsilon).toInt
    bars.iterator.map(at).dropWhile(_.answered < least).takeWhile(holds).toVector.lastOption
