package okay.teach.domains

import okay.datalog.Program
import okay.study.{Book, Carroll, Model, Score, Verdict}
import okay.teach.RuleTeacher
import okay.teach.Vocabulary

import java.nio.file.{Files, Path}

/**
 * CARROLL'S SORITESES, THE RULES TAUGHT (okay-study specs/study.md §4): the book's reading forms are kept as the
 * reference teacher wrote them (sentences, terms, answers: `models/carroll-sorites.json`); only the Datalog rules are
 * written — the part the local 35B failed (0 of 16 held out). The vocabulary: what the forms give (`no/3`, `all/3`,
 * `some/3`, `neg/2`, `term/1`) and what the answers ask (`existing_nullity/2`, `nullity/2`); the plan declares the
 * rest. The judge: the program in the reference's place, run on the training exercises; the measure: the held out.
 */
object CarrollRules:
  /** okay-study's checkout (its book and model files): OKAY_STUDY, else beside this repository */
  val home: Path = Path.of(sys.env.getOrElse("OKAY_STUDY", "../okay-study"))
  lazy val book: Book = Book(Files.readString(home.resolve("books/carroll-symbolic-logic.txt")))
  lazy val forms: Model = Model.parse(Files.readString(home.resolve("models/carroll-sorites.json")))
    .fold(e => sys.error(e), identity)
  /** the 26 consistent exercises: the first `train` judge, the rest are held out */
  def exercises(train: Int) =
    Carroll.abstractSoriteses(book).filterNot(e => Carroll.misprinted.contains(e.number)).splitAt(train)

  val vocabulary: Vocabulary = Vocabulary(Map("no" -> 3, "all" -> 3, "some" -> 3, "neg" -> 2, "term" -> 1,
    "existing_nullity" -> 2, "nullity" -> 2), maxBody = 4, maxVars = 5)

  def lesson: String = Carroll.soritesLesson(book) +
    "\n\nThe premisses are read into facts: no(N, X, Y) for \"No X are Y\" (N the premiss's number), all(N, X, Y) " +
    "for \"All X are Y\", some(N, X, Y), neg(T, T') and neg(T', T) for a term and its negation, term(T). An answer " +
    "is read from existing_nullity(X, Y) (\"X_1 Y_0\": some X exist, none of them Y) and nullity(X, Y) (\"XY_0\")."

  def scored(p: Program, on: Vector[okay.study.Exercise]): Score = Score.of(forms.copy(rules = p.toString), on)

  /** the judge: how many training exercises pass, and the first failures in words */
  def judge(train: Vector[okay.study.Exercise])(p: Program): RuleTeacher.Verdict =
    val s = scored(p, train)
    val failed = s.verdicts.collect {
      case (e, Verdict.Wrong(said, expected)) =>
        s"exercise ${e.number} (${e.sentences.mkString("; ")}): the book says $expected, the rules gave " +
          s"${if said.isEmpty then "nothing" else said.mkString(" + ")}"
      case (e, Verdict.Failed(why)) => s"exercise ${e.number}: $why" }
    RuleTeacher.Verdict(s.right, s.total, failed.take(3).mkString("; "))
