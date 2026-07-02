-- Update ai_training_qa for material tiering on existing seeded databases (I-4).
-- Content mirrors ai-training/qa-seed.json edits; ASCII-only literals.

UPDATE ai_training_qa
   SET answer = 'Two tracks: Innovation Talent (PhD + 3+ years at a well-known institution, serve China 2-3 years, no relocation required, multiple visits per year, costs covered) and Entrepreneur Talent (business plan to start in China, remote control allowed, 1-2 visits per year). Selected candidates may receive government research funding in the range of 3-12 million RMB (RMB 3,000,000-12,000,000), with enterprises providing personal salary support separately; full-time roles may also include additional housing allowance.',
       updated_at = CURRENT_TIMESTAMP
 WHERE source = 'MANUAL_IMPORT' AND source_ref = 'PROJECT_CONTENT';

UPDATE ai_training_qa
   SET answer = 'Applicants should hold the title of associate professor or above, have outstanding research achievements in their field, and be able to contribute to industrial services and scientific and technological innovation. We can discuss fit first -- no documents needed at this stage.',
       updated_at = CURRENT_TIMESTAMP
 WHERE source = 'MANUAL_IMPORT' AND source_ref = 'APPLYING_CRITERIA';

UPDATE ai_training_qa
   SET answer = 'Selected candidates receive government project funding (RMB 3-12 million, RMB 3,000,000-12,000,000) for research plus salary from the enterprise separately; full-time roles may include additional housing allowance.',
       updated_at = CURRENT_TIMESTAMP
 WHERE source = 'MANUAL_IMPORT' AND source_ref = 'SALARY';

UPDATE ai_training_qa
   SET question = 'Why do you need a confirmation video?',
       answer = 'Identity-verification details are handled by an operator after clear interest and a short call. We can walk you through the safeguards and next steps at your pace; this is not an early auto-reply topic.',
       keywords = 'video,vcr,record,statement,promise,confirmation video',
       updated_at = CURRENT_TIMESTAMP
 WHERE source = 'MANUAL_IMPORT' AND source_ref = 'VCR_VIDEO';

UPDATE ai_training_qa
   SET answer = 'Follow-up verification (education, awards/patent lists, invitation letters if needed, recent achievements) is handled by an operator after clear interest. This is an operator follow-up template, not an auto-reply.',
       keywords = 'invitation letter,recent achievements,operator follow-up,additional information',
       updated_at = CURRENT_TIMESTAMP
 WHERE source = 'MANUAL_IMPORT' AND source_ref = 'REVERSE_INFO_CHECKLIST';

INSERT INTO ai_training_qa (topic, question, answer, keywords, source, source_ref, enabled)
SELECT
    CONVERT(UNHEX('E8BDBBE997AEE69D90E69699') USING utf8mb4),
    'What documents should I send at this stage?',
    'Thank you for your interest in getting started. At this early stage, we ask for only three items:

Your CV (resume): this helps us match you with the right enterprise and research direction.

Patent certificates: these highlight your innovation track record and can strengthen your application.

A publication list: this substantiates your research level, which is a key review criterion.

All materials are kept strictly confidential, we never charge any fees, and you may redact sensitive technical details if you prefer.',
    'what documents,materials needed,cv,what to send,provide,what do you need,send my documents,what should i send',
    'MANUAL_IMPORT',
    'MATERIALS_LIGHT',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM ai_training_qa WHERE source = 'MANUAL_IMPORT' AND source_ref = 'MATERIALS_LIGHT'
);
