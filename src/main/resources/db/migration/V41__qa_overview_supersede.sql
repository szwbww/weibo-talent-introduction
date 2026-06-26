-- Phase 2b-2: OVERVIEW parent rule with supersede coverage.

ALTER TABLE qa_rule
    ADD COLUMN supersedes_children TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'When matched, replace child rule hits with this composite rule only';

INSERT INTO qa_category (category_code, category_name, description, compose_order, enabled) VALUES
('OVERVIEW', 'Program overview', 'Bundled program overview for multi-topic opening inquiries', 0, 1);

INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    display_name, auto_reply_enabled, handoff_required, enabled, supersedes_children
) VALUES (
    (SELECT id FROM qa_category WHERE category_code = 'OVERVIEW'),
    'learn more,more information,name and background,objectives and scope,before sharing,understand the program',
    'ANY', 5, 'Program overview',
    'Thank you for your interest in our talent program. This is a national-level initiative that connects outstanding overseas experts with Chinese enterprises through two main tracks: Innovative Talent Schemes for high-caliber researchers joining enterprises, and Entrepreneurial Talent Schemes for experts who can convert research into products. Selected candidates may receive government research funding in the range of 3–12 million RMB, with enterprises providing personal salary support. Typical application materials include a passport, doctoral degree certificate, CV with publications and achievements, proof of employment, and supporting certificates. After you submit materials, our team matches partner enterprises, prepares application documents, and submits them for review; the overall cycle often spans six months or longer, with results commonly announced in late autumn. We keep all materials strictly confidential, never charge fees, and you may take time to decide after selection.',
    '项目总览',
    1, 0, 1, 1
);
