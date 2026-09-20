import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('prepare_review', Path(__file__).resolve().parents[1] / 'prepare_review.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ReviewTests(unittest.TestCase):
    def test_domain_is_not_verification(self):
        self.assertEqual(module.classify_email('person@yahoo.com', 'Applied Materials'), 'PERSONAL_PROVIDER')
        self.assertEqual(module.classify_email('person@amat.com', 'Applied Materials'), 'CURRENT_COMPANY')
        self.assertEqual(module.classify_email('***@gmail.com', 'Applied Materials'), 'INVALID')
        self.assertEqual(module.classify_email('person@unfamiliar.org', 'Other'), 'UNKNOWN_DOMAIN')

    def test_title_and_personal_email_do_not_make_a_person_send_ready(self):
        row = {'name': 'Test Person', 'company': 'Applied Materials', 'jobTitle': 'Cloud Architect', 'emails': 'a@gmail.com; b@hotmail.com; a@amat.com', 'profileText': 'Cloud Architect'}
        report = module.review_row(row, [], {'checkedEmails': ['unrelated@gmail.com'], 'checkedName': 'Someone Else', 'sampleContacts': [], 'sampleEsMatches': {'total': {'value': 0}}})
        self.assertEqual(len(report['emailOptions']), 3)
        self.assertIsNone(report['selectedEmail'])
        self.assertIsNone(report['expertType'])
        self.assertFalse(report['sendReady'])
        self.assertEqual(report['duplicateCheck'], 'NOT_CHECKED')
        self.assertIn('PRIMARY_EMAIL_SELECTION_REQUIRED', report['blockers'])
        self.assertIn('CONTACTOUT_TASK_NOT_CONFIGURED', report['blockers'])

if __name__ == '__main__':
    unittest.main()
