package okay.teach.domains

import okay.effects.json.Json
import okay.mcp.{Content, ToolCall}

/** THE MODEL'S DOOR: every tool listed with its schema; a check answered by counts; an unknown tool or a missing
 * argument an error, never a crash */
class McpDoorSuite extends munit.FunSuite:
  private def text(r: okay.mcp.ToolResult) = r.content.collect { case Content.Text(t) => t }.mkString

  test("the tools listed: check, lesson, drive, book, status, result"):
    assertEquals(McpDoor.specs.map(_.name), List("teach_check", "teach_lesson", "teach_drive", "teach_book",
      "teach_status", "teach_result"))

  test("a check answered by counts; errors for a wrong call"):
    val rows = java.nio.file.Files.createTempFile("rows", ".tsv")
    java.nio.file.Files.writeString(rows, "document: cukr\ten\tI am from Ukraine, which card?\n")
    val r = McpDoor.answer(ToolCall("teach_check", Json.obj("spec" -> Json.Str("examples/residence.json"),
      "rows" -> Json.Str(rows.toString))))
    assert(!r.isError && text(r).startsWith("1 rows: 1 fine"), text(r))
    assert(McpDoor.answer(ToolCall("teach_nothing", Json.obj())).isError)
    assert(McpDoor.answer(ToolCall("teach_check", Json.obj())).isError)
