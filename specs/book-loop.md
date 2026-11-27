# The book loop — a local model teaches a micro-model a book's topic (SPRINT T13)

The operator's aim (2026-11-27): local training of models, a self-sufficient closed loop — a LOCAL model teaches
micro-models on a given topic from a book, with documentation of how to do it. Nothing in the loop needs a person or
a remote model once the book is given; a person only chooses the book and reads the result.

## The loop

```
book (sections, each with its own questions)
  │  split: the text the teacher may read │ the questions it may not (the sealed set, labelled by section)
  ▼
topics ── a DomainSpec built by the tool from the book: one enum hole `topic`, an option a section, shown as its
  │        printed title (a book without titles: the local model titles each section — BACKLOG)
  ▼
lesson ── driven by the local model (Teach drive … model): it chooses each operation among those the laws allow
  │        ask: the teacher writes questions as people who do not know the answer (Domain.about = the section)
  │        train: a LoRA on the micro-model (Qwen3-0.6B)
  │        measure: untaught, taught, the baseline (L7) — on the book's own questions, sealed
  │        release: through the gate (L4, L7)
  ▼
competence ── a question → its section (the micro-model reads) → the section's text quoted (the engine: exact)
  │
  ▼
trace ── every operation appended; the teacher model (T7) learns from the traces of many books
```

## The book's form

A book is a text file of sections, each a heading line `# <id> <title as printed>`, its text, and its questions
apart: lines beginning `?? ` (a question the book asks of that section). `Book.parse` splits it: the sections'
texts (what the teacher reads; the `??` lines are NEVER in them), the questions (the sealed set: the section is the
meaning). A fetcher turns a source into this form — `scripts/fetch-openstax-chapter.zsh` for an OpenStax chapter (its
sections' pages; each page's end-of-section "Conceptual Questions" become that section's `??` lines). Books under a
non-commercial licence are fetched into `books/` and never committed.

## Why the book's own questions are the right sealed set

They are written by the book's authors, not by the teacher or by whoever runs the lesson (L1, L2, L7 hold without a
person): the teacher never sees them (they are cut out of the text it reads), the baseline is learned from the
teacher's questions only, and they are labelled by the book itself (the section they follow). They are harder than
a user's question — they ask about the topic, they do not name it — which makes the measure honest, not generous.

## What a person does

1. Chooses a book (an open one) and fetches it into the form above.
2. Runs one command: `Teach book <book.txt> [out] [model|standard]` — the spec, the sealed set (beside the book),
   the lesson driven by the local model, the competence, the trace.
3. Reads the report: the numbers, whether it was released, the trace.

## Done when

On an OpenStax chapter (2: kinematics, eight sections), the loop runs with no person and no remote model between
the fetch and the report: the local 35B titles the sections and drives the lesson; the released micro-model beats
the baseline on the book's own questions; GUIDE.md and the skill say how, step by step.
