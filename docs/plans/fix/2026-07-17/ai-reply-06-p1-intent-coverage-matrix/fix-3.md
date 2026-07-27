## Verification Blocked

**Round:** 3/3
**Divergence:** fix-2 had 1 P1; fix-3 has 1 P1. Verification is not converging.

### Root-cause diagnosis

The phase-6 catalog repair completed word-boundary matching but did not implement the other half of the original URL-safety constraint: a URL/query string received by the catalog can still match an alias such as `selected` (`AiReplyIntentCatalog.kt:117-147`). The required URL-query regression test from fix-1 is also absent. This is a plan-quality gate failure: the repair scope mixed two independently auditable concerns (catalog alias classification and request extraction/URL masking), while the test set only asserted the former.

### Decomposition proposal

1. **Catalog alias safety** — `AiReplyIntentCatalog.kt`, `AiReplyDraftServiceTest.kt`: define and test the catalog's input contract for URL/query text and word-boundary aliases.
2. **Request extraction handoff** — `QaRequestExtractor` and its tests: prove URL masking prevents URL/query fragments from reaching `gapItems`, then make the catalog contract consistent with that guarantee.
