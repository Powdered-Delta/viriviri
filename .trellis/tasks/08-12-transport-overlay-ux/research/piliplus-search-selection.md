# PiliPlus Search Selection Behavior

## Reference

Reference worktree: `temp/PiliPlus-latest`, commit `e5dfc6394`.

Relevant source:

- `lib/pages/search/view.dart`
- `lib/pages/search/controller.dart`
- `lib/pages/search/widgets/search_text.dart`

## Observed Behavior

PiliPlus uses a single `SSearchController.onClickKeyword(String keyword)` path
for selectable non-IME search entries:

```dart
void onClickKeyword(String keyword) {
  controller.text = keyword;
  validateUid();
  if (searchSuggestion) searchSuggestList.clear();
  submit();
}
```

`submit()` records the query when enabled, calls `searchFocusNode.unfocus()`,
and routes to the search-result page. After the result route returns, it
requests focus again.

Call sites:

| Entry type | View call site | Result |
| --- | --- | --- |
| Query-driven content suggestion | `view.dart:_buildSearchSuggest` | `onClickKeyword(item.term!)` |
| Search history | `view.dart:_buildHistory` | `onClickKeyword` |
| Trending/hot search | `view.dart:_buildHotKey` | `onClickKeyword` |
| Search discovery/recommendation | `view.dart:_buildHotKey` | `onClickKeyword` |
| Text-field submit | App bar `onSubmitted` | `submit()` |
| Search icon | App bar action | `submit()` |

## ViriViri Spatial Mapping

All non-IME selectable entries use one `SelectSearchEntryAndSubmit` reducer:

1. Replace `committedQuery` with the selected term.
2. Clear active local Pinyin composition and input-method candidates.
3. Clear query-content suggestions.
4. Dispatch the existing explicit `SubmitSearch` behavior.
5. Dismiss the application-owned input console and candidate popup.
6. Request dismissal of a visible Horizon OS system IME.

The spatial implementation does not need PiliPlus's page navigation semantics.
It instead preserves the Search Canvas and renders results in its browse body.
The relevant behavioral equivalence is immediate submit plus loss of text-input
focus, not route replacement.

This applies to hot searches, search discovery/recommendation terms, query
content suggestions, and search history. It does not apply to local Pinyin
conversion candidates, which partially commit their consumed composition range
and keep the console open.
