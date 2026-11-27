# SPRINT — okay-teach

The queue, in order. `[~]` in progress, `[x]` done (with its outcome), `[ ]` next. Read `specs/theory.md` first
(the contract), `specs/teach.md` (the tools), `GUIDE.md` (how to run them). Can-wait work is in `BACKLOG.md`.

| task | state | notes |
|---|---|---|
| T11 calibration-apart | next | the bar chosen on a calibration set apart from the sealed one (theory L2) |
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

## T7 — the teacher taught  `[ ]`

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
  Left: the done-when check — a fresh agent given only the skill runs a lesson on a new domain (needs the GPU after
  T1); the MCP tools.

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

## T11 — the bar validated on a calibration set apart  `[ ]`

- **What**: lesson 2's bar (0.421: 71 answered, 2 wrong) was chosen on the 192 it is reported on — its ε is not
  validated (theory L2: calibration and sealed disjoint). Calibrate on one set, report the bar's realised wrong on
  another.
- **How**: a calibration set of the same kind as the sealed one (people's questions, written apart — not the
  teacher's): either split okay-tax's 192 by a fixed rule (by meaning id hash: half calibrate, half report — fixed
  before looking), or write a second set; `Lesson.Calibrate(on: Sealed)` returning the bar, `Measure` reporting the
  realised wrong at it. Record both numbers.
- **Done when**: specs/teach.md §7.3 — the bar from calibration, its answered / wrong on the report half.
