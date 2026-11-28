# SPRINT — okay-teach

The queue, in order. `[~]` in progress, `[x]` done (with its outcome), `[ ]` next. Read `specs/theory.md` first
(the contract), `specs/teach.md` (the tools), `GUIDE.md` (how to run them). Can-wait work is in `BACKLOG.md`.

| task | state | notes |
|---|---|---|
| T14 three-doors | done | MCP tools for a model, an okay-teach command, the skill |
| T16 overlapping-meanings | next | answer with the two likeliest when unsure (run e: errors fall into one general section) |
| T7 the-teacher-taught | next | traces of 6 lessons, the 35B as teacher (T5), then a local model trained on the good steps |
| T9 teaching-skill | in progress | the whole process as a skill any model can follow: a domain as a JSON spec in the command, SKILL.md, then MCP tools |

## T1 — first-run: the first real lesson, okay-tax's rules  `[x]`

- **Outcome** (2026-11-27 13:04–13:28; specs/teach.md §7.1; trace data/traces/okay-teach-tax-rules.jsonl): the
  35B wrote 359 of 800 questions (4–32 a rule, none refused); taught 116 of 192 (untaught 14), calibrated 90; no bar
  holds at ε 0.05; the search 134 not yet beaten. Next: T10 (the corpus repaired).

- **What**: `Teach all tax-rules` end to end — the 35B writes training questions, a LoRA on Qwen3-0.6B, okay-tax's
  192 held-out questions read four ways (untaught / taught × plain / calibrated), the bar at ε 0.05.
- **Why**: the tools were built and their laws pass, but no real run yet; the teacher model (T5–T7) needs at least
  one real lesson and its trace. Baselines to beat: the word search 134 of 192 first; Qwen3-0.6B untaught 16; 4B 89
  (okay-tax specs/tax.md §6.2).
- **How**: `sbt "domains/runMain okay.teach.domains.Teach all tax-rules 8 2 ../okay-tax/data/heldout.tsv runs/tax-rules"`
  (8 questions a language a rule: 25 rules × 4 × 8 = 800 asked); with nohup, log to `runs/tax-rules/all.log`. Steps
  are skipped when their output exists (`cases.jsonl`, `adapter/`), so a rerun resumes.
- **Resume**: the run lives in `../okay-teach-run` (a worktree pinned to a commit, so edits here do not leak into a
  run that compiles when the lock frees); `runs/wait.log` there says where it stands; rerunning the same command
  there resumes (finished steps are skipped).
- **Seen while it runs**: the kept count a rule varies (4 for scale.health, zus; 28–32 for others) — T1's run
  predates the raw replies being kept, so its cause (a truncated reply? another line format?) is checked by a
  rerun of `write` for those rules now that `<cases>.replies.txt` records each reply and whether it was cut.
- **Gotchas**: the 35B (okay-mlx on :8090) and the LoRA share the Mac's GPU with other sessions — check `ps` for
  other GPU jobs first; the teacher must never see `heldout.tsv` (it is only an argument of `read`) — L1 of the
  theory; okay-tax's held-out also includes a rule added later (`loss`), so its 192 cover 24 rules.
- **Done when**: the four numbers and the bar are in `specs/teach.md` §7 with the run's date; the cases'
  kept/refused counts recorded; the result, good or bad, stated plainly.

## T2 — the Lesson effect  `[x]`

- **Outcome** (c57e260): `core/Lesson.scala` — `Lesson[X]` (Ask, Train, Measure, Release, Uncovered), `Sealed`, `Asked`,
  `Measured`, the gate `Lesson.passes` (L4), `Lesson.standard` (ask → train → four measures → release if the gate
  passes); `LessonSuite` on fakes (the order, the gate refusing more silent wrong). `domains/LessonOnGpu.scala` —
  the handler on this Mac, every op and result appended to `<out>/trace.jsonl`, `Release` writes the competence
  directory (theory §8). `Teach lesson <domain> <n> <epochs> <sealed.tsv> [out]`. Not yet: Describe, Ground,
  Theorize, Diagnose, Repair as operations (they come with T4, T5); `Teach all` kept as it was (T1 runs it).


