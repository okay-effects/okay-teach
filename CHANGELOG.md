# CHANGELOG — okay-teach

Newest first.

- 2026-11-28 — T7: a small teacher. A 0.6B taught on the 35B's decisions (ChoiceDomain) agrees on 10 of 11 held out
  and drove the new chapter 6 to the 35B's own release (46 of 74, baseline 36). T21 queued (a small question writer).
- 2026-11-28 — T20: L7 clearly (beat the baseline by more than chance) and asking more after a pass; the 35B took
  ch. 4 from 39 to 48 of 82 by asking for 1440 questions itself.
- 2026-11-28 — T19: the teacher checks on questions apart (L8), merges an inseparable pair, votes 3 seeds — the 35B
  alone: ch. 2 79/98, ch. 3 83/92 (merged), ch. 4 39/82, ch. 5 58/61. T20 queued (ch. 4's questions).
- 2026-11-27 — T18 the pair merged: ch. 3 three seeds voting 83 of 92 first (unmerged 74), all agreeing 55
  with 1 wrong; one adapter swings ±10 by seed; balancing throws data away. T19 queued (repairs as operations).
- 2026-11-27 — T17 contrast questions: negative. More questions of confused meanings lower the reader ~8 (seeds
  checked), contrast more; an inseparable pair flips wholesale by seed. Answer in two / merge the pair instead.
- 2026-11-27 — a second chapter, nothing tuned (OpenStax ch. 3, 92 questions): taught 71 first, 87 in two, baseline 60;
  a 5% bar answers 22 with 0 wrong; released.
- 2026-11-27 — readers voting (`Vote`, `measure a,b,c,d`): four seeds' adapters vote 79 of 98 first, 90 in two
  (each alone 68–74, 85–89); all agreeing is no guarantee (53 agreed, 5 wrong).
- 2026-11-27 — the spread over 4 seeds (book ch. 2, 98 questions): taught 71.5 first (68–74), 86.8 in two (85–89),
  baseline 52.3; always released; a 5% bar holds on 1 seed of 4 (the calibration set is too small).
- 2026-11-27 — run f: the 35B drove the book loop on the repaired set: 74 of 98 first, 87 in two, baseline 57,
  released; identical to run e (reproducible). Lint checks every sealed set before it measures.
- 2026-11-27 — the book's sealed set repaired: 36 of 134 questions were formula fragments (the fetch script
  read MathML lines as question numbers); 98 whole. Run e: 74 first, 87 in two (baseline 57, 72). `Bar.pair`; each
  measure writes its margins (no text).
- 2026-11-27 — T16: two readings (the runner-up at the least sure choice) and `okay-teach measure`; the book's
  134: right first 96, right among the first two 118 (baseline 59, 78). T13, T15: the 35B drove the book loop to 96,
  released.
- 2026-11-27 — T7 step 1: the 35B drove a whole lesson itself (taught 52 of 56, baseline 20, released); T14: three
  doors (okay-teach command, MCP tools, the skill); T13: the book loop built (OpenStax ch. 2 queued).
- 2026-11-27 — T12: the skill checked by a fresh agent (a new domain, a lesson end to end, released); its findings
  repaired, the baseline every lesson must beat (L7): residence 49 vs 22, tax 141 vs 115.
- 2026-11-27 — T1, T10: the first lessons — taught 116, then (the teacher repaired) 141 of 192 above the search's 134;
  the bar 0.421 answers 71 with 2 wrong; released.
- 2026-11-27 — T9: the teaching skill (SKILL.md, a domain as a JSON spec in the command); T6: 35 trace steps from four
  projects.
- 2026-11-27 — T8: the equations engine, laws by choosing (LawSpace, LawTeacher), kinematics from OpenStax as the
  first book (fa4dddc).
- 2026-11-27 — T4 (domain as data: tax rules and pay ported), T5 (the teacher's space, the driven lesson) built.
- 2026-11-27 — T2/T3: the Lesson effect, its GPU handler with the trace, the sealed set by id (c57e260).
- 2026-11-27 — the theory (`specs/theory.md`: seven orthogonal factors, the Lesson as an effect, the laws of the
  process, diagnosis by factor, the domain as data, the catalog of engines, recursion L0/L1), the board
  (`SPRINT.md` T1–T8, `BACKLOG.md`), `GUIDE.md`.
- 2026-11-27 — `Teach all`: the whole line in one command (f688966).
- 2026-11-27 — the rule teacher on a real book: CarrollRules, fair by its laws (293802a).
- 2026-11-27 — the tools: Domain, Reader (content-free calibration), Teacher (questions kept by the laws), Bar,
  Choose, RuleSpace, RuleTeacher, OnGpu, Teach (d893ad1).
