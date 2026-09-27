# Child 04 Execution

## Controller baseline

- Product base: `3fc33d82463cb63602ff41e8a633ef1cd57b4d8e` (child 03 terminal code head).
- Required focused command: `JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=OpenAlexDataSourceTest,CoreDataSourceTest,JatsXmlEmailParserTest,EuropePmcDataSourceTest,PmcOaDataSourceTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,DiscoveryPipelineServiceTest`
- Result: exit 0, Maven BUILD SUCCESS. Backend: 342 tests, 0 failures, 0 errors, 1 skipped. Configured Node phase: 1,193 tests, 0 failures, 0 skips. JDK 11, Maven 3.9.11.
- Exact Maven log: `artifact://196`.

## Epoch 1 agent availability events

- Child 04, role IMPLEMENTER, attempt 1, 2026-09-27T02:05:17+08:00: `XmlRouteImplementer` task failed (exit 1) with output `The operation was aborted`. Product code head remained `3fc33d82463cb63602ff41e8a633ef1cd57b4d8e`; partial authorized product edits and the XML fixture ZIP remained uncommitted. Action: RETRY; no fix round consumed.
- Child 04, role IMPLEMENTER, attempt 2, 2026-09-27T02:50:16+08:00: `XmlRouteImplementerRetry` task failed (exit 1) with output `The operation was aborted`. Its focused run passed before a later test edit: 350 backend tests, 0 failures, 0 errors, 1 skipped; 1,193 configured Node tests, 0 failures, 0 skips (`artifact://239`). The full required command has not been rerun after that later test edit. Action: RETRY; no fix round consumed.
- Child 04, role IMPLEMENTER, attempt 3, 2026-09-27T02:52:29+08:00: `XmlRouteImplementerFinal` returned `PAUSED_FOR_HUMAN` (task completed) after interpreting controller handoff instructions as conflicting with `execute-p`. It made no changes and ran no tests. Epoch 1 paused; evidence commit `53e359ba60f74dc7c60547dbd2fbcae0a3ff302f`.

## Human resume

- Resumed epoch 2 at 2026-09-27T09:54:28+08:00 after user asked `可以复验了么？`.
- Product tree still has partial authorized child 04 edits from epoch 1. Preserve and inspect them; they are not a completed implementation. The latest full focused test run predates a test edit; rerun the full required command after all final changes.

## Implementation report

## Epoch 2 implementation report

- Execution result: READY_FOR_VERIFICATION. This is implementation evidence, not independent plan verification.
- Approved plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-repair-04-xml-route.md`
- Plan SHA-256: `0551cd1d533a2f4bb127bc17e8807793e6b6e38d9bf000fb0adcde94712cb2b2`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-repair-04-xml-route.md@0551cd1d533a2f4bb127bc17e8807793e6b6e38d9bf000fb0adcde94712cb2b2` (RESUME; current bytes and authorized ten-file scope unchanged).
- Target: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`, branch `fast/2026-09-26-discovery-repair-00-master`, worktree Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`.
- Pre-execution HEAD: `edfa820da84759d1e83562d732fb472fb8193955`; child 03 product base: `3fc33d82463cb63602ff41e8a633ef1cd57b4d8e`.
- Product commit: `5f3599b519952e036a48a05d0398ed7ca141c060` (`feat(fast-p): implement 04`); product code SHA and evidence HEAD are both `5f3599b519952e036a48a05d0398ed7ca141c060`. The commit contains exactly the ten authorized files and excludes this execution report.

### Implemented contract

- OpenAlex PMC routing now collects only strict raw PMCID identifiers and valid structured landing-page paths on the approved exact hosts/protocols; conflicting valid IDs resolve to null. It adds no lookup request. OpenAlex and CORE names special-case exactly one comma with nonempty family/given sides; legacy no-comma behavior remains.
- JATS identity selection supports a direct name or unambiguous name-alternatives, ignores only direct numeric labels in corresp/fn targets, and extracts exact-name/email pairs only from this article's contributor-information sections outside ref-list/sub-article. Existing element-based candidate conflict handling remains the final resolver.
- `EXTRACTION_VERSION` is `20260928`; proof `VERSION` remains `20260925`. Existing unique ORCID-only author-ID enrichment and old-cache rejection remain. Existing consumer/write, validation, qualification, duplicate, and pipeline behavior was covered by the existing and added tests; no send/config/schema/migration/production-data files changed.
- The byte-preserved evidence ZIP at `src/test/resources/discovery/xml-route-recall.zip` includes its SHA-256 manifest; parser tests verify all archived manifest entries against their bytes.

### Fresh verification

Required command, run after final code/test edits from the target worktree:

`JAVA_HOME=/Users/lukai/.jenv/versions/zulu64-11.0.15 /opt/homebrew/Cellar/maven/3.9.11/libexec/bin/mvn test -Dtest=OpenAlexDataSourceTest,CoreDataSourceTest,JatsXmlEmailParserTest,EuropePmcDataSourceTest,PmcOaDataSourceTest,ExpertDiscoveryServiceTest,DiscoveryIdentityTest,DiscoveryPipelineServiceTest`

- Result: exit 0, `BUILD SUCCESS`; Maven backend: 350 tests, 0 failures, 0 errors, 1 skipped. Configured Node phase: 1,193 tests, 0 failures. Maven elapsed time: 2m42s.
- `git diff --check`: pass.
- Machine acceptance output: `target/discovery-plan-acceptance/04.json`, generated by the integrated test from actual fixture parsing and isolated consumer calls. It records metadata/XML fixture SHA-256s `6e7cacbc7b61c38e1c42783b674b9b54da8f95694a85400c27cd49d98ab033d0` / `ee45ae9621d29ac77fbbe489e29deede9d93271dd2abcb6c6523720d103ae29b`, the PMC7759461 route, all three author/email identities, 1 metadata request, 1 XML extraction call, 3 validations, 3 RAW and 3 candidate writes, and unsupported old extraction version with unchanged RAW count 3. The JSON explicitly notes that its direct `consumeQueuedItem` scenario does not exercise a pipeline checkpoint; existing `DiscoveryPipelineServiceTest` in the required command separately verifies old-version terminal `FAILED` behavior.

### Changed files and deviations

The product commit modifies only:

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
10. `src/test/resources/discovery/xml-route-recall.zip`

- Deviations: none. No push, merge, plan amendment, or out-of-scope changes.
- Final rechecks: plan identity unchanged; worktree root/branch/Git directory unchanged; product commit is HEAD and reachable from target branch; index clean. The only working-tree change is this uncommitted fast-p execution report, excluded from the product commit.
