#!/usr/bin/env python3
"""Finite 2026-10-02 operator approval. Run on the original ES application host.

All identity data and rollback receipts stay on that host. No discovery automation
calls this migration. Default: prepare a CAS backup, without modifying ES.
"""
import argparse
import base64
import collections
import glob
import hashlib
import json
import os
import urllib.request

SOURCE = 'LEGACY_USER_APPROVED_20261002'
VERSION = 20260925
EXPECTED = 16029
MANIFEST_SHA256 = 'b001a87aea84ab98f096b676c8a8a568a1714e00155193914b369ce2cb759096'
ROOT = '/root/talent-data-backups/discovery-legacy-approval-20261002'
INDICES = ('orcid_info_candidate', 'orcid_info', 'orcid_info_application')
BINDING_FIELDS = ('orcidId', 'email', 'givenNames', 'familyNames', 'institution', 'country', 'institutionType')
FIELDS = list(BINDING_FIELDS) + ['identityVerification', 'operatorStatus', 'tags', 'expertClassification', 'filterResult']


def normalized(email):
    return (email or '').strip().lower()


def identity(s):
    return [s.get('orcidId', ''), normalized(s.get('email')), s.get('givenNames'), s.get('familyNames')]


def receipt(s):
    values = [SOURCE] + identity(s) + [s.get(k) for k in ('institution', 'country', 'institutionType')]
    digest = hashlib.sha256(json.dumps(values, ensure_ascii=False, separators=(',', ':')).encode('utf-8')).hexdigest()
    return dict(status='LEGACY_APPROVED', version=VERSION, source=SOURCE, email=normalized(s.get('email')),
                givenNames=s.get('givenNames'), familyNames=s.get('familyNames'), evidenceHash=digest)


def current_scope(s):
    return ('discovered' in (s.get('tags') or []) and s.get('operatorStatus') is None and
            (s.get('expertClassification') or {}).get('type', '') in
            ('PRODUCTION_RND', 'ACADEMIC_RND', 'HYBRID_RND', 'UNKNOWN', ''))


def make_update(row, rollback=False, current=None):
    """A single-field CAS patch. Rollback never restores the whole document."""
    snapshot = current or row
    meta = {'_index': row['_index'], '_id': row['_id'],
            'if_seq_no': snapshot['_seq_no'], 'if_primary_term': snapshot['_primary_term']}
    if not rollback:
        body = {'doc': {'identityVerification': row['approved']}}
    elif 'identityVerification' in row['_source']:
        body = {'doc': {'identityVerification': row['_source']['identityVerification']}}
    else:
        body = {'script': {'lang': 'painless', 'source': "ctx._source.remove('identityVerification')"}}
    return {'update': meta}, body


class ES:
    def __init__(self):
        env = None
        for path in glob.glob('/proc/[0-9]*/cmdline'):
            try:
                cmd = open(path, 'rb').read()
                if b'org.apache.catalina.startup.Bootstrap' not in cmd or b'java' not in cmd:
                    continue
                candidate = dict(x.decode().split('=', 1) for x in open(path.replace('cmdline', 'environ'), 'rb').read().split(b'\0') if b'=' in x)
                if 'ES_PASSWORD' in candidate:
                    env = candidate
                    break
            except (OSError, UnicodeError):
                continue
        if env is None:
            raise RuntimeError('Application environment unavailable')
        self.base = env.get('ES_BASE_URL', 'https://es-fcxvip4d.public.tencentelasticsearch.com:9200')
        self.auth = base64.b64encode((env.get('ES_USERNAME', 'elastic') + ':' + env['ES_PASSWORD']).encode()).decode()

    def request(self, path, body=None, ndjson=False):
        data = body.encode('utf-8') if ndjson else (None if body is None else json.dumps(body).encode('utf-8'))
        request = urllib.request.Request(self.base + path, data=data, headers={
            'Authorization': 'Basic ' + self.auth, 'Content-Type': 'application/x-ndjson' if ndjson else 'application/json'})
        result = json.load(urllib.request.urlopen(request, timeout=90))
        if result.get('timed_out') or result.get('_shards', {}).get('failed', 0):
            raise RuntimeError('Incomplete ES response')
        return result

    def fetch(self, references):
        docs = [{'_index': r['_index'], '_id': r['_id'], '_source': FIELDS} for r in references]
        result = self.request('/_mget', {'docs': docs})['docs']
        if len(result) != len(docs) or any('error' in r for r in result):
            raise RuntimeError('Incomplete mget')
        return result

    def bulk(self, updates):
        lines = [json.dumps(line, ensure_ascii=False) for pair in updates for line in pair]
        result = self.request('/_bulk', '\n'.join(lines) + '\n', ndjson=True)
        if len(result.get('items', [])) != len(updates):
            raise RuntimeError('Incomplete bulk response')
        return result['items']


