package okay.teach

import okay.frames.Laws

/** THE DOMAIN AS DATA: its kinds, `when`, the laws, and the JSON form read back */
class DomainSpecSuite extends munit.FunSuite:
  private val pay = DomainSpec("pay", "You read a question about pay into lines.", Vector(
    HoleSpec("contract", HoleKind.Enum(Vector("uop", "b2b"))),
    HoleSpec("gross", HoleKind.Numbers(), when = Map("contract" -> Vector("uop"))),
    HoleSpec("revenue", HoleKind.Numbers(), when = Map("contract" -> Vector("b2b"))),
    HoleSpec("form", HoleKind.Enum(Vector("scale", "linear", "lump"), Map("lump" -> "ryczałt")),
      when = Map("contract" -> Vector("b2b"))),
    HoleSpec("years", HoleKind.Derived("differences"), notSaid = true)))
  private val space = pay.domain.space
  private val data: DomainSpec.Data = Map.empty

  test("the numbers a text writes: grouped thousands, k, decimals"):
    assertEquals(DomainSpec.numbers("20 000 zł, or 20k, or 1 000,50"), Vector("20000", "1000.5"))
    assertEquals(DomainSpec.derived("differences", "I'm 35 and retire at 60"), Vector("25"))

  test("holes asked by `when`, options from the text, the laws on meanings the text offers"):
    val uop = Vector("contract" -> "uop", "gross" -> "15000", "years" -> DomainSpec.notSaid)
    val b2b = Vector("contract" -> "b2b", "revenue" -> "20000", "form" -> "lump", "years" -> "25")
    assertEquals(Laws.reach(space)(data, "UoP at 15 000 gross", uop), None)
    assertEquals(Laws.reach(space)(data, "B2B 20 000 a month on ryczałt, 35 now, retire at 60", b2b), None)
    assert(Laws.reach(space)(data, "B2B on ryczałt", b2b).nonEmpty, "no number written, none offered")
    for m <- Vector(uop, b2b) do assertEquals(Laws.roundTrip(space)(data, m, Seq("en")), None)
    assertEquals(space.say(data, b2b, "en"), "contract: b2b; revenue: 20000; form: ryczałt; years: 25")
    assertEquals(pay.domain.line(okay.frames.Hole("form", Vector()), "lump"), "form: ryczałt")

  test("the JSON form reads back to the same spec"):
    val back = DomainSpec.fromJson(DomainSpec.toJson(pay))
    assertEquals(back, Right(pay))
    assert(DomainSpec.fromJson(okay.effects.json.Json.fromText("""{"name": "x", "system": "y", "holes": [{"key":
      "a", "kind": "code"}]}""")).isLeft, "a kind outside the closed set is refused")
