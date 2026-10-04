import copy
import io
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch
import apply_legacy_discovery_approval as migration


class FakeES:
    def __init__(self, docs):
        self.docs = docs
        self.updates = []
    def fetch(self, refs):
        return [copy.deepcopy(self.docs.get((r['_index'], r['_id']), dict(r, found=False))) for r in refs]
    def bulk(self, updates):
        self.updates.extend(updates)
        return [{'update': {'_index': u[0]['update']['_index'], 'status': 200}} for u in updates]
    def request(self, *args):
        return {}


class ApprovalMigrationTest(unittest.TestCase):
    def source(self):
        return dict(orcidId='legacy-1', email='a@example.org', givenNames='Jürgen', familyNames=None,
                    institution='Example University', country='Germany', institutionType='education',
                    tags=['discovered'], filterResult='PASSED')
    def row(self, index='orcid_info_candidate', **overrides):
        source = self.source()
        source.update(overrides)
        return dict(_index=index, _id='doc-1', _seq_no=7, _primary_term=2, found=True,
                    _source=source, approved=migration.receipt(source))
    def run_op(self, row, live, mode):
        es = FakeES({(live['_index'], live['_id']): live})
        with redirect_stdout(io.StringIO()):
            result = migration.operate(es, [row], mode)
        return result, es.updates
    def test_digest_unicode_null_and_normalization(self):
        self.assertEqual('60ad8ad4b58e121f4ed7deb6bdd7cbe774a69cda32318057e2f0c125b8c537ac', migration.receipt(self.source())['evidenceHash'])
        s = self.source(); s['email'] = ' A@EXAMPLE.ORG '
        self.assertEqual(migration.receipt(self.source()), migration.receipt(s))
        s['familyNames'] = ''
        self.assertNotEqual(migration.receipt(self.source()), migration.receipt(s))
    def test_apply_cas_and_single_field_only(self):
        row = self.row(); counts, updates = self.run_op(row, row, 'apply')
        self.assertEqual(1, counts['orcid_info_candidate:updated'])
        self.assertEqual(7, updates[0][0]['update']['if_seq_no'])
        self.assertEqual({'identityVerification'}, set(updates[0][1]['doc']))
    def test_changed_document_and_existing_proof_are_not_overwritten(self):
        row = self.row()
        for live in (dict(row, _seq_no=8), self.row(identityVerification={'status': 'VERIFIED'})):
            counts, updates = self.run_op(row, live, 'apply')
            self.assertEqual([], updates)
            self.assertEqual(1, counts['orcid_info_candidate:conflict'])
    def test_idempotency_and_live_binding_verification(self):
        row = self.row(); live = copy.deepcopy(row)
        live['_source']['identityVerification'] = row['approved']
        counts, updates = self.run_op(row, live, 'apply')
        self.assertEqual([], updates)
        self.assertEqual(1, counts['orcid_info_candidate:already_approved'])
        live['_source']['country'] = 'France'
        counts, _ = self.run_op(row, live, 'verify')
        self.assertEqual(1, counts['orcid_info_candidate:not_approved'])
    def test_rollback_preserves_other_fields_and_later_proofs(self):
        row = self.row(); live = copy.deepcopy(row)
        live['_source']['identityVerification'] = row['approved']; live['_seq_no'] = 9
        counts, updates = self.run_op(row, live, 'rollback')
        self.assertEqual(9, updates[0][0]['update']['if_seq_no'])
        self.assertEqual("ctx._source.remove('identityVerification')", updates[0][1]['script']['source'])
        live['_source']['identityVerification'] = {'status': 'VERIFIED'}
        counts, updates = self.run_op(row, live, 'rollback')
        self.assertEqual([], updates)
        self.assertEqual(1, counts['orcid_info_candidate:not_ours'])
        null_row = self.row(identityVerification=None)
        self.assertEqual({'doc': {'identityVerification': None}}, migration.make_update(null_row, rollback=True)[1])
    def test_prepare_uses_only_same_identity_replicas(self):
        candidate = self.row(); raw = self.row('orcid_info', email='someone@example.org')
        application = self.row('orcid_info_application', identityVerification={'status': 'VERIFIED'})
        es = FakeES({(r['_index'], r['_id']): r for r in (candidate, raw, application)})
        with tempfile.TemporaryDirectory() as d, patch.object(migration, 'EXPECTED', 1):
            counts = migration.prepare(es, [candidate], d + '/backup.jsonl')
            self.assertEqual(1, counts['orcid_info_candidate:planned'])
            self.assertEqual(1, counts['orcid_info:different_identity'])
            self.assertEqual(1, counts['orcid_info_application:existing_proof'])
            self.assertEqual(1, len(migration.load(d + '/backup.jsonl')))
        self.assertEqual([], es.updates)


if __name__ == '__main__':
    unittest.main()
