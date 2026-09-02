# Offline Extensible Input Board

## Decision

The application will not implement Android `InputMethodService`. Horizon OS owns
the system virtual keyboard, and an app-provided IME would require separate
installation and system selection without guaranteeing an immersive overlay.

ViriViri instead supplies an application-owned input board. It is a pure
Compose client of a pure Kotlin input-method interface. The default Chinese
engine is a multi-tap T9 Pinyin engine backed by an offline lexicon; another
developer can provide another engine and layout through the same registry.

## Offline Lexicon

The historical TinyPinyin artifact was not resolvable through this project's
Maven Central and Google repository set, so it is intentionally not a runtime
dependency. The first-party Chinese method instead ships an explicit offline phrase and
common-character ranking table, then uses Android's built-in ICU `Han-Latin`
transliterator to derive a local Pinyin-to-Hanzi fallback index. It does not
contact Bilibili or any third party while typing.

The bundled phrase ranking is intentionally small and is not a complete,
frequency-optimized Chinese IME dictionary. `OfflinePinyinLexicon` remains a
public extension point: a product or language package can supply a larger,
frequency-ranked, license-reviewed offline dictionary without changing the
board contract.

## Extension Contract

`SearchInputMethod` owns its keyboard rows and reduces `SearchInputAction` into
`SearchInputSession`. The shared UI renders the method-provided layout and
candidates. `SearchInputMethodRegistry` selects the active method and is
injected into `ViriViriAppState`; no Compose or provider source needs a
language-specific conditional.