def chunks(rows, size=200):
    for start in range(0, len(rows), size):
        yield rows[start:start + size]


def save(path, rows):
    with open(path, 'x', encoding='utf-8') as f:
        os.chmod(path, 0o600)
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + '\n')
        f.flush()
        os.fsync(f.fileno())


def load(path):
    with open(path, encoding='utf-8') as f:
        return [json.loads(line) for line in f]


def prepare(es, manifest, backup):
    if os.path.exists(backup):
        raise RuntimeError('Backup already exists; use verify/apply/rollback, never replace it')
    counts = collections.Counter()
    rows = []
    for batch in chunks(manifest):
        originals = {h['_id']: h['_source'] for h in batch}
        refs = [{'_index': index, '_id': h['_id']} for index in INDICES for h in batch]
        for h in es.fetch(refs):
            index = h['_index']
            if not h.get('found'):
                counts[index + ':absent'] += 1
                continue
            s = h['_source']
            original = originals[h['_id']]
            if identity(s) != identity(original):
                counts[index + ':different_identity'] += 1
                continue
            if s.get('identityVerification') is not None:
                counts[index + ':existing_proof'] += 1
                continue
            if index == INDICES[0] and (not current_scope(s) or any(s.get(k) != original.get(k) for k in FIELDS)):
                counts[index + ':changed_since_manifest'] += 1
                continue
            h['approved'] = receipt(s)
            rows.append(h)
            counts[index + ':planned'] += 1
    if counts[INDICES[0] + ':planned'] != EXPECTED:
        raise RuntimeError('Cohort changed; no writes performed: ' + json.dumps(counts))
    save(backup, rows)
    return counts


def operate(es, rows, mode):
    counts = collections.Counter()
    for batch in chunks(rows):
        current = es.fetch(batch)
        updates = []
        for original, live in zip(batch, current):
            if (live['_index'], live['_id']) != (original['_index'], original['_id']):
                raise RuntimeError('mget identity mismatch')
            key = original['_index'] + ':'
            if not live.get('found'):
                counts[key + 'missing'] += 1
                continue
            s = live['_source']
            proof = s.get('identityVerification')
            if mode == 'verify':
                valid = proof == original['approved'] and receipt(s) == original['approved']
                counts[key + ('approved' if valid else 'not_approved')] += 1
                continue
            if mode == 'apply':
                if proof == original['approved'] and receipt(s) == original['approved']:
                    counts[key + 'already_approved'] += 1
                    continue
                if proof is not None or (live['_seq_no'], live['_primary_term']) != (original['_seq_no'], original['_primary_term']):
                    counts[key + 'conflict'] += 1
                    continue
                updates.append(make_update(original))
            elif mode == 'rollback':
                if proof != original['approved']:
                    counts[key + 'not_ours'] += 1
                    continue
                updates.append(make_update(original, rollback=True, current=live))
        if updates:
            for item in es.bulk(updates):
                result = item['update']
                key = result['_index'] + ':'
                counts[key + ('updated' if result['status'] < 300 else 'conflict' if result['status'] == 409 else 'error')] += 1
        print(json.dumps({'progress': dict(counts)}), flush=True)
    if mode in ('apply', 'rollback'):
        es.request('/' + ','.join(INDICES) + '/_refresh')
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=('prepare', 'apply', 'verify', 'rollback'), default='prepare')
    args = parser.parse_args()
    manifest_path = ROOT + '/candidate-manifest.jsonl'
    raw = open(manifest_path, 'rb').read()
    if hashlib.sha256(raw).hexdigest() != MANIFEST_SHA256:
        raise RuntimeError('Frozen manifest digest mismatch')
    manifest = load(manifest_path)
    if len(manifest) != EXPECTED or len({h['_id'] for h in manifest}) != EXPECTED:
        raise RuntimeError('Frozen manifest cardinality mismatch')
    backup = ROOT + '/approval-cas-backup.jsonl'
    es = ES()
    if args.mode == 'prepare':
        counts = prepare(es, manifest, backup)
    else:
        rows = load(backup)
        cohort = {h['_id'] for h in manifest}
        if any(r['_id'] not in cohort or r['_index'] not in INDICES or r['approved'] != receipt(r['_source']) for r in rows):
            raise RuntimeError('Backup scope or receipt mismatch')
        if sum(r['_index'] == INDICES[0] for r in rows) != EXPECTED:
            raise RuntimeError('Backup candidate count mismatch')
        counts = operate(es, rows, args.mode)
    print(json.dumps({'mode': args.mode, 'counts': dict(counts), 'backup': backup}), flush=True)
    if any(k.endswith((':conflict', ':error', ':not_approved', ':missing')) for k in counts):
        raise SystemExit(2)


if __name__ == '__main__':
    main()
