# Discovery samples: offline parser replay

This test-classpath CLI reads schemaVersion **1** final packages from `DiscoverySampleStore`. It is not deployed in the WAR and starts no Spring application. It does not use task/queue JSON, business repositories, ES, Apollo, or provider APIs. Reports have reportVersion **1**, scope **PARSER_ONLY**. This release adds a read-only replay tool, not a parser correction or a measured improvement.

## Prerequisites and safe operation

Use an **independent linked analysis worktree**, not the primary/production checkout, with JDK 11 and Maven. The launcher refuses the primary checkout and main/master branches. It takes a worktree-local lock in the Git worktree directory; a second launcher exits 2 (`REPLAY_ALREADY_RUNNING`). An interrupted process normally releases the lock; after SIGKILL, confirm no replay/build remains before manually removing that exact lock directory. Never run builds concurrently in that worktree.

The launcher always runs:

```text
mvn -DskipTests -DskipNodeTests=true clean test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=<temporary-file>
java -Dreplay.repository=<worktree> -Dreplay.cleanBuild=true -cp <test-classes:classes:test-dependencies> com.weibo.talentintroduction.discovery.sample.DiscoverySampleReplay <individual argv>
```

Every argument is passed separately with `"$@"`; paths containing spaces are supported. Maven may access dependency repositories **during the build**. Runtime PDF/HTML HTTP execution is local, serving only the currently validated saved body/status/content type once. Any other execution or request-factory access is rejected. XML invokes the production JATS parser with its external-entity restrictions unchanged. This is code-level isolation, not a claim of an OS network sandbox or measured heap/runtime bound.

## Commands

Run these from this analysis worktree. All JSON output paths must be **outside** the canonical sample root (including through symlinks). Keep reports private: explicit reports include author/email bindings; ordinary diagnostics contain only fixed codes.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
bash scripts/expert_discovery/replay_samples.sh list \
  --samples /tmp/discovery-sample-acceptance/capture/samples \
  --output /tmp/discovery-sample-list.json
bash scripts/expert_discovery/replay_samples.sh replay \
  --samples /tmp/discovery-sample-acceptance/capture/samples --execution-id 9001 \
  --output /tmp/discovery-sample-A.json
bash scripts/expert_discovery/replay_samples.sh replay \
  --samples /tmp/discovery-sample-acceptance/capture/samples --execution-id 9001 \
  --output /tmp/discovery-sample-B.json
bash scripts/expert_discovery/replay_samples.sh compare \
  --before /tmp/discovery-sample-A.json --after /tmp/discovery-sample-B.json \
  --output /tmp/discovery-sample-diff.json
