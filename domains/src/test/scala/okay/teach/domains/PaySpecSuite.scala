package okay.teach.domains

import okay.frames.Laws
import okay.teach.{DomainSpec, HoleKind, HoleSpec}

/** T4's second port: okay-desk's pay lines (`okay.desk.books.PayLines`) as data — its lines, in its order, written
 * as it writes them (contract; gross, or revenue | rate and days; form; lump rate; zus; sickness; costs; the
 * compared gross; end) */
class PaySpecSuite extends munit.FunSuite:
  private val b2b = Vector("b2b", "compare")
  val pay = DomainSpec("pay",
    "You read a question about take-home pay in Poland into lines, one at a time, each line one of the options.",
    Vector(
      HoleSpec("contract", HoleKind.Enum(Vector("uop", "b2b", "compare"))),
      HoleSpec("gross", HoleKind.Numbers(), when = Map("contract" -> Vector("uop"))),
      HoleSpec("amount", HoleKind.Either(Vector("revenue" -> HoleKind.Numbers(), "rate" -> HoleKind.Numbers())),
        when = Map("contract" -> b2b)),
      HoleSpec("days", HoleKind.Numbers(), when = Map("amount" -> Vector("rate*"))),
      HoleSpec("form", HoleKind.Enum(Vector("scale", "linear", "lump")), when = Map("contract" -> b2b)),
      HoleSpec("lump rate", HoleKind.Enum(Vector("8.5", "12", "14", "15", "17")), when = Map("form" -> Vector("lump"))),
      HoleSpec("zus", HoleKind.Enum(Vector("full", "preferential", "start")), when = Map("contract" -> b2b)),
      HoleSpec("sickness", HoleKind.Enum(Vector("yes", "no")), when = Map("contract" -> b2b)),
      HoleSpec("costs", HoleKind.Numbers(Vector("none")), when = Map("contract" -> b2b)),
      HoleSpec("gross", HoleKind.Numbers(), when = Map("contract" -> Vector("compare")))),
    end = true)
  private val d = pay.domain
  private val none: DomainSpec.Data = Map.empty

  /** the lines a meaning is written in, as the reader writes them */
  private def lines(text: String, m: DomainSpec.Meaning) = okay.teach.Reader.lines(d, none, text, m)

  test("UoP, B2B by a day rate on ryczałt, and a comparison: PayLines' lines, reached and parsed back"):
    val cases = Vector(
      ("UoP 15 000 brutto", Vector("contract" -> "uop", "gross" -> "15000"),
        Vector("contract: uop", "gross: 15000", "end")),
      ("B2B 1000 zł dziennie, 21 dni, ryczałt 12%, pełny ZUS, bez chorobowego, koszty 500",
        Vector("contract" -> "b2b", "amount" -> "rate: 1000", "days" -> "21", "form" -> "lump", "lump rate" -> "12",
          "zus" -> "full", "sickness" -> "no", "costs" -> "500"),
        Vector("contract: b2b", "rate: 1000", "days: 21", "form: lump", "lump rate: 12", "zus: full", "sickness: no",
          "costs: 500", "end")),
      ("compare B2B 20 000 on the linear tax with UoP at 18 000",
        Vector("contract" -> "compare", "amount" -> "revenue: 20000", "form" -> "linear", "zus" -> "full",
          "sickness" -> "no", "costs" -> "none", "gross" -> "18000"),
        Vector("contract: compare", "revenue: 20000", "form: linear", "zus: full", "sickness: no", "costs: none",
          "gross: 18000", "end")))
    for (text, meaning, written) <- cases do
      assertEquals(Laws.reach(d.space)(none, text, meaning), None, text)
      assertEquals(Laws.roundTrip(d.space)(none, meaning, Seq("en")), None, text)
      assertEquals(lines(text, meaning), written, text)

  test("the spec's JSON reads back"):
    assertEquals(DomainSpec.fromJson(DomainSpec.toJson(pay)), Right(pay))
