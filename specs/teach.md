# okay-teach — the tools (the theory they serve: specs/theory.md)

The problems it answers (okay-tax specs/tax.md §6, okay-study specs/study.md §4, okay-desk specs/frames.md):

- a small model does not understand a new domain's questions untaught: Qwen3-0.6B chose the right tax rule for 16
  of 192 held-out questions, mostly the first listed (`scale` 114 times); 4B 89; the word search 134;
- the local 35B does not write Datalog rules from a book: 0 of 16 held out, twice; a Claude agent under the same
  protocol 16 of 16;
- every reader so far was taught by hand, each its own way (okay-invest ReaderTrain, okay-desk FrameTrain).

## 1. A domain

`Domain[D, T]`: a space of meanings (okay-frames `Space[D, T]`), the reader's system prompt, the user's words for a
text, each choice written as a line of the answer, the languages. One definition for the reading, the teacher's
questions, the training examples and the laws. `Case(language, text, meant)`: a text and its meaning.

## 2. The reader, calibrated

`Reader.read`: okay-frames' `Reading` over the domain's space, each hole's options as lines scored after the prompt
and the lines before (okay-llm `Read.score`), a hole of one option taken with no forward. `Scoring.ContentFree`
(Zhao et al. 2021): each option's score less its score after a text with no content ("N/A") — what a model prefers
for itself (the first listed, the short, the fluent) taken away, what the text adds kept. The law
(`TeachSuite`): a model that prefers the first option reads it plainly whatever the text, and reads what the text
names once calibrated.

## 3. The teacher's questions, filtered by the laws

