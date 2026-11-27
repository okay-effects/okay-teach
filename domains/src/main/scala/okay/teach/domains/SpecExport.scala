package okay.teach.domains

import okay.teach.{DomainSpec, HoleKind, HoleSpec}

import java.nio.file.{Files, Path}

/** `sbt "domains/runMain okay.teach.domains.SpecExport"` — the examples a skill user starts from: okay-tax's rules
 * as a JSON spec (examples/tax-rules.json), made from okay-tax's own data */
object SpecExport:
  val taxRules: DomainSpec =
    val rules = okay.tax.Rules.all
    DomainSpec("tax-rules", TaxRules.system(()), Vector(HoleSpec("rule",
      HoleKind.Enum(rules.map(_.id), rules.map(r => r.id -> r.title("en")).toMap))))

  def main(args: Array[String]): Unit =
    Files.createDirectories(Path.of("examples"))
    Files.writeString(Path.of("examples/tax-rules.json"), okay.effects.json.Json.pretty(DomainSpec.toJson(taxRules)))
    println("examples/tax-rules.json")
