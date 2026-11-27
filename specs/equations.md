# The equations engine — theory §6, SPRINT T8

The catalog's missing general engine: quantities with dimensions, laws as equations (data), a solver that chains
them from what is given to what is asked, exactly. With it, a subject like physics, chemistry or engineering is a
THEORY (equations) for one engine, which a teacher writes by choosing and a book's worked examples judge — not a
new engine per subject.

## 1. Quantities

- A **dimension** is a vector of integer exponents over the SI base: length m, mass kg, time s, current A,
  temperature K, amount mol, luminosity cd.
- A **unit** is a dimension and a scale (exact rational) to the coherent SI unit: `km` = length × 1000,
  `min` = time × 60, `N` = kg·m·s⁻², `kWh` = energy × 3 600 000. The catalog: the SI base and derived units (N, J,
  W, Pa, Hz, C, V, Ω, …), the prefixes (k, M, m, µ, …), and the common non-SI (min, h, km/h, l, °C as an offset —
  temperatures kept apart: differences only, unless stated).
- A **quantity** is an exact value (a rational: numerator and denominator, BigInt) in coherent SI units, and its
  dimension. Arithmetic: + and − only of equal dimensions (a law, checked), × and ÷ add and subtract dimensions,
  integer powers multiply them. A square root of a non-square rational is the one inexact step: kept as a decimal to
  a stated precision and marked inexact in the derivation.

## 2. Equations as data

```
variable v: speed [m/s]
variable s: distance [m]
variable t: time [s]
law speed: v = s / t
```

- A **variable** has a name, a description and a dimension.
- A **law** is `name: x = expr`, `expr` over variables, rational constants, + − × ÷, integer powers, sqrt. A law is
  well-formed when both sides have the same dimension (checked on loading — the first law of a theory).
- A law SOLVES for any of its variables that occurs in it ONCE: the expression is inverted along the path to that
  occurrence (+ ↔ −, × ↔ ÷, x² ↔ sqrt with the sign stated by the law, e.g. `nonnegative t`). A variable occurring
  twice is not solved for by that law (no general algebra: the closed, exact part only).

## 3. The solver

Forward chaining, as Datalog does: given quantities and an asked variable, repeat — find a law where all but one of
its variables are known and the unknown one occurs once, compute it — until the asked is known or no law applies.
The answer comes with its **derivation**: each law used, with the values substituted, in order (what a person
checks, what a page shows). Several derivations to the same variable must agree (a law of the theory, checked on
the book's examples).

## 4. Theory, reading, judging

- **Theory** (factor F3): the variables and laws of a chapter — written by `Theorize`: the teacher declares the
  variables (by name, description and dimension, chosen from the units catalog) and builds each law by choosing in
  an expression space (left variable; then the right side's tree: an operator or a variable or a constant, to a
  bounded depth) — L5, as `RuleSpace` does for Datalog.
- **Judge**: the chapter's worked examples — given quantities, the asked one, the book's answer with its unit; right
  when equal within the book's rounding (its printed significant figures).
- **Reader's domain** (F1): a question into lines — `find: <variable>`, then `given: <variable> = <number> <unit>`
  for each quantity the text writes (the number from the text, the unit from the catalog, the variable from the
  theory), `end`. A `DomainSpec` with `numbers`, `enum` of units and variables, `end`.

## 5. Where it lives

A module `equations` of okay-teach first (core: JVM and Scala.js, no dependency but okay-frames for its spaces);
extracted to a library of its own (as okay-datalog) once a second project uses it.

## 6. The first theory

A chapter of a physics textbook under an open licence (OpenStax College Physics 2e, CC BY-NC-SA 4.0 — fetched
by `scripts/fetch-openstax.zsh` into `books/`, never committed: this repository is Apache-2.0; kinematics in one
dimension) — its worked examples split into the judge's (training) and the measure's (held out), as Carroll's are.

## 7. Laws of the engine (its tests)

- dimensions: a law with sides of different dimensions is refused; + of different dimensions refused;
- units: every catalog unit converts to SI and back exactly (rationals); km/h → m/s = 5/18;
- inversion: for every law and every variable occurring once, solving and substituting back gives an identity;
- the solver: on hand-made chains (s, t → v; v, t → s; v₀, a, t → v → s) the answers are exact and the derivation
  lists the laws used; an unsolvable question says which variables are missing.
