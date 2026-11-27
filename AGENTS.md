# AGENTS.md — okay-teach

- Read `specs/teach.md` first; it is the contract.
- Scala 3, `-Werror`, lines at most 120 characters, English in code and docs. Scripts are scala-cli, never Python.
- Held-out questions are never written by the teacher here, never used to tune anything; a number in the spec says
  which run gave it.
- GPU runs share the Mac with other sessions: check what runs before starting one; long runs with nohup.
- Commit and push after each change; one version (0.1.0) while unused.
