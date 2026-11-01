#!/usr/bin/env python3
"""Reconcile file-only production PDFBox receipts against the immutable audit.

Never performs network requests or business writes. Historical extraction, baseline
replay and candidate replay are separate columns; author names alone are not keys.
"""
import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def key(row):
    return (int(row['recordId']), row['bodySha256'], row['emailHash'],
            row['nameHash'], int(row['page']))


def receipt(path, manifest_hash, expected):
    value = json.loads(path.read_text())
    if value['manifestSha256'] != manifest_hash:
        raise ValueError('REPLAY_MANIFEST_MISMATCH')
    if value['networkCalls'] != 0 or value['businessWriteServicesConstructed'] != 0:
        raise ValueError('REPLAY_SIDE_EFFECT_BOUNDARY_VIOLATED')
    indexed = {key(row): row for row in value['rows']}
    if len(indexed) != len(value['rows']) or set(indexed) != expected:
        raise ValueError('REPLAY_COMBINATIONS_MISSING_OR_DUPLICATED')
    allowed = {'REPRODUCED', 'ALREADY_RECOVERED', 'SOURCE_MISSING', 'AMBIGUOUS', 'NOT_REPRODUCED'}
    if any(row['status'] not in allowed for row in indexed.values()):
        raise ValueError('UNKNOWN_REPLAY_STATUS')
    return indexed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--old-replay', type=Path, required=True)
    parser.add_argument('--new-replay', type=Path, required=True)
    parser.add_argument('--audit-directory', type=Path, required=True)
    parser.add_argument('--csv', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--runtime-evidence', type=Path, required=True)
    args = parser.parse_args()
    if args.csv.exists() or args.report.exists():
        raise ValueError('REPORT_ALREADY_EXISTS')
    manifest = json.loads(args.manifest.read_text())
    for name, digest in manifest['auditSha256'].items():
        if sha(args.audit_directory / name) != digest:
            raise ValueError('ORIGINAL_AUDIT_CHANGED: ' + name)
    audit = list(csv.DictReader((args.audit_directory / 'confirmed-bindings.csv').open()))
    combinations = manifest['combinations']
    expected = {key(row) for row in combinations}
    audit_keys = {(int(r['archive_record_id']), r['blob_hash_fingerprint'],
                   r['email_sha256_prefix24'], r['name_sha256_prefix24'], int(r['page'])) for r in audit}
    manifest_audit_keys = {(r['recordId'], hashlib.sha256(r['bodySha256'].encode()).hexdigest()[:24],
                           r['emailHash'], r['nameHash'], r['page']) for r in combinations}
    if len(expected) != len(combinations) or manifest_audit_keys != audit_keys or len(audit) != len(combinations):
        raise ValueError('AUDIT_MANIFEST_COMBINATION_MISMATCH')
    old = receipt(args.old_replay, sha(args.manifest), expected)
    new = receipt(args.new_replay, sha(args.manifest), expected)
    runtime = json.loads(args.runtime_evidence.read_text())
    merged = []
    for c in combinations:
        row = dict(new[key(c)])
        row['baselineReplayNames'] = old[key(c)]['newNames']
        row['baselineReplayStatus'] = old[key(c)]['status']
        row['baselineReplayReason'] = old[key(c)]['reason']
        row['personGroup'] = c['personGroup']
        row['sameBatchStatus'] = c['sameBatchStatus']
        merged.append(row)
    with args.csv.open('x', newline='') as out:
        writer = csv.DictWriter(out, fieldnames=list(merged[0]))
        writer.writeheader()
        for row in merged:
            writer.writerow({k: json.dumps(v, ensure_ascii=False, sort_keys=True) if isinstance(v, (list, dict)) else v
                             for k, v in row.items()})
    counts = collections.Counter(r['status'] for r in merged)
    reasons = collections.Counter((r['status'], r['reason']) for r in merged)
    inventory = collections.Counter(r['inventory']['status'] for r in merged)
    categories = collections.defaultdict(list)
    for row in merged:
        if row['status'] == 'REPRODUCED':
            categories[(row['auditRule'], row['reason'])].append(row)
    lines = ['# #30711 production PDFBox replay — child 00', '',
             'Execution evidence only; independent verification is still required.', '',
             '## Frozen inputs and boundaries', '',
             f'- Manifest SHA-256: `{sha(args.manifest)}`.',
             f'- Original-byte directory (outside Git): `{manifest["sourceDirectory"]}`.',
             f'- {len(manifest["records"])} record snapshots; {len(combinations)} audited combinations.',
             '- Historical counts remain **337 combinations / 333 conservative groups / 325 batch binding opportunities**. None is an admission or new-expert count.',
             f'- {len(manifest["negativeCandidates"])} unconfirmed column candidates are preserved verbatim in the manifest, excluded from repair truth.',
             '- Baseline and candidate are two real PDFBox invocations on the identical manifest. Child 00 changes no parser rules; these invocations use the same current code. `oldNames` is immutable first.outcome, not a fabricated execution of a historical binary.',
             '- Same-process configuration provenance is recorded in the manifest. Historical deployed binary equivalence is not asserted.',
             '- SOURCE_MISSING is never a reproduced category. Ambiguous and non-reproduced cases are not repair truth.',
             '- Replay constructs no Spring context or business write services; network clients fail closed. Read-only source export and bounded inventory queries are separate from offline replay.', '',
             '## Status and per-combination diagnostics', '', '| Status | Count |', '|---|---:|']
    lines += [f'| {status} | {count} |' for status, count in sorted(counts.items())]
    lines += ['', '| Status | Observed condition | Count |', '|---|---|---:|']
    lines += [f'| {status} | {reason} | {count} |' for (status, reason), count in sorted(reasons.items())]
    lines += ['', 'Diagnostics report observed page/text/contact coverage, not guessed internal regex failures. CSV records exact production method, selected pages, PDFBox page digest, actual contact-rule hits, old/new/expected names and immutable keys.', '',
              '## Reproduced candidate categories for 01', '',
              'These are observed failures, not permission to invent a broader heuristic. Each source pattern must still be repaired only inside the approved 01 contract. No claim that all 325 are repairable.', '',
              '| Audit pattern | Production observation | Count | Exact sample record / page / email fingerprint |', '|---|---|---:|---|']
    for (rule, reason), cases in sorted(categories.items()):
        example = cases[0]
        lines.append(f'| {rule} | {reason} | {len(cases)} | {example["recordId"]} / {example["page"]} / {example["emailHash"]} |')
    lines += ['', '## Missing, ambiguous and unreproduced samples', '']
    excluded = [r for r in merged if r['status'] in {'SOURCE_MISSING', 'AMBIGUOUS', 'NOT_REPRODUCED'}]
    lines += [f'- `{r["recordId"]}:{r["page"]}:{r["emailHash"]}`: {r["status"]} / {r["reason"]}' for r in excluded]
    if not excluded:
        lines.append('- None in the confirmed set. The separate 47 column candidates remain unconfirmed.')
    lines += ['', '## Bounded inventory (combination counts, not people)', '', '| State | Combinations |', '|---|---:|']
    lines += [f'| {state} | {count} |' for state, count in sorted(inventory.items())]
    lines += ['', 'Per-document IDs, index, version, seq_no, primary_term and admission revision are in the CSV. Any manual/contact/APPLICATION protection is retained. Multiple distinct document IDs are conflicts, never merged by name. No inventory classification grants admission.', '',
              '## Runtime, commands and unchanged-state evidence', '', '```json', json.dumps(runtime, ensure_ascii=False, indent=2), '```', '',
              f'- Baseline receipt SHA-256: `{sha(args.old_replay)}`.',
              f'- Candidate receipt SHA-256: `{sha(args.new_replay)}`.',
              '- Per-file wall time is recorded as elapsedFileMs (repeated for combinations of the same record). Concurrency remains 1; RSS/CPU measurements below do not promise future throughput.',
              '- Historical snapshots, audit files and original PDF bytes were only read. No enrichment, Apollo, OCR, admission, promotion or production-file writes were performed.', '']
    args.report.write_text('\n'.join(lines))
    print(json.dumps({'rows': len(merged), 'statuses': counts, 'reproducedCategories': len(categories)}, sort_keys=True))


if __name__ == '__main__':
    main()
