# #30711 production PDFBox replay — child 00

Execution evidence only; independent verification is still required.

## Frozen inputs and boundaries

- Manifest SHA-256: `de85addd76afe6123dce7b78ee0b2731e602a2fe0b2bf8aac442a7a4f506027c`.
- Original-byte directory (outside Git): `/Users/lukai/.local/share/paper-reprocess-30711`.
- 244 record snapshots; 337 audited combinations.
- Historical counts remain **337 combinations / 333 conservative groups / 325 batch binding opportunities**. None is an admission or new-expert count.
- 47 unconfirmed column candidates are preserved verbatim in the manifest, excluded from repair truth.
- Baseline and candidate are two real PDFBox invocations on the identical manifest. Child 00 changes no parser rules; these invocations use the same current code. `oldNames` is immutable first.outcome, not a fabricated execution of a historical binary.
- Same-process configuration provenance is recorded in the manifest. Historical deployed binary equivalence is not asserted.
- SOURCE_MISSING is never a reproduced category. Ambiguous and non-reproduced cases are not repair truth.
- Replay constructs no Spring context or business write services; network clients fail closed. Read-only source export and bounded inventory queries are separate from offline replay.

## Status and per-combination diagnostics

| Status | Count |
|---|---:|
| ALREADY_RECOVERED | 1 |
| NOT_REPRODUCED | 6 |
| REPRODUCED | 330 |

| Status | Observed condition | Count |
|---|---|---:|
| ALREADY_RECOVERED | CURRENT_BASELINE_RECOVERS_BINDING | 1 |
| NOT_REPRODUCED | EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE | 2 |
| NOT_REPRODUCED | NAME_NOT_LITERAL_IN_PDFBOX_PAGE | 4 |
| REPRODUCED | AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION | 1 |
| REPRODUCED | NO_PRODUCTION_LAYOUT_CONTACT | 329 |

Diagnostics report observed page/text/contact coverage, not guessed internal regex failures. CSV records exact production method, selected pages, PDFBox page digest, actual contact-rule hits, old/new/expected names and immutable keys.

## Reproduced candidate categories for 01

These are observed failures, not permission to invent a broader heuristic. Each source pattern must still be repaired only inside the approved 01 contract. No claim that all 325 are repairable.

| Audit pattern | Production observation | Count | Exact sample record / page / email fingerprint |
|---|---|---:|---|
| email_parenthesized_name | NO_PRODUCTION_LAYOUT_CONTACT | 21 | 31372 / 1 / 1522c43ad4d32fd525a502bb |
| explicit_correspondence | NO_PRODUCTION_LAYOUT_CONTACT | 9 | 31992 / 1 / 1b188405be73a9179d0abf41 |
| immediate_name_email | AUDITED_PAGE_OUTSIDE_PRODUCTION_SELECTION | 1 | 34257 / 15 / f21c4bc29bc87c95b91f57f0 |
| immediate_name_email | NO_PRODUCTION_LAYOUT_CONTACT | 198 | 31398 / 1 / 37e167f68fe2e9427ee6dd85 |
| initials_in_contact_block | NO_PRODUCTION_LAYOUT_CONTACT | 1 | 32689 / 1 / 72b2dfe83d5a035d73ace1e0 |
| owner_label_on_email | NO_PRODUCTION_LAYOUT_CONTACT | 50 | 31375 / 1 / b99e79a68d1f712d9a730bbb |
| single_manual | NO_PRODUCTION_LAYOUT_CONTACT | 35 | 31494 / 1 / 652c1ff996e54508acd077b2 |
| unique_author_marker | NO_PRODUCTION_LAYOUT_CONTACT | 15 | 32137 / 1 / 1cb9e85da59581715a61f338 |

## Missing, ambiguous and unreproduced samples

- `31742:1:0662e287db0e0d61dc6d883d`: NOT_REPRODUCED / EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE
- `33816:1:153fd7476453c0cc281adeb2`: NOT_REPRODUCED / EMAIL_NOT_LITERAL_IN_PDFBOX_PAGE
- `35802:1:c344f556949815852c128d94`: NOT_REPRODUCED / NAME_NOT_LITERAL_IN_PDFBOX_PAGE
- `36435:1:43d824ac08c739b71ce22ede`: NOT_REPRODUCED / NAME_NOT_LITERAL_IN_PDFBOX_PAGE
- `37245:1:e329da6289fb8a95301c86e1`: NOT_REPRODUCED / NAME_NOT_LITERAL_IN_PDFBOX_PAGE
- `37328:1:943091f45c5385e4ac7fc0ce`: NOT_REPRODUCED / NAME_NOT_LITERAL_IN_PDFBOX_PAGE

## Bounded inventory (combination counts, not people)

| State | Combinations |
|---|---:|
| COMPLETE | 11 |
| COMPLETE_NAME_MISSING_PROOF | 41 |
| EMPTY_NAME | 6 |
| MANUAL_CONTACT_OR_APPLICATION_PROTECTED | 37 |
| MULTIPLE_DOCUMENT_CONFLICT | 1 |
| NOT_CREATED | 241 |

