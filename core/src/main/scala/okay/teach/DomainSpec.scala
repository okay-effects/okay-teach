package okay.teach

import okay.effects.json.Json
import okay.frames.{Choice, Hole, Space}

/** HOW A HOLE'S OPTIONS ARE MADE (specs/domain-spec.md): a closed set of kinds — no code in a spec */
enum HoleKind:
  /** fixed options; `shown` how an option is written in the reader's line (default: itself) */
  case Enum(options: Vector[String], shown: Map[String, String] = Map.empty)
  /** the numbers the text writes, after the fixed `extra` options ("none") */
  case Numbers(extra: Vector[String] = Vector.empty)
  /** options a named function computes from the text: `differences` (b − a of two numbers it writes) */
  case Derived(fn: String)
  /** the members of a named list of the data */
  case Data(source: String)
  /** one of several keyed kinds: each option is "key: value" and is its own line (`revenue: 20000`, `rate: 1000`) */
  case Either(of: Vector[(String, HoleKind)])

/** a hole: its key, its kind, when it is asked (each earlier key's value among the listed; "x*" a prefix), whether
 * "not said" is offered */
final case class HoleSpec(key: String, kind: HoleKind, when: Map[String, Vector[String]] = Map.empty,
    notSaid: Boolean = false)

/**
 * A DOMAIN AS DATA (specs/domain-spec.md, theory §5): what a teacher model can write by choosing. The data a reading
 * runs over is named lists (`Map[String, Vector[String]]`); a meaning is the `(key, value)`s chosen, in order;
 * `end`: the reading closes with a line "end" (okay-desk's line domains).
 */
final case class DomainSpec(name: String, system: String, holes: Vector[HoleSpec],
    languages: Vector[String] = Vector("en", "pl", "ru", "uk"), end: Boolean = false):
  def domain: Domain[DomainSpec.Data, DomainSpec.Meaning] = DomainSpec.domainOf(this)

