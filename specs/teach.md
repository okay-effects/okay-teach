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
