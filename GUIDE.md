# GUIDE — teaching a model a domain

How to use okay-teach today, step by step. The why is in `specs/theory.md`; the tools in `specs/teach.md`.

## What you get

A **competence**: a small model (Qwen3-0.6B, which runs in a phone's browser) that reads a question of your domain
into its meaning — choosing among options, never writing — an exact engine that answers it, and a bar under which
the model asks instead of answering, so that wrong answers stay at most ε on questions like the calibration's.

## Prerequisites

- A Mac with Apple silicon; `../mlx-scala/native-lib/out/lib/libokm.dylib` built (or `MLX_SCALA_LIB`).
- The teacher: an OpenAI-compatible endpoint, by default the local okay-mlx gateway at `http://127.0.0.1:8090/v1`
  with `mlx-community/Qwen3.6-35B-A3B-4bit` (`TEACHER_URL`, `TEACHER_MODEL`).
- The base model in the Hugging Face cache: `mlx-community/Qwen3-0.6B-4bit`.

## 1. Describe the domain

Write a `Domain[D, T]` (`domains/src/main/scala/okay/teach/domains/`): its `space` (an okay-frames `Space`: holes,
each with finite options computed from the data and the text), the reader's `system` prompt, how a choice is
written as a line (`line`). `TaxRules.scala` is the smallest example: one hole, the rule. Register it in
`Teach.domains` with the meanings a teacher should write for and one sentence of what the domain is about.

Check the laws before anything else: for a few meanings, `Laws.reach(space)(data, text, meaning)` is `None` and
`Laws.roundTrip` holds (`domains/src/test/`).

## 2. Seal a held-out set — yourself, before anything runs

Write questions people really ask, with their meanings, as a tsv: `meaning<TAB>language<TAB>text` (okay-tax's
`data/heldout.tsv`: 192, 2 a rule a language). Never let a teacher see it, never tune anything on it. It is the
only honest number.

## 3. Run the lesson

```
okay-teach all <domain> <n> <epochs> <heldout.tsv> runs/<domain>
```

It writes `runs/<domain>/cases.jsonl` (the teacher's questions, n a language a meaning, kept only when the space
reaches their meaning), trains `runs/<domain>/adapter/`, and reads the held-out set four ways: untaught and taught,
plain and calibrated, each with its bar at ε 0.05. Each step is skipped when its output exists: delete it to redo.

## 4. Read the numbers

- **right of N, by language** — the reader's own accuracy;
- **the bar**: `bar b: answered a of N, wrong w` — answering only above b, w wrong among a;
- **the misread lines** — `[lang] text → read (meant)`: attribute each to a factor (theory §4) before repairing.

Compare with a baseline that needs no model (a word search, a parser): a reader that does not beat it is not worth
shipping.

## 5. Rules from a book (a theory)

When the domain's engine takes a theory (Datalog rules), a big model writes it by choosing, judged by the book's
exercises: `okay-teach rules <train> <rounds> runs/<book>` (Carroll's soriteses
today; `CarrollRules.scala` shows how a book, its vocabulary and its judge are given).

## Let a model drive

`Teach drive <domain|spec.json> <sealed.tsv> model` — the local model chooses each operation among those the laws
allow (theory §7); `Teach book` below does it for a book. The trained teacher model (SPRINT T7) is next.

## From a book — the closed loop (specs/book-loop.md)

A local model teaches a micro-model which section of a book a question is about; the book's own questions measure
it. No person and no remote model between the fetch and the report.

1. **Get the book into the loop's form**: sections `# <id> <title>`, their text, their questions as `?? ` lines.
   For an OpenStax chapter: `scripts/fetch-openstax-chapter.zsh 2` → `books/openstax-cp2e-ch2.txt` (8 sections,
   136 questions; never committed — CC BY-NC-SA). Any book can be written in this form by hand or by a script.
2. **Check it**: `sbt "domains/testOnly *BookLoopSuite"` (for chapter 2) — every section a meaning, every question
   labelled, no question inside a text the teacher reads.
3. **Run the loop**: `scripts/gpu.zsh runs/<book>.log okay-teach book books/<book>.txt` — the local Qwen3.6-35B (okay-mlx on :8090) drives the lesson: it chooses how many questions
   to ask, how long to train, what to measure, whether to release; the teacher writes questions from each section's
   text; the micro-model (Qwen3-0.6B) is trained; it is measured on the book's questions against the baseline; the
   gate decides. `standard` instead of the model: the fixed policy.
4. **Read the result**: `runs/<book>/sigma.txt` (the state and every choice), `trace.jsonl`, `competence/` if
   released. A question → its section (the micro-model) → the section's text (the book, quoted).
