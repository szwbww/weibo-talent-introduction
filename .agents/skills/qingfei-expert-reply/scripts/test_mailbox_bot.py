import importlib.util
import pathlib
import unittest
from unittest.mock import MagicMock


SCRIPT_PATH = pathlib.Path(__file__).with_name("mailbox_bot.py")


def load_module():
    spec = importlib.util.spec_from_file_location("mailbox_bot", SCRIPT_PATH)
    if spec is None or spec.loader is None:
        raise ImportError(f"Cannot load {SCRIPT_PATH}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class MailboxBotUnitTest(unittest.TestCase):
    def setUp(self):
        self.bot = load_module()

    def test_normalize_manual_text_and_plain_to_html_preserves_paragraphs_and_raw_urls(self):
        raw = (
            "Dear Dr Aghsami,\r\n\r\n\r\n"
            "1. Official documentation. See the Wikipedia article below.\n\n"
            "https://en.wikipedia.org/wiki/Thousand_Talents_Plan\n\n"
            "Best regards,\nWuWei\nQingfei Tech Talent Team\n"
        )
        normalized = self.bot.normalize_manual_text(raw)
        self.assertEqual(
            normalized,
            "Dear Dr Aghsami,\n\n"
            "1. Official documentation. See the Wikipedia article below.\n\n"
            "https://en.wikipedia.org/wiki/Thousand_Talents_Plan\n\n"
            "Best regards,\nWuWei\nQingfei Tech Talent Team",
        )
        html = self.bot.plain_text_to_html(normalized)
        self.assertEqual(
            html,
            "<p>Dear Dr Aghsami,</p>"
            "<p>1. Official documentation. See the Wikipedia article below.</p>"
            '<p><a href="https://en.wikipedia.org/wiki/Thousand_Talents_Plan">'
            "https://en.wikipedia.org/wiki/Thousand_Talents_Plan</a></p>"
            "<p>Best regards,<br>WuWei<br>Qingfei Tech Talent Team</p>",
        )

    def test_infer_draft_target_matches_mailbox_chat_inbound_and_outbound_rules(self):
        conv_with_inbound = {
            "contactId": 42,
            "sentCount": 2,
            "latestInbound": {"processingId": 789, "accountCode": "WuWei"},
        }
        self.assertEqual(
            self.bot.infer_draft_target(conv_with_inbound, []),
            {
                "contactId": 42,
                "kind": "INBOUND",
                "processingId": 789,
                "accountScope": "WuWei",
            },
        )

        conv_outbound_only = {
            "contactId": 99,
            "sentCount": 1,
            "latestInbound": None,
        }
        self.assertEqual(
            self.bot.infer_draft_target(conv_outbound_only, []),
            {
                "contactId": 99,
                "kind": "OUTBOUND",
                "processingId": 0,
                "accountScope": "",
            },
        )

    def test_build_save_payload_preserves_active_attachments_and_meeting_and_handles_reopen(self):
        # Case 1: Brand new draft (id=None, version=0)
        new_detail = {"id": None, "version": 0, "state": None, "content": None}
        payload_new = self.bot.build_save_payload(
            current_detail=new_detail,
            subject="Re: Research Collaboration",
            plain_text="Dear Dr Test,\n\nHello.",
        )
        self.assertEqual(payload_new["expectedVersion"], 0)
        self.assertFalse(payload_new["reopen"])
        self.assertEqual(payload_new["content"]["subject"], "Re: Research Collaboration")
        self.assertEqual(payload_new["content"]["context"]["schemaVersion"], 1)
        self.assertEqual(
            payload_new["content"]["context"]["outboundAttachmentDraft"],
            {"revision": 0, "items": []},
        )

        # Case 2: Existing ACTIVE draft with ready PDF attachment & recipientEmail
        active_detail = {
            "id": 15,
            "version": 3,
            "state": "ACTIVE",
            "content": {
                "subject": "Old Subject",
                "html": "<p>Old</p>",
                "text": "Old",
                "context": {
                    "schemaVersion": 1,
                    "recipientEmail": "a.aghsami@ut.ac.ir",
                    "qa": {"ragFactCodes": ["F1"], "ragCorpusFingerprint": "abc", "baselineText": "Old"},
                    "requestId": "req-1",
                    "meeting": {"state": "ready", "revision": 1, "blockHtml": "", "blockText": ""},
                    "meetingAccountCode": "WuWei",
                    "followUpAnchorMailRecordId": None,
                    "outboundAttachmentDraft": {
                        "revision": 2,
                        "items": [
                            {
                                "key": "att-1",
                                "state": "ready",
                                "id": "9001",
                                "filename": "Qiming_Program_Information.pdf",
                                "contentType": "application/pdf",
                                "byteLength": 12345,
                                "sha256": "deadbeef",
                                "error": None,
                                "downloadUrl": "/api/mail/conversations/42/outbound-attachments/9001/download",
                            }
                        ],
                    },
                },
            },
        }
        payload_active = self.bot.build_save_payload(
            current_detail=active_detail,
            subject="Re: Program Overview",
            plain_text="Dear Dr Aghsami,\n\nUpdated body.",
        )
        self.assertEqual(payload_active["expectedVersion"], 3)
        self.assertFalse(payload_active["reopen"])
        ctx = payload_active["content"]["context"]
        self.assertIsNone(ctx["qa"])  # Cleared so manual text doesn't collide with old QA fingerprint
        self.assertEqual(ctx["recipientEmail"], "a.aghsami@ut.ac.ir")
        self.assertEqual(ctx["meetingAccountCode"], "WuWei")
        self.assertEqual(len(ctx["outboundAttachmentDraft"]["items"]), 1)
        # downloadUrl stripped because MailReplyDraftContextCodec has FAIL_ON_UNKNOWN_PROPERTIES!
        self.assertNotIn("downloadUrl", ctx["outboundAttachmentDraft"]["items"][0])
        self.assertEqual(ctx["outboundAttachmentDraft"]["items"][0]["id"], "9001")

        # Case 3: Existing SENT draft -> reopen=True
        sent_detail = {"id": 15, "version": 4, "state": "SENT", "content": None}
        payload_sent = self.bot.build_save_payload(
            current_detail=sent_detail,
            subject="Re: Follow up",
            plain_text="Dear Dr Aghsami,\n\nFollowing up.",
        )
        self.assertEqual(payload_sent["expectedVersion"], 4)
        self.assertTrue(payload_sent["reopen"])

    def test_select_best_conversation_prefers_exact_email_match(self):
        items = [
            {"contactId": 1, "name": "Amir Other", "email": "other@ut.ac.ir", "emails": ["other@ut.ac.ir"]},
            {
                "contactId": 2,
                "name": "Amir Aghsami",
                "email": "a.aghsami@ut.ac.ir",
                "emails": ["a.aghsami@ut.ac.ir", "amir@gmail.com"],
            },
        ]
        chosen = self.bot.select_best_conversation(items, "a.aghsami@ut.ac.ir")
        self.assertEqual(chosen["contactId"], 2)


if __name__ == "__main__":
    unittest.main()
