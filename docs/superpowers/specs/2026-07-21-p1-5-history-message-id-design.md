# P1-5 History Message ID Design

## Scope

Repair only P1-5. Preserve the existing history eligibility, ordering, block formatting, limits, continuity prompt, and fallback behavior.

## Shared normalization policy

Add one module-internal `MailMessageIdNormalizer` in the mail service package. It accepts a nullable message ID and applies exactly:

1. trim outer whitespace;
2. remove one surrounding `<...>` pair;
3. trim the remaining value;
4. return an empty string for null or blank input.

`AiReplyContextBuilder` and `BounceDetector` must call this policy. They must not retain local `normalizeMessageId` implementations. This makes the normalization algorithm unique across the repository without introducing a mail-to-LLM or LLM-to-bounce dependency.

## Final-history integration contract

The inbox AI-turn and training-simulation integration tests must capture the final `mailHistory` argument received by `AiReplyDraftService`. Each fixture must prove all five outcomes together:

- old inbound is retained;
- SENT outbound is retained;
- FAILED outbound is excluded;
- PENDING outbound is excluded;
- current inbound is excluded by normalized message ID.

Existing builder tests remain the detailed contract for eligibility, current-ID matching, ordering, complete-block formatting, and the 5000-character budget.

## Authority boundary

History remains continuity-only. Existing A/B tests must continue proving that different history and operator turns cannot alter deterministic fallback content or readiness.

## Verification

Use test-first development for the shared policy, then run:

1. targeted normalizer/context/controller tests;
2. JDK 11 `mvn clean test`;
3. `node --test src/test/js/*.test.js`;
4. `git diff --check`.

No database, API, UI, or P1-1/P1-2/P1-3/P1-4/P1-6/P1-7 behavior changes are in scope.