- **What**: `Lesson` as an effect (theory §2): the operations as an enum over Σ, handlers wrapping today's tools
  (`Teacher.write`, `OnGpu.train`, `OnGpu.read`, `Bar.choose`, `RuleTeacher`); a lesson is a program, run in one
  line with its handlers.
- **How**: core: `Lesson[X]` enum (Describe, Ground, Theorize, Ask, Train, Measure, Calibrate, Diagnose, Repair,
  Release, Uncovered), `Sigma` (the state, as data), programs `Lesson.run`; mlx/domains: the handler. Effects on
  okay-scala (as okay-llm's `Llm`, `Read`).
- **Done when**: `Teach all` is a Lesson program with the same output as T1; a test runs a lesson on fakes.

## T3 — sealed as a capability  `[x]`

- **Outcome** (c57e260): a lesson program holds only `Sealed(id)`; the handler alone maps it to a file, `Measure`
  returns `Measured` (counts by language, the bar) — no texts reach a lesson. `Teach read` (a person's tool)
  prints misread texts only with `SHOW_TEXTS=1`. T1's run predates this and prints them to its log: a person's
  log, never a teacher's input.


- **What**: theory L1 in code: the sealed corpus is an opaque handle (`Sealed`), only the `Measure` handler opens
  it, and it returns aggregates (counts by language and hole), never texts.
- **Done when**: no API outside `Measure` can read sealed texts (a test: a lesson program cannot); `Teach read` on
  sealed goes through it.

## T4 — the domain as data  `[~]`

- **Progress** (this commit's parent): `core/DomainSpec.scala`, `specs/domain-spec.md` (the format); `DomainSpecSuite`
  (kinds, `when`, laws, JSON back); `TaxRulesSpecSuite` — the JSON spec prompts, offers and writes lines exactly as
  `TaxRules` on all 192 held-out texts. Then (next commit): `either`, numbers with extra,
  `when` lists and prefixes, `end`, holes walked by position; okay-desk's pay lines ported (`PaySpecSuite`: its exact
  lines on three kinds of question). Left: the goal (needs a `percents` kind and a line label apart from the key).
  The pay port is checked by lines written as PayLines writes them, not against PayLines itself (okay-desk is not a
  library this repo can depend on).


- **What**: `DomainSpec` (theory §5) as JSON and its interpreter to a `Space` + `Domain`; hole kinds Enum,
  FromText, FromData, Derived (a closed set of named functions), Lines; the words `key: value` with an exact parser.
- **How**: port `TaxRules` first (one Enum hole), then okay-desk's pay lines (FromText numbers, Enum forms), then
  okay-invest's goal slots; each port checked by the laws and by reading the same as the code domain did.
- **Done when**: TaxRules from JSON reads identically to the Scala one on the 192; the laws hold for pay and goal.

## T5 — the teacher's space and the 35B as the first teacher  `[~]`

- **Progress**: `core/Teaching.scala` — `Sigma` (Σ, said in words), `Action`, `TeacherSpace` (the actions Σ allows:
  the laws as preconditions; release only past the gate), `Teaching.drive` (a chooser → the Lesson → Σ, history =
  the trace), `Teaching.standard` (the fixed policy), `Teaching.byModel` (any `Llm` by `Choose`); `TeachingSuite`
  (17 core laws in all, incl. a chooser asking for what is not allowed is stopped). `Teach drive <domain>
  <sealed.tsv> [standard|model] [out]`. Left: the run with the 35B (after T1 frees the GPU: T1's cases and
  adapter can be copied into the drive's out dir so it starts from them), and Ask's arguments beyond n.


- **What**: the L1 space (theory §7): given Σ, the next Lesson operation and its arguments as holes; the 35B reads
  it by `Choose`; each run writes its trace (Σ, operation, outcome).
- **Done when**: a lesson on tax-rules driven by the 35B alone reaches a released competence or stops on its budget
  with its trace written.

## T6 — the traces of past lessons  `[~]`

- **Progress**: `specs/traces.md` (the step's fields: source, state, op, args, outcome, factor, verdict);
  `data/traces/`: okay-invest-goal (9 steps), okay-tax-rules (6), okay-desk-frames (12), okay-study-carroll (8) —
  35 steps, every number copied from its source spec; `TracesSuite` checks each step's form. Left: okay-study's
  CUKR, T1's own trace (from its run).

- **What**: lessons already run by people and Claude written as traces (data): okay-desk (frames, self-play, the
  domains), okay-invest (goal reader v1→v2), okay-tax (§6: search vs models), okay-study (Carroll, CUKR).
- **Done when**: `data/traces/*.jsonl`, each step Σ → operation → measured outcome, with its source commit.

## T7 — the teacher taught  `[x]`

- **Outcome** (specs/teach.md §7.14): `ChoiceDomain` + `SmallTeacher`; a 0.6B taught on 51 decisions agrees with
  the 35B on 10 of 11 held out (untaught 1); trained on 62, it drove the new chapter 6 itself: the 35B's path, the
  same release (46 of 74, baseline 36). The questions are still written by the 35B (T21).

- **Plan (2026-11-28)**: the teacher's choice IS a space (TeacherSpace: Σ.said → one allowed action), so a small
  teacher is a reader taught by okay-teach itself. (1) every decision recorded — `decisions.jsonl` (state, allowed,
  chosen, by) — done 3beea66; (2) decisions collected: the 35B on chapters 2–5 re-driven from cached files (an ask
  reuses `cases.<k>-n<n>.jsonl`), plus residence / tax rules; (3) a TeacherDomain (DomainSpec-free: options = the
  allowed actions' words) and Qwen3-0.6B + LoRA trained on the 35B's decisions, measured by agreement on a held-out
  chapter's decisions; (4) a real lesson (a new chapter) driven by the small teacher vs the 35B.

- **Step 1 done** (2026-11-27; data/traces/residence-driven.jsonl): the 35B's first drive stopped unreleased (the
  state never said a release needs the baseline); with Σ saying it, its second drive ran a whole lesson — measure
  untaught 19, ask 4 a language, train 1 epoch, taught 52 of 56, baseline 20, RELEASE, stop: better and cheaper than
  the fixed policy (49 with 8 and 2 epochs). The first lesson a local model ran end to end.

- **Step 1 running** (2026-11-27 ~14:25): the 35B drives a lesson itself — `Teach drive examples/residence.json
  examples/residence.sealed.tsv model runs/residence-driven` in ../okay-teach-run (gpu.zsh); its Σ and choices in
  `runs/residence-driven/sigma.txt` and `trace.jsonl`. Compare with the fixed policy's lesson (49 taught, released).

- **What**: a local model (Qwen3-1.7B or 4B + LoRA) trained on the traces' good steps; measured on held-out domains
  (theory §7: CUKR from okay-migrant first) — alone, does it get a reader past the baseline, or say `Uncovered`?

## T8 — the equations engine  `[~]`

- **Progress**: module `equations` (JVM, Scala.js; no dependency): `Quantities.scala` (Rational, Dim, `SiUnit`,
  `Units` catalog and expressions "km/h", "kg*m/s^2", `Quantity`), `Expr.scala` (parse, eval with an inexact flag
  for a non-square root, dimension, `isolate`), `Theory.scala` (`Variable`, `Law`, `Theory.parse` of
  `variable v: speed [m/s]` / `law speed: v = s / t`, `check`, `solve` with `Step`s). `EquationsSuite` — the
  spec's §7 laws pass. Then: `core/LawSpace.scala` (laws by choosing, depth
  bound 5, one-dimension laws only), `Worked` (an example right to the book's figures), `core/LawTeacher.scala`
  (plan → laws by `Choose` → judge → rounds); `LawSpaceSuite`. Then (fa4dddc): `domains/Kinematics.scala` — OpenStax
  College Physics 2e §2.5 (CC BY-NC-SA 4.0: `scripts/fetch-openstax.zsh` fetches it into `books/`, never
  committed), the lesson's variables, worked examples 2.8–2.13 transcribed (train 2.8, 2.9, 2.10, 2.12a; held out
  2.11, 2.12b, 2.12c, 2.13), the reference laws pass 4/4 and 4/4 and are reached by choosing (`KinematicsSuite`);
  `Teach laws [rounds] [out]`. Left: run `Teach laws` with the 35B (GPU after T1), the reader's DomainSpec
  (find / given / end).
- **Gotchas**: `Unit` and `given` are names Scala takes (scala.Unit; the `given` keyword) — `SiUnit`, `givens`;
  tuple patterns in a `for` over `Either` need `withFilter` in Scala 3.3 — bind the tuple, then take its parts.

- **What**: the catalog's missing engine (theory §6): quantities with dimensions, equations as data, a solver that
  chains them to the unknown with exact arithmetic and checks dimensions; then a physics chapter (OpenStax,
  CC BY-NC-SA 4.0: fetched, never committed) as its first theory, written by `Theorize` and judged by the chapter's worked examples.
- **How**: the spec is written: `specs/equations.md` (quantities with dimensions and exact rationals, laws as data
  solved for a variable occurring once, forward chaining with a derivation, the theory written by choosing, the
  judge the chapter's worked examples, the engine's own laws). Build as a module `equations` (core JVM/JS), its
  laws first (§7), then the expression space for `Theorize`, then the OpenStax chapter (§6).

## T9 — the teaching skill  `[~]`

- **Progress** (166851e): `Teach` takes `<domain>.json` (`Teach.fromSpec`; meanings from `<domain>.meanings.txt` or
  enumerated when every hole is an enum — `Meanings`), `examples/tax-rules.json` (made by `SpecExport`, read back
  by a test), `skills/teach-a-model/SKILL.md` linked into `~/.agents/skills` and `~/.claude/skills`, theory §9.
  DONE 2026-11-27 (T12): the done-when check passed — a fresh agent given only the skill and a new domain
  (which Polish residence or work document a question is about, 7 meanings) writes examples/residence.json and its
  sealed set, runs the lesson in the main checkout (do not edit code there until its run has compiled), writes
  data/traces/residence.jsonl, reports where the skill was unclear; then the MCP tools.

- **What**: the whole process as a skill for any model (theory §9): (1) `Teach` takes a domain as a JSON
  `DomainSpec` file (no Scala), its meanings listed or enumerated, its sealed set as `meaning words<TAB>lang<TAB>text`;
  (2) `skills/teach-a-model/SKILL.md` (the open Agent Skills format, linked into `~/.agents/skills` and
  `~/.claude/skills`): when to use, the steps, the laws, how to read the numbers, the diagnosis table, what to
  record; (3) later, the Lesson's operations as MCP tools (okay-mcp) so a model without a shell can teach, the laws
  enforced by the server.
- **Why**: the operator's aim — models that teach and learn; each step built and understood, the process handed to
  any model as a skill; lessons run by the skill write traces that feed the teacher model (T7).
- **Done when**: a fresh agent given only the skill and a domain spec runs a lesson end to end (asks, trains,
  measures, releases or stops) without reading this repository's code.

## T10 — the corpus repaired: the teacher's undersupply  `[x]`

- **Outcome** (specs/teach.md §7.2): 768 questions in people's words; taught 141 of 192 — above the search's 134;
  the bar 0.421 answers 71 with 2 wrong; the competence released. Two fixes on the way: the teacher's task (6a4b118),
  the lesson's nested runs (runIsolated). Next: T11 (the bar validated on a calibration set apart).

- **What**: T1's diagnosis (F4/F7): 359 of 800 questions, 4 for some rules. Find why (the raw replies are now
  kept: `cases.jsonl.replies.txt`), then ask again so every rule has its share, retrain, measure; the gate decides.
- **Found** (T1's cases.jsonl, read): (a) many rules got ONE question a language, not 8 (4, 16, 28, 32 kept:
  multiples of the four languages); (b) the questions QUOTE THE ANSWER — "the full rate starting at 5,652 zł
  (including 1,926.76 zł …)" — the teacher was shown the rule's text (`say` = title — what it says) and wrote it
  into the question: texts unlike a person's, who does not know the answer (F7 Teacher, F4 Corpus).
- **How**: `Domain.about` (what the teacher is told of a meaning) apart from `space.say` (the canonical words);
  TaxRules tells the title and, marked as for the teacher only, what the rule says; `Teacher.task` asks for the
  questions of a person who does NOT know the answer (no quoting it), ONE language a call (n lines, numbering
  stripped); `Teach lesson` into `runs/tax-rules-2` (a new out
  dir: the first lesson's files stay as they are). Baselines: taught 116, the search 134.
- **Done when**: the second lesson's numbers in specs/teach.md §7.2 and its trace written, better or not.

## T11 — the bar validated on a calibration set apart  `[x]`

- **Outcome** (specs/teach.md §7.3): split by a fixed rule (96 + 96); the bar 0.484 from calibration; on the report
  half 30 answered of 96, 0 wrong. Small: more sealed questions would tighten the bound.

- **What**: lesson 2's bar (0.421: 71 answered, 2 wrong) was chosen on the 192 it is reported on — its ε is not
  validated (theory L2: calibration and sealed disjoint). Calibrate on one set, report the bar's realised wrong on
  another.
- **How**: a calibration set of the same kind as the sealed one (people's questions, written apart — not the
  teacher's): either split okay-tax's 192 by a fixed rule (by meaning id hash: half calibrate, half report — fixed
  before looking), or write a second set; `Lesson.Calibrate(on: Sealed)` returning the bar, `Measure` reporting the
  realised wrong at it. Record both numbers.
- **Done when**: specs/teach.md §7.3 — the bar from calibration, its answered / wrong on the report half.

## T12 — the skill's check, its findings repaired  `[x]`

- **Outcome** (9f97768, 2bca9cd; specs/teach.md §7.4): all eleven findings repaired; the baseline in every lesson
  and the gate (L7); re-measured: residence taught 49 vs baseline 22, tax rules taught 141 vs baseline 115 — both
  released.

- **The check** (2026-11-27 14:06–14:15, a fresh agent given only the skill; its files: examples/residence.json,
  examples/residence.sealed.tsv, data/traces/residence.jsonl, runs/residence/): the lesson ran end to end in ~3 min
  — 224 teacher questions (32 each of 7), untaught 19 / taught 49 of 56 (en 11, pl 14, ru 11, uk 13 of 14), the
  bar 0.154 answering 24 of 56 with 0 wrong, released. The skill works for a stranger. Its findings:
  1. THE GATE LACKS A BASELINE: it compares taught with untaught only; the agent's own word search scored 54 of 56
     but was written from memory of the sealed questions (not fair). → a baseline every lesson measures that never
     sees the sealed set: a word search learned from the TEACHER's training questions; the gate must beat it (L7).
  2. No way to check a spec before the lesson (the skill pointed at the lesson command; `read` with no adapter
     failed on "none") → `Teach check <spec.json> <file.tsv>` (no GPU: each row's meaning parsed and reached);
     `-` for no adapter documented.
  3. `Teach` with no arguments crashed → a usage message.
  4. The sealed file's meaning column (key or shown text?) → both accepted; said in the skill.
  5. The domain recorded as its path → the spec's name.
  6. The skill silent on: teacher calls (meanings × languages), the gate's wrong when no bar holds (all wrong),
     citing a log without a commit, "commit it" when the checkout is not one's to commit (freeze: record a hash).

## T13 — the book loop: a local model teaches a micro-model a book's topic  `[x]`

- **Outcome**: done-when met — on OpenStax ch. 2 the loop runs with no person and no remote model; the 35B drove
  run e to 96 of the book's 134 (baseline 59), released; GUIDE.md and the skill (5b) say how.

- **What**: `specs/book-loop.md` — a book (sections + their own questions) → topics titled by the local model → a
  spec built by the tool → a lesson driven by the local model → a released competence (a question → its section,
  quoted) → its trace; the book's own questions are the sealed set. One command: `Teach book <book.txt> [out]`.
- **Why**: the operator's aim — local training, a self-sufficient closed loop, documented (2026-11-27).
- **How**: (a) `Book` (core): the form `# id title`, text, `?? question` lines; `parse` keeps questions out of the
  text; (b) `scripts/fetch-openstax-chapter.zsh`: an OpenStax chapter's section pages → that form (each page's
  "Conceptual Questions" as `??`); (c) `Topics`: the local model titles and summarises each section → `DomainSpec`;
  `BookDomain.about` = the section's text (for the teacher only); (d) `Teach book` = topics + `Teaching.drive` by
  the model + the competence; (e) GUIDE.md "from a book", the skill's step; (f) the first book: OpenStax College
  Physics 2e chapter 2 (CC BY-NC-SA: fetched, never committed).
- **Progress** (to ac5d018): Book, the fetcher (chapter 2: 8 sections, 136 questions), BookDomain, `Teach book`,
  `BookLoopSuite` (no question leaks into a teacher's text), GUIDE.md and the skill (5b). RUNNING since 14:52: the
  first loop on chapter 2, driven by the 35B, in ../okay-teach-run2 (a second pinned worktree: ../okay-teach-run
  holds the residence-driven-2 lesson), log runs/book-ch2.log, out runs/openstax-ch2.
- **First run** (data/traces/openstax-ch2.jsonl): the 35B asked 4 a section (32, English only), trained 1 epoch:
  taught 50, baseline 53, untaught 44 of the book's 134 — refused by the gate; it could not ask more (the space
  allowed one ask). Repaired: ask more after a failed gate, train again. SECOND RUN queued 15:04 in
  ../okay-teach-run2 (bin/okay-teach book …, log runs/book-ch2-b.log, out runs/openstax-ch2-b).
- **Done when**: specs/book-loop.md's done-when.

## T14 — one tool, three doors: a model's (MCP), the command line's, an agent's (the skill)  `[~]`

- **What** (the operator, 2026-11-27): the toolkit is a tool for a MODEL (MCP tools it calls), for the COMMAND LINE
  (a real `okay-teach` command, not `sbt "domains/runMain …"`) and a SKILL for an agent — the same operations, the
  same laws, enforced by the tool in all three.
- **How**: (1) `bin/okay-teach`: runs `okay.teach.domains.Teach` on a classpath exported once from sbt (rebuilt
  when the sources change), the MLX flags set; linkable into `~/.local/bin`. (2) an MCP server (`okay-teach mcp`,
  stdio) on okay-mcp: tools `teach_check`, `teach_lesson`, `teach_book`, `teach_status`, `teach_result` — a long
  operation starts in the background and returns a run id, `teach_status` follows it; sealed sets by path only,
  results as counts (theory L1). (3) the skill and GUIDE point at both doors.
- **Done when**: a model with only the MCP tools runs a lesson; the command line runs one with `okay-teach lesson …`.

## T15 — the book loop's questions: by passage, in the book's style  `[x]`

- **Outcome** (specs/teach.md §7.5): passages and 2 epochs: fixed policy 80, the 35B 96 of 134 (run e, 176
  questions, released) — from ~50; the space repaired on the way (ask again once an adapter; epochs 2 or 3; the
  adapters' trend; the confusions measured).

- **Why**: the loop closed (T13) but weak and unsteady: taught 57 / 50 of 134 with 32 questions (4 a section). The
  book's questions hold on to specifics of a section (bacteria's flagella, a car on wet concrete); a teacher shown a
  whole section writes generic ones; and it writes them as a forum user, while a book is asked as a student and an
  exercise ask.
- **How**: `Domain.passages(data, meant)` — what the teacher is told, one passage at a time (default: `about`
  alone); `Teacher.write` asks n questions a language FOR EACH PASSAGE; `BookDomain` cuts a section into passages of
  ~2000 characters at paragraph ends (at most 8) and asks for questions as a student or the book's own exercises put
  them (conceptual and numeric), never quoting the passage. The control run (the fixed policy, 8 a section, 2 epochs)
  first, then the 35B again.
- **Done when**: the book loop's taught reader measured with passages, against T13's 57 / 50 and the control.
- **Progress** (9eb0b95): built — passages (chapter 2: 4, 3, 4, 8, 8, 2, 8, 7 = 44), the student's style. QUEUED
  15:21 in ../okay-teach-run3: run c (the 35B drives) then run d (the fixed policy: 8 a passage, 2 epochs), logs
  runs/book-ch2-{c,d}.log. The control without passages (fixed policy, 8 a section) runs before them in
  ../okay-teach-run2 (runs/book-ch2-std.log).
- **Seen** (runs c and the control): the control without passages (fixed policy, 64 questions, 2 epochs): taught
  49, calibrated 54, baseline 39 — released; twice the questions gave nothing. Run c (the 35B, passages): 176 →
  taught 55 vs baseline 59 (refused) → it asked more: 880 → 58 vs 59 → asked again: 1584 … — a plateau near 55–58
  of 134; likely the domain (sections that overlap: 2.4, 2.5, 2.6, 2.7), not the corpus. Added (f975232 and the one
  before): the measure's most-confused meanings (counts), Σ's trend of adapters, the teacher told to stop when more
  questions stop helping.

## T16 — overlapping meanings: answer with the two likeliest when unsure  `[x]`

- **Outcome** (specs/teach.md §7.6): `Reader.readTwo`, `Measured.inTwo`, `Baseline.ranked`, `Bar.pair`, the step
  `measure` (writes margins, no text). The margins exposed a broken sealed set: 36 of 134 were formula fragments
  (the fetch read MathML lines as question numbers; fixed, BookLoopSuite checks whole questions). On the 98 whole
  questions run e's adapter: 74 first, 87 in two (baseline 57, 72); the "overlap" was mostly the fragments.

- **Why**: run e's errors fall into one general section (2.5) from its neighbours — the meanings overlap (F1); more
  questions do not help, and no bar holds at 5%.
- **How**: `Measured` reports also "right among the first two"; a competence answers with one meaning above its bar
  and with the two likeliest below it (a page shows both sections). Measure: right-first, right-in-two, and the bar.
- **Done when**: specs/teach.md §7.6 — run e's adapter re-measured by the new measure.

## T17 — contrast questions for the pairs a reader confuses  `[x]`

- **Outcome** (specs/teach.md §7.10): NEGATIVE. Contrast and control both lower the reader (ch. 3 base 71/65/66 over
  seeds; control 63/58/55; contrast 49); readings drift to the meanings that got questions. An inseparable pair
  (3.2/3.3) goes wholesale to one side by seed; in two stays 86–90. Remedy: answer in two or merge the pair (F1).

- **Why**: the taught reader's errors are pairs of neighbouring meanings (ch. 2: 2.3 as 2.2; ch. 3: 3.3 as 3.2 12
  times). More plain questions stopped helping (§7.5); questions that separate the pair might.
- **How**: `okay-teach contrast <book.txt> <run> [pairs] [n]` (Teach.contrast): the run's adapter reads questions the
  teacher writes APART (`valid.jsonl`, 2 a passage, seeds 7000+; never trained on) → its top confused pairs there
  (never on the sealed set: L1) → for each pair (A read as B) `Domain.contrast` asks n a passage of A "easily taken
  for B, yet about A" (`contrast.jsonl`); the CONTROL asks as many plain questions of the same A (`control.jsonl`).
  Two adapters (cases + contrast, cases + control, 2 epochs) read the sealed set.
- **Done when**: specs/teach.md §7.10 — contrast vs control vs before on ch. 2 (run f) and ch. 3; if contrast wins
  on both, it becomes a Lesson operation the teacher can choose.

## T18 — merge an inseparable pair (a Repair of the space, F1)  `[x]`

- **Outcome** (specs/teach.md §7.11): `Book.merge`, `okay-teach merge`, `Balance` (`OKAY_TEACH_BALANCE`). Ch. 3 merged:
  seeds 60/82/83 (unmerged 71/65/66), three voting 83 of 92 (unmerged 74); all agreeing 55 with 1 wrong. Next: the
  teacher detects the pair itself on questions apart and chooses Repair(merge); release a vote of seeds.

- **Why** (§7.10): 3.2/3.3 flip wholesale by seed; merged, right first is 81–83 of 92 on three seeds (unmerged
  65–71), counted from the same readings.
- **How**: detect on questions APART (valid.jsonl): a pair whose confusions both ways are a large share of the
  pair's questions, or that flips between two adapters. Then a space repair: `HoleKind.Enum` options grouped (one
  option `3.2+3.3`, shown as both titles; the answer shows both sections), cases relabelled, the reader retrained,
  the gate on the merged sealed labels. The operation the teacher chooses: `Repair(merge A B)`; in the trace.
- **Done when**: ch. 3 merged and retrained: right first ≥ 80 of 92 on 2 seeds, released by the gate; §7.11.

## T19 — the repairs as the teacher's operations: Repair(merge), Release(vote)  `[x]`

- **Outcome** (specs/teach.md §7.12): Lesson Check / Merge / Train(seeds), L8 (released only once checked). The 35B
  driving: ch. 3 check → merge → vote of 3 = 83 of 92; ch. 2 vote 79 of 98, ch. 4 39 of 82, ch. 5 58 of 61; it
  merged only where the check found a pair.

- **Why** (§7.10–7.11): the two levers that worked are a merged pair (+8 mean) and a vote of seeds (+7 to +9); one
  adapter swings ±10. Today both are steps a person runs.
- **How**: Lesson gets `Merge(a, b)` (detected on `valid.jsonl` — a pair whose confusions both ways are a large share
  of its questions, or that flips between seeds; never the sealed set) and `Train(seeds = k)`; `Release` writes the
  set of adapters and the vote; TeacherSpace offers Merge only for detected pairs. Traces record both.
- **Done when**: `okay-teach book` on ch. 3 with the 35B driving ends merged and voted, ≥ 80 of 92; on ch. 2 the
  teacher's choice measured too (§7.12).

## T20 — chapter 4: more questions kept helping, the teacher stopped early  `[x]`

- **Outcome** (§7.13): asking more allowed after a passing gate; L7 clearly (z ≥ 1.645). The 35B alone: 160 → 800 →
  1440 questions, votes 39 → 40 → 48 of 82 (baseline 36), released. (Fixed policy, 640: 50.) Re-runs of ch. 2/3/5
  under the new rules (runs/t20c): 79, 83, 58 — unchanged.

- **Found** (§7.12): not the questions' kind — their number. Fixed policy, ask 8: 160 → 27, 320 → 35, 640 → 44 of
  82; three seeds voting on 640: 50 of 82. Asking more is now allowed after a passing gate (commit after f5d3720);
  the 35B re-run on ch. 4 with that freedom is runs/t20-ch4-model in ../okay-teach-run3.

- **Why** (§7.12): ch. 4 (Newton's laws) — questions apart read 52 of 80, the book's own 39 of 82; one adapter 27,
  below the word search (35); the book mixes the laws (4.3 read as 4.4 eleven times), the teacher's questions keep
  them apart.
- **How**: look at the teacher's questions per section (not the sealed ones) against the book's exercise STYLE as the
  passages show it; try `Domain.asking` variants (problems with numbers vs conceptual, the book's end-of-chapter
  wording), measured on questions apart first; only then once on the sealed set. Also: more questions (ask 8).
- **Done when**: ch. 4 released ≥ 50 of 82 on a vote, with the same change not lowering ch. 2/3/5.

## T21 — a small question writer (the last part of the loop on the 35B)  `[~]`

- **Untrained small writers** (§7.15): 4B's questions teach a reader nearly as well as the 35B's (ch. 3/5/6 votes
  71/54/49 vs 74/58/46) but teach the word search too, so L7-clearly refuses 5 of 6; the small teacher then stops
  (its data has only 2 "ask more" steps). Next: lessons that fail first, driven by the 35B, into the teacher's data;
  then a writer taught on the 35B's questions.

- **Why** (§7.14): the small teacher drives a lesson, but every `Ask` (and the questions apart of `Check`) is written
  by the 35B. A closed loop on small models needs the questions from a small model too.
- **How**: the 35B's written questions per passage are data (`cases.*.jsonl` with the passages they came from):
  train a small writer (Qwen3-1.7B or 4B + LoRA; generation, not choice — so its output is filtered by the space's
  laws as the 35B's is, and measured by what the reader trained on it reaches on the sealed set). Compare per chapter:
  reader taught on the small writer's questions vs on the 35B's.
- **Done when**: a chapter's reader taught entirely from a small writer's questions passes the gate (L4, L7 clearly).
