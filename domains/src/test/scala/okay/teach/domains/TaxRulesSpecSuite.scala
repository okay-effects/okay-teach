package okay.teach.domains

import okay.teach.{DomainSpec, HoleKind, HoleSpec}
import okay.frames.Hole

/** T4's first port: okay-tax's rules as data read exactly as the code domain — the same prompt, the same options,
 * the same lines, on every held-out question (so the same tokens before the model, the same reading) */
class TaxRulesSpecSuite extends munit.FunSuite:
  private val rules = okay.tax.Rules.all
  val spec = DomainSpec("tax-rules", TaxRules.system(()), Vector(HoleSpec("rule",
    HoleKind.Enum(rules.map(_.id), rules.map(r => r.id -> r.title("en")).toMap))))

  test("the spec's JSON reads back, and its domain prompts, offers and writes lines as TaxRules does"):
    val d = DomainSpec.fromJson(DomainSpec.toJson(spec)).fold(e => fail(e), identity).domain
    val texts = java.nio.file.Files.readAllLines(java.nio.file.Path.of("../okay-tax/data/heldout.tsv"))
      .toArray.toVector.map(_.toString).filterNot(l => l.startsWith("#") || l.isBlank).map(_.split('\t')(2))
    assert(texts.size == 192)
    assertEquals(d.system(Map.empty), TaxRules.system(()))
    for t <- texts do
      val a = TaxRules.space.next((), t, Vector.empty).get
      val b = d.space.next(Map.empty, t, Vector.empty).get
      assertEquals(b.options, a.options)
      assertEquals(b.options.map(o => d.line(b, o)), a.options.map(o => TaxRules.line(Hole("rule", a.options), o)))

  test("the example spec file: read by the command as a domain, its 25 meanings enumerated, each said and read back"):
    val t = Teach.fromSpec(java.nio.file.Path.of("examples/tax-rules.json"))
    assertEquals(t.meanings.size, okay.tax.Rules.all.size)
    for m <- t.meanings do assertEquals(t.read(t.show(m)), m)
    assertEquals(t.domain.system(Map.empty), TaxRules.system(()))
