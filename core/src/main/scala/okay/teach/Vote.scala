package okay.teach

/**
 * READERS VOTING (specs/teach.md §7.8): several adapters taught on the same domain with other seeds read the same
 * text; each gives two votes to its best reading and one to its runner-up; the most voted is the answer, the next
 * the second. On the book's 98 questions four adapters voted 79 first and 91 in two — each alone 68 to 74 and 85 to
 * 89. Their AGREEMENT is no guarantee: all four agreed on 53 and 5 of them were wrong (they learn the same mistakes
 * from the same teacher); a bar still needs its calibration.
 */
object Vote:
  /** the vote over readers' (best, runner-up): the first two meanings, and how many votes the first had of all */
  final case class Voted[T](first: Option[T], second: Option[T], votes: Int, of: Int):
    def inTwo(meant: T): Boolean = first.contains(meant) || second.contains(meant)

  def of[T](readings: Vector[(Option[T], Option[T])]): Voted[T] =
    val order = readings.flatMap((a, b) => a.toVector ++ b.toVector).distinct
    val votes = order.map(m => m -> readings.map((a, b) =>
      (if a.contains(m) then 2 else 0) + (if b.contains(m) then 1 else 0)).sum)
    // the most voted first; a tie goes to the meaning read first by the first reader
    val ranked = votes.sortBy(-_._2)
    Voted(ranked.headOption.map(_._1), ranked.lift(1).map(_._1), ranked.headOption.fold(0)(_._2), 3 * readings.size)