# Only compare accepts optional --labels /absolute/path/labels.json.
```

`list`/`replay` require `--samples` and `--output`; `--execution-id` is optional and must be a positive integer. Omitting it selects all currently retained executions. `compare` requires `--before`, `--after`, `--output`; optional `--labels`. Unknown/duplicate/missing flags, extra positional arguments and invalid execution IDs exit **64**. Successful operations exit **0**; input/validation/parser/report errors exit **2**. A binding difference is not a failed comparison: comparable changed results exit 0. Build failures propagate Maven's nonzero exit unchanged.

## Input boundaries and reports

Canonical layout is `<root>/<positive-execution-id>/<64-lowercase-hex-sample-id>/sample.json` and at most `body-01.bin`, `body-02.bin`. Temporary/noncanonical names are ignored, not repaired or deleted. Symlinked final directories/body files, escapes and nonregular inputs are rejected. The reader validates complete typed metadata, context, canonical paper/input/sample SHA, body length/SHA and original settings **before parsing**. Metadata is limited to 1 MiB, bodies to 10 MiB each, saved bodies to 2, attempts to 16. No truncated partial body is accepted. Limits are contractual conservative initial values, **待实测**, not tuned production measurements. Corrupt packages are individually reported without preventing other packages from being reported. A vanished file is `EXPIRED_OR_REMOVED`, never an empty successful parse.

`list` groups only valid currently retained packages by executionId/bucket: `savedSamples`, `savedBodies`, `metadataOnly`, `truncatedSamples`, `retainedBytes`; invalid packages are under `invalidSamples`. Units are **samples and bodies**, not people. Retention cannot reconstruct historical skips or population failure rates.

`replay.bodies` identifies sampleId + attempt and input/body SHA; it records all original PDF properties, known response status/content type, `deadline=null`, no postprocessing, every `AuthorEmail` field, duplicate count, parse failure, elapsedMs, parserCalls and request-factory networkCalls. `NO_BODY` (including retained 403 metadata), `BODY_LIMIT`, validation errors and `PARSE_ERROR` remain distinct. NO_BODY/BODY_LIMIT are non-error skips and never refetch. Invalid/schema/hash/expired packages make the command exit 2. XML output excludes OpenAlex ORCID/ID/institution postprocessing. PDF/HTML calls the actual production extractor with saved author order, not a copied algorithm. Offline `deadline=null` does not reconstruct production network deadlines, retries, fallback addresses or historical timeout improvements. Original production outcomes are separately retained under `productionObservations`; they are not an A baseline.

Replay provenance records HEAD, hash of local source diff plus untracked source content (not the diff itself), complete source-tree SHA, actual loaded `target/classes` and `target/test-classes` canonical paths, class counts and aggregate SHA, Java version, classpath-string SHA, and launcher cleanBuild marker. Class aggregation is SHA256 over sorted relative path UTF-8 + NUL + file bytes + NUL. Source-tree aggregation uses the same algorithm for `src/main`, `src/test`, launcher and pom. `dirtyDiffHash` excludes plans/evidence/docs; evidence-only changes cannot masquerade as parser changes. `classpathSha256` fingerprints the actual classpath string, not dependency file contents. Actual loaded main/test class fingerprints, not source state alone, identify replay code. `cleanBuild=true` is supplied only by the mandatory clean-building launcher; direct test calls report false and are not standalone clean-build evidence.

`compare` validates report version/scope, successful replay keys (and skip identities), input/body SHA and complete parser configuration before differences. Any mismatch/errors produce `INPUT_MISMATCH`, exit 2, no percentages. Different source/classes versions are expected for A/B; input/config versions must match. Bindings compare **all fields**, preserving duplicate multiplicity. Unique normalized-email pairs produce changed; conflicts/duplicates are exposed separately and compared by exact multisets, never arbitrarily paired. Reports include added/removed/changed/unchanged and full explicit differences. Without labels, `accuracy=null`, `labelledBodies=0`, metrics=null. There is no aggregate population accuracy field even with labels.

### Independent manual labels

Only independently checked truth belongs here. Do not derive labels from predictions, filenames or a model. Example format:

```json
{
  "reportVersion": 1,
  "bodies": [{
    "inputSha256": "<exact 64 lowercase hex from input>",
    "bodySha256": "<exact 64 lowercase hex from body>",
    "bindings": [{"email": "alice@example.org", "givenNames": "Alice", "familyNames": "Smith"}]
  }]
}
```

Only exact input/body SHA pairs in successful reports are accepted; mismatched/duplicate labels are rejected. Truth triples must contain nonblank strings. An empty bindings array explicitly labels a negative body; an absent body is unlabelled. `beforeMetrics`/`afterMetrics` calculate exact email/givenNames/familyNames set TP/FP/FN, precision and recall on labelled bodies only. Zero denominators yield null. Duplicates are separately visible; empty names are identity loss, not correct recall. Name conflicts are separately counted. Correct 1 + extra 1 versus truth 1 yields TP=1, FP=1, FN=0, precision=0.5, recall=1.0. That synthetic acceptance result says nothing about online quality.

## Acceptance artifacts

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -Dtest=DiscoverySampleReplayTest,PdfEmailExtractorTest,EuropePmcDataSourceTest \
  -DskipNodeTests=true -Dsample.acceptanceDir=/tmp/discovery-sample-acceptance test
bash -n scripts/expert_discovery/replay_samples.sh
```

Tests export synthetic `replay-fixtures/{good,no-body,hash-mismatch,unsupported}`, `replay/{A,B,diff,labelled-diff,good,no-body,hash-mismatch,unsupported,input-mismatch}.json`, labels.json, hashes-before.json, hashes-after.json and cases.json. Run each fixture through the CLI: expected replay exits are 0, 0, 2, 2; compare different inputs exits 2. The acceptance labels are **synthetic**, not real human truth. Tests use unchanged bytes/authors from existing `discovery/pdf-contact-coverage.zip` for real-PDF parser equivalence, plus generated HTML/JATS and external-entity XML. They do not add or rewrite historical zip resources.

For real A/B, first obtain authorization to capture a small **new** execution in the integrated test environment; copy that execution read-only into a private analysis directory, record before hashes, and run both worktrees against exactly that copy. Preserve after hashes and verify equality. Existing synthetic execution9001 packages and old 2026-09-29 archives are not task26127 or newly captured production evidence. Actual outcome improvement requires a separate labelled parser-fix evaluation.

## Enabling, stopping and retention

Capture is separately controlled by external `talent-introduction.expert-discovery.samples` properties, default `enabled=false`. Before authorized enablement, use a real absolute private directory (directory 0700, files 0600), service ownership and space check; never use a webroot. See capture runbook for configuration and limits. Default retention is 7 days with enabled startup/precommit/hourly cleanup; while disabled/stopped there is **no automatic expiry promise**. Disabling capture must not erase retained inputs. Replay does not enable capture, lock its writer, clean packages, repair SHA or mutate data. Operations may manually remove a specifically reviewed retained root after retention/privacy review; this runbook intentionally provides no generic variable-based deletion script. Keep report/labels copies under equally restricted access and retention policy.
