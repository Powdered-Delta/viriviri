# Pinyin IME Composition And Candidate Notes

## Scope

Research for the ViriViri application-owned 26-key Pinyin input method. This
records input-method behavior that should guide the project contract; it is not
a claim that the current multi-tap implementation already has these features.

## Findings

- A Pinyin IME keeps raw Latin input as a composition buffer, then segments that
  buffer into convertible spans. Candidate selection operates on a span or
  range, rather than requiring the entire buffer to be committed at once.
- An apostrophe is a common explicit Pinyin syllable boundary marker. It guides
  segmentation for ambiguous sequences such as `xi'an` and `chang'an`; it is
  not copied into the committed Hanzi after conversion.
- Partial conversion must preserve the unconsumed raw buffer. For example, with
  the composition `ni'hao`, selecting a candidate covering `ni` commits `你`,
  consumes only that raw range, and leaves `hao` composing with refreshed
  candidates. This supports normal continuous Chinese entry.
- Rime/librime release notes include a change described as committing selected
  words while keeping the selected composition. This is consistent with the
  partial-commit model above.
- Candidate UI conventions commonly show a compact first row and expose more
  candidates through paging or expansion. For the spatial controller target,
  use a one-row collapsed strip with a final expand affordance; expansion opens
  a list anchored above the strip. Selecting a candidate, editing composition,
  changing candidate mode, switching language, or dismissing the keyboard
  closes the expanded list.

## Project Contract Derived From Findings

```kotlin
data class InputCandidate(
    val value: String,
    val consumedInputRange: IntRange,
    val label: String = value,
)
```

- `consumedInputRange` indexes raw composition input after segmentation markers
  are normalized for conversion. The reducer commits `value`, removes only that
  range, retains the remainder, and recomputes its candidates.
- The collapsed strip keeps its fixed allocated height in Chinese mode whether
  it has zero, one, or many candidates. It presents a trailing expand control
  only when more candidates exist than fit on the first row.
- The expanded list is a topmost input-console overlay. It does not replace
  platform hot searches or query-content suggestions.
- English phase one writes literal Latin text and has no conversion candidate
  strip; the console reserves stable geometry rather than reflowing keys.

## Sources

- Rime/librime release notes, commit `2350b775c5037d2cf1b79a7b029df3cfd6ab3cc6`,
  search result summary: "composition: commit script, keeping selected words".
- Rime composition/segmentation documentation returned by web research. Direct
  document retrieval was blocked by the local proxy fake-IP range, so the
  project contract above relies on the documented model and normal IME behavior,
  not a line-by-line vendor API citation.
