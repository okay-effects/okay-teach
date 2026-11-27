# okay-teach

Teach a small model a domain, and make a big model write formal rules by choosing.

- `Domain` — a space of meanings ([okay-frames](https://github.com/okay-effects/okay-frames)) and how a reader is
  asked about it.
- `Reader` — a text read by a language model in the space, optionally calibrated (content-free).
- `Teacher` — questions written by a teacher model, kept only when the space reaches their meaning.
- `Bar` — the margin under which a reading asks instead of answering, so wrong answers stay rare.
- `Choose`, `RuleSpace`, `RuleTeacher` — a model that cannot write Datalog whole builds it choice by choice, judged
  by a book's exercises.
- `OnGpu` — the reader and the LoRA training on Apple's GPU (MLX); `Teach` — the command.

See `specs/teach.md`. Built on [okay-effects](https://github.com/okay-effects).
