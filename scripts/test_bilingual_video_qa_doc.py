import unittest

from docx import Document

try:
    import build_bilingual_video_qa_doc as builder
    import translate_video_qa_cache as translator
except ModuleNotFoundError:
    builder = None
    translator = None


class BilingualDocumentTests(unittest.TestCase):
    def test_appends_chinese_below_english_without_replacing_source(self):
        self.assertIsNotNone(builder, 'bilingual builder module is missing')
        doc = Document()
        paragraph = doc.add_paragraph('Question: Can I work remotely?')
        builder.append_translation(paragraph, '问题：我可以远程工作吗？')
        self.assertEqual(paragraph.text, 'Question: Can I work remotely?\n问题：我可以远程工作吗？')

    def test_translation_filter_skips_identifiers_but_keeps_sentences(self):
        self.assertIsNotNone(builder, 'bilingual builder module is missing')
        self.assertFalse(builder.should_translate('SVID_20251105_150344_1.mp4'))
        self.assertFalse(builder.should_translate('2025-11-05'))
        self.assertTrue(builder.should_translate('What documents are required?'))

    def test_batching_preserves_text_order_and_limit(self):
        self.assertIsNotNone(translator, 'translation cache module is missing')
        items = ['What documents are required?', 'When is the deadline?', 'Can I work remotely?']
        batches = translator.pack_batches(items, max_chars=70)
        self.assertEqual([item for batch in batches for item in batch], items)
        self.assertTrue(all(len(translator.join_batch(batch)) <= 70 for batch in batches))


if __name__ == '__main__':
    unittest.main()
