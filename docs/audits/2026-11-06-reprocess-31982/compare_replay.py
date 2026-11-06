"""Compare two file-only production replays; emit hashes/counts, never treat binds as admissions."""
import json
import sys
from pathlib import Path

before = json.loads(Path(sys.argv[1]).read_text())
after = json.loads(Path(sys.argv[2]).read_text())
assert before['manifestSha256'] == after['manifestSha256']
assert before['networkCalls'] == after['networkCalls'] == 0
assert before['businessWriteServicesConstructed'] == after['businessWriteServicesConstructed'] == 0

def key(row):
    return tuple(row[k] for k in ('recordId', 'bodySha256', 'emailHash', 'nameHash', 'page'))

old = {key(r): r for r in before['rows']}
new = {key(r): r for r in after['rows']}
assert old.keys() == new.keys() and len(old) == len(before['rows'])

def correct(row):
    return row['expectedName'] in row['newNames']

gains = [r for k, r in new.items() if correct(r) and not correct(old[k])]
lost = [r for k, r in new.items() if not correct(r) and correct(old[k])]
wrong = [r for r in new.values() if any(n and n != r['expectedName'] for n in r['newNames'])]
known = json.loads(Path(__file__).with_name('confirmed-missing-replay-masked.json').read_text())['rows']
recovered = {(r['emailHash'], r['nameHash']) for r in new.values() if correct(r)}
report = {
    'manifestSha256': after['manifestSha256'], 'networkCalls': 0,
    'businessWriteServicesConstructed': 0, 'combinations': len(new),
    'beforeCorrect': sum(map(correct, old.values())), 'afterCorrect': sum(map(correct, new.values())),
    'additionalCorrectCombinations': len(gains), 'lostCorrectCombinations': len(lost),
    'wrongBindingsWithinConfirmedTruth': len(wrong),
    'known283NowRecoverableTargets': sum((r['emailHash'], r['nameHash']) in recovered for r in known),
    'gains': [{k: r[k] for k in ('recordId', 'emailHash', 'nameHash', 'page', 'productionRules')} for r in gains],
    'lost': [{k: r[k] for k in ('recordId', 'emailHash', 'nameHash', 'page')} for r in lost],
    'limits': ['Parser binding opportunities, not new experts or admissions.',
               '47 unconfirmed column candidates remain excluded from truth; no negative-corpus coverage claim.',
               'No claim of recoverability for the other 3276 unbound targets.']
}
Path(sys.argv[3]).write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({k: v for k, v in report.items() if k not in ('gains', 'lost', 'limits')}, ensure_ascii=False))
assert not lost and not wrong, 'Binding regression against frozen truth'