Per-document IDs, index, version, seq_no, primary_term and admission revision are in the CSV. Any manual/contact/APPLICATION protection is retained. Multiple distinct document IDs are conflicts, never merged by name. No inventory classification grants admission.

## Runtime, commands and unchanged-state evidence

```json
{
  "commands": [
    {
      "command": "python3 docs/audits/2026-11-01-discovery-30711-pdf/recount.py",
      "exit": 0,
      "result": "PERSON 333 PAPER 244 PAPER_BINDINGS 337 prior_known 7 other_same_name 1"
    },
    {
      "command": "JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperIdentityReplayToolTest",
      "exit": 0,
      "tests": 6,
      "failures": 0,
      "errors": 0,
      "skipped": 0,
      "elapsedSeconds": 185.21,
      "artifact": "artifact://34"
    },
    {
      "command": "/usr/bin/time -l /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json /tmp/paper-reprocess-30711-baseline.json",
      "exit": 0,
      "realSeconds": 64.19,
      "userSeconds": 100.45,
      "systemSeconds": 2.74,
      "maxRssBytes": 1128906752,
      "artifact": "artifact://35"
    },
    {
      "command": "/usr/bin/time -l /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Xmx1536m -cp 'target/classes:target/weibo-talent-introduction-1.0.0-SNAPSHOT/WEB-INF/lib/*' com.weibo.talentintroduction.discovery.service.DiscoveryPaperIdentityReplayTool /Users/lukai/.local/share/paper-reprocess-30711 docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json /tmp/paper-reprocess-30711-candidate.json",
      "exit": 0,
      "realSeconds": 70.14,
      "userSeconds": 104.01,
      "systemSeconds": 2.77,
      "maxRssBytes": 1243250688,
      "artifact": "artifact://37"
    }
  ],
  "cwd": "/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master",
  "originalPdfCount": 244,
  "originalBytes": 657180346,
  "sourceRecords": 244,
  "combinationRows": 337,
  "inventoryEmailTargets": 336,
  "inventoryEsHits": 191,
  "inventoryAdmissionRows": 43,
  "inventoryContactRows": 8,
  "inventoryVersionEquality": {
    "es": true,
    "admissions": true,
    "contacts": true
  },
  "inventoryVersionDigestBefore": "d5efc55eb33af82de62e5438e5578ac057347eceb59f6d6c368b5b54caa7844c",
  "inventoryVersionDigestAfter": "d5efc55eb33af82de62e5438e5578ac057347eceb59f6d6c368b5b54caa7844c",
  "auditHashesUnchanged": true,
  "pdfHashesUnchanged": true,
  "sourceSnapshotHashesUnchanged": true,
  "baselineAndCandidateEqualExcludingTiming": true,
  "concurrency": 1,
  "sourceDiscovery": "Original audit /tmp/30711_fitz_live.py identifies /opt/talent/data/discovery-papers/blobs. Read-only SSH access resolved using configured production target. Bounded MySQL read-only transactions exported only audited IDs; 244 PDFs read via streamed tar without remote writes. ES terms query only 336 confirmed email targets, size 1000 with exact total check; metadata includes seq_no, primary_term, version. Same target inventory rerun after offline replay unchanged. No Apollo/provider calls.",
  "warnings": "PDFBox/fontbox emitted font mapping/resource warnings; not suppressed. This does not prove complete visible-text extraction."
}
```

- Baseline receipt SHA-256: `e0fe5b1e008fda6188315f73972f72af79b3897b6ca59f1a4f04c8d452b3d91c`.
- Candidate receipt SHA-256: `4f4f30bb5f5c32eeb042db366341fefbaf2a61082a8f92fb8faebf44d4fa5e38`.
- Per-file wall time is recorded as elapsedFileMs (repeated for combinations of the same record). Concurrency remains 1; RSS/CPU measurements below do not promise future throughput.
- Historical snapshots, audit files and original PDF bytes were only read. No enrichment, Apollo, OCR, admission, promotion or production-file writes were performed.

### Reconciler measurement

```sh
/usr/bin/time -l python3 scripts/discovery_admission_probe/reconcile_paper_identity_replay.py --manifest docs/audits/2026-11-01-discovery-30711-pdf/production-replay-manifest.json --old-replay /tmp/paper-reprocess-30711-baseline.json --new-replay /tmp/paper-reprocess-30711-candidate.json --audit-directory docs/audits/2026-11-01-discovery-30711-pdf --csv docs/audits/2026-11-01-discovery-30711-pdf/production-replay-results.csv --report docs/audits/2026-11-01-discovery-30711-pdf/production-replay-report.md --runtime-evidence /tmp/paper-reprocess-30711-runtime.json
```

Exit 0; 337 rows; 330 REPRODUCED, 1 ALREADY_RECOVERED, 6 NOT_REPRODUCED; 8 audit-pattern/observed-condition pairs. Time: 0.08 s real, 0.04 s user, 0.01 s system; maximum RSS 25,870,336 bytes. Both replay runs agree except per-file timing. Replay and reconciler refuse existing output paths: use fresh receipt/report filenames when repeating, never replace the frozen manifest or original audit.
