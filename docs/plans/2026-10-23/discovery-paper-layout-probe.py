import collections, datetime, glob, json, os, re, unicodedata

ROOT = '/root/discovery-layout-gain-czy_fjjk'
MAIL = re.compile(r'\s*(?:[Ee]-?mail\s*:\s*)?([A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})\s*')
AFFILIATION = re.compile(r'universit|department|institut|school|laborator|college|faculty|centre|center|academy', re.I)
STOP = re.compile(r'^\s*(abstract|references|introduction|acknowledg|keywords|conclusion)\b', re.I)

def norm(value):
    text = ''.join(c for c in unicodedata.normalize('NFKD', value or '') if not unicodedata.combining(c))
    return ' '.join(''.join(c.lower() if c.isalnum() else ' ' for c in text).split())

def fullname(author):
    return norm((author.get('givenNames') or '') + ' ' + (author.get('familyNames') or ''))

def complete(author):
    return bool(author.get('givenNames') and author.get('familyNames'))

def owner_of(line, names):
    clean = re.sub(r'^[\s*∗†‡§★]+|[\s,*∗†‡§★\d]+$', '', line)
    matches = names.get(norm(clean), [])
    return matches[0] if len(matches) == 1 else None

def candidates(page, authors):
    names = collections.defaultdict(list)
    for i, a in enumerate(authors):
        if complete(a):
            names[fullname(a)].append(i)
    lines = sorted(page['lines'], key=lambda l: (l['y'], l['x']))
    found = []
    for anchor in lines:
        owner = owner_of(anchor['text'], names)
        if owner is None:
            continue
        previous_y = anchor['y']
        middle = []
        for line in lines:
            if line['y'] <= anchor['y'] + 1:
                continue
            if line['y'] - anchor['y'] > 90:
                break
            # Same column, overlapping horizontal spans, bounded indentation.
            if abs(line['x'] - anchor['x']) > 36 or line['right'] < anchor['x'] - 8 or line['x'] > anchor['right'] + 8:
                continue
            if line['y'] - previous_y > 24:
                break
            previous_y = line['y']
            text = line['text'].strip()
            if not text:
                continue
            if owner_of(text, names) is not None or STOP.search(text):
                break
            if '@' in text:
                match = MAIL.fullmatch(text)
                if match:
                    found.append({'email': match.group(1).lower(), 'owner': owner,
                                  'rule': 'ADJACENT' if not middle else 'AUTHOR_BLOCK',
                                  'affiliation': bool(AFFILIATION.search(' '.join(middle))),
                                  'page': page['page'], 'anchorY': anchor['y'],
                                  'emailY': line['y'], 'middleLines': len(middle)})
                break
            middle.append(text)
            if len(middle) > 5:
                break
    # A block with unrecognized institution wording needs a repeated independent layout.
    block_owners = {c['owner'] for c in found if c['rule'] == 'AUTHOR_BLOCK'}
    return [c for c in found if c['rule'] == 'ADJACENT' or c['affiliation'] or len(block_owners) >= 2]

def checks():
    authors = [{'givenNames': 'Alice', 'familyNames': 'Smith'}, {'givenNames': 'Bob', 'familyNames': 'Jones'}]
    def line(text, y, x=10, right=140):
        return dict(text=text, y=y, x=x, right=right)
    def page(lines):
        return dict(page=1, lines=lines)
    assert len(candidates(page([line('Alice Smith', 10), line('alice@example.org', 22)]), authors)) == 1
    assert not candidates(page([line('Alice Smith', 10), line('alice@example.org', 22, 300, 450)]), authors)
    assert not candidates(page([line('Alice Smith', 10), line('Bob Jones', 22), line('bob@example.org', 34)]), authors)[0]['owner'] == 0
    assert not candidates(page([line('Alice Smith', 10), line('a@example.org, b@example.org', 22)]), authors)
    assert not candidates(page([line('Alice Smith', 10), line('alice@example.org', 90)]), authors)
    assert not candidates(page([line('Alice Smith', 10), line('alice@example.org', 22)]), authors + [authors[0]])
    assert not candidates(page([line('Alice Smith', 10), line('References', 22), line('alice@example.org', 34)]), authors)
    assert len(candidates(page([line('Alice Smith', 10), line('Example University', 22), line('alice@example.org', 34)]), authors)) == 1
    return 8

def main():
    tests = checks()
    summary = collections.Counter()
    by_bucket = collections.defaultdict(collections.Counter)
    private = []
    unique_added = collections.defaultdict(set)
    for path in sorted(glob.glob(ROOT + '/*.json')):
        data = json.load(open(path, encoding='utf8'))
        if not isinstance(data, dict) or 'manifest' not in data:
            continue
        manifest = data['manifest']
        authors = manifest['paper']['authors']
        bucket = manifest['bucket']
        original = (manifest.get('outcome') or {}).get('emails') or []
        original_emails = {v['email'].lower() for v in original}
        original_unresolved = {v['email'].lower() for v in original if not complete(v)}
        base = collections.defaultdict(set)
        extracted = set()
        all_candidates = []
        for body in data['bodies']:
            for v in body.get('emails', []):
                extracted.add(v['email'].lower())
                if complete(v):
                    base[v['email'].lower()].add(fullname(v))
            for page in body.get('pages', []):
                all_candidates.extend(candidates(page, authors))
        claims = collections.defaultdict(set)
        for c in all_candidates:
            claims[c['email']].add(fullname(authors[c['owner']]))
        additions = []
        conflicts = 0
        for email, names in claims.items():
            if len(names | base[email]) != 1:
                conflicts += 1
                continue
            if base[email]:
                continue
            evidence = [c for c in all_candidates if c['email'] == email]
            rule = 'ADJACENT' if any(c['rule'] == 'ADJACENT' for c in evidence) else 'AUTHOR_BLOCK'
            additions.append({'email': email, 'name': next(iter(names)), 'rule': rule, 'evidence': evidence,
                              'wasUnresolved': email in original_unresolved,
                              'wasExtracted': email in extracted, 'wasInOriginal': email in original_emails})
            unique_added[email].update(names)
        counts = collections.Counter(samples=1, addedPairs=len(additions), samplesWithGain=int(bool(additions)),
                                     candidateConflicts=conflicts, originalEmails=len(original),
                                     originalUnresolved=len(original_unresolved),
                                     originalResolved=sum(complete(v) for v in original),
                                     originalResolvedPreserved=sum(complete(v) and fullname(v) in base[v['email'].lower()] for v in original),
                                     newEmailAddresses=sum(not a['wasExtracted'] for a in additions),
                                     gainedFromOriginalUnresolved=sum(a['wasUnresolved'] for a in additions))
        for a in additions:
            counts[a['rule']] += 1
        summary.update(counts)
        by_bucket[bucket].update(counts)
        if additions or conflicts:
            private.append({'sample': manifest['sampleId'], 'executionId': manifest['context']['executionId'],
                            'bucket': bucket, 'additions': additions, 'conflicts': conflicts})
    report = {'asOf': datetime.datetime.utcnow().isoformat()+'Z', 'syntheticChecksPassed': tests,
              'summary': summary, 'byBucket': by_bucket, 'uniqueAddedEmails': len(unique_added),
              'crossSampleNameConflicts': sum(len(v) > 1 for v in unique_added.values())}
    for name, value in [('gain-summary.json', report), ('gain-private.json', private)]:
        with open(ROOT+'/'+name, 'w', encoding='utf8') as handle:
            json.dump(value, handle, ensure_ascii=False)
        os.chmod(ROOT+'/'+name, 0o600)
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__':
    main()
