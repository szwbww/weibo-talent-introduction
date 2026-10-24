# Child06 execution brief

## Identity and authority
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master
- Branch: fast/discovery-recheck-master
- Exact approved child plan: docs/plans/2026-10-24/discovery-recheck-06-evidence.md
- Identity: sha256:c3a21b4a2fbfe80968f19302d23979dbfec9d1a97e14c7717ba86c55674829ae
- Product base: 440b7e0cb1daae1eb0d985fef3e12f78d1c7b607
- Prior05 terminal LIGHT_PASS_WITH_NOTES after one repair. Both full ten-child checkpoint modes VALID; clean governance HEAD e7a0dc4f72dc8da40ba60f8ac1e410e6dbe5216f.
- Read full exact child plan and linked master/evidence. Use execute-p immutable plan/worktree gates. Master identity commit:bc1cf7fedf59dc3399c2bfbd5be7db0a403b6af7; A7 records user “后续修改都授权 把所有子计划都跑完”. Necessary in-goal scope amendments have standing human authority, but controller must first evidence/commit each exact amended plan/list and ledger row. Do not self-edit extra files/plans; report concrete required paths/reason as PLAN_CONFLICT to controller for automatic governance resolution, not another user permission request. No unrelated behavior, production publishing/paid operation or history rewrite.

## Authorized Files (nine)
- src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt (new)
- src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisService.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/PaperVerificationEvidenceService.kt (new)
- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceService.kt (new)
- src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisServiceTest.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepositoryIT.kt
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/PaperVerificationEvidenceServiceTest.kt (new)
- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckAcademicEvidenceServiceTest.kt (new)

## Required invariants / acceptance
- One pure PaperBodyAnalyzer compute kernel: bytes/metadata/baseline/versions input, full result/format/identity/qualification/coverage/delta output, zero DB/ES writes. Existing AnalysisService handles lease/cache/explicit commit. Same bytes produce equivalent analysis result; explicit analysis saves once, first/body SHA unchanged. Keep02/04 cache/version contracts.
- Indexed/bounded batch paper lookup: original sourceName/sourceItemKey first, normalized DOI/PMID/PMCID validation, declared cross-execution provenance and duplicate SHA parsed once. Never load whole archive per expert. Frozen profile/source refs/evaluatedAt consumed; fixed record/attempt/hash unavailable or changed => SOURCE_UNAVAILABLE, no new download replacement. Lease only while reading, do not auto-protect archives; existing protection/deletion semantics retained.
- Reliable current email/name proof or already-bound author ID required for this expert. Same paper/name/shared mailbox/adjacent author's biography cannot grant another author's qualifications. Ambiguity diagnostics distinct from missing source. Do not change profile hash or bypass Gate. Existing reliable identity may still use ORCID/Apollo when paper missing.
- PAPER provider/SELF author self-description, real DOI/source URL or truthful same-domain archive detail URL; no fabricated provider homepage. Completed doctorate maps role="PhD completed", completed=true; PhD student never completed. Publication employment HISTORICAL only, birth only explicit full valid date. Preserve source excerpt/hash/page/paragraph/year and archiveRecordId/attemptId/bodySHA/coverage. Bounded/truncated analysis marked PARTIAL, never fake FULL_SCAN.
- REUSE zero external HTTP across all providers; frozen cache/evidence only, missing => EVIDENCE_NOT_RETAINED, never silent refresh. REFRESH only existing clients and frozen configuration. Academic data remains this round's decision/display evidence, zero ES/three-layer/profile/admission/enrichProfiles/updateExpertAcademicFields/old-reevaluation writes.
- Trusted bound OpenAlex/ORCID from IdentityVerification plus DiscoveryIdentity.allowed only; no name guessing; EMAIL-* is not ORCID, W* or unbound author rejected, multiple OpenAlex authors for one ORCID => AMBIGUOUS_IDENTITY, absent ID => NO_TRUSTED_ACADEMIC_ID/insufficient not failed qualification. Reuse existing batchEnrichByAuthorIds/Orcids, RequestKind.HISTORY_ENRICHMENT; match existing trustworthy-ID input matrix.
- Do not implement later Worker/orchestrator/API/production start paths or another crawler. No old evidence reset/re-sign, queue/proof version invalidation or ordinary discovery/mail/sender behavior change.

## Required commands and runtime proof
- env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperAnalysisServiceTest,DiscoveryPaperArchiveRepositoryIT,PaperVerificationEvidenceServiceTest,DiscoveryRecheckAcademicEvidenceServiceTest -DmysqlIt=true
- Final command after last product/test edit; real archive MySQL IT must execute, not skip/mock-SQL substitute. Bound fullJS/syntax also required by Maven. Run tests/build/lint only after implementation complete.
- Independent disposable runtime outside JUnit through actual analyzer/archive lease/lookup/bridge and academic service, real isolated MySQL where repository involved, strict fixture provider transport. Observe pure-vs-explicit saved analysis equivalence/first immutability/write counts, unique author facts vs shared/adjacent negatives, fixed refs/change/unavailability/coverage, duplicate-SHA reuse, PAPER/SELF/HISTORICAL mapping and REUSE zero external HTTP/REFRESH trusted-ID/no write boundaries. Exercise changed behavior, not prefilled desired output. No production/provider cost. Remove temporary scaffold and report exact command/exits/counts/skips/runtime limits.

## Prior outputs / downstream interfaces
- 05 V161 adds only run.recheck_json + item.review_epoch to shared stores, independent step table, null/epoch0 old compatibility. Business reads exclude uncommitted/AUDIT rechecks; history includes all. READY roster RESERVED with closed frozen hash and zero step rows; transactional READY→RUNNING activation creates eight slots idempotently. Do not regress after fixed F-05-01/F-05-02. Independent fresh primary60/additional89 JVM and eachJS1827, zero skips, actual standalone MySQL runtime passed. O-05-01 existing warnings only.
- 04 new runs freeze rule3, Worker supports stored0..3/missing default1; evaluatedAt optional Rules input; qualification RULE_VERSION3 cache key independent. Later07 wires frozen evaluatedAt/paper refs into Worker/Gate payload. AnalysisVersion2, first/bodySHA immutable.
- 03 layoutRULE_VERSION20261024, DiscoveryIdentity.VERSION20260925, EXTRACTION_VERSION20261004; no compatibility bump or cache invalidation.
- 07 consumes read-only evidence outputs then persists this round's item/trace; 08 freezes selected source refs during preparation. Provide truthful reusable contracts, not second workflow or prematurely committed analysis.

## Commit / evidence
- Explicit local implementation commit authority: feat(fast-p): implement 06, only nine authorized product/test paths. Exclude fast-p reports/logs/ledger/plans; controller commits evidence separately.
- Append full execute-p result to docs/plans/fast/discovery-recheck-master/children/06/execution.md; exact immutable identity/base/head/commands/limits, no whole-system or production acceptance claim.
- No full review-p/verify-p/repair-p/fix-v inside child loop; controller obtains fresh distinct light verifier. No later-child investigation, unrelated cleanup, other-worktree edits, push/merge/rebase/amend/reset.
