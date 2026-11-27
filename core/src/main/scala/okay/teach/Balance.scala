package okay.teach

/**
 * THE MEANINGS' PROPORTIONS KEPT (specs/teach.md §7.10–7.11): a reader trained on more questions of some meanings
 * reads more texts as those — questions added to confused meanings pulled ch. 3 from 71 to 60, a merged meaning with
 * twice the questions took 22 of another's. Each meaning's cases cut to the median count (a seeded choice, the order
 * kept); meanings below the median keep all theirs.
 */
object Balance:
  def toMedian[T](cases: Vector[Case[T]], seed: Long = 0): Vector[Case[T]] =
    val by = cases.groupBy(_.meant)
    if by.isEmpty then cases
    else
      val sizes = by.values.map(_.size).toVector.sorted
      val median = sizes(sizes.size / 2)
      val rnd = scala.util.Random(seed)
      val kept = by.values.flatMap(cs => if cs.size <= median then cs else rnd.shuffle(cs).take(median)).toSet
      cases.filter(kept)
