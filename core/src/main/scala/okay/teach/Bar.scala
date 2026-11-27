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

  /** a pair bar chosen: one meaning shown at margins ≥ `one`, the two likeliest at margins in [`two`, `one`), a person
   * asked below `two`; `wrong`: answered and the meant shown in neither */
  final case class Pair(one: Double, two: Double, answeredOne: Int, answeredTwo: Int, wrong: Int, total: Int):
    def said: String =
      val o = if one.isPosInfinity then "never" else f"$one%.3f"
      f"pair bar: one at $o (answered $answeredOne), two at $two%.3f (answered $answeredTwo) of $total, wrong $wrong"

  /**
   * THE PAIR BAR (SPRINT T16, for overlapping meanings): readings each its margin, whether the best was right and
   * whether the meant was among the first two. First the one-answer bar (`choose`; none: never one); then, below it,
   * the two-answer bar by the same fixed sequence, counting as wrong every answered reading whose shown meanings miss
   * the meant — the one-answered among them — so the bound (wrong + 1) / (answered + 1) ≤ epsilon covers all answers.
   */
  def pair(results: Seq[(Double, Boolean, Boolean)], epsilon: Double): Option[Pair] =
    val one = choose(results.map(r => (r._1, r._2)), epsilon).fold(Double.PositiveInfinity)(_.bar)
    val below = results.map(_._1).filter(_ < one).distinct.sorted(using Ordering.Double.TotalOrdering).reverse
      .filterNot(_.isNaN) :+ Double.NegativeInfinity
    def at(two: Double): Pair =
      val ones = results.filter(_._1 >= one); val twos = results.filter(r => r._1 < one && r._1 >= two)
      Pair(one, two, ones.size, twos.size, ones.count(!_._2) + twos.count(!_._3), results.size)
    def holds(p: Pair): Boolean = (p.wrong + 1).toDouble / (p.answeredOne + p.answeredTwo + 1) <= epsilon
    val least = math.ceil(1 / epsilon).toInt
    below.iterator.map(at).dropWhile(p => p.answeredOne + p.answeredTwo < least).takeWhile(holds).toVector.lastOption