`Teacher.write`: for a meaning in its canonical words, a teacher (any model behind okay-llm's `Llm`) writes n
questions a language; a question is kept only when the space offers its meaning for it step by step
(okay-frames `Laws.reach`) — a question that drops a number the meaning needs is refused. The kept are training
cases; held-out questions are never written here.

## 4. The bar

`Bar.choose`: readings of calibration texts (margin, right); bars from the strictest down, kept while the wrong
among the answered stay within ε by (wrong + 1) / (answered + 1) ≤ ε, the sequence beginning where 1 / ε are
answered (fixed-sequence testing, Learn then Test). A fixed sequence stops at its first failure: trying lower bars
after it would choose the bar by the data. Valid for texts like the calibration's only.

## 5. A big model writes by choosing

`Choose.read`: any space read by a model that only generates — each hole a numbered question, the answer a
number parsed strictly; the pick scores 1 (margin 1), an answer that is no option 0 (a doubt). `RuleSpace`: a
Datalog rule (okay-datalog) as holes — the head, its arguments, the body's length, each literal's kind, predicate
and arguments — over a `Vocabulary` (predicates with arities, constants, the variables X, Y, Z …); only a safe rule
closes; the words the rule's text, its variables renamed in order (`canonical`). `RuleTeacher.teach`: (1) the
teacher declares the predicates it needs ("predicate: name/arity — meaning") and lists the rules wanted in plain
words; (2) each built by `Choose` in `RuleSpace` over the vocabulary grown by those predicates; (3) the program
judged on the book's exercises, what failed told back; rounds until all pass, the best kept.

`CarrollRules` (module `domains`): Carroll's soriteses (okay-study) — the reading forms the reference teacher wrote
kept, only the rules taught; the judge the training exercises (10), the measure the held out (16). Fair by its
laws: every reference rule is reached by choosing, and the judge passes the reference 10 of 10 and 16 of 16.

## 6. On Apple's GPU, and the command

`OnGpu` (module `mlx`): the reader on okay-llm's Qwen3 forward on MLX (the page's forward, so the numbers are the
page's), with an adapter or none; `train`, a LoRA on a domain's cases (rank 16, every projection, lr 2e-4, the
prompt not learned), saved for mlx-scala and for the page; `read`, the measure and its bar.

`Teach` (module `domains`): `write`, `train`, `read` over a named domain — the first `tax-rules` (okay-tax's rules,
one hole, a line "rule: <English title>"); and `rules <train> <rounds> <out>` for Carroll's rules.

## 7. Not yet measured

Everything above is built and its laws pass; the numbers come next: the calibrated reader on okay-tax's 192
held-out questions; the teacher's questions and a LoRA, against the word search's 134; the 35B building Carroll's
rules by choosing, against its 0 of 16.

### 7.1 The first lesson, okay-tax's rules (T1, 2026-11-27)

`Teach all tax-rules 8 2 ../okay-tax/data/heldout.tsv` (13:04–13:28, one GPU lock): the 35B asked for 8 questions a
language for each of 25 rules (800) wrote 359, all reached (refused 0); kept per rule from 4 to 32. A LoRA (rank 16,
2 epochs, loss 0.0988 → 0.0140, 216 s). The 192 sealed questions, right first:

| reader | right of 192 | en / pl / ru / uk (of 48) | bar at ε 0.05 |
|---|---|---|---|
| untaught, plain | 14 | 4 / 3 / 4 / 3 | none holds |
| untaught, calibrated | 16 | 5 / 2 / 3 / 6 | none holds |
| **taught, plain** | **116** | 32 / 25 / 30 / 29 | none holds |
| taught, calibrated | 90 | 24 / 20 / 23 / 23 | none holds |
| (the word search, okay-tax §6.1) | 134 | 29 / 29 / 39 / 37 | — |

Teaching works (14 → 116 from 359 teacher questions) but does not yet beat the search; content-free calibration
helps an untaught reader a little and hurts a taught one (the taught reader's preferences ARE what the text adds);
no bar keeps wrong at 5% (the margin does not separate right from wrong well enough yet). Diagnosis (theory §4):
the corpus is thin and uneven (F4: 359 of 800, 4 for some rules) — the teacher's undersupply (F7) first.

### 7.2 The second lesson: the teacher asked as people ask (T10, 2026-11-27)

T1's questions were few (one a language for many rules) and quoted the answer ("the full rate starting at 5,652 zł
(including 1,926.76 zł …)"). Repaired in the tool, not the model (6a4b118): `Domain.about` tells the teacher the
answer as its own, never to be quoted; the task asks for messages of a person who does not know the answer, one
language a call. `Teach lesson tax-rules 8 2 ../okay-tax/data/heldout.tsv runs/tax-rules-2` (13:31–13:48):

| | lesson 1 | lesson 2 |
|---|---|---|
| the teacher's questions kept | 359 of 800 (4–32 a rule) | **768** (32 every rule) |
| training (LoRA r16, 2 epochs) | loss → 0.0140 | loss 0.1884 → 0.0761 |
| untaught, plain / calibrated | 14 / 16 | 14 / 16 |
| **taught, plain** | 116 | **141** (en 35, pl 34, ru 36, uk 36 of 48) |
| taught, calibrated | 90 | 122 |
| the bar at ε 0.05 | none holds | **0.421: answered 71 of 192, wrong 2** |
| the word search | 134 | 134 |

The gate passed: the competence was released (`runs/tax-rules-2/competence/`). The taught 0.6B beats the search
(141 against 134) and, above its bar, answers 37% of the questions with 2 wrong. Caveat (theory L2): the bar was
chosen on the same 192 it is reported on — its ε is not yet validated; a calibration set apart is the next step.

### 7.3 The bar validated on a set apart (T11, 2026-11-27)

okay-tax's 192 split by a rule fixed before any look: of each rule's two questions in a language, the first
calibrates, the second reports (`data/tax-rules.calibration.tsv`, `data/tax-rules.report.tsv`, 96 each). Lesson 2's
adapter, `Teach calibrate`:

| set | right | the bar (ε 0.05, chosen on calibration) | answered | wrong among answered |
|---|---|---|---|---|
| calibration (96) | 79 | 0.484 | 28 | 0 |
| **report (96)** | 62 | 0.484 | **30** | **0** |

Above the bar the reader answered 30 of the 96 questions it had never been calibrated on, all right; below it, it
asks. 30 is few: "0 wrong of 30" bounds the true rate only loosely (one-sided 95%: under ~10%); more sealed questions
tighten it. The method holds: the bar is the calibration's, the number the report's.

### 7.4 The skill's check, and the baseline every lesson now beats (T12, 2026-11-27)

A fresh agent given only `skills/teach-a-model/SKILL.md` and a new domain (which Polish residence or work document a
question is about, 7 meanings) wrote the spec and 56 sealed questions and ran the lesson end to end in ~3 minutes
(`examples/residence.*`, `data/traces/residence.jsonl`): 224 teacher questions (32 each), untaught 19, taught 49 of
56, the bar answering 24 with 0 wrong, released. Its eleven findings were repaired in the tools and the skill
(SPRINT T12); the largest: the gate had no baseline. Both lessons re-measured with the baseline (L7: a word search
learned from the teacher's training questions, never seeing the sealed set):

| domain | untaught | baseline (words) | taught | released |
|---|---|---|---|---|
| residence documents (56) | 19 | 22 | **49** | yes |
| okay-tax's rules (192) | 14 | 115 | **141** | yes |

(okay-tax's hand-written search scored 134; the agent's own word search 54 of 56 was written from memory of the
sealed questions — tuned on them, worth nothing: why law 6 forbids it.)

### 7.5 The book loop, measured (T13, T15; 2026-11-27)

OpenStax College Physics 2e chapter 2 (8 sections), the book's own 134 questions sealed; right first:

| run | driver | the teacher's questions | epochs | untaught | baseline | taught | released |
|---|---|---|---|---|---|---|---|
| a | the 35B | 4 a section (32) | 1 | 44 | 53 | 50 | no |
| b | the 35B | 4 a section (32) | 1 | 44 | 43 | 57 | yes |
| control | fixed | 8 a section (64) | 2 | 44 | 39 | 49 | yes |
| c | the 35B | 4, then 16 a passage (176 → 880 → …) | 1 | 44 | 59 | 55 → 58 | no (budget) |
| **d** | fixed | **8 a passage (352)** | **2** | 44 | 52 | **80** (bar: 23, 0 wrong) | **yes** |
| **e** | **the 35B** | 4 a passage (176) | 2 | 44 | 59 | **96** (72%) | **yes** |

With the space repaired, the 35B's own run (e) is the best: 96 of 134 with half the fixed policy's questions. Its
errors fall into one general section (2.5, Motion Equations: from 2.4 nine times, 2.8 seven, 2.3 five) — the
meanings overlap (theory §4, F1): the next step answers with the two likeliest sections when unsure.

Passages (T15: a section cut into ~2000-character pieces, questions as a student and the book's exercises ask) and
two epochs lift the reader from ~50 to 80 of 134; more questions at one epoch do not (880 → 58). The teacher's space
was repaired from runs a and c: asking again after a failed gate, once an adapter; epochs 2 or 3.

### 7.6 Two readings, and a broken sealed set found (T16; 2026-11-27)

Run e's errors seemed to fall into one general section, 2.5 (Motion Equations), from its neighbours, so the reader
now reads twice (`Reader.readTwo`). The first reading is the best one. The second takes the runner-up at the least
sure choice, then reads the holes after it again. A measure counts both: right first, and right among the first two;
the baseline counts its own first two (`Baseline.ranked`). `okay-teach measure` re-measures an adapter already made
(12 s) and writes each reading's margin, outcome and labels — no text — to `margins.<what>.tsv`.

Those margins showed confident errors with equal margins (four at 0.3101). The cause was the SEALED SET, not the
reader: the fetch script took a formula's MathML lines ("1", ".", "80 m /s") for new numbered questions, so 36 of the
134 were fragments or duplicates, 20 of them under 2.5. Fixed (`scripts/fetch-openstax-chapter.zsh`: a number starts
a question only when it is the next expected one; the formula glued back): the chapter has 98 whole questions.
Run e's adapter on them:

| reader on the book's 98 whole questions | right first | right among the first two |
|---|---|---|
| run e's adapter (taught, plain) | **74 (76%)** | **87 (89%)** |
| the word search (baseline) | 57 | 72 |

The most confused are now small and spread (2.3 read as 2.2 four times, as 2.5 three). The margins now separate:
the 40 surest readings have 1 wrong. No bar holds at ε 5% only because that one error falls among the first 20, where
the fixed sequence begins (`Bar.choose`; `Bar.pair` likewise); at ε 10% the bar holds. Runs a–e (§7.5) were measured
on the 134; their order stands, their numbers are on that set. Lesson for the teacher (theory §4): when a measure
surprises, diagnose the measuring set (F4 Corpus) before the reader.

Run f (2026-11-27 17:19, commit 1568505, `okay-teach book books/openstax-cp2e-ch2.txt runs/openstax-ch2-f model` in a
pinned worktree): the 35B drove the loop again on the 98 whole questions. It chose the same operations (measure
untaught 24, ask 4 a passage = 176, train 2 epochs, baseline 57, measure taught **74**, in two 87, release). Every
number equals run e's adapter re-measured: the loop is reproducible end to end (the teacher's seeds, the training
seed, a greedy driver). Run-to-run spread therefore needs other seeds on purpose (BACKLOG).

### 7.7 The spread over seeds (2026-11-27, commit 42a1809)

`OKAY_TEACH_SEED=<n> okay-teach book books/openstax-cp2e-ch2.txt runs/openstax-ch2-s<n> model` (pinned worktree; the
seed changes the teacher's questions and the LoRA's start). The 35B chose the same operations every time (ask 4 a
passage = 176, train 2 epochs, release). On the 98 whole questions:

| seed | taught first | taught in two | baseline first | baseline in two | bar at ε 5% |
|---|---|---|---|---|---|
| 0 (run f) | 74 | 87 | 57 | 72 | none |
| 1 | 72 | 85 | 50 | 70 | one at 0.378: 23 answered, 0 wrong (pair: +11 in two, 0 wrong) |
| 2 | 68 | 86 | 45 | 64 | none |
| 3 | 72 | 89 | 57 | 74 | pair only: 43 answered with two, 1 wrong |
| **mean** | **71.5 (73%)** | **86.8 (89%)** | 52.3 | 70 | |

The taught reader beats the baseline by 15 to 23 on every seed, and the in-two count barely moves (85–89): it is the
number to rely on. The first-answer count moves by ±3. The bar is the unstable part: on two seeds of four it holds
nothing at 5%. One answer with a guarantee needs a bigger calibration set than 98 (BACKLOG, the bar's start).

### 7.8 Readers voting (2026-11-27)

The four adapters of §7.7 read the same 98 questions and vote (`Vote.of`: two votes for a reader's best reading,
one for its runner-up). `okay-teach measure books/openstax-cp2e-ch2.txt - <a1>,<a2>,<a3>,<a4> <cases.jsonl>`:

| | right first | right in two |
|---|---|---|
| each adapter alone | 68–74 (mean 71.5) | 85–89 |
| **the four voting** | **79 (81%)** | **90 (92%)** |

Four LoRAs on one base cost four readings a question (the base's weights shared; on the phone the adapters are a few
MB each). Agreement is no bar: all four agreed on 53 questions and 5 of those were wrong. They learn the same
mistakes from the same teacher and the same labels, so the vote raises accuracy without bounding the error.

### 7.9 A second book chapter: the loop unchanged (2026-11-27, commit 4a29167)

`scripts/fetch-openstax-chapter.zsh 3 <the five slugs>` (two-dimensional kinematics: 5 sections, 92 whole
questions; 3.1 prints none), then `okay-teach book books/openstax-cp2e-ch3.txt runs/openstax-ch3 model`, nothing
tuned for it. The 35B chose as on chapter 2 (ask 4 a passage = 124, train 2 epochs, release):

| reader on chapter 3's 92 | right first | right in two | bar at ε 5% |
|---|---|---|---|
| untaught | 5 | 20 | none |
| the word search (baseline) | 60 | 81 | — |
| **taught** | **71 (77%)** | **87 (95%)** | **0.349: 22 answered, 0 wrong** |

The confusions are the book's own overlap: 3.3 (vectors, analytical methods) read as 3.2 (graphical methods) 12 times,
3.5 as 3.4 four. The baseline is stronger here (the sections' words differ more), and the taught reader still beats
it by 11 first and 6 in two.

### 7.10 Contrast questions: a negative result (T17; 2026-11-27)

`okay-teach contrast <book.txt> <run>`: the run's adapter reads questions the teacher writes apart (2 a passage,
never trained on, never the sealed set); its confused pairs there get contrast questions ("easily taken for B, yet
about A"), a control gets as many plain questions of the same meanings; both retrained (2 epochs), read on the
sealed set. Right first, of chapter 2's 98 and chapter 3's 92:

| | ch. 2 | ch. 3 |
|---|---|---|
| before (run f; ch. 3 run) | 74 | 71 |
| contrast, one side of each pair | 78 | 39 |
| control, one side | 70 | 60 |
| contrast, both sides (pairs seen ≥ 2) | 54 | 49 |
| control, both sides | 64 | 63 |

Training noise was checked on chapter 3. The base 124 questions with other LoRA seeds give 71, 65, 66. The
both-sides control with other seeds gives 63, 58, 55. More questions of the confused meanings lower the reader by
about 8 on average, and contrast questions lower it more. Its readings drift toward the meanings that got questions:
3.5 is read 49–52 times where it is meant 25. The one gain (+4 on ch. 2) is within the spread.

What the seeds show instead: the reader cannot split 3.2 from 3.3 (vector addition, graphical vs analytical). It gives
the whole pair to one side, and which side depends on the seed: 3.2 read 4 times, then 34 times. Right in two stays
86–90. Such a pair is one meaning for this reader at this size. The remedy is the answer in two (§7.6), or merging
the pair in the space (F1), not more questions (F4). Contrast stays an experiment step and is not a Lesson operation.
The gate would have refused every one of these adapters.

Merging the pair, counted from the same readings without retraining: with 3.2 and 3.3 one meaning ("vector addition",
both sections shown), the three chapter 3 adapters are right 83, 82 and 81 of 92 first (89%). Unmerged they score
71, 65 and 66. The spread across seeds falls from 6 to 2. This is the repair to build (SPRINT T18).

### 7.11 The pair merged, trained (T18; 2026-11-27)

`okay-teach merge books/openstax-cp2e-ch3.txt 3.2 3.3 books/openstax-cp2e-ch3-m.txt <cases> <merged cases>` (a repair
of the space as data: a new book file, the teacher's 124 questions relabelled), then `train` with LoRA seeds 0–2 and
`measure`. Chapter 3's 92, right first:

| reader | seed 0 | seed 1 | seed 2 | mean | three voting |
|---|---|---|---|---|---|
| unmerged | 71 | 65 | 66 | 67.3 | 74 |
| unmerged, balanced (`OKAY_TEACH_BALANCE=1`) | 55 | 70 | 56 | 60.3 | — |
| **merged** | 60 | 82 | 83 | 75.0 | **83 (90%)** |
| merged, balanced | 72 | 68 | 82 | 74.0 | — |

1. Merging raises the mean by 8 and the vote by 9.
2. One adapter alone swings by ±10 between seeds: seed 0 merged read 22 of 3.5's questions as the merged section. The
   vote removes most of that swing.
3. Balancing (each meaning cut to the median) does not help. It throws away questions.
4. With the pair merged, agreement can be trusted. All three agreed on 55 questions and were wrong on 1 (unmerged: 49
   agreed, 4 wrong; ch. 2's four: 53, 5 wrong). The inseparable pair was where the agreeing errors came from.

The best reader of this chapter is the repaired space read by a vote of three seeds: 83 of 92 first. Where all agree
it answers 55 with 1 wrong.

### 7.12 The repairs chosen by the teacher (T19; 2026-11-27)

The Lesson gained three operations. `Check` reads questions the teacher writes apart (`valid.jsonl`, 2 a passage,
never trained on) and finds inseparable pairs (`Checked.pairs`). `Merge(a, b)` is a repair of the space; the book
loop merges two sections. `Train(epochs, seeds)` trains several adapters, which are read as a vote. A new law: **L8,
a reader is released only once checked on questions apart.** Without L8 the 35B released chapter 3 at 71 and never
looked (run t19-ch3).

Under L8, with the 35B driving (`okay-teach book … model`, runs t19b):

- **ch. 3**: measure → ask 124 → train → baseline 60 → taught 71 → **check: 3.2 and 3.3 inseparable (13 of the
  questions apart)** → **merge** → untaught again → train → taught **73** (in two 83, bar 26 answered, 0 wrong) →
  baseline 64 → check: no pair left → release.
- **ch. 2**: taught 74 → check: no inseparable pair → release 74. Nothing was merged where nothing needed it.

The teacher found and repaired the pair itself, from questions apart only. It did not choose several seeds; the state
now says what a vote of three gave (runs t19c).

Then the state said what a vote of three seeds gave (commit e9de33e; runs t19c, t19d, the 35B driving, every number on
the book's own questions):

| chapter | the teacher's path | taught (one adapter) | released | baseline |
|---|---|---|---|---|
| 2 (98) | check: no pair → 3 seeds | 74 | **79 (81%)**, vote of 3 | 57 |
| 3 (92) | check: 3.2/3.3 → merge → train → check: none → 3 seeds | 71 → 73 merged | **83 (90%)**, merged, vote of 3 | 64 |
| 4 (82) | 3 seeds → check: no pair | 27 | **39 (48%)**, vote of 3 | 35 |
| 5 (61) | check: no pair → 3 seeds | 56 | **58 (95%)**, vote of 3 | 51 |

The teacher repaired the space only where the check found a pair (chapter 3) and asked for a vote everywhere.
Chapter 3 meets T19's mark (≥ 80 of 92) with no person in the loop.

Chapter 4 (Newton's laws) was the weak one. One adapter was below the word search (27 against 35), and the vote
cleared it by only 4. The cause was the corpus size (F4), not the questions' kind. With the fixed policy asking 8 a
passage, more questions kept helping: 160 gave 27, 320 gave 35, 640 gave 44 of 82 (47 calibrated). Three seeds on
the 640 voted **50 of 82** (in two 65). On chapters 2 and 3 more questions had stopped helping. The 35B had released at
160 because asking more was allowed only after a failed gate. Now it is allowed once an adapter is measured, and the
state quotes these numbers (SPRINT T20).

A bug was found on the way: a merge relabelled the questions apart in place, so a run that copied them checked
merged labels against an unmerged space and found no pair. Fixed: `valid.m<k>.jsonl`.
