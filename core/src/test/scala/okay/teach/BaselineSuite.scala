package okay.teach

/** THE BASELINE: a word search learned from training questions only */
class BaselineSuite extends munit.FunSuite:
  test("a text is read as the meaning whose training questions share its rarer words; none when it shares none"):
    val cases = Vector(Case("en", "how much income tax on my salary", "scale"),
      Case("en", "the tax bracket above 120000", "scale"),
      Case("pl", "ile wynosi składka zdrowotna na ryczałcie", "lump.health"),
      Case("en", "health contribution when I pay ryczalt", "lump.health"))
    val read = Baseline.words(cases)
    assertEquals(read("which bracket is my salary in"), Some("scale"))
    assertEquals(read("Składka zdrowotna, ryczałt — ile?"), Some("lump.health"))
    assertEquals(read("xyz"), None)
