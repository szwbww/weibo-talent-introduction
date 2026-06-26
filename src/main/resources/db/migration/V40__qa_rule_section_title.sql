-- Phase 2b-1: section titles for multi-rule aggregated replies.

ALTER TABLE qa_rule
    ADD COLUMN section_title VARCHAR(120) NULL COMMENT 'English heading prefix when multiple rules are composed';

UPDATE qa_rule SET section_title = 'Program & eligibility'
    WHERE reply_subject IN ('About the talent program', 'Application format', 'Application criteria');

UPDATE qa_rule SET section_title = 'Role & work style'
    WHERE reply_subject IN (
        'Possible role', 'Responsibilities and benefits',
        'Full-time and part-time options', 'Workplace arrangement'
    );

UPDATE qa_rule SET section_title = 'Funding & timeline'
    WHERE reply_subject IN (
        'Funding support', 'Application process', 'Application deadline',
        'Success rate and reapplication'
    );

UPDATE qa_rule SET section_title = 'Process actions'
    WHERE reply_subject IN (
        'Confirmation video requirement', 'Single application commitment', 'After selection process'
    );

UPDATE qa_rule SET section_title = 'Trust & compliance'
    WHERE reply_subject IN (
        'Our support', 'Document confidentiality and no fees',
        'Agency credentials and government cooperation', 'Multi-agency rights protection',
        'Project sensitivity concerns'
    );

UPDATE qa_rule SET section_title = 'Communication & other'
    WHERE reply_subject IN (
        'Meeting arrangement', 'Email-only communication preference',
        'Partner company information', 'Thank you for your reply'
    );
