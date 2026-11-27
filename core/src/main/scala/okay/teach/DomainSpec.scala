package okay.teach

import okay.effects.json.Json
import okay.frames.{Choice, Hole, Space}

/** HOW A HOLE'S OPTIONS ARE MADE (specs/domain-spec.md): a closed set of kinds — no code in a spec */
enum HoleKind:
  /** fixed options; `shown` how an option is written in the reader's line (default: itself) */
  case Enum(options: Vector[String], shown: Map[String, String] = Map.empty)
  /** the numbers the text writes */
  case Numbers
  /** options a named function computes from the text: `differences` (b − a of two numbers it writes) */
  case Derived(fn: String)
  /** the members of a named list of the data */
  case Data(source: String)

/** a hole of a spec: its key, its kind, when it is asked (earlier keys' values), whether "not said" is offered */
final case class HoleSpec(key: String, kind: HoleKind, when: Map[String, String] = Map.empty, notSaid: Boolean = false)

/**
 * A DOMAIN AS DATA (specs/domain-spec.md, theory §5): what a teacher model can write by choosing. The data a reading
 * runs over is named lists (`Map[String, Vector[String]]`); a meaning is the `(key, value)`s chosen, in order.
 */
final case class DomainSpec(name: String, system: String, holes: Vector[HoleSpec],
    languages: Vector[String] = Vector("en", "pl", "ru", "uk")):
  def domain: Domain[DomainSpec.Data, DomainSpec.Meaning] = DomainSpec.domainOf(this)

object DomainSpec:
  type Data = Map[String, Vector[String]]
  type Meaning = Vector[(String, String)]
  val notSaid = "not said"

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

  def options(h: HoleSpec, data: Data, text: String): Vector[String] =
    val made = h.kind match
      case HoleKind.Enum(os, _) => os
      case HoleKind.Numbers => numbers(text)
      case HoleKind.Derived(fn) => derived(fn, text)
      case HoleKind.Data(source) => data.getOrElse(source, Vector.empty)
    if h.notSaid then notSaid +: made else made

  def shown(h: HoleSpec, option: String): String = h.kind match
    case HoleKind.Enum(_, s) => s.getOrElse(option, option)
    case _ => option

  /** the next hole after the choices: the first not chosen whose `when` holds */
  def nextHole(spec: DomainSpec, chosen: Vector[Choice]): Option[HoleSpec] =
    val made = chosen.map(c => c.hole -> c.option).toMap
    spec.holes.drop(spec.holes.lastIndexWhere(h => made.contains(h.key)) + 1)
      .find(h => h.when.forall((k, v) => made.get(k).contains(v)))

  def spaceOf(spec: DomainSpec): Space[Data, Meaning] = new Space[Data, Meaning]:
    def name: String = spec.name
    def next(data: Data, text: String, chosen: Vector[Choice]): Option[Hole] =
      nextHole(spec, chosen).map(h => Hole(h.key, options(h, data, text)))
    def assemble(data: Data, chosen: Vector[Choice]): Option[Meaning] = Some(chosen.map(c => c.hole -> c.option))
    def path(data: Data, m: Meaning): Vector[Choice] = m.map(Choice(_, _))
    def say(data: Data, m: Meaning, language: String): String =
      m.map((k, v) => s"$k: ${spec.holes.find(_.key == k).fold(v)(shown(_, v))}").mkString("; ")
    def parse(data: Data, words: String, language: String): Option[Meaning] =
      val parts = words.split("; ").toVector.map { p =>
        val i = p.indexOf(": ")
        Option.when(i > 0)((p.take(i), p.drop(i + 2))) }
      Option.when(parts.forall(_.nonEmpty))(parts.flatten).map(_.map { (k, w) =>
        val back = spec.holes.find(_.key == k).flatMap(h => h.kind match
          case HoleKind.Enum(os, _) => os.find(shown(h, _) == w)
          case _ => None)
        k -> back.getOrElse(w) })

  def domainOf(spec: DomainSpec): Domain[Data, Meaning] = new Domain[Data, Meaning]:
    val space: Space[Data, Meaning] = spaceOf(spec)
    def system(data: Data): String = spec.system
    override def line(hole: Hole, option: String): String =
      s"${hole.name}: ${spec.holes.find(_.key == hole.name).fold(option)(shown(_, option))}"
    override def languages: Vector[String] = spec.languages

  // THE JSON FORM
  def toJson(spec: DomainSpec): Json =
    def s(x: String) = Json.Str(x)
    def kind(k: HoleKind): Vector[(String, Json)] = k match
      case HoleKind.Enum(os, sh) => Vector("kind" -> s("enum"), "options" -> Json.Arr(os.map(s))) ++
        Option.when(sh.nonEmpty)("shown" -> Json.obj(sh.toVector.sortBy(_._1).map((a, b) => a -> s(b))*))
      case HoleKind.Numbers => Vector("kind" -> s("numbers"))
      case HoleKind.Derived(fn) => Vector("kind" -> s("derived"), "fn" -> s(fn))
      case HoleKind.Data(src) => Vector("kind" -> s("data"), "source" -> s(src))
    Json.obj("name" -> s(spec.name), "system" -> s(spec.system), "languages" -> Json.Arr(spec.languages.map(s)),
      "holes" -> Json.Arr(spec.holes.map(h => Json.obj((Vector("key" -> s(h.key)) ++ kind(h.kind) ++
        Option.when(h.when.nonEmpty)("when" -> Json.obj(h.when.toVector.map((a, b) => a -> s(b))*)) ++
        Option.when(h.notSaid)("notSaid" -> Json.Bool(true)))*))))

  def fromJson(j: Json): Either[String, DomainSpec] =
    def str(x: Json, k: String) = x.field(k).map(_.string)
    def strings(x: Json, k: String) = x.field(k).fold(Vector.empty[String])(_.list.map(_.string))
    def pairs(x: Json, k: String): Map[String, String] = x.field(k).fold(Map.empty)(o => o match
      case Json.Obj(fs) => fs.map((a, b) => a -> b.string).toMap
      case _ => Map.empty)
    val holes = j.field("holes").fold(Vector.empty[Json])(_.list).map { h =>
      val kind = str(h, "kind") match
        case Some("enum") => Right(HoleKind.Enum(strings(h, "options"), pairs(h, "shown")))
        case Some("numbers") => Right(HoleKind.Numbers)
        case Some("derived") => str(h, "fn").toRight("derived without fn").map(HoleKind.Derived(_))
        case Some("data") => str(h, "source").toRight("data without source").map(HoleKind.Data(_))
        case other => Left(s"no kind $other")
      for key <- str(h, "key").toRight("a hole without key"); k <- kind
      yield HoleSpec(key, k, pairs(h, "when"), h.field("notSaid").contains(Json.Bool(true))) }
    for
      name <- str(j, "name").toRight("no name")
      system <- str(j, "system").toRight("no system")
      hs <- holes.foldLeft[Either[String, Vector[HoleSpec]]](Right(Vector.empty))((acc, h) =>
        acc.flatMap(done => h.map(done :+ _)))
    yield DomainSpec(name, system, hs, Option(strings(j, "languages")).filter(_.nonEmpty)
      .getOrElse(Vector("en", "pl", "ru", "uk")))
