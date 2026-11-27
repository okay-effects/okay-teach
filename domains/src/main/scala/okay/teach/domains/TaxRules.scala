package okay.teach.domains

import okay.frames.{Choice, Hole, Space}
import okay.tax.Rules
import okay.teach.Domain

/**
 * OKAY-TAX'S RULES AS A DOMAIN (okay-tax specs/tax.md §6): a question about Polish tax law read into the one rule it
 * asks about. One hole, `rule`, its options the rules' ids; a choice written as the line "rule: <English title>"
 * (as okay-desk's `RuleLines` wrote it, measured there untaught). The canonical words of a rule: its English title
 * and what it says — what the teacher writes questions for.
 */
object TaxRules extends Domain[Unit, String]:
  object space extends Space[Unit, String]:
    def name: String = "tax rules"
    def next(u: Unit, text: String, chosen: Vector[Choice]): Option[Hole] =
      Option.when(chosen.isEmpty)(Hole("rule", Rules.all.map(_.id)))
    def assemble(u: Unit, chosen: Vector[Choice]): Option[String] =
      chosen match
        case Vector(c) if Rules.all.exists(_.id == c.option) => Some(c.option)
        case _ => None
    def path(u: Unit, id: String): Vector[Choice] = Vector(Choice("rule", id))
    def say(u: Unit, id: String, language: String): String =
      Rules.all.find(_.id == id).fold(id)(r => s"${r.title("en")} — ${r.said("en")}")
    def parse(u: Unit, words: String, language: String): Option[String] =
      Rules.all.map(_.id).find(say(u, _, language) == words)

  override def about(u: Unit, id: String): String = Rules.all.find(_.id == id).fold(id)(r =>
    s"The question is about this rule: ${r.title("en")}.\nFor you only — the answer, which the person asking does " +
      s"NOT know and must not be quoted: ${r.said("en")}")
  def system(u: Unit): String =
    "You read a question about Polish tax law into the one rule it asks about, a line: rule: TITLE.\n" +
      Rules.all.map(r => s"rule: ${r.title("en")}").mkString("\n")
  override def line(hole: Hole, option: String): String =
    s"rule: ${Rules.all.find(_.id == option).fold(option)(_.title("en"))}"

  val topic: String = "Questions people ask about Polish income tax, social and health contributions (PIT, ZUS), " +
    "employment (UoP) and running a sole business (B2B, JDG)."
