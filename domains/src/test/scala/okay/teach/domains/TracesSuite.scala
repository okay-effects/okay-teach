package okay.teach.domains

import okay.effects.json.Json
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** THE TRACES ARE DATA A TEACHER CAN LEARN FROM (specs/traces.md): every step parses, has the fields, names an
 * operation of the Lesson and a verdict of the closed set, and the steps of a file are in order */
class TracesSuite extends munit.FunSuite:
  private val ops = Set("Ask", "Train", "Measure", "Calibrate", "Diagnose", "Repair", "Release", "Uncovered")
  private val verdicts = Set("helped", "hurt", "neutral", "informative")

  test("every trace step is well-formed"):
    val files = Files.list(Path.of("data/traces")).iterator.asScala.filter(_.toString.endsWith(".jsonl")).toVector
    assert(files.nonEmpty)
    for f <- files do
      val steps = Files.readAllLines(f).asScala.toVector.filter(_.trim.nonEmpty).map(Json.fromText)
      for s <- steps do
        for k <- Vector("source", "domain", "state", "op", "args", "outcome", "verdict") do
          assert(s.field(k).nonEmpty, s"$f: no $k in ${s.render}")
        assert(ops(s("op").string), s"$f: op ${s("op").string}")
        assert(verdicts(s("verdict").string), s"$f: verdict ${s("verdict").string}")
      assertEquals(steps.map(_("step").int), (1 to steps.size).toVector, f.toString)
