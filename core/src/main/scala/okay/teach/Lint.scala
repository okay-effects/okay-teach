package okay.teach

/**
 * A MEASURING SET CHECKED BEFORE IT MEASURES (specs/teach.md §7.6; theory §4, F4): a sealed set whose texts are not
 * whole questions measures its own breakage. The book's first fetch took a formula's MathML lines for questions — 36
 * of 134 fragments ("80 m /s"), read with confident wrong margins. What comes back is counts and the meanings' names,
 * never a text (L1): who reads it learns the set is broken, not what it asks.
 */
object Lint:
  /** the problems of a set of cases: texts too short to be a question, texts twice, meanings with fewer than `least` */
  def cases[T](cs: Vector[Case[T]], show: T => String, shortest: Int = 20, least: Int = 2): Vector[String] =
    val short = cs.count(_.text.trim.length < shortest)
    val twice = cs.size - cs.map(_.text.trim).distinct.size
    val thin = cs.groupBy(_.meant).filter(_._2.size < least).keys.map(show).toVector.sorted
    Vector(
      Option.when(short > 0)(s"$short texts shorter than $shortest characters (fragments, not questions?)"),
      Option.when(twice > 0)(s"$twice texts twice"),
      Option.when(thin.nonEmpty)(s"meanings with fewer than $least texts: ${thin.mkString(", ")}")).flatten
