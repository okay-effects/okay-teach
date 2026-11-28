package okay.teach.domains

import okay.effects.json.Json
import okay.scala3.*
import okay.teach.{Action, Case, ChoiceDomain, Sigma, TeacherSpace, Teaching}
import okay.teach.mlx.OnGpu

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/**
 * A SMALL TEACHER (SPRINT T7; theory §7, level L1): Qwen3-0.6B + LoRA taught the 35B's decisions
 * (`decisions.jsonl`: the state as read, the actions allowed, the one chosen) through `ChoiceDomain` — the same
 * reader, training and measure as any domain. Measured by agreement with the 35B on decisions held out; then it
 * drives a lesson (`Teach book … small:<adapter>`).
 */
object SmallTeacher:
  final case class Decision(state: String, allowed: Vector[String], chosen: String)

  val domain = ChoiceDomain(Teaching.task)
  private val model = "mlx-community/Qwen3-0.6B-4bit"

  def load(files: Seq[Path], by: String = "35B"): Vector[Decision] =
    files.toVector.flatMap(f => Files.readAllLines(f).asScala.toVector.filterNot(_.isBlank).map(Json.fromText))
      .filter(_("by").string == by).map(j => Decision(j("state").string,
        j("allowed") match { case Json.Arr(xs) => xs.map(_.string).toVector; case _ => Vector.empty },
        j("chosen").string))

  /** `teacher-train <out dir> <epochs> <decisions.jsonl>…` */
  def train(out: Path, epochs: Int, files: Seq[Path]): Unit =
    val ds = load(files)
    val all = ds.flatMap(_.allowed).distinct
    println(s"${ds.size} decisions of the 35B, ${all.size} actions")
    OnGpu.train(domain, all, ds.map(d => Case("en", d.state, d.chosen)), model, out, epochs)

  /** each decision read by the adapter (or untaught) among ITS allowed actions: agreement with the 35B */
  def agree(adapter: Option[Path], ds: Vector[Decision]): Vector[(Decision, Option[String])] =
    val gpu = OnGpu.Gpu(model, adapter)
    try ds.map(d => d -> OnGpu.read(domain, d.allowed, Vector(Case("en", d.state, d.chosen)), gpu).head.meaning)
    finally gpu.close()

  /** `teacher-measure <adapter|-> <decisions.jsonl>…` — counts, and the disagreements as kinds of action */
  def measure(adapter: Option[Path], files: Seq[Path]): Unit =
    val ds = load(files)
    val read = agree(adapter, ds)
    val same = read.count((d, r) => r.contains(d.chosen))
    // the plainest policy for comparison: the first allowed action that is not Stop
    val first = ds.count(d => d.allowed.find(!_.startsWith("stop")).contains(d.chosen))
    println(s"${adapter.fold("untaught")(_.toString)}: agrees with the 35B on $same of ${ds.size} " +
      s"(the first allowed action: $first)")
    def kind(s: String) = s.split(' ').take(2).mkString(" ")
    read.filterNot((d, r) => r.contains(d.chosen)).groupBy((d, r) => (kind(d.chosen), r.fold("nothing")(kind)))
      .toVector.sortBy(-_._2.size).foreach { case ((a, b), v) => println(s"  35B: $a, small: $b — ${v.size}") }

  /** the small teacher as a chooser: Σ in words, the allowed actions, one read (none read: stop) */
  def chooser(adapter: Path): (Teaching.Chooser[okay.teach.Lesson], () => Unit) =
    val gpu = OnGpu.Gpu(model, Some(adapter))
    val choose: Teaching.Chooser[okay.teach.Lesson] = (s: Sigma) =>
      val allowed = TeacherSpace.allowed(s)
      val words = allowed.map(Action.said)
      val read = OnGpu.read(domain, words, Vector(Case("en", s.said, words.head)), gpu).head.meaning
      pure(read.flatMap(w => allowed.find(Action.said(_) == w)).getOrElse(Action.Stop))
    (choose, () => gpu.close())
