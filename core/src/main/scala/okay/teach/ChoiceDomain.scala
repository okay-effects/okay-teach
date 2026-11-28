package okay.teach

import okay.frames.{Choice, Hole, Space}

/**
 * A CHOICE AMONG GIVEN OPTIONS AS A DOMAIN (SPRINT T7): the data is the options offered, the meaning the one
 * chosen, the text whatever the chooser reads. The teacher's own decisions are such a domain — Σ in words, the
 * actions the laws allow, the one taken — so a small teacher is a reader taught by okay-teach itself: the recursion
 * of theory §7 with nothing new but this file.
 */
final class ChoiceDomain(task: String, hole: String = "action") extends Domain[Vector[String], String]:
  val space: Space[Vector[String], String] = new Space[Vector[String], String]:
    def name = hole
    def next(options: Vector[String], text: String, chosen: Vector[Choice]) =
      Option.when(chosen.isEmpty)(Hole(hole, options))
    def assemble(options: Vector[String], chosen: Vector[Choice]) =
      chosen.headOption.map(_.option).filter(options.contains)
    def path(options: Vector[String], meant: String) = Vector(Choice(hole, meant))
    def say(options: Vector[String], meant: String, language: String) = meant
    def parse(options: Vector[String], words: String, language: String) = Option.when(options.contains(words))(words)
  def system(options: Vector[String]): String = task
  override def line(h: Hole, option: String): String = s"$hole: $option"
  override def languages: Vector[String] = Vector("en")
