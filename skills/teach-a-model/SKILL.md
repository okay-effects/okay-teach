---
name: teach-a-model
description: "Teach a small language model (Qwen3-0.6B, runs in a phone's browser) to read the questions of a domain into their meaning — choosing among finite options, never writing — with an exact engine answering and a calibrated bar keeping wrong answers rare; and record the lesson as a trace a teacher model learns from. Use when a project needs a model that understands its users' questions (tax rules, pay, units, a book's problems), when asked to train / measure / release such a reader, or to write a theory (Datalog rules, equations) a book's exercises judge."
argument-hint: "describe | seal | lesson | read | diagnose | theory | record"
---

# Teach a model — the lesson, step by step

This skill is the procedure of okay-teach (github.com/okay-effects/okay-teach) for any agent with a shell. Its
theory is `specs/theory.md` there; you do not need to read the code. The checkout is `~/work/my/okay-teach`
(else clone it). Every command below runs from the checkout's root.

## Three doors, one tool

- **The command line**: `okay-teach <step> …` (`bin/okay-teach`; `scripts/install.zsh` links it into
  `~/.local/bin`). `okay-teach` alone lists the steps. Paths are the checkout root's (or absolute).
- **A model's tools (MCP)**: `okay-teach mcp` serves `teach_check`, `teach_lesson`, `teach_drive`, `teach_book`,
  `teach_status`, `teach_result` over stdio — long operations start in the background and return a run to follow.
  `scripts/install.zsh` prints how to add it to Claude Code or opencode.
- **This skill**: the steps and the laws, for an agent with either door. The laws are kept by the tool in all three:
  sealed sets by path only, counts back, the gate.

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
   more silent wrong answers (a reader with no bar counts every wrong answer as silent). Coverage never buys silent
   errors. The tool applies it; you do not override it.
4. **Numbers are copied, not remembered.** Every number you report names the run (date, command) that gave it.
5. **Budget.** Say up front how many teacher calls and GPU minutes the lesson may use; stop when spent, and say
   where you stand.
6. **Beat the baseline.** Every lesson also measures a word search learned from the teacher's training questions
   (it never sees the sealed set), and the gate requires the taught reader to beat it too. Never write a baseline
   yourself after writing the sealed questions — you would tune it on them (the skill's first check did exactly
   that: 54 of 56, worth nothing).

## Steps

### 1. describe — the domain as data

Write `<domain>.json` (format: `specs/domain-spec.md`). Holes are asked in order; each has a closed kind:
`enum` (fixed options, `shown` for how they read), `numbers` (what the text writes, `extra` fixed ones like "none"),
`derived` (`differences`), `data` (a named list), `either` (keyed alternatives: `revenue: N` or `rate: N`); `when`
(asked only after given values, lists, `x*` prefixes); `notSaid`; `end`. Smallest example: `examples/tax-rules.json`
(one enum hole). If a hole is not an enum, list the meanings to teach in `<domain>.meanings.txt`, one a line in the
spec's words (`contract: uop; gross: 15000; end`).

Check it before anything else, with a few rows you write FOR THE CHECK (not the sealed ones), same format as step 2:
`okay-teach check <domain>.json <check.tsv>` — no GPU; it says how many rows'
meanings were read and reached, and names the first that were not. `Teach` alone prints every step's arguments.
Keep the spec in a file named for the domain; the lesson records the spec's `name`, not its path.

If no hole kind fits the domain, or the answer needs an engine the catalog lacks (`specs/theory.md` §6), stop and
say so (the `Uncovered` outcome): what is missing, not a workaround.

### 2. seal — the held-out questions

Write `<domain>.sealed.tsv`: `meaning words<TAB>language<TAB>question`, questions as real people put them, 2+ per
meaning per language (en, pl, ru, uk by default). The meaning words are the spec's lines joined by "; " — an enum's
option key or its `shown` text, both read (`document: cukr`). Freeze it before step 3: commit it if the checkout is
yours to commit, else record its hash (`shasum <file>`) in your report. From now on, never open it.

### 3. lesson — ask, train, measure, release

```
scripts/gpu.zsh runs/<domain>/lesson.log \
  okay-teach lesson <domain>.json 8 2 <domain>.sealed.tsv runs/<domain>
```

`scripts/gpu.zsh` waits for the GPU (other sessions' MLX jobs and benchmarks) and holds a shared lock. The lesson:
the teacher (the local Qwen3.6-35B at `127.0.0.1:8090`, or `TEACHER_URL`/`TEACHER_MODEL`) writes 8 questions a
language for each meaning, kept only when the spec reaches their meaning (`runs/<domain>/cases.jsonl`; its raw replies
in `cases.jsonl.replies.txt`); a LoRA trains 2 epochs (`adapter/`); the reader is measured four ways (untaught /
taught × plain / calibrated) and the baseline (law 6); if the best taught one passes the gate against both, it is
released into `competence/`. Every operation is appended to `runs/<domain>/trace.jsonl`. Steps already done are
skipped on a rerun. Cost: one teacher call per meaning per language (7 meanings × 4 languages = 28), a few minutes
of GPU for a small domain (the first check: 3 minutes for 7 meanings), longer for many meanings.

To let a model choose the operations itself instead: `Teach drive <domain>.json <sealed.tsv> model`.

### 4. read — the numbers

Each measure: `right R of N (by language)` and the bar `answered A, wrong W`; the baseline's line `baseline (words)`.
It also says `right among the first two`: the meant is the best reading or the runner-up (overlapping meanings —
then show both). `okay-teach measure <book.txt|domain|spec.json> <sealed|-> <adapter> <cases.jsonl>` measures an
adapter already made, with no lesson.
A reader that does not beat the baseline is not released. Look at the counts the teacher kept per meaning in the
log: a meaning with few kept questions is a corpus gap. The bar the lesson prints is chosen on the set it is
reported on — to trust it, calibrate on one half and report on the other:
`Teach calibrate <domain>.json <adapter> <calibration.tsv> <report.tsv>` (split the sealed rows by a rule fixed
before you look, e.g. the first question of each meaning and language calibrates, the second reports).
To read without an adapter, pass `-`.

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

### 5b. a book — the closed loop

When the domain is "which part of this book answers the question", the loop needs no spec and no sealed set from
you: put the book in the form `# <id> <title>` + text + `?? question` lines (`scripts/fetch-openstax-chapter.zsh`
does it for OpenStax), then `Teach book <book.txt>` — the book's titles are the meanings, its own questions the
sealed set, the local model drives the lesson. Check first that no question is inside a section's text (the
teacher would read it): `BookLoopSuite` shows how.

### 6. theory — when the engine needs rules or equations

`Teach rules` (Datalog, Carroll's soriteses) and `Teach laws` (equations, OpenStax kinematics) show how a big model
writes a theory by choosing, judged by a book's worked exercises, measured on exercises held out. A new book: its
vocabulary (predicates or variables), its exercises split train / held out, a judge — see `CarrollRules.scala`,
`Kinematics.scala`. Check first that a reference theory passes the judge (the judge must be fair).

### 7. record — the trace

Add the lesson to `data/traces/<domain>.jsonl` (format: `specs/traces.md`): each step's state, operation, outcome
copied from the log, the factor for a diagnosis or repair, the verdict (helped / hurt / neutral / informative).
Cite the source as the commit and spec section when there is one, else the log's path, the run's date and its
command. The lesson's own `runs/<domain>/trace.jsonl` holds every operation already; yours adds the diagnosis.
Lessons recorded this way are what the teacher model (okay-teach SPRINT T7) learns to teach from — a failed lesson
is as useful as a good one, if it is recorded honestly.
