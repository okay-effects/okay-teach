package okay.teach.domains

import okay.effects.{Sum, Void}
import okay.effects.json.Json
import okay.effects.machine.Machine
import okay.mcp.{Link, Servers, ToolCall, ToolResult, ToolSpec}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/**
 * THE MODEL'S DOOR (SPRINT T14): okay-teach's operations as MCP tools over stdio (`okay-teach mcp`), on okay-mcp —
 * the same operations and laws as the command line (`bin/okay-teach`) and the skill. A long operation (a lesson, a
 * book loop) starts in the background through `scripts/gpu.zsh` and returns its run (the output directory); the model
 * follows it with `teach_status` and reads `teach_result`. What comes back is the lesson's state and trace — counts,
 * choices, measures — never a sealed question's text (theory L1). Paths are the checkout root's.
 */
object McpDoor:
  val servers = Servers()(Machine)
  import Machine.*
  type L[X] = Sum[Void, Link, X]

  private def string(name: String, about: String) =
    name -> Json.obj("type" -> Json.Str("string"), "description" -> Json.Str(about))
  private def integer(name: String, about: String) =
    name -> Json.obj("type" -> Json.Str("integer"), "description" -> Json.Str(about))
  private def schema(required: Vector[String], props: (String, Json)*) =
    Json.obj("type" -> Json.Str("object"), "properties" -> Json.obj(props*),
      "required" -> Json.Arr(required.map(Json.Str(_))))

  val specs: List[ToolSpec] = List(
    ToolSpec("teach_check",
      "Check a domain spec (JSON) against a few question rows you wrote FOR THE CHECK (never the " +
      "sealed ones): how many rows' meanings are read and reached. No GPU.",
      schema(Vector("spec", "rows"), string("spec", "the spec's path, e.g. examples/residence.json"),
        string("rows", "a tsv: meaning words<TAB>language<TAB>question"))),
    ToolSpec("teach_lesson", "Start the standard lesson in the background: the teacher's questions, a LoRA on " +
      "Qwen3-0.6B, the four measures and the baseline on the sealed set, the gate, the release. Returns the run.",
      schema(Vector("domain", "sealed"), string("domain", "a spec's path (…json) or a registered domain (tax-rules)"),
        integer("questions", "questions a language a meaning (default 8)"), integer("epochs", "default 2"),
        string("sealed", "the sealed set's path (tsv) — you never read it"), string("run", "the output directory"))),
    ToolSpec("teach_drive",
      "Start a lesson the local model drives itself (it chooses each operation the laws allow). " +
      "Returns the run.",
      schema(Vector("domain", "sealed"), string("domain", "a spec's path or a registered domain"),
        string("sealed", "the sealed set's path"), string("run", "the output directory"))),
    ToolSpec("teach_book", "Start the book loop: a book's sections become the meanings, its own questions the sealed " +
      "set, the local model drives the lesson. Returns the run.",
      schema(Vector("book"), string("book", "the book's path (sections '# id title', text, '?? question' lines)"),
        string("run", "the output directory"), string("driver", "model (default) or standard"))),
    ToolSpec("teach_status", "Where a run stands: waiting for the GPU, running (the operations so far), or finished.",
      schema(Vector("run"), string("run", "the run's directory, as a start returned it"))),
    ToolSpec("teach_result",
      "A finished run's result: the lesson's state (measures, choices, released or not) and its " +
      "trace. Counts only.", schema(Vector("run"), string("run", "the run's directory"))))

  private val root = Path.of(".").toAbsolutePath.normalize

  private def arg(c: ToolCall, k: String): Option[String] = c.arguments match
    case o: Json.Obj => o.get(k).collect {
      case Json.Str(s) => s
      case Json.Num(n) => n.toString }
    case _ => None

  /** a long operation started in the background: `scripts/gpu.zsh <run>.log bin/okay-teach <args>` */
  private def start(run: String, args: String*): ToolResult =
    Files.createDirectories(root.resolve(run).getParent)
    val log = s"$run.log"
    new ProcessBuilder((Vector("scripts/gpu.zsh", log, "bin/okay-teach") ++ args).asJava).directory(root.toFile)
      .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    ToolResult.text(s"started: run $run (log $log). It waits for the GPU, then runs; follow it with teach_status.")

  /** a string field of a JSON line (a trace step), empty when there is none */
  private def field(line: String, k: String): String =
    scala.util.Try(Json.fromText(line)).toOption.flatMap(_.field(k)).map(_.string).getOrElse("")

  private def lines(p: Path): Vector[String] =
    if Files.exists(p) then Files.readAllLines(p).asScala.toVector else Vector.empty

  def answer(c: ToolCall): ToolResult =
    def need(k: String) = arg(c, k).getOrElse(sys.error(s"$k is required"))
    try c.name match
      case "teach_check" =>
        val (t, _) = domainOf(need("spec"))
        val out = java.io.ByteArrayOutputStream()
        Console.withOut(out)(Teach.check(t, root.resolve(need("rows"))))
        ToolResult.text(out.toString)
      case "teach_lesson" =>
        val run = arg(c, "run").getOrElse(s"runs/${Teach.name(need("domain")).replace('/', '-')}")
        start(run, "lesson", need("domain"), arg(c, "questions").getOrElse("8"), arg(c, "epochs").getOrElse("2"),
          need("sealed"), run)
      case "teach_drive" =>
        val run = arg(c, "run").getOrElse(s"runs/${Teach.name(need("domain")).replace('/', '-')}-driven")
        start(run, "drive", need("domain"), need("sealed"), "model", run)
      case "teach_book" =>
        val book = need("book")
        val run = arg(c, "run").getOrElse(s"runs/${Path.of(book).getFileName.toString.stripSuffix(".txt")}")
        start(run, "book", book, run, arg(c, "driver").getOrElse("model"))
      case "teach_status" =>
        val run = need("run")
        val waits = lines(root.resolve(run).getParent.resolve("wait.log")).filter(_.contains(s"$run.log"))
        val ops = lines(root.resolve(run).resolve("trace.jsonl")).map(field(_, "op").takeWhile(_ != '('))
        val state = waits.lastOption.getOrElse("not started (no line in the wait log)")
        ToolResult.text(s"$state\noperations so far: ${if ops.isEmpty then "none" else ops.mkString(", ")}")
      case "teach_result" =>
        val run = root.resolve(need("run"))
        val sigma = lines(run.resolve("sigma.txt"))
        val trace = lines(run.resolve("trace.jsonl")).map(l => s"${field(l, "op")} → ${field(l, "result")}")
        val released = Files.exists(run.resolve("competence"))
        val state = if sigma.nonEmpty then sigma else Vector("(no state written: a standard lesson)")
        ToolResult.text((state ++ Vector("", s"released: $released", "", "trace:") ++ trace).mkString("\n"))
      case other => ToolResult.error(s"no tool $other")
    catch case e: Exception => ToolResult.error(Option(e.getMessage).getOrElse(e.toString))

  private def domainOf(arg: String) =
    val t: Taught[?, ?] = if arg.endsWith(".json") then Teach.fromSpec(root.resolve(arg))
      else Teach.domains.getOrElse(arg, sys.error(s"no domain $arg"))
    (t, arg)

  val tools: servers.Tools[L] = new servers.Tools[L]:
    def list: List[ToolSpec] ! L = pure(specs)
    def call(c: ToolCall): ToolResult ! L = pure(answer(c))

  def serve(): Unit =
    val server = servers.Server[L]("okay-teach", "0.1.0", tools, instructions = Some(
      "Teach a small model (Qwen3-0.6B) to read a domain's questions into their meaning; the laws are kept by these " +
        "tools: you never read a sealed set, a reader is released only past the gate. Read skills/teach-a-model/" +
        "SKILL.md in the okay-teach checkout for the steps."))
    Machine.run(servers.links.stdio(servers.serve[L](server)))
