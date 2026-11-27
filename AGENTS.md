# AGENTS.md — okay-teach

Models teaching models: a toolkit and a theory for making neurosymbolic competences (a small model that reads, an
exact engine that answers, a bar that keeps wrong answers rare), and for a teacher model that makes them itself.

Read, in this order:
1. `specs/theory.md` — the contract: the factors, the Lesson, the laws (L1 sealed stays sealed, L4 the gate, L5
   choose don't write), diagnosis, recursion. Changes to the design start there.
2. `SPRINT.md` — the queue (T1…), resume-cold; `BACKLOG.md` — can-wait work. Board before work (scrumban): queue
   an item before doing it, mark it `[x]` with its outcome when done, `CHANGELOG.md` newest first.
3. `specs/teach.md` — the tools built and their measures; `GUIDE.md` — how to run them.

Rules:
- Held-out (sealed) questions and domains are never seen by a teacher and never used to tune anything; every
  number in a spec says which run gave it (date, command, commit).
- Scala 3, `-Werror`, lines at most 120 characters, English in code and docs; scripts are scala-cli, never Python.
- GPU runs share the Mac with other sessions: check what runs first (`ps`); long runs with nohup, logs under
  `runs/` (not in git).
- Commit and push after each change; docs and board in their own commits; one version (0.1.0) while unused.
