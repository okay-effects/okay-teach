---
name: teach-a-model
description: "Teach a small language model (Qwen3-0.6B, runs in a phone's browser) to read the questions of a domain into their meaning — choosing among finite options, never writing — with an exact engine answering and a calibrated bar keeping wrong answers rare; and record the lesson as a trace a teacher model learns from. Use when a project needs a model that understands its users' questions (tax rules, pay, units, a book's problems), when asked to train / measure / release such a reader, or to write a theory (Datalog rules, equations) a book's exercises judge."
argument-hint: "describe | seal | lesson | read | diagnose | theory | record"
---

# Teach a model — the lesson, step by step

This skill is the procedure of okay-teach (github.com/okay-effects/okay-teach) for any agent with a shell. Its
theory is `specs/theory.md` there; you do not need to read the code. The checkout is `~/work/my/okay-teach`
(else clone it). Every command below runs from the checkout's root.

## What you make

A **competence**: a JSON domain spec (what questions can mean), a trained adapter for Qwen3-0.6B (it reads a
question into one of those meanings), a bar (under it the reader asks instead of answering), and the record of how
each was made. The model never writes an answer: an exact engine computes it from the meaning.

## The laws — never break them

1. **Sealed stays sealed.** You write the held-out questions yourself, BEFORE anything is trained, and you never
   look at them again, never tune anything on them, never show them to a teacher. You see only their counts.
2. **Choose, don't write.** Anything formal (a meaning, a rule, an equation) is built by choosing among options a
   space offers. If you are about to write free text that a program must parse, stop: describe a space instead.
3. **The gate.** A reader replaces the one before only if, on the sealed set, it is right more often AND has no
   more silent wrong answers. Coverage never buys silent errors.
4. **Numbers are copied, not remembered.** Every number you report names the run (date, command) that gave it.
5. **Budget.** Say up front how many teacher calls and GPU minutes the lesson may use; stop when spent, and say
   where you stand.

## Steps

### 1. describe — the domain as data

Write `<domain>.json` (format: `specs/domain-spec.md`). Holes are asked in order; each has a closed kind:
`enum` (fixed options, `shown` for how they read), `numbers` (what the text writes, `extra` fixed ones like "none"),
`derived` (`differences`), `data` (a named list), `either` (keyed alternatives: `revenue: N` or `rate: N`); `when`
(asked only after given values, lists, `x*` prefixes); `notSaid`; `end`. Smallest example: `examples/tax-rules.json`
(one enum hole). If a hole is not an enum, list the meanings to teach in `<domain>.meanings.txt`, one a line in the
spec's words (`contract: uop; gross: 15000; end`).

Check it before anything else: `sbt "domains/testOnly *SpecSuite"` covers the examples; for yours, read one
meaning back with the command in step 3 on a two-line sealed file — a meaning the space cannot reach is refused.

If no hole kind fits the domain, or the answer needs an engine the catalog lacks (`specs/theory.md` §6), stop and
say so (the `Uncovered` outcome): what is missing, not a workaround.

### 2. seal — the held-out questions

Write `<domain>.sealed.tsv`: `meaning words<TAB>language<TAB>question`, questions as real people put them, 2+ per
meaning per language (en, pl, ru, uk by default). Commit it before step 3. From now on, never open it.

### 3. lesson — ask, train, measure, release

```
scripts/gpu.zsh runs/<domain>/lesson.log \
  sbt "domains/runMain okay.teach.domains.Teach lesson <domain>.json 8 2 <domain>.sealed.tsv runs/<domain>"
```

`scripts/gpu.zsh` waits for the GPU (other sessions' MLX jobs and benchmarks) and holds a shared lock. The lesson:
the teacher (the local Qwen3.6-35B at `127.0.0.1:8090`, or `TEACHER_URL`/`TEACHER_MODEL`) writes 8 questions a
language for each meaning, kept only when the spec reaches their meaning (`runs/<domain>/cases.jsonl`; its raw replies
in `cases.jsonl.replies.txt`); a LoRA trains 2 epochs (`adapter/`); the reader is measured four ways (untaught /
taught × plain / calibrated); if the best taught one passes the gate, `competence/` is released. Every operation is
appended to `runs/<domain>/trace.jsonl`. Steps already done are skipped on a rerun.

To let a model choose the operations itself instead: `Teach drive <domain>.json <sealed.tsv> model`.

### 4. read — the numbers

Each measure: `right R of N (by language)` and the bar `answered A, wrong W`. Compare with a baseline that needs no
model (a word search, a parser) — a reader that does not beat it is not worth shipping. Look at the counts the
teacher kept per meaning in the log: a meaning with few kept questions is a corpus gap.

### 5. diagnose — one failure, one factor

Use only what you may see (counts by language, the teacher's kept/refused, misreads of NON-sealed texts — read
`Teach read <domain>.json <cases or a dev set> <adapter> plain` with `SHOW_TEXTS=1` on a set you wrote apart from the
sealed one):

| what you see | factor | repair |
|---|---|---|
| a meaning the spec cannot reach for a natural question | space | add an option source, split a hole |
| one option chosen for everything | scoring | calibrated scoring; train |
| wrong with a high margin on a kind of question | corpus | ask the teacher for that kind; more of it |
| few kept questions for some meanings | teacher | read the raw replies: a format slip, a truncated reply |
| right on your dev set, wrong on sealed | corpus | your dev set is unlike real questions: write it as people do |

Repair one factor, run again, and let the gate decide.

### 6. theory — when the engine needs rules or equations

`Teach rules` (Datalog, Carroll's soriteses) and `Teach laws` (equations, OpenStax kinematics) show how a big model
writes a theory by choosing, judged by a book's worked exercises, measured on exercises held out. A new book: its
vocabulary (predicates or variables), its exercises split train / held out, a judge — see `CarrollRules.scala`,
`Kinematics.scala`. Check first that a reference theory passes the judge (the judge must be fair).

### 7. record — the trace

Add the lesson to `data/traces/<domain>.jsonl` (format: `specs/traces.md`): each step's state, operation, outcome
copied from the log, the factor for a diagnosis or repair, the verdict (helped / hurt / neutral / informative).
Lessons recorded this way are what the teacher model (okay-teach SPRINT T7) learns to teach from — a failed lesson
is as useful as a good one, if it is recorded honestly.
