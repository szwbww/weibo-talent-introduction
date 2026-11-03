#!/usr/bin/env python3
"""Reconcile file-only production PDFBox receipts and actual reprocess runs against the immutable audit.

Never performs network requests or business writes. Historical extraction, baseline
replay and candidate replay are separate columns; author names alone are not keys.
Every stable combination is compared individually: totals alone never establish agreement.
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


def target_key(row):
    """Stable target identity. A target aggregates pages, so page is not a target key."""
    return (int(row['recordId']), row['bodySha256'], row['emailHash'], row['nameHash'])


def expected_status(old_row):
    """Every manifest combination is a human-confirmed binding. Old replay may already have
    recovered it; otherwise it remains an expected, still-unrepaired binding."""
    if old_row['status'] == 'SOURCE_MISSING':
        return 'SOURCE_MISSING'
    return 'EXPECTED_MATCH' if old_row['status'] == 'ALREADY_RECOVERED' else 'EXPECTED_MISSING'


def expected_doc(old_row):
    documents = (old_row.get('inventory') or {}).get('documents') or []
    ids = {d.get('docId') for d in documents if not d.get('protected') and d.get('docId')}
    return ids.pop() if len(ids) == 1 else None


# A replay receipt status is either explicit identity evidence, an honest absence of a binding,
# or a rejection. REPRODUCED only proves the audited page fact was re-read, never a binding.
REPLAY_BOUND_STATUSES = {'ALREADY_RECOVERED'}
REPLAY_ABSENT_STATUSES = {'REPRODUCED', 'NOT_REPRODUCED'}
# A run target outcome only binds when the actual target identity (docId and target address) is
# present and matches. SKIPPED proves identity only, never a business or admission success.
RUN_BOUND_OUTCOMES = {'NEW', 'REPAIRED', 'UNCHANGED', 'SKIPPED'}


def normalized_email_hash(email):
    """Mirror DiscoveryIdentity.normalizedEmail: trim, locale-independent lowercase, UTF-8 SHA-256 prefix 24.

    Never introduce RFC validation, alias/plus-address inference, casefold or NFKC here.
    """
    return hashlib.sha256(email.strip().lower().encode('utf-8')).hexdigest()[:24]


def flat_evidence(old_row, actual):
    """Evidence protocol for a flat --new-replay receipt: only its own status decides."""
    status = actual.get('status')
    if status in REPLAY_ABSENT_STATUSES:
        return 'ABSENT'
    if status not in REPLAY_BOUND_STATUSES or actual.get('reason') == 'CONFLICTING_PRODUCTION_BINDING':
        return 'INVALID'
    # A baseline-confirmed binding must remain confirmed; a fresh parse is not its proof.
    if old_row['status'] == 'ALREADY_RECOVERED' and status != 'ALREADY_RECOVERED':
        return 'INVALID'
    return 'BOUND'


def run_evidence(old_row, actual):
    """Evidence protocol for a real reprocess run: the actual target identity is always enforced."""
    outcome = actual.get('targetOutcome')
    if outcome == 'AMBIGUOUS_TARGETS' or outcome not in RUN_BOUND_OUTCOMES:
        return 'INVALID'
    # A real run export must carry the actual target address. A missing, null, blank or non-string
    # address never becomes a binding, and the candidate's self-reported hash is not its own proof.
    email = actual.get('targetEmail')
    if not isinstance(email, str) or not email.strip() or normalized_email_hash(email) != old_row['emailHash']:
        return 'INVALID'
    doc = actual.get('docId')
    if not doc:
        return 'INVALID'
    expected = expected_doc(old_row)
    if expected and doc != expected:
        return 'INVALID'
    return 'BOUND'


def evidence(old_row, actual, is_run):
    """Return BOUND, ABSENT or INVALID for actual evidence. Totals and names never establish identity.

    The evidence protocol is chosen by the CLI input origin (flat --new-replay vs --reprocess-run),
    never by candidate self-reported fields: a run row that also carries a `status` key is still a run.
    """
    if actual is None:
        return 'ABSENT'
    return run_evidence(old_row, actual) if is_run else flat_evidence(old_row, actual)


def classify(old_row, actual, external_reason, is_run):
    if external_reason:
        return 'EXTERNAL_DATA_CHANGED'
    if old_row['status'] == 'SOURCE_MISSING':
        return 'SOURCE_MISSING'
    state = evidence(old_row, actual, is_run)
    if state == 'INVALID':
        return 'REGRESSION'
    if state == 'ABSENT':
        # A baseline-confirmed binding that disappeared is a regression, not an honest absence.
        return 'REGRESSION' if old_row['status'] == 'ALREADY_RECOVERED' else expected_status(old_row)
    return 'EXPECTED_MATCH'


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


def run_rows(path, manifest_hash):
    """Flatten a real reprocess run export; every target candidate retains its stable key."""
    value = json.loads(path.read_text())
    if value.get('schemaVersion') != 1:
        raise ValueError('REPROCESS_RUN_SCHEMA_UNSUPPORTED')
    run = value.get('run') or {}
    if run.get('manifestSha256') and run['manifestSha256'] != manifest_hash:
        raise ValueError('REPROCESS_RUN_MANIFEST_MISMATCH')
    rows = []
    for target in value.get('targets') or []:
        for candidate in target.get('candidates') or []:
            rows.append({**candidate, 'targetOutcome': target.get('targetOutcome'),
                         'docId': target.get('docId'), 'qualification': target.get('qualification'),
                         'reasonCode': target.get('reasonCode'), 'targetEmail': target.get('email'),
                         'alreadyAdmitted': target.get('alreadyAdmitted'),
                         'actualNew': target.get('actualNew'), 'actualUpdated': target.get('actualUpdated'),
                         'actualAdmitted': target.get('actualAdmitted')})
    return value, rows


def external_changes(path):
    if not path:
        return {}
    value = json.loads(path.read_text())
    items = value if isinstance(value, list) else value.get('changes') or []
    result = {}
    for item in items:
        key_text = item.get('key') if isinstance(item, dict) else str(item)
        reason = item.get('reason', 'EXTERNAL_DATA_CHANGED') if isinstance(item, dict) else 'EXTERNAL_DATA_CHANGED'
        if not key_text:
            raise ValueError('EXTERNAL_CHANGE_WITHOUT_KEY')
        result[key_text] = reason
    return result


def external_reason_for(row, changes):
    keys = ['%s:%s:%s:%s' % (row['recordId'], row['bodySha256'], row['emailHash'], row['nameHash']),
            '%s:%s' % (row['recordId'], row['bodySha256']), str(row['recordId'])]
    for candidate in keys:
        if candidate in changes:
            return changes[candidate]
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--old-replay', type=Path, required=True)
    parser.add_argument('--new-replay', type=Path)
    parser.add_argument('--reprocess-run', type=Path)
    parser.add_argument('--external-changes', type=Path)
    parser.add_argument('--audit-directory', type=Path, required=True)
    parser.add_argument('--csv', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--runtime-evidence', type=Path, required=True)
    args = parser.parse_args()
    if not args.new_replay and not args.reprocess_run:
        raise ValueError('REPLAY_OR_REPROCESS_RUN_REQUIRED')
    if args.csv.exists() or args.report.exists():
        raise ValueError('REPORT_ALREADY_EXISTS')
    manifest = json.loads(args.manifest.read_text())
    manifest_hash = sha(args.manifest)
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
    old = receipt(args.old_replay, manifest_hash, expected)
    new = receipt(args.new_replay, manifest_hash, expected) if args.new_replay else None
    run_value, actual_rows = run_rows(args.reprocess_run, manifest_hash) if args.reprocess_run else (None, [])
    changes = external_changes(args.external_changes)
    # The evidence protocol follows the CLI input origin, not the flattened row's own fields.
    is_run = new is None

    actual_by_key = collections.defaultdict(list)
    for row in actual_rows:
        actual_by_key[target_key(row)].append(row)
    actual_manifest_keys = set(actual_by_key)
    merged = []
    for c in combinations:
        k = key(c); tk = target_key(c)
        if new is not None:
            actual = dict(new[k])
            actual['targetOutcome'] = None
        else:
            matches = actual_by_key.get(tk, [])
            actual = matches[0] if len(matches) == 1 else (None if not matches else {**matches[0], 'targetOutcome': 'AMBIGUOUS_TARGETS'})
        row = dict(new[k]) if new is not None else {'recordId': c['recordId'], 'bodySha256': c['bodySha256'],
            'page': c['page'], 'emailHash': c['emailHash'], 'nameHash': c['nameHash'],
            'auditRule': c['auditRule'], 'status': None, 'reason': None}
        row['baselineReplayNames'] = old[k]['newNames']
        row['baselineReplayStatus'] = old[k]['status']
        row['baselineReplayReason'] = old[k]['reason']
        row['personGroup'] = c['personGroup']
        row['sameBatchStatus'] = c['sameBatchStatus']
        row['expectedClassification'] = expected_status(old[k])
        row['actualTargetOutcome'] = actual.get('targetOutcome') if actual else None
        row['actualDocId'] = actual.get('docId') if actual else None
        row['actualQualification'] = actual.get('qualification') if actual else None
        row['actualReasonCode'] = actual.get('reasonCode') if actual else None
        row['externalChange'] = external_reason_for(c, changes)
        row['actualEvidence'] = evidence(old[k], actual, is_run)
        row['classification'] = classify(old[k], actual, row['externalChange'], is_run)
        merged.append(row)
    extras = []
    for tk, rows in actual_by_key.items():
        if tk in {target_key(c) for c in combinations}:
            continue
        for actual in rows:
            extras.append(dict(actual, classification='UNEXPECTED_MATCH', expectedClassification=None,
                               externalChange=external_reason_for(actual, changes), baselineReplayStatus=None))
    fieldnames = list(merged[0])
    for row in extras:
        for name in row:
            if name not in fieldnames:
                fieldnames.append(name)
    with args.csv.open('x', newline='') as out:
        writer = csv.DictWriter(out, fieldnames=fieldnames, extrasaction='ignore')
        writer.writeheader()
        for row in merged + extras:
            writer.writerow({k: json.dumps(v, ensure_ascii=False, sort_keys=True) if isinstance(v, (list, dict)) else v
                             for k, v in row.items()})
    counts = collections.Counter(r['classification'] for r in merged + extras)
    expected_counts = collections.Counter(r['expectedClassification'] for r in merged)
    reasons = collections.Counter((r['classification'], r.get('actualReasonCode') or r.get('reason')) for r in merged)
    inventory = collections.Counter(((r.get('inventory') or {}).get('status') if isinstance(r.get('inventory'), dict) else None)
                                    for r in merged)
    categories = collections.defaultdict(list)
    for row in merged:
        if row['status'] == 'REPRODUCED':
            categories[(row['auditRule'], row['reason'])].append(row)
    runtime = json.loads(args.runtime_evidence.read_text())
    lines = ['# #30711 paper binding reconciliation — child 06', '',
             'Execution evidence only; independent verification is still required. Per-row comparison is authoritative; totals are a summary.', '',
             '## Frozen inputs and boundaries', '',
             f'- Manifest SHA-256: `{manifest_hash}`.',
             f'- Original-byte directory (outside Git): `{manifest["sourceDirectory"]}`.',
             f'- {len(manifest["records"])} record snapshots; {len(combinations)} audited combinations.',
             '- Historical counts remain **337 combinations / 333 conservative groups / 325 batch binding opportunities**. None is an admission or new-expert count.',
             f'- {len(manifest["negativeCandidates"])} unconfirmed column candidates are preserved verbatim in the manifest, excluded from repair truth.',
             f'- Expected status source: baseline replay receipt `{args.old_replay}`.',
             f'- Actual source: ' + (f'actual reprocess run `{args.reprocess_run}`' if args.reprocess_run else f'candidate replay `{args.new_replay}`') + '.',
             '- SOURCE_MISSING is never a reproduced category. Ambiguous and non-reproduced cases are not repair truth.',
             '- External changes, when supplied, are listed separately and are never counted as program regressions.', '',
             '## Classification (per stable combination)', '', '| Classification | Count |', '|---|---:|']
    lines += [f'| {name} | {count} |' for name, count in sorted(counts.items())]
    lines += ['', '| Expected | Count |', '|---|---:|']
    lines += [f'| {name} | {count} |' for name, count in sorted(expected_counts.items())]
    lines += ['', '| Classification | Observed reason | Count |', '|---|---|---:|']
    lines += [f'| {name} | {reason} | {count} |' for (name, reason), count in sorted(reasons.items())]
    lines += ['', '## Per-row comparison', '',
              '| record | page | emailHash | expected | classification | actual outcome | reason |',
              '|---:|---:|---|---|---|---|---|']
    for row in merged:
        lines.append('| {recordId} | {page} | {emailHash} | {expectedClassification} | {classification} | {actualTargetOutcome} | {actualReasonCode} |'.format(**row))
    if extras:
        lines += ['', '## Actual rows outside the frozen manifest', '',
                  'These rows have no stable manifest counterpart and are listed separately, not converted into EXPECTED_MATCH.', '',
                  '| record | emailHash | classification | target outcome | reason |', '|---:|---|---|---|---|']
        for row in extras:
            lines.append('| {recordId} | {emailHash} | {classification} | {targetOutcome} | {reasonCode} |'.format(**row))
    lines += ['', '## Bounded inventory (combination counts, not people)', '', '| State | Combinations |', '|---|---:|']
    lines += [f'| {state} | {count} |' for state, count in sorted(inventory.items(), key=lambda item: str(item[0]))]
    lines += ['', 'Per-document IDs, index, version, seq_no, primary_term and admission revision are in the CSV. Any manual/contact/APPLICATION protection is retained. Multiple distinct document IDs are conflicts, never merged by name. No inventory classification grants admission.', '']
    if run_value:
        lines += ['## Actual reprocess run summary', '', '```json', json.dumps(run_value.get('run', {}), ensure_ascii=False, indent=2), '', '```', '']
    lines += ['## Runtime, commands and unchanged-state evidence', '', '```json',
              json.dumps(runtime, ensure_ascii=False, indent=2), '```', '']
    if args.new_replay:
        lines += [f'- Candidate receipt SHA-256: `{sha(args.new_replay)}`.', '']
    lines += ['- Per-file wall time is recorded as elapsedFileMs (repeated for combinations of the same record). Concurrency remains 1; RSS/CPU measurements below do not promise future throughput.',
              '- Historical snapshots, audit files and original PDF bytes were only read. No enrichment, Apollo, OCR, admission, promotion or production-file writes were performed.', '']
    args.report.write_text('\n'.join(lines))
    print(json.dumps({'rows': len(merged), 'extras': len(extras), 'classifications': counts,
                      'expected': expected_counts, 'reproducedCategories': len(categories)}, sort_keys=True))


if __name__ == '__main__':
    main()
