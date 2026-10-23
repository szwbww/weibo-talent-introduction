#!/usr/bin/env python3
"""Compare private DiscoverySampleReplay reports against frozen, body-bound labels.

Labels JSON: {"bodies": [{"sampleId": str, "bodySha256": str,
"bindings": [{"email": str, "givenNames": str, "familyNames": str}]}]}.
Report bodies use the same identity/bindings fields. Detailed output is private;
stdout contains aggregate counts only. Exit 2 means differences/errors, not success.
"""
import argparse
import collections
import hashlib
import json
import os
import re
import unicodedata
from pathlib import Path


def norm(value):
    return ' '.join(''.join(c.lower() if c.isalnum() else ' ' for c in
        unicodedata.normalize('NFKD', value or '') if not unicodedata.combining(c)).split())


def index(report):
    bodies = {}
    configs = {}
    for body in report['bodies']:
        key = (body['sampleId'], body['bodySha256'])
        if not re.fullmatch('[0-9a-f]{64}', key[1] or ''):
            raise ValueError('INVALID_BODY_HASH')
        if key in bodies:
            raise ValueError('DUPLICATE_BODY_IDENTITY')
        if body.get('status', 'PARSED') != 'PARSED' or body.get('networkCalls', 0) != 0:
            raise ValueError('NON_PURE_OR_UNPARSED_BODY')
        bindings = collections.defaultdict(set)
        for binding in body['bindings']:
            email = binding['email'].lower()
            name = norm((binding.get('givenNames') or '') + ' ' + (binding.get('familyNames') or ''))
            bindings[email]  # unresolved mailbox still participates in new-email detection
            if binding.get('givenNames') and binding.get('familyNames'):
                bindings[email].add(name)
        bodies[key] = dict(bindings)
        configs[key] = body.get('parserConfig')
    if report.get('errors'):
        raise ValueError('REPLAY_ERRORS')
    return bodies, configs


def compare(before, after, labels):
    old, old_config = index(before)
    new, new_config = index(after)
    truth, _ = index(labels)
    mismatch = sorted(set(old) ^ set(new) | set(old) ^ set(truth))
    config_mismatch = sorted(k for k in set(old) & set(new) if old_config[k] != new_config[k])
    lost, added, emails, conflicts, incorrect = [], [], [], [], []
    for key in sorted(set(old) & set(new)):
        for email, names in old[key].items():
            lost.extend([*key, email, name] for name in sorted(names - new[key].get(email, set())))
        for email, names in new[key].items():
            if email not in old[key]:
                emails.append([*key, email])
            if len(names) > 1:
                conflicts.append([*key, email, sorted(names)])
            for name in sorted(names - old[key].get(email, set())):
                added.append([*key, email, name])
            for name in sorted(names - truth.get(key, {}).get(email, set())):
                incorrect.append([*key, email, name])
    return dict(aggregate=dict(beforeBodies=len(old), afterBodies=len(new), labelledBodies=len(truth),
        bodyHashMismatches=len(mismatch), configMismatches=len(config_mismatch),
        lostAssociations=len(lost), addedAssociations=len(added), newEmails=len(emails),
        conflicts=len(conflicts), incorrectAssociations=len(incorrect)),
        bodyHashMismatches=mismatch, configMismatches=config_mismatch, lostAssociations=lost,
        addedAssociations=added, newEmails=emails, conflicts=conflicts, incorrectAssociations=incorrect)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('before', 'after', 'labels', 'output'):
        parser.add_argument('--' + key, required=True, type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    try:
        inputs = [args.before, args.after, args.labels]
        if args.output.resolve() in [p.resolve() for p in inputs]:
            raise ValueError('OUTPUT_OVERWRITES_INPUT')
        reports = [json.loads(p.read_text(encoding='utf8')) for p in inputs]
        result = compare(*reports)
        result['inputSha256'] = [hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs]
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf8')
        os.chmod(args.output, 0o600)
        print(json.dumps(result['aggregate'], sort_keys=True))
        a = result['aggregate']
        return 2 if any(a[k] for k in ('bodyHashMismatches', 'configMismatches', 'lostAssociations',
            'newEmails', 'conflicts', 'incorrectAssociations')) else 0
    except (OSError, ValueError, KeyError, TypeError):
        print(json.dumps({'error': 'INVALID_OR_UNREADABLE_PRIVATE_INPUT'}))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
