# Child 03 — Approved offline replay brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-samples-master
- Branch: fast/discovery-samples-master
- Product base: e40863f6d5864ac5c882e7cb83f49e639a84396e
- Exact approved child: docs/plans/2026-10-18/discovery-samples-03-replay.md
- Plan identity: sha256:33d8fc1e3b2e00a2a865c683b8c8677ba2655cdaa4af30aea6a7eb7ef8a429b0
- Master: docs/plans/2026-10-18/discovery-samples-master.md, approved amendment commit cd6189360196700cf65c5e21ab3512ddedb98abc. A1/A2 authorize only completed child02 frozen Apollo import, not extra replay scope.
- Approval: user /fast-p invocation and subsequent 授权本地集成并继续 (after regression continue02→03).

## Complete contract
Read full exact child plan, amended master, contracts C-3..C-6 (and C-1/C-2 for input bounds), and code audit. Use execute-p, bind immutable plan SHA256 and exact worktree/branch identity. No new design or relaxation of A-1/A-2/A-3.

## Authorized files
1. src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleReplay.kt (new)
2. src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleReplayTest.kt (new)
3. scripts/expert_discovery/replay_samples.sh (new)
4. docs/runbooks/discovery-sample-replay.md (new)

No pom, production parser/model/store, existing fixture bytes/truth, or existing regression-test modifications. No Spring boot/app/DB/ES/Apollo/provider runtime use; no production API. New files are exact complete implementation, not scaffolds.

## Existing prerequisite evidence
Child01 schema/store/capture and child02 actual response hooks completed independent LIGHT_PASS_WITH_NOTES; through02 full-manifest checkpoint validated clean at5607e5024128b16608dca28f2e310e3f3be4323e. Preserve completed logs/commits.
Child02 synthetic packages are available at /tmp/discovery-sample-acceptance/capture/samples (execution9001/9002/9011) with complete PDF/HTML/XML and 403 metadata-only inputs. Its capture/cases.json and capture/apollo.json were independently verified. Only synthetic fixtures, not task26127 or real new production samples. Replay must read these unchanged as well as generate own planned fixture dirs.
Known baseline sample/Apollo broad commands have53 pre-existing service fixture errors; irrelevant to replay scope, no unrelated fixes. Current parser regression optional real EuropePMC API test skips intentionally.

## Required invariants and behavior
Child I-1..I-3, master I-2/I-4, all report/schema/limit rules C-3..C-6. Validate schema/path/size/SHA before parse; canonical final packages only, no symlink/path escapes; concurrently removed file reports EXPIRED_OR_REMOVED not empty success. Bodies readonly, outputs outside canonical sample root, no sample cleanup or rewriting.
PDF/HTML replay calls production PdfEmailExtractor.extract using saved authors/all PdfExtractionProperties, fake BoundedHttpExecutor only returning current validated bytes/status/contentType, deadline=null offline mode. RestTemplate rejects all network requests. XML calls production JatsXmlEmailParser.parse(bytes), preserving external-entity defense; PARSER_ONLY explicitly excludes OpenAlex postprocessing. Do not copy algorithms or add production parse-only interface.
list/replay/compare exact argv interface in plan; schemaVersion1/reportVersion1, stable sorting, all AuthorEmail fields and duplicate counts; input/config mismatches exit2 INPUT_MISMATCH; args exit64; bad inputs error2 while other samples still reported; NO_BODY/BODY_LIMIT/errors distinct. Missing labels accuracy=null/labelledBodies0; exact input/bodySHA labels only, metrics only labelled bodies; changed aligns unambiguous normalized email and exposes conflicts/duplicates rather than arbitrary pairing. No sampled population/accuracy/network improvement claims.
Shell must always clean test-compile/build classpath before java with argv quoted individually, validate JDK11, one run per worktree; no Spring app or production checkout clean. Classes aggregate SHA from actual loaded target/classes and HEAD/dirtyDiffHash/Java/classpath provenance. Do not use stale target or report source as compiled evidence.

## Commands and actual CLI smoke
Use JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home.
Required after final implementation:
- mvn -Dtest=DiscoverySampleReplayTest,PdfEmailExtractorTest,EuropePmcDataSourceTest -DskipNodeTests=true -Dsample.acceptanceDir=/tmp/discovery-sample-acceptance test
- bash -n scripts/expert_discovery/replay_samples.sh
- At least one full shell clean build + java path; execute actual list/replay(A)/replay(B)/compare on child02 synthetic execution9001 samples, preserving before/after input hash lists and showing zero diffs/accuracy=null. Required shell itself uses mvn -DskipTests -DskipNodeTests=true clean test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=<mktemp> then java, per plan. No shortcut to satisfy required full-shell proof. Additional synthetic malformed/no-body/labels CLI behavior should be exercised and exported by planned tests; record exits.
- Export replay-fixtures good/no-body/hash-mismatch/unsupported, replay/cases.json, labels and comparison reports as A-2/A-3 require, using only synthetic inputs. Actual PDF comes from existing approved resource zip unchanged.
Skip mid-flight build/lint/tests/formatters except necessary plan-required failing-before check. Fresh final commands and actual runtime proof are mandatory; report limits rather than fabricate network isolation/heap/runtime claims.

## Git/evidence authority
Commit only four authorized product/docs/script files locally as feat(fast-p): implement 03. Do not stage plans or fast-p logs. Append full execute-p result, exact command results/counts, CLI smoke report paths/hashes and provenance to children/03/execution.md. Do not change ledger/verify-log/fix-log/other evidence. Controller commits verification evidence separately and obtains distinct fresh light verifier.
No push/merge/rebase/reset/amend/squash/deployment/later work/unrelated fixes. If an unlisted file/behavioral decision is required, PLAN_CONFLICT. Return READY_FOR_VERIFICATION|BLOCKED|PLAN_CONFLICT, SHA, command summary and report path. Never declare overall PASS; whole-system verification is deferred.
