package okay.teach

/**
 * THE BASELINE EVERY LESSON MUST BEAT (theory L7; SPRINT T12): a reader that needs no model — a word search learned
 * from the TEACHER'S training questions alone, so it never sees the sealed set. Each meaning is the bag of its
 * training questions' word stems (diacritics folded, the first five letters); a text is read as the meaning whose
 * stems it shares most, each shared stem weighed by how few meanings have it. A taught model that does not beat this
 * has learned nothing a word count could not — the skill's check found the gate compared taught with untaught only.
 */
object Baseline:
  private val folded = "ąćęłńóśźż".zip("acelnoszz").toMap

  def stems(text: String): Vector[String] =
    text.toLowerCase.map(c => folded.getOrElse(c, c)).map(c => if c.isLetterOrDigit then c else ' ').split(' ')
      .toVector.filter(_.length >= 3).map(_.take(5))

  /** the search learned from the cases: a text to its meaning, none when it shares no stem with any */
  def words[T](cases: Vector[Case[T]]): String => Option[T] =
    val r = ranked(cases); text => r(text).headOption

  /** the meanings the search ranks for a text, the best first (those sharing a stem only) — its first two are what
   * a taught reader's two readings are compared with (SPRINT T16) */
  def ranked[T](cases: Vector[Case[T]]): String => Vector[T] =
    val bags: Map[T, Set[String]] = cases.groupBy(_.meant).map((m, cs) => m -> cs.flatMap(c => stems(c.text)).toSet)
    val df: Map[String, Int] = bags.values.toVector.flatten.groupBy(identity).map((w, ws) => w -> ws.size)
    val n = bags.size.toDouble
    val order = cases.map(_.meant).distinct
    text =>
      val q = stems(text).toSet
      val scored = order.map(m => m -> q.toVector.map(w => if bags(m)(w) then math.log(1 + n / df(w)) else 0.0).sum)
      scored.filter(_._2 > 0).sortBy(-_._2).map(_._1)
