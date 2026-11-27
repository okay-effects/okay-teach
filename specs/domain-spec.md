# The domain as data (`DomainSpec`) — theory §5, SPRINT T4

A teacher model must be able to describe a domain (theory L5: choose, don't write code). So a domain is data: a
`DomainSpec` in JSON, interpreted into a `Domain` (an okay-frames `Space` and the reader's prompt).

## Format

```json
{
  "name": "tax-rules",
  "system": "You read a question about … into …",
  "languages": ["en", "pl", "ru", "uk"],
  "holes": [
    {"key": "rule", "kind": "enum", "options": ["scale", "free"], "shown": {"scale": "The tax scale: 12% and 32%"}},
    {"key": "gross", "kind": "numbers", "when": {"contract": "uop"}},
    {"key": "years", "kind": "derived", "fn": "differences", "notSaid": true},
    {"key": "table", "kind": "data", "source": "tables"}
  ]
}
```

- `key` — the hole's name and the line's key; a meaning is the vector of `(key, value)` chosen, in order.
- `kind`:
  - `enum` — `options` fixed; `shown` optional: how an option is written in the reader's line (default itself);
  - `numbers` — the numbers the text writes ("20 000", "20k", "1 000,50" — okay-desk `PayLines.numbers`), after
    fixed `extra` options (`"extra": ["none"]`);
  - `derived` — options computed by a NAMED function from the text (a closed set: `differences` — b − a of two
    numbers the text writes, a horizon from two ages); never code;
  - `data` — the members of a named list of the data (`D = Map[String, Vector[String]]`);
  - `either` — one of several keyed kinds (`"of": [{"key": "revenue", "kind": "numbers"}, …]`): each option is
    "key: value" and is its own line (okay-desk's `revenue: 20000` or `rate: 1000`).
- `when` — the hole is asked only when each given earlier key's value is among the listed (`{"contract":
  ["b2b", "compare"]}`; a value "x*" matches by prefix: `{"amount": ["rate*"]}`). Holes are walked by position, so
  a key may appear twice (the compared `gross` after the b2b lines).
- `end` (on the spec) — the reading closes with a line "end", as okay-desk's line domains do.
- `notSaid` — the hole also offers `not said` (the meaning then has no value for it).

## Its space

Holes in order, skipping those whose `when` fails; a hole's options as its kind computes them; the meaning closes
when no hole is left. A choice is written as the line `key: shown(value)`. The words of a meaning are its lines
joined by "; ", parsed back by splitting and reading `key: value` (a `shown` text mapped back to its option). The
laws (okay-frames `Laws.reach`, `roundTrip`) hold by construction for meanings whose values the text offers.

## Ports (each must read as its code domain did)

| Domain | Kinds it needs | State |
|---|---|---|
| okay-tax's rules (`TaxRules`) | one `enum` with `shown` | T4 first: the same prompt, the same lines |
| okay-desk's pay lines (`PayLines`) | `enum`, `numbers` with extra, `either`, `when` lists and prefixes, `end` | done: its lines on three kinds of question (`PaySpecSuite`) |
| okay-invest's goal (`GoalSpace`) | `numbers`, `derived`, `enum`, `notSaid`; needs percents and a line label apart from the key ("horizon years") | next |
| okay-desk's tables (`FrameSpace`) | `data`, `derived` over the tables | later: its options depend on earlier choices' data |
