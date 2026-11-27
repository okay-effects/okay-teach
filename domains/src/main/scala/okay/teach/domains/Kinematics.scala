package okay.teach.domains

import okay.teach.Worked
import okay.teach.equations.{Quantity, Theory, Variable}

import java.nio.file.{Files, Path}

/**
 * KINEMATICS IN ONE DIMENSION, THE LAWS TAUGHT (specs/equations.md §6): OpenStax College Physics 2e §2.5 (Paul
 * Peter Urone, Roger Hinrichs; CC BY-NC-SA 4.0 — the text is fetched into `books/` by `scripts/fetch-openstax.zsh`
 * and never committed). The lesson names the variables (as Carroll's predicates are named); the teacher writes the
 * laws. The worked examples below are transcribed from the section: their givens, the asked quantity, the book's
 * answer to its printed figures — four to judge, four held out.
 */
object Kinematics:
  val book: Path = Path.of("books/openstax-cp2e-2.5.txt")
  def lesson: String =
    if !Files.exists(book) then sys.error(s"$book is missing: run scripts/fetch-openstax.zsh")
    Files.readString(book)

  private def v(name: String, about: String, unit: String) =
    Variable(name, about, okay.teach.equations.Units.parse(unit).get.dim)
  val variables: Vector[Variable] = Vector(v("x", "final position", "m"), v("x0", "initial position", "m"),
    v("v", "final velocity", "m/s"), v("v0", "initial velocity", "m/s"), v("vbar", "average velocity", "m/s"),
    v("a", "acceleration (constant)", "m/s^2"), v("t", "elapsed time", "s"))

  private def q(s: String) = Quantity.parse(s).get
  private def ex(n: Int, givens: (String, String)*)(asked: String, answer: String) =
    Worked(n, givens.map((k, s) => k -> q(s)).toMap, asked, q(answer))

  /** the section's worked examples (2.8–2.13), by their numbers: 2.12 in its three parts as 121, 122, 123 */
  val train: Vector[Worked] = Vector(
    ex(208, "x0" -> "0 m", "vbar" -> "4.00 m/s", "t" -> "2.00 min")("x", "480 m"),
    ex(209, "v0" -> "70.0 m/s", "a" -> "-1.50 m/s^2", "t" -> "40.0 s")("v", "10.0 m/s"),
    ex(210, "x0" -> "0 m", "v0" -> "0 m/s", "a" -> "26.0 m/s^2", "t" -> "5.56 s")("x", "402 m"),
    ex(2121, "x0" -> "0 m", "v0" -> "30.0 m/s", "v" -> "0 m/s", "a" -> "-7.00 m/s^2")("x", "64.3 m"))
  val held: Vector[Worked] = Vector(
    ex(211, "x0" -> "0 m", "v0" -> "0 m/s", "a" -> "26.0 m/s^2", "x" -> "402 m")("v", "145 m/s"),
    ex(2122, "x0" -> "0 m", "v0" -> "30.0 m/s", "v" -> "0 m/s", "a" -> "-5.00 m/s^2")("x", "90.0 m"),
    ex(2123, "x0" -> "0 m", "v0" -> "30.0 m/s", "a" -> "0 m/s^2", "t" -> "0.500 s")("x", "15.0 m"),
    ex(213, "x0" -> "0 m", "x" -> "200 m", "v0" -> "10.0 m/s", "a" -> "2.00 m/s^2")("t", "10.0 s"))

  /** the laws a person writes from the section (its equations for constant acceleration): the judge's fairness */
  val reference: Theory = Theory.parse(
    """variable x: final position [m]
      |variable x0: initial position [m]
      |variable v: final velocity [m/s]
      |variable v0: initial velocity [m/s]
      |variable vbar: average velocity [m/s]
      |variable a: acceleration [m/s^2]
      |variable t: elapsed time [s]
      |law average: x = x0 + vbar * t
      |law velocity: v = v0 + a * t
      |law position: x = x0 + v0 * t + a * t^2 / 2
      |law squares: v = sqrt(v0^2 + 2 * a * (x - x0))""".stripMargin).fold(e => sys.error(e), identity)
