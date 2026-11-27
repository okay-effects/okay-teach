# Traces — the teacher model's data (theory §7, SPRINT T6)

A trace is a lesson told as steps: the state before, the operation chosen, what it gave, and whether it helped.
Traces of lessons people and Claude ran are the first corpus of the teacher model (L1); lessons the 35B or the
trained teacher runs append to it (`LessonOnGpu` writes `<out>/trace.jsonl` with the same fields).

## A step (one JSON object a line, `data/traces/<domain>.jsonl`)

| field | what |
|---|---|
| `source` | where it is recorded: a repository, a spec section, a commit |
| `domain` | the competence taught |
| `step` | its order in the lesson |
| `state` | Σ before, in words: what existed, the last measures |
| `op` | the Lesson operation (theory §2): `Ask`, `Train`, `Measure`, `Calibrate`, `Diagnose`, `Repair`, `Release`, `Uncovered` |
| `args` | the operation's arguments, in words (the data asked for, the base model, the epochs, the repair) |
| `outcome` | what it gave: counts, measures — copied from the source, never re-derived |
| `factor` | for `Diagnose` and `Repair`: the factor attributed (theory §4: F1…F7) |
| `verdict` | `helped` (the gate passed after it, or a measure rose with no more silent wrong), `hurt`, `neutral`, `informative` (a measure or diagnosis: it decides the next step) |

Numbers are copied from the source; a step whose source gives none says so. A trace is evidence, not a policy:
the teacher model learns which operation tended to help in which state.

## The traces written

| file | lesson | source |
|---|---|---|
| `okay-invest-goal.jsonl` | okay-invest's goal reader: v1, the misses read, v2 with templates aimed at them | okay-invest specs/invest.md §14.2, §19.1 |
| `okay-tax-rules.jsonl` | okay-tax's rules: the word search, untaught readers, the search narrowing for the model, agreement | okay-tax specs/tax.md §6.1, §6.2 |

To write next: okay-desk's frames (self-play rounds and their gate; the pair chosen by agreement; the negative
results — canonical reader, looped, semantic links), okay-study (Carroll: the 35B 0/16 writing freely, Claude
16/16; CUKR 48/48), okay-tax's T1 (this repository's first lesson, from its `trace.jsonl`).