object DomainSpec:
  type Data = Map[String, Vector[String]]
  type Meaning = Vector[(String, String)]
  val notSaid = "not said"
  val endLine = "end"

  /** the numbers a text writes: "20 000", "20k", "1 000,50", "3,5 tys." — thousands joined, "k" a thousand */
  def numbers(text: String): Vector[String] =
    """(?i)(\d{1,3}(?:[  ]\d{3})+|\d+)(?:[.,](\d+))?\s*(k|тыс|tys)?""".r.findAllMatchIn(text).toVector.map { m =>
      val whole = BigDecimal(m.group(1).replaceAll("[  ]", "") + Option(m.group(2)).fold("")("." + _))
      (if m.group(3) != null then whole * 1000 else whole).bigDecimal.stripTrailingZeros.toPlainString }.distinct

  /** the named functions of `derived` — the closed set */
  def derived(fn: String, text: String): Vector[String] = fn match
    case "differences" =>
      val ns = numbers(text).flatMap(_.toDoubleOption)
      (for a <- ns; b <- ns if b > a yield BigDecimal(b - a).bigDecimal.stripTrailingZeros.toPlainString).distinct
    case other => sys.error(s"no derived function $other")

  private def made(k: HoleKind, data: Data, text: String): Vector[String] = k match
    case HoleKind.Enum(os, _) => os
    case HoleKind.Numbers(extra) => extra ++ numbers(text)
    case HoleKind.Derived(fn) => derived(fn, text)
    case HoleKind.Data(source) => data.getOrElse(source, Vector.empty)
    case HoleKind.Either(of) => of.flatMap((key, kind) => made(kind, data, text).map(v => s"$key: $v"))

  def options(h: HoleSpec, data: Data, text: String): Vector[String] =
    if h.notSaid then notSaid +: made(h.kind, data, text) else made(h.kind, data, text)

  def shown(h: HoleSpec, option: String): String = h.kind match
    case HoleKind.Enum(_, s) => s.getOrElse(option, option)
    case _ => option

  /** a choice as the reader's line: "key: shown", an `either`'s option as it is, the end as "end" */
  def line(spec: DomainSpec, key: String, option: String): String =
    if key == endLine then endLine
    else spec.holes.find(_.key == key) match
      case Some(HoleSpec(_, HoleKind.Either(_), _, _)) => if option == notSaid then s"$key: $option" else option
      case Some(h) => s"$key: ${shown(h, option)}"
      case None => s"$key: $option"

  private def holds(h: HoleSpec, values: Map[String, String]): Boolean =
    h.when.forall((k, vs) => values.get(k).exists(v => vs.exists(w =>
      if w.endsWith("*") then v.startsWith(w.dropRight(1)) else v == w)))

  /** the holes asked, walked by position: each choice fills the first hole from where the last one stood whose
   * `when` holds; the next is the first after it that holds (the end, when asked for, after all) */
  def nextHole(spec: DomainSpec, chosen: Vector[Choice]): Option[HoleSpec] =
    val all = if spec.end then spec.holes :+ HoleSpec(endLine, HoleKind.Enum(Vector(endLine))) else spec.holes
    var pos = 0
    var values = Map.empty[String, String]
    var ok = true
    for c <- chosen if ok do
      val i = all.indexWhere(h => holds(h, values), pos)
      if i < 0 || all(i).key != c.hole then ok = false
      else { values += c.hole -> c.option; pos = i + 1 }
    if !ok then None else all.drop(pos).find(h => holds(h, values))

  def spaceOf(spec: DomainSpec): Space[Data, Meaning] = new Space[Data, Meaning]:
    def name: String = spec.name
    def next(data: Data, text: String, chosen: Vector[Choice]): Option[Hole] =
      nextHole(spec, chosen).map(h => Hole(h.key, options(h, data, text)))
    def assemble(data: Data, chosen: Vector[Choice]): Option[Meaning] =
      Some(chosen.map(c => c.hole -> c.option).filterNot(_._1 == endLine))
    def path(data: Data, m: Meaning): Vector[Choice] =
      m.map(Choice(_, _)) ++ Option.when(spec.end)(Choice(endLine, endLine))
    def say(data: Data, m: Meaning, language: String): String =
      path(data, m).map(c => line(spec, c.hole, c.option)).mkString("; ")
    def parse(data: Data, words: String, language: String): Option[Meaning] =
      val parts = words.split("; ").toVector.filterNot(_ == endLine)
      val read = parts.map { p =>
        val i = p.indexOf(": ")
        Option.when(i > 0)((p.take(i), p.drop(i + 2))).flatMap { (k, w) =>
          spec.holes.find(_.key == k) match
            case Some(h) => Some(k -> (h.kind match
              case HoleKind.Enum(os, _) => os.find(shown(h, _) == w).getOrElse(w)
              case _ => w))
            case None => spec.holes.find(h => h.kind match
              case HoleKind.Either(of) => of.exists(_._1 == k)
              case _ => false).map(h => h.key -> p) } }
      Option.when(read.forall(_.nonEmpty))(read.flatten)

  def domainOf(spec: DomainSpec): Domain[Data, Meaning] = new Domain[Data, Meaning]:
    val space: Space[Data, Meaning] = spaceOf(spec)
    def system(data: Data): String = spec.system
    override def line(hole: Hole, option: String): String = DomainSpec.line(spec, hole.name, option)
    override def languages: Vector[String] = spec.languages

  // THE JSON FORM
  private def s(x: String) = Json.Str(x)

  private def kindJson(k: HoleKind): Vector[(String, Json)] = k match
    case HoleKind.Enum(os, sh) => Vector("kind" -> s("enum"), "options" -> Json.Arr(os.map(s))) ++
      Option.when(sh.nonEmpty)("shown" -> Json.obj(sh.toVector.sortBy(_._1).map((a, b) => a -> s(b))*))
    case HoleKind.Numbers(extra) => Vector("kind" -> s("numbers")) ++
      Option.when(extra.nonEmpty)("extra" -> Json.Arr(extra.map(s)))
    case HoleKind.Derived(fn) => Vector("kind" -> s("derived"), "fn" -> s(fn))
    case HoleKind.Data(src) => Vector("kind" -> s("data"), "source" -> s(src))
    case HoleKind.Either(of) => Vector("kind" -> s("either"),
      "of" -> Json.Arr(of.map((key, kind) => Json.obj((("key" -> s(key)) +: kindJson(kind))*))))

  def toJson(spec: DomainSpec): Json =
    Json.obj((Vector("name" -> s(spec.name), "system" -> s(spec.system),
      "languages" -> Json.Arr(spec.languages.map(s)),
      "holes" -> Json.Arr(spec.holes.map(h => Json.obj((Vector("key" -> s(h.key)) ++ kindJson(h.kind) ++
        Option.when(h.when.nonEmpty)("when" -> Json.obj(h.when.toVector.map((a, b) => a -> Json.Arr(b.map(s)))*)) ++
        Option.when(h.notSaid)("notSaid" -> Json.Bool(true)))*)))) ++
      Option.when(spec.end)("end" -> Json.Bool(true)))*)

  private def str(x: Json, k: String) = x.field(k).map(_.string)
  private def strings(x: Json, k: String) = x.field(k).fold(Vector.empty[String])(_.list.map(_.string))

  private def kindOf(h: Json): scala.util.Either[String, HoleKind] =
    str(h, "kind") match
      case Some("enum") =>
        val shown = h.field("shown").fold(Map.empty[String, String])(o => o match
          case Json.Obj(fs) => fs.map((a, b) => a -> b.string).toMap
          case _ => Map.empty)
        Right(HoleKind.Enum(strings(h, "options"), shown))
      case Some("numbers") => Right(HoleKind.Numbers(strings(h, "extra")))
      case Some("derived") => str(h, "fn").toRight("derived without fn").map(HoleKind.Derived(_))
      case Some("data") => str(h, "source").toRight("data without source").map(HoleKind.Data(_))
      case Some("either") =>
        h.field("of").fold(Vector.empty[Json])(_.list).foldLeft[scala.util.Either[String, Vector[(String, HoleKind)]]](
          Right(Vector.empty))((acc, o) => for done <- acc; k <- str(o, "key").toRight("either without key");
          kind <- kindOf(o) yield done :+ (k -> kind)).map(HoleKind.Either(_))
      case other => Left(s"no kind $other")

  def fromJson(j: Json): scala.util.Either[String, DomainSpec] =
    def when(h: Json): Map[String, Vector[String]] = h.field("when").fold(Map.empty)(o => o match
      case Json.Obj(fs) => fs.map((a, b) => a -> (b match
        case Json.Arr(vs) => vs.map(_.string).toVector
        case one => Vector(one.string))).toMap
      case _ => Map.empty)
    val holes = j.field("holes").fold(Vector.empty[Json])(_.list).map(h =>
      for key <- str(h, "key").toRight("a hole without key"); k <- kindOf(h)
      yield HoleSpec(key, k, when(h), h.field("notSaid").contains(Json.Bool(true))))
    for
      name <- str(j, "name").toRight("no name")
      system <- str(j, "system").toRight("no system")
      hs <- holes.foldLeft[scala.util.Either[String, Vector[HoleSpec]]](Right(Vector.empty))((acc, h) =>
        acc.flatMap(done => h.map(done :+ _)))
    yield DomainSpec(name, system, hs, Option(strings(j, "languages")).filter(_.nonEmpty)
      .getOrElse(Vector("en", "pl", "ru", "uk")), j.field("end").contains(Json.Bool(true)))
