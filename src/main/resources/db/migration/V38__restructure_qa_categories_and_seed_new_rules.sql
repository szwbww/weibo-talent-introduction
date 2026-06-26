-- Phase 1: Restructure QA categories (12 → 6 themes) and seed new FAQ rules.
-- Existing 12 rules: only category_id changes (I-1). Matching fields unchanged.

-- 1. Insert 6 new top-level categories (I-4)
INSERT INTO qa_category (category_code, category_name, description, enabled) VALUES
('PROGRAM_AND_ELIGIBILITY', 'Program and eligibility', 'Project overview, application format, and eligibility', 1),
('ROLE_AND_WORKSTYLE', 'Role and work style', 'Role, duties, full/part-time, and workplace', 1),
('FUNDING_AND_TIMELINE', 'Funding and timeline', 'Funding, process, deadlines, and success rate', 1),
('PROCESS_ACTIONS', 'Process actions', 'Video confirmation, single-application commitment, post-selection steps', 1),
('TRUST_AND_COMPLIANCE', 'Trust and compliance', 'Advantages, confidentiality, agency credentials, and rights', 1),
('COMMUNICATION_AND_OTHER', 'Communication and other', 'Meetings, contact preferences, partner companies, retired experts', 1);

-- 2. Reassign existing 12 rules to new categories (I-1, I-3) — only category_id
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'PROGRAM_AND_ELIGIBILITY')
    WHERE reply_subject = 'About the talent program';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'PROGRAM_AND_ELIGIBILITY')
    WHERE reply_subject = 'Application format';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'PROGRAM_AND_ELIGIBILITY')
    WHERE reply_subject = 'Application criteria';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'ROLE_AND_WORKSTYLE')
    WHERE reply_subject = 'Possible role';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'ROLE_AND_WORKSTYLE')
    WHERE reply_subject = 'Responsibilities and benefits';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'ROLE_AND_WORKSTYLE')
    WHERE reply_subject = 'Full-time and part-time options';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'ROLE_AND_WORKSTYLE')
    WHERE reply_subject = 'Workplace arrangement';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'FUNDING_AND_TIMELINE')
    WHERE reply_subject = 'Funding support';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'FUNDING_AND_TIMELINE')
    WHERE reply_subject = 'Application process';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'FUNDING_AND_TIMELINE')
    WHERE reply_subject = 'Application deadline';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE')
    WHERE reply_subject = 'Our support';
UPDATE qa_rule SET category_id = (SELECT id FROM qa_category WHERE category_code = 'COMMUNICATION_AND_OTHER')
    WHERE reply_subject = 'Thank you for your reply';

-- 3. Delete old 12 categories (I-3) — no rules reference them after step 2
DELETE FROM qa_category WHERE category_code IN (
    'PROJECT_CONTENT', 'ENTRY_FORMAT', 'APPLYING_CRITERIA', 'ROLE', 'DUTY_AND_RIGHT',
    'FULL_TIME_PART_TIME', 'WORKPLACE', 'SALARY', 'PROJECT_STREAM', 'DEADLINE',
    'OUR_ADVANTAGE', 'RETIRED'
);

-- 4. Insert new FAQ rules (I-2, I-5)
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    display_name, auto_reply_enabled, handoff_required, enabled
) VALUES
(
    (SELECT id FROM qa_category WHERE category_code = 'PROCESS_ACTIONS'),
    'record a video,confirmation video,self-statement video,show passport,read the statement',
    'ANY', 120, 'Confirmation video requirement',
    'To prevent AI-forged materials and duplicate applications, we need a short confirmation video (about 3–7 minutes) showing you holding your passport and reading the commitment statement. Please submit it together with your application materials.',
    '承诺视频 VCR',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'PROCESS_ACTIONS'),
    'apply through one company,duplicate application,only apply,single agency,commitment to apply',
    'ANY', 120, 'Single application commitment',
    'For the same project each year, you may apply through only one agency. Duplicate applications will be invalidated. Please confirm your single-application commitment.',
    '单一申报承诺',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'PROCESS_ACTIONS'),
    'after selected,research topic,labor contract,sign contract,after selection',
    'ANY', 120, 'After selection process',
    'After selection, you will work with the enterprise to finalize the research topic and sign a labor contract. You may also enjoy complimentary visits to China and landing support such as mobile phone setup, bank account assistance, and document submission.',
    '入选后流程',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'FUNDING_AND_TIMELINE'),
    'success rate,not selected,chance of success,probability of selection',
    'ANY', 120, 'Success rate and reapplication',
    'This is a national-level project with an approximate success rate of about 10%. Competition is strong. If you are not selected in the first year, you may apply again in a subsequent cycle.',
    '成功率/未入选',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
    'confidential,keep my documents,never charge,any fee,money transfer',
    'ANY', 120, 'Document confidentiality and no fees',
    'Your materials are kept strictly confidential and used only for application purposes. We never charge any fees throughout the entire process. Technical details you prefer not to disclose can be handled with appropriate redaction.',
    '资料保密·绝不收费',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
    'accredited,official agency,prove government,cooperation with government,authorized',
    'ANY', 120, 'Agency credentials and government cooperation',
    'The project is confidential and does not have a public website, but our cooperation with the government is documented through talent office certificates and talent summit participation, which we can share as evidence.',
    '代理资质·政府合作证明',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
    'other agency,switch agency,guarantee selection,subsidy not paid,protect my rights',
    'ANY', 120, 'Multi-agency rights protection',
    'When duplicate applications occur, the authorities require the agency to provide video authorization from the expert to prevent material misuse. We guarantee transparent subsidy disbursement and an open process.',
    '多代理·权益保障',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
    'sensitive project,classified,national project confidential,security concern',
    'ANY', 120, 'Project sensitivity concerns',
    'The project is legitimate and information is kept confidential. We can build trust step by step at your pace.',
    '项目敏感性',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'COMMUNICATION_AND_OTHER'),
    'arrange a meeting,zoom,teams,webex,time zone,available for a call',
    'ANY', 120, 'Meeting arrangement',
    'Zoom, Teams, or Webex are all fine. We typically schedule 15–20 minutes and will arrange a time based on your time zone, sending the meeting link before the call.',
    '会议安排',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'COMMUNICATION_AND_OTHER'),
    'only email,not on linkedin,no social media,contact me by email',
    'ANY', 120, 'Email-only communication preference',
    'Understood. We will contact you via email only going forward.',
    '只邮件·不用LinkedIn',
    1, 0, 1
),
(
    (SELECT id FROM qa_category WHERE category_code = 'COMMUNICATION_AND_OTHER'),
    'which company,partner company,company profile,is it a good match',
    'ANY', 120, 'Partner company information',
    'We will introduce the matched partner company with a profile, website, address, and how their direction aligns with your research expertise.',
    '合作企业信息',
    1, 0, 1
);
