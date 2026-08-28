## Epoch 2 — Round 1/3
- Findings: F-1, F-2
- Before: 5ce2d706f8669415b03db53df4ff201fc292a744
- Fix commit: 7f8b28d2f09c0df7551703d8037c2b521b189152
- Authorized files changed: src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt (F-1), src/test/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexServiceTest.kt (F-2), src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt (stale live=ACTIVE assertion updated to CANDIDATE per I-5; A3-authorized #11)
- Commands: mvn test -Dtest=UnsupportedAnswerIndexServiceTest,PendingMailOperationServiceTest (zulu-11) -> exit 0, 10/0/0/0 + 2/0/0/0; mvn test (zulu-11) -> exit 0, 3005/0/0/5, JS 765 pass / 0 fail; mvn clean package (zulu-11) -> exit 0, BUILD SUCCESS; node --test src/test/js/*.test.js -> exit 0, 765 pass / 0 fail; git diff --check -> exit 0
- Result: FIXED
- Notes: F-1 — liveDocument() now enters as CANDIDATE per I-5 (ACTIVE = channel-B-converted only; activatePendingTopic() is the sole ACTIVE writer); no live=ACTIVE pins remain after updating UnsupportedAnswerIndexApiTest.kt's stale assertion, which failed the full gate. F-2 — added acceptance test asserting create() accepts TRAINING+ACTIVE and LIVE+CANDIDATE (status × sourceMode unbundled). All required commands green; evidence files excluded from the fix commit.
