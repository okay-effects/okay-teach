# The theory: models teaching models

This is the contract of okay-teach. `specs/teach.md` describes the tools built so far; `SPRINT.md` the work in
flight; `GUIDE.md` how to use them. This file says what the tools are FOR and why they are shaped as they are, so
that any agent can extend them without re-deriving the design.

## 0. The aim

**Recursive teaching.** Not one model that learns everything by itself, but a process in which one model teaches
another, and the process itself is carried out by models — so the teacher can in turn be taught by the same
process. The analogy is skills: an agent's skill is a package of instructions another agent can write. A
*competence* here is a skill made stronger: besides instructions it carries a verified engine, a trained reader and
a guarantee, each part checked by a law, not trusted.

The building block is the neurosymbolic reader of okay-desk, okay-tax, okay-invest: a small model (Qwen3-0.6B, in
a phone's browser) only *understands* — it chooses a question's meaning among finite options — and an exact engine
*answers*. The claim a competence makes is bounded, not "always right": wrong answers at most ε on questions like
its calibration's, otherwise it asks.

## 1. The factors

A competence is made of seven factors. They are ORTHOGONAL: each has its own object, its own laws, its own measure,
and its own producer, and any one can be changed while the others stay — a new base model without touching the
theory, a new engine implementation under the same laws, a new language without a new space. Because each has its
own measure, a failure is attributed to one factor (§4), which is what makes the process diagnosable and therefore
automatable.

| # | Factor | Object | Laws (checked, never trusted) | Measure | Tool today |
|---|---|---|---|---|---|
| F1 | Space | the meanings, as holes with finite options | finite options; reach; round trip; distinct words | reach share on cases | okay-frames `Space`, `Laws`, `Reach` |
| F2 | Engine | the exact semantics: meaning → answer | deterministic; total on closed meanings; its own tests | worked examples right | okay-datalog, okay-desk tables/units, okay-tax |
| F3 | Theory | the data an engine runs: rules, equations, tables | well-formed (parse, safety); judged on training exercises | held-out exercises right | `RuleSpace`, `RuleTeacher`, okay-study |
| F4 | Corpus | cases: training, calibration, sealed | disjoint provenance; sealed read only by Measure (§3) | size, coverage by meaning and language, refusals | `Teacher`, `data/*.tsv` |
| F5 | Reader | a base model, an adapter, a scoring | reads only the space's options (by construction) | right / asked / wrong on sealed | `Reader`, `OnGpu` |
| F6 | Bar | the margin under which the reader asks | fixed-sequence bound on calibration | realised wrong on sealed | `Bar` |
| F7 | Teacher | who makes the decisions of F1–F6 | acts only through the Lesson's operations (§2) | lessons' outcomes | a person, Claude, the 35B; later a trained one |

A **competence** is `C = (Space, Engine, Theory, Reader, Bar, Provenance)`, Provenance the record of how each part
was made and measured (the corpus's sources, the runs, the numbers).

**The guarantee composes.** An answer is right when the meaning is read right (F5 under F6: wrong ≤ ε_read on
questions like the calibration's), the theory gives the right result for that meaning (F3: its held-out error
ε_theory), and the engine computes it exactly (F2: by its laws, error 0). By the union bound the answer is wrong
at most ε_read + ε_theory. Each term is measured on its own factor; nothing is claimed for questions unlike the
calibration's (a new kind of question needs a new calibration, a new domain a new competence).

## 2. The lesson: teaching as an effect

The process of making a competence is a program over one effect, `Lesson`; each operation is a decision of the
teacher, each handler a tool. This is the architecture rule of every okay-effects project — the data model is
effects, the implementations are handlers — applied to teaching itself.

| Operation | What it decides | Handler today | Precondition (law) |
|---|---|---|---|
| `Describe` | the space: a `DomainSpec` (§5) | Scala code (`Domain`) | — |
| `Ground` | the engine, from the catalog (§6), or `Uncovered(why)` | the catalog | a space |
| `Theorize` | the engine's theory, by choosing, judged | `RuleTeacher` | an engine that takes a theory; training exercises |
| `Ask` | training questions for meanings | `Teacher.write` + `Laws.reach` | a space |
| `Train` | an adapter on the training cases | `OnGpu.train` | reach share ≥ θ on the cases |
| `Measure` | right / asked / wrong — aggregates only | `OnGpu.read` on the sealed set | an adapter or none; never on training cases |
| `Calibrate` | the bar | `Bar.choose` on the calibration set | calibration cases disjoint from training |
| `Diagnose` | the failures, attributed to factors (§4) | — (to build) | a measure |
| `Repair` | one action per attributed failure | — (to build) | a diagnosis |
| `Release` | the competence, if the gate passes | — (to build) | the gate (§3) |
| `Uncovered` | that no engine covers the domain, and what is missing | — (to build) | — |

The STATE of a lesson, Σ, is what exists so far: the spec, the engine, the theory, the corpora (as references,
the sealed one only by its id), the adapters, the measures, the history of operations. A teacher is anything that,
given Σ, chooses the next operation and its arguments.

## 3. The laws of the process

- **L1 — Sealed stays sealed.** The sealed set (held-out questions, held-out exercises, held-out domains) is
  readable only by `Measure`, and `Measure` returns aggregates (counts by language, by hole), never the texts. In
  code this is a capability: the sealed corpus is a handle only the `Measure` handler can open. A teacher that
  could see sealed texts would tune on them and every number would be a lie.
- **L2 — Provenance is disjoint.** Training, calibration and sealed cases come from different producers or
  different draws; a case's provenance is recorded with it.
- **L3 — Reach before training.** A case whose meaning the space does not reach is refused (it would teach what
  cannot be chosen); training starts only when the reach share is at least θ.
- **L4 — The gate.** A competence is released only when, on the sealed set, it has no more wrong than the one it
  replaces and more right overall (okay-desk's self-play gate). Silent errors are the number the guarantee rests on;
  the gate never trades them for coverage.
- **L5 — Choose, don't write.** Whatever must be formal (a space, a rule, an equation, an operation's arguments) is
  produced by choosing among options a space offers, not by free text; free text is allowed only where the next
  step reads it as prose (a plan's descriptions, a question). This is why the local 35B can do what it failed to
  do writing freely.
- **L6 — Budget.** Every lesson has a budget (teacher calls, GPU minutes, rounds); a lesson that spends it stops
  and reports where it stands.

## 4. Diagnosis: one failure, one factor

A failure on the sealed set is attributed by its signature; each signature names a factor and the repairs that
belong to it. This is the multi-factor model: the next action depends on which factor failed.

| Signature (measured) | Factor | Repairs |
|---|---|---|
| the meant meaning is not reachable for the text | F1 Space | add an option source (a number written in words, a synonym); split a hole |
| reachable, read wrong, high margin | F5 Reader (or F4: the corpus lacks such texts) | more training questions of that kind; another base size |
| reachable, read wrong, low margin | F6 Bar | nothing to repair: it asks; check the bar's coverage |
| one option chosen for everything | F5 scoring | content-free calibration; train |
| read right, answer wrong | F3 Theory | `Theorize` again with the failure told |
| read right, theory right, answer wrong | F2 Engine | fix the engine — a person's or a strong model's work, under its tests |
| right on calibration, wrong on sealed | F4 Corpus | calibration unlike the sealed: draw it the sealed's way |

## 5. The domain as data

To let a model describe a domain (L5), the space must be data, not code. A `DomainSpec` is a list of holes, each
of a kind from a closed set, and how a choice is written as a line:

- `Enum(options)` — a fixed list (a rule, a tax form, a relation);
- `FromText(kind)` — values the text itself writes: numbers, dates, quoted names, amounts with a unit;
- `FromData(source)` — values of the data: a table's column, a list's members;
- `Derived(f)` — options computed from earlier choices (a column's values after the column; differences of two
  numbers for a horizon) — a closed set of named functions, not code;
- `Lines(keys)` — a sequence of such holes by key, ended by `end` (okay-desk's line domains);

plus the meaning's assembly (a record of the chosen values) and its words (`key: value` lines, parsed back). Every
space built so far (tax rules, pay, units, clues, goals, tables) is expressible this way; `specs/teach.md` and the
SPRINT keep the list of those ported.

## 6. The catalog of engines

Most domains need a new THEORY for a general engine, not a new engine. The catalog:

| Engine | Covers | State |
|---|---|---|
| rules and logic | law, tax, regulations, syllogisms | okay-datalog |
| tables and queries | data, reports | okay-desk |
| units and dates | conversions, deadlines | okay-desk units |
| constraints and search | timetables, puzzles, plans | okay-desk's puzzle solver (partial) |
| quantities and equations | physics, chemistry, engineering, finance formulas | MISSING — the first to build (SPRINT) |
| exact arithmetic and proof | mathematics, checks | okay-proof (partial) |
| processes and states | workflows, protocols | okay-serverless (partial) |
| calculators | a domain's fixed computation (okay-tax's take-home) | per domain, registered |

When no engine fits, the teacher says so (`Uncovered`, with what is missing). A new engine is written by a person
or a strong model under its laws (compiles, its worked examples pass, exact); once accepted it joins the catalog
and serves every later domain of its kind.

## 7. Recursion

The teacher (F7) is itself a competence, so it is taught by the same lesson:

| Level | Space (its meanings) | Engine | Corpus | Sealed | Measure |
|---|---|---|---|---|---|
| L0 — a domain reader | the domain's meanings | the domain's engine | questions of the domain | held-out questions | right / asked / wrong |
| L1 — the teacher | Lesson operations and their arguments, given Σ | the Lesson's handlers (the toolkit) | traces of lessons: (Σ, the operation that helped) | held-out DOMAINS | lessons' outcomes on them |

- The **L1 space** is finite by construction: the operations of §2, their arguments chosen from Σ (which meaning
  needs questions, which repair of §4) — L5 holds at L1 too.
- The **L1 corpus** is traces: every lesson run writes its Σ and its operations; a trace step is a good example
  when the gate (L4) passed after it. The first traces come from lessons a person or Claude ran (okay-desk,
  okay-invest, okay-tax, Carroll), written as data; then from the 35B choosing operations; then from the trained
  teacher itself.
- The **L1 sealed set** is whole domains the teacher never taught: its competence is measured by what it achieves
  there alone (does its reader beat the baseline? does it say `Uncovered` where it should?).
- **Closure**: the tools that teach a reader teach the teacher; nothing new is needed at L1 but a space of
  operations and a corpus of traces.
- **Safety under recursion**: L1 holds at every level (a teacher never sees the sealed set of what it teaches;
  the teacher's own sealed set is domains); L4 holds at every level (a new teacher replaces the old only if it does
  better on sealed domains).
- **Stop**: L2 (a teacher improving the teacher's own space of operations) is a person's for now — recorded, not
  automated.

## 8. What a competence is, on disk

A competence is released as a directory: `spec.json` (the DomainSpec), the engine's name and version (from the
catalog), `theory/` (rules, equations — data), `adapter/reader.adapter.safetensors`, `bar.json` (ε, the bar, the
calibration's counts), `provenance.json` (each part's producer, run and numbers), `README.md` (what it answers,
what it does not). A page loads the base model, the adapter and the theory, and answers by `Reader` + the engine.

## 9. The process as a skill

The lesson is carried three ways, one procedure and one set of laws in each:

| Carrier | Who runs it | Where | The laws held by |
|---|---|---|---|
| the skill | any agent with a shell (Claude, opencode, a local model) | `skills/teach-a-model/SKILL.md` (Agent Skills format, linked into `~/.agents/skills`, `~/.claude/skills`) | the agent's reading of it, and the tools below |
| the tools | the skill's agent, the teacher model | `Teach` (`lesson`, `drive`, `write`, `train`, `read`, `rules`, `laws`), a domain as a JSON spec; later the same as MCP tools | the tools themselves: sealed by id, aggregates only, the gate, reach before training |
| the teacher model | itself | weights trained on traces (T7) | the space of operations it chooses in (§7: it cannot choose an operation out of order) |

The recursion closes here: a lesson run by any carrier writes a trace; traces teach the teacher model; the teacher
model, given the same skill and tools, runs lessons and writes traces. What makes it safe to hand to any model is that
the laws live in the tools, not in the model's good behaviour: a careless agent can choose badly — the trace shows it
and the gate refuses its reader — but it cannot see the sealed set or release a worse reader.
