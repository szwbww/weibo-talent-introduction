# fix-1: ai-reply-03 structured generation fallback (P1-A/B)

复验对象: ai-reply-03-structured-generation-fallback

## Closed decisions (not reopened)
- Ack slot may use `frame.greeting` (spec OK). Do not require `resolveAck` unless easy one-liner.
- FREE_FORM multi LLM flat pool: out of scope for phase 3 (mode when no QA matches); fallback already uses composer when `requestFacts.size >= 2`.
- 7-item `generate()` e2e: P2 test gap — optional, not required for this fix.

## P1-A: REQUEST block includes request text
**File:** `AiReplyDraftService.buildGroundedUserContent`

Under each `REQUEST n`, emit `TEXT: <item.requestText>` before `STATUS` / `APPROVED FACTS` so the model binds facts→question without re-parsing inbound alone.

Shape:
```
REQUEST 1
TEXT: <requestText>
STATUS: GROUNDED
APPROVED FACTS FOR REQUEST 1:
...
```

**Test:** grounded user content asserts `TEXT:` per request item (multi-request + 7-request LLM path).

## P1-B: Research GROUNDED empty facts → not blank section
**File:** `AiReplyPointByPointComposer`

- `compose(requestFacts, expertProfile: String? = null)`
- When status is GROUNDED/PARTIAL and `joinFacts` is blank:
  - If `expertProfile` non-blank: append truncated profile excerpt (≤500 chars); do not invent research claims
  - Else: use `UNSUPPORTED_TEXT`
- `fallbackDraftText` passes `expertProfile` into composer

**Tests:**
- research GROUNDED empty `factRuleIds` + profile → section not empty (profile excerpt)
- without profile → `UNSUPPORTED_TEXT`

## Verify
```
mvn test-compile -q
mvn -Dtest=AiReplyPointByPointComposerTest,AiReplyDraftServiceTest,AiPromptConfigServiceTest surefire:test
```
