package okay.teach.mlx

import mlx.llm.{Adaptable, Chat, Lora, Model, Models}
import okay.effects.json.Json
import okay.llm.compute.OnMlx
import okay.llm.qwen.{Qwen3Compute, Qwen3Config, Safetensors, Tokenizer}
import okay.llm.read.{Adapted, OnQwen, Read, Reading}
import okay.frames.Reading as FramesReading
import okay.scala3.*
import okay.teach.{Bar, Case, Domain, Reader}

import java.nio.file.{Files, Path}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*

/**
 * ON APPLE'S GPU (specs/teach.md §6): the reader answered by okay-llm's Qwen3 forward on MLX — the same forward the
 * page runs on WebGPU, so a number measured here is the page's — with a LoRA adapter or none; a LoRA trained on a
 * domain's cases (mlx-scala), saved as the page reads it (okay-llm `AdapterExport`); a measure with its bar.
 */
object OnGpu:
  /** a cached model's directory (`mlx-community/Qwen3-0.6B-4bit`), or a path */
  def directory(repo: String): Path =
    Models.snapshot(repo).orElse(Some(Path.of(repo)).filter(Files.isDirectory(_)))
      .getOrElse(sys.error(s"$repo not in the cache"))

  /** a reading on the GPU: the model, the adapter if any; `close` when done */
  final class Gpu(repo: String, adapter: Option[Path]) extends Reading:
    given ExecutionContext = ExecutionContext.parasitic
    private val dir = directory(repo)
    private val st = Safetensors(Files.readAllBytes(dir.resolve("model.safetensors")))
    private val config = Qwen3Config.parse(Files.readString(dir.resolve("config.json")))
    private val tok = Tokenizer.fromJson(Json.fromText(Files.readString(dir.resolve("tokenizer.json"))))
    private val model = adapter.fold(Qwen3Compute(OnMlx(), st, config)) { a =>
      val adapted = Adapted(OnMlx(), st, Safetensors(Files.readAllBytes(a)))
      val m = Qwen3Compute(adapted, adapted.tensors, config); adapted.releaseSources(); m }
    private val on = OnQwen(model, tok, config.layers)
    def answer[X](op: Read[X]): X = Await.result(on.answer(op), 10.minutes)
    def close(): Unit = on.close()

  /** one case read: the reading's meaning and margin, and whether it is the meant */
  final case class Read1[T](c: Case[T], meaning: Option[T], margin: Double):
    def right: Boolean = meaning.contains(c.meant)

  def read[D, T](domain: Domain[D, T], data: D, cases: Vector[Case[T]], gpu: Reading,
      scoring: Reader.Scoring = Reader.Scoring.Plain): Vector[Read1[T]] =
    cases.map { c =>
      // (isolated: a measure runs inside a lesson's handler — okay-scala's NestedRun otherwise)
      val r = okay.scala3.runIsolated(handle(Reader.read[D, T, Read](domain, data, c.text, scoring),
        Read.sync[Pure, FramesReading[D, T]](gpu)))
      Read1(c, r.meaning, r.margin) }

  /** the counts of a measure, by language, and the bar `epsilon` allows on these readings */
  def said[T](reads: Vector[Read1[T]], epsilon: Double): String =
    val byLang = reads.groupBy(_.c.language).toVector.sortBy(_._1).map((l, rs) =>
      s"$l ${rs.count(_.right)}/${rs.size}").mkString(", ")
    val bar = Bar.choose(reads.map(r => (r.margin, r.right)), epsilon).fold("no bar holds")(_.said)
    s"right ${reads.count(_.right)} of ${reads.size} ($byLang); ε $epsilon: $bar"

  private def msg(role: String, content: String) = Json.obj("role" -> Json.Str(role), "content" -> Json.Str(content))

  /**
   * THE LORA TAUGHT (okay-invest ReaderTrain's recipe: rank 16 on every projection, lr 2e-4, the prompt not
   * learned): each case its prompt — the domain's system and user words, thinking off — and its answer, the meaning's
   * lines as the reader weighs them (each line's tokens and a newline). Writes `<out>/adapter.safetensors` (mlx-scala)
   * and `<out>/reader.adapter.safetensors` (the page's)
   */
  def train[D, T](domain: Domain[D, T], data: D, cases: Vector[Case[T]], repo: String, out: Path, epochs: Int,
      seed: Int = 5): Unit =
    Files.createDirectories(out)
    mlx.Mlx.randomSeed(seed)
    val dir = directory(repo)
    val chat = Chat.load(dir); val loaded = Model.load(dir, mapped = true); val tok = chat.tokenizer
    val newline = tok.encode("\n").head
    val examples = cases.map { c =>
      val prompt = chat.prompt(Vector(msg("system", domain.system(data)), msg("user", domain.user(data, c.text))),
        Nil, Map("enable_thinking" -> false))
      (prompt, Reader.lines(domain, data, c.text, c.meant).flatMap(l => tok.encode(l) :+ newline).toArray)
    }
    val lora = Lora(loaded.asInstanceOf[Adaptable], rank = 16, projs = Seq("q", "k", "v", "o", "gate", "up", "down"),
      lr = 2e-4f)
    val rnd = scala.util.Random(seed)
    val t0 = System.nanoTime()
    for e <- 1 to epochs do
      var sum = 0f; var k = 0
      for (prompt, answer) <- rnd.shuffle(examples) do
        sum += lora.step(prompt ++ answer, prompt.length); k += 1
      println(f"epoch $e: ${examples.size} examples, mean loss ${sum / math.max(1, k)}%.4f " +
        f"(${(System.nanoTime() - t0) / 1e9}%.0f s)")
    lora.save(out.resolve("adapter.safetensors").toString)
    lora.close(); loaded.close()
    okay.llm.mlx.AdapterExport.write(out.resolve("adapter.safetensors"), out.resolve("reader.adapter.safetensors"))
