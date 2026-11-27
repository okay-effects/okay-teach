# SPRINT — okay-teach

The queue, in order. `[~]` in progress, `[x]` done (with its outcome), `[ ]` next. Read `specs/theory.md` first
(the contract), `specs/teach.md` (the tools), `GUIDE.md` (how to run them). Can-wait work is in `BACKLOG.md`.

| task | state | notes |
|---|---|---|
| T1 first-run | in progress | queued 2026-11-27 13:00 in ../okay-teach-run (a worktree pinned at 84b2130 + gpu.zsh), waiting for the GPU lock; log runs/tax-rules/all.log there |

## T1 — first-run: the first real lesson, okay-tax's rules  `[~]`

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

## T6 — the traces of past lessons  `[ ]`

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
  (plan → laws by `Choose` → judge → rounds); `LawSpaceSuite`. Left: the OpenStax chapter (its text and worked
  examples into a book file, train / held out), `Teach laws`, the reader's DomainSpec (find / given / end).
- **Gotchas**: `Unit` and `given` are names Scala takes (scala.Unit; the `given` keyword) — `SiUnit`, `givens`;
  tuple patterns in a `for` over `Either` need `withFilter` in Scala 3.3 — bind the tuple, then take its parts.

- **What**: the catalog's missing engine (theory §6): quantities with dimensions, equations as data, a solver that
  chains them to the unknown with exact arithmetic and checks dimensions; then a physics chapter (OpenStax,
  CC BY-NC-SA 4.0: fetched, never committed) as its first theory, written by `Theorize` and judged by the chapter's worked examples.
- **How**: the spec is written: `specs/equations.md` (quantities with dimensions and exact rationals, laws as data
  solved for a variable occurring once, forward chaining with a derivation, the theory written by choosing, the
  judge the chapter's worked examples, the engine's own laws). Build as a module `equations` (core JVM/JS), its
  laws first (§7), then the expression space for `Theorize`, then the OpenStax chapter (§6).
