-- ============================================================================
-- V112 rag knowledge base (plan 01: 01-rag-knowledge-base-schema.md)
--
-- 五张 rag_* 表 + 45 条语料种子 + G-2 指纹。本迁移只建新表，不触碰 qa_rule /
-- qa_category 与既有迁移链 V1..V111（What must NOT change 1-3、G-4）。
-- 部署顺序固定为 V112（G-9，本轮四份迁移之首）。
--
-- 关键不变量：
--   I-1  rag_fact.fact_code UNIQUE，格式恒为 KB-<AREA>-<NNN> 且与 area/seq 列自洽。
--   I-2  enabled 与 status 两列都存；任何读取路径先按 enabled=false -> DISABLED 归一。
--   I-4  列表字段分隔符是数据契约：question_variants/keywords 用 '|'，
--        coverage_keys/source_refs 用 ','（与 spike 脚本 split 完全同构）。
--   I-5  retrieval_text 的拼接顺序固定（title | variants | keywords | keys | answer）。
--   G-1  fact_code 是唯一业务标识；自增 id 绝不进入提示词/响应/审计。
--   G-2  语料指纹是启动门禁；常量 e62421a42c432cf3（A1 修订，双实现等价）。
--   G-3  answer 是对外正文唯一来源；title 只到检索为止。
--   G-4  legacy_rule_id 只读对账，运行时禁读。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- rag_fact：45 条事实（种子段由 scripts/export_rag_kb_sql.py 机器生成，禁止手抄）。
-- D-1：reply_policy 与 status=REVIEW 在无自动发送的前提下退化为纯展示标签，
--      不参与任何分支判断。
-- ----------------------------------------------------------------------------
CREATE TABLE rag_fact (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    fact_code         VARCHAR(32)  NOT NULL COMMENT 'KB-<AREA>-<NNN>，全链路唯一业务键（G-1）',
    area              VARCHAR(8)   NOT NULL COMMENT '如 FUND',
    seq               INT          NOT NULL COMMENT '如 33',
    title             VARCHAR(128) NOT NULL COMMENT '中文内部名，绝不进对外正文（G-3）',
    category          VARCHAR(64)  NOT NULL,
    question_variants TEXT         NOT NULL COMMENT "'|' 分隔（I-4）",
    keywords          TEXT         NOT NULL COMMENT "'|' 分隔，与 question_variants 同源（I-4）",
    answer            MEDIUMTEXT   NOT NULL COMMENT '对外正文唯一来源（G-3）',
    coverage_keys     VARCHAR(512) NOT NULL DEFAULT '' COMMENT "',' 分隔（I-4）",
    reply_policy      VARCHAR(16)  NOT NULL COMMENT 'AUTO/REVIEW/NEVER，仅展示标签（D-1）',
    status            VARCHAR(16)  NOT NULL COMMENT 'APPROVED/REVIEW/DISABLED',
    risk_level        VARCHAR(8)   NOT NULL COMMENT 'LOW/MEDIUM/HIGH',
    render_mode       VARCHAR(16)  NOT NULL COMMENT 'COMPOSE/VERBATIM',
    source_refs       TEXT         NOT NULL COMMENT "',' 分隔（MySQL 8 不允许 TEXT 字面量 DEFAULT，T2 的 DEFAULT '' 省略）",
    legacy_rule_id    BIGINT       NULL COMMENT '只读对账，运行时禁读（G-4）',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1,
    sort_order        INT          NOT NULL COMMENT 'fact_code 升序序号 1..45（G-2 规范化输入）',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rag_fact_code (fact_code),
    KEY idx_rag_fact_enabled (enabled, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 语料事实；reply_policy 与 status=REVIEW 仅为展示标签，不参与任何分支判断（D-1）';

-- ----------------------------------------------------------------------------
-- rag_phrase_group：短语组（DETAIL_INQUIRY / PROGRAMME_NAME / ... / COMPENSATION /
-- POSITIVE_INTENT / NEXT_STEP / COMPENSATION_MENTION / GOVERNMENT_FUNDING_MENTION）。
-- ----------------------------------------------------------------------------
CREATE TABLE rag_phrase_group (
    group_code VARCHAR(48) NOT NULL,
    phrase     VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (group_code, phrase)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 短语组；02 用归一化子串命中（_contains_any 同构）';

-- ----------------------------------------------------------------------------
-- rag_intent_coverage：命中短语组 -> 追加的 coverage_key（21 行）。
-- ----------------------------------------------------------------------------
CREATE TABLE rag_intent_coverage (
    group_code   VARCHAR(48) NOT NULL,
    coverage_key VARCHAR(64) NOT NULL,
    sort_order   INT NOT NULL,
    PRIMARY KEY (group_code, coverage_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 覆盖键；命中组时按组内 sort_order 追加';

-- ----------------------------------------------------------------------------
-- rag_mandatory_rule：硬性事实规则（6 行；sort_order 15 = D-3 COMPENSATION）。
-- match_groups 为 any-of（',' 分隔，任一命中即生效）；fact_codes 有序追加（',' 分隔）。
-- ----------------------------------------------------------------------------
CREATE TABLE rag_mandatory_rule (
    rule_code    VARCHAR(48) NOT NULL,
    match_groups VARCHAR(128) NOT NULL COMMENT 'any-of，"," 分隔',
    fact_codes   VARCHAR(128) NOT NULL COMMENT '有序，"," 分隔',
    sort_order   INT NOT NULL,
    PRIMARY KEY (rule_code),
    KEY idx_rag_mandatory_rule_sort (sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 强制事实规则；sort_order 15 = COMPENSATION -> KB-FUND-033（D-3）';

-- ----------------------------------------------------------------------------
-- rag_prefilter_exclusion：预筛剔除规则（4 行；T2 小节标题「3 行」为笔误）。
-- when_groups 全部命中且 unless_groups 均未命中时，按 target_type 剔除目标。
-- ----------------------------------------------------------------------------
CREATE TABLE rag_prefilter_exclusion (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    rule_code     VARCHAR(48) NOT NULL,
    when_groups   VARCHAR(128) NOT NULL DEFAULT '' COMMENT '全部命中才生效',
    unless_groups VARCHAR(128) NOT NULL DEFAULT '' COMMENT '任一命中即取消',
    target_type   VARCHAR(16) NOT NULL COMMENT 'FACT_CODE | COVERAGE_KEY',
    target_value  VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 预筛剔除规则；02 I-8 第 3 步数据驱动';

-- ----------------------------------------------------------------------------
-- rag_kb_meta：单行语料元信息（G-2 指纹门禁）。id=1 由 CHECK 强制单行；
-- fingerprint 常量由 export 脚本校验后写入，应用启动时重算比对（I-3）。
-- ----------------------------------------------------------------------------
CREATE TABLE rag_kb_meta (
    id          TINYINT     NOT NULL DEFAULT 1,
    fingerprint VARCHAR(64) NOT NULL,
    fact_count  INT         NOT NULL,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_rag_kb_meta_singleton CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 语料指纹与事实数（G-2 启动门禁）';

-- =====================================================================
-- Machine-generated corpus seed -- do not hand-edit.
-- Regenerate with:  python3 scripts/export_rag_kb_sql.py
-- Source of truth:   scripts/spike_deepseek_reply.py RAG_KNOWLEDGE_BASE
-- =====================================================================
-- rag_fact: 45 rows (fact_code ascending), fact_code UNIQUE,
-- '|'/',' separators per I-4, sort_order = code-order ordinal 1..45,
-- legacy_rule_id read-only (G-4).
INSERT INTO rag_fact
    (fact_code, area, seq, title, category, question_variants, keywords, answer, coverage_keys, reply_policy, status, risk_level, render_mode, source_refs, legacy_rule_id, enabled, sort_order)
VALUES
    ('KB-AGCY-010', 'AGCY', '10', '我方服务范围', 'Trust and compliance', 'your role|mediator|middleman|what do you provide|why work with you', 'your role|mediator|middleman|what do you provide|why work with you', 'Our team supports initial review, enterprise matching, application preparation, submission coordination and subsequent administrative assistance. Experts are not charged for these services.', 'agency.service_scope', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-10,QA_DIGEST:application-process,VIDEO_QA_INDEX:SVID_20251217_160358@05:30', '11', '1', '1'),
    ('KB-AGCY-046', 'AGCY', '46', '多代理及材料保护', 'Trust and compliance', 'other agency|duplicate agency|protect my rights|material misuse', 'other agency|duplicate agency|protect my rights|material misuse', 'Duplicate applications should be avoided. Where authorisation verification is required, the expert should be informed of its exact purpose before providing it. No guarantee of selection or subsidy payment should be made.', 'application.multi_agency_protection', 'REVIEW', 'REVIEW', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-46,QA_DIGEST:2143-Q1-Q2,VIDEO_QA_INDEX:SVID_20251226_161329@11:36', '19', '1', '2'),
    ('KB-APP-016', 'APP', '16', '个人或团队申报', 'Program and eligibility', 'apply individually|apply jointly|as a team|with my team|research partner', 'apply individually|apply jointly|as a team|with my team|research partner', 'Candidates may apply individually or jointly and may participate with relevant research or entrepreneurial partners.', 'application.format', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-16,QA_DIGEST:1095-track-description,VIDEO_QA_INDEX:SVID_20251110_160339@09:30', '2', '1', '3'),
    ('KB-APP-017', 'APP', '17', '申报条件与材料（停用）', 'Program and eligibility', 'criteria|qualification|eligible|requirements', 'criteria|qualification|eligible|requirements', 'Applicants should hold the title of associate professor or above, have outstanding research achievements and be able to contribute to industrial services and scientific and technological innovation.', 'researcher.selection', 'AUTO', 'DISABLED', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-17,QA_DIGEST:eligibility-conflict,VIDEO_QA_INDEX:eligibility-needs-manual-review', '3', '0', '4'),
    ('KB-APP-018', 'APP', '18', '初审材料', 'Program and eligibility', 'send CV|initial materials|what should I provide|documents needed|provide my CV', 'send CV|initial materials|what should I provide|documents needed|provide my CV', 'At the initial stage, a CV is sufficient for eligibility review and enterprise matching. Additional supporting materials may be requested later if the application proceeds.', 'application.required_materials', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-18,QA_DIGEST:staged-material-rule,VIDEO_QA_INDEX:SVID_20251215_100710@01:36', '33', '1', '5'),
    ('KB-APP-019', 'APP', '19', '后续支持材料', 'Program and eligibility', 'patent certificate|publication list|supporting documents|additional materials', 'patent certificate|publication list|supporting documents|additional materials', 'Patent information, publication lists, education records and other supporting evidence may be requested later according to application requirements. Original identity documents are not required for initial review.', 'application.supporting_materials', 'REVIEW', 'REVIEW', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-19,QA_DIGEST:1095-Q4,VIDEO_QA_INDEX:SVID_20251222_150048@08:48', NULL, '1', '6'),
    ('KB-APP-020', 'APP', '20', '申请步骤', 'Funding and timeline', 'application process|next steps|procedure|what happens next', 'application process|next steps|procedure|what happens next', 'After initial materials are received, the team conducts an eligibility review, begins enterprise matching, prepares application documents with the matched enterprise and submits the completed materials for review.', 'application.steps', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-20,QA_DIGEST:1095-Q6,VIDEO_QA_INDEX:SVID_20251217_160358@05:30', '9', '1', '7'),
    ('KB-APP-021', 'APP', '21', '完整申请周期', 'Funding and timeline', 'full cycle|application timeline|how long does the process take', 'full cycle|application timeline|how long does the process take', 'The complete matching, application-preparation, submission and review process generally takes approximately six months or longer.', 'application.timeline', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-21,QA_DIGEST:2061-Q3', NULL, '1', '8'),
    ('KB-APP-022', 'APP', '22', '申报及公布窗口', 'Funding and timeline', 'deadline|when to apply|submission deadline|submission window|when results', 'deadline|when to apply|submission deadline|submission window|when results', 'Materials are usually submitted around March-May, with results announced in November-December.

The exact submission deadline should be confirmed according to the latest project notice before application.', 'application.submission_window', 'AUTO', 'REVIEW', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-22,ONLINE_QA:rule-10,QA_DIGEST:2077-Q4,VIDEO_QA_INDEX:SVID_20251222_083119@07:06', '10', '1', '9'),
    ('KB-APP-023', 'APP', '23', '成功率及再次申报', 'Funding and timeline', 'success rate|chance|probability|not selected|apply again', 'success rate|chance|probability|not selected|apply again', 'This is a national-level project with an approximate success rate of about 10%. Competition is strong. If you are not selected in the first year, you may apply again in a subsequent cycle.', 'application.success_rate', 'REVIEW', 'REVIEW', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-23,ONLINE_QA:rule-16,QA_DIGEST:1095-Q2', '16', '1', '10'),
    ('KB-APP-025', 'APP', '25', '确认视频', 'Process actions', 'confirmation video|VCR|passport video|record a video|identity verification', 'confirmation video|VCR|passport video|record a video|identity verification', 'Any identity-verification requirement, including a confirmation video, is handled by an operator only after clear interest and before formal submission. The exact requirement and safeguards must be explained before materials are requested.', 'application.identity_verification', 'REVIEW', 'REVIEW', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-25,QA_DIGEST:1095-Q11,VIDEO_QA_INDEX:video-requirement-needs-manual-review', '13', '1', '11'),
    ('KB-APP-026', 'APP', '26', '入选后流程', 'Process actions', 'after selection|what happens after selected|onboarding|finalise project scope', 'after selection|what happens after selected|onboarding|finalise project scope', 'After selection, the researcher and matched enterprise finalise the project scope and written agreement. Visits, onboarding and administrative support are then arranged according to the agreed collaboration.', 'application.after_selection', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-26,QA_DIGEST:2061-Q4,VIDEO_QA_INDEX:SVID_20251105_150344@04:42', '15', '1', '12'),
    ('KB-APP-043', 'APP', '43', '护照及敏感证件顾虑', 'Process actions', 'share passport|passport privacy|identity document|sensitive documents', 'share passport|passport privacy|identity document|sensitive documents', 'No passport or original identity document is required for initial review. If identity verification becomes necessary later, an operator must explain the requirement, safeguards and permitted redactions before requesting anything.', 'application.sensitive_documents', 'REVIEW', 'REVIEW', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-43,QA_DIGEST:staged-material-rule,VIDEO_QA_INDEX:passport-video-needs-manual-review', '32', '1', '13'),
    ('KB-COMM-044', 'COMM', '44', '会议安排', 'Communication and other', 'meeting|Zoom|Teams|Webex|schedule a call|time zone', 'meeting|Zoom|Teams|Webex|schedule a call|time zone', 'Zoom, Teams or Webex may be used. A typical introductory call lasts approximately 15–20 minutes and is arranged according to the expert''s time zone.', 'communication.meeting', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-44,QA_DIGEST:2094-Q2', '21', '1', '14'),
    ('KB-COMM-045', 'COMM', '45', '仅邮件联系', 'Communication and other', 'email only|not on LinkedIn|no social media|contact me by email', 'email only|not on LinkedIn|no social media|contact me by email', 'Understood. Communication may continue by email only.', 'communication.email_only', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-45,QA_DIGEST:1095-Q8', '22', '1', '15'),
    ('KB-COMM-047', 'COMM', '47', '项目敏感性', 'Trust and compliance', 'sensitive programme|legitimacy|security concern|trust', 'sensitive programme|legitimacy|security concern|trust', 'Caution is understandable. Communication may continue by email, and available company-registration and talent-office documentation may be provided for independent verification before the expert proceeds.', '', 'AUTO', 'APPROVED', 'MEDIUM', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-47,QA_DIGEST:2285-Q1-Q2,VIDEO_QA_INDEX:SVID_20251222_083119@02:36', '20', '1', '16'),
    ('KB-COMM-048', 'COMM', '48', '退休专家答复', 'Communication and other', 'retired|I am retired|no longer working', 'retired|I am retired|no longer working', 'Thank you for letting us know. We will not continue discussing participation. A referral may be requested only where appropriate and without pressure.', 'communication.retired', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-48', '12', '1', '17'),
    ('KB-COMP-005', 'COMP', '5', '公司法定身份', 'Trust and compliance', 'company name|legal name|registered location|registered address', 'company name|legal name|registered location|registered address', 'Our registered company name is Jiangsu Qingfei Talent Technology Co., Ltd. (江苏清飞人才科技有限公司), registered in Nanjing, China.', 'company.legal_name,company.registered_location', 'AUTO', 'APPROVED', 'MEDIUM', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-05', '35', '1', '18'),
    ('KB-COMP-006', 'COMP', '6', '公司官方网站', 'Trust and compliance', 'company website|your website|official company website|company site', 'company website|your website|official company website|company site', 'Our official company website is https://www.qingfeitalent.com.', 'company.official_website', 'AUTO', 'APPROVED', 'MEDIUM', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-06', NULL, '1', '19'),
    ('KB-COMP-007', 'COMP', '7', '清飞与政府人才办合作证明', 'Trust and compliance', 'verify|proof|registration information|certificate|government cooperation|talent summit|policy documents', 'verify|proof|registration information|certificate|government cooperation|talent summit|policy documents', 'Qingfei cooperates with local government talent offices in multiple regions. We can provide supporting evidence of these working relationships, including company registration information; supporting documents, policy materials and relevant certificates from local talent offices; records of official talent activities; and materials relating to government talent summits.', 'company.verification_evidence,company.government_cooperation', 'AUTO', 'APPROVED', 'MEDIUM', 'VERBATIM', 'QA_FACT_PROPOSAL:fact-07,QA_DIGEST:2143-Q3,QA_DIGEST:talent-office-certificates-and-summits,VIDEO_QA_INDEX:official-talent-activity-records', '18', '1', '20'),
    ('KB-CONF-036', 'CONF', '36', '线上申请材料保密', 'Trust and compliance', 'materials confidential|keep my documents|redaction|application privacy|confidentiality', 'materials confidential|keep my documents|redaction|application privacy|confidentiality', 'Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction.', 'confidentiality.materials', 'AUTO', 'APPROVED', 'MEDIUM', 'VERBATIM', 'ONLINE_QA:rule-36', '36', '1', '21'),
    ('KB-CONT-037', 'CONT', '37', '合同及签约主体', 'Funding and timeline', 'contract terms|contractual relationship|written agreement|formal agreement|who signs|contracting party|partner company', 'contract terms|contractual relationship|written agreement|formal agreement|who signs|contracting party|partner company', 'After selection, the expected arrangement is a written agreement directly between the expert and the matched enterprise, not Qingfei Tech Talent Team. The exact legal relationship, contract type and full terms must be confirmed for the specific project and reviewed before any commitment.', 'contract.party,contract.terms', 'REVIEW', 'REVIEW', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-37,QA_DIGEST:2061-Q4,VIDEO_QA_INDEX:SVID_20251215_160308@08:18-CONFLICT', '38', '1', '22'),
    ('KB-ENT-011', 'ENT', '11', '企业匹配原则', 'Communication and other', 'matching process|how do you match|matched enterprise|research matching', 'matching process|how do you match|matched enterprise|research matching', 'Enterprise matching is based on the expert''s research background and the specific technical needs of potential Chinese partners; experts are not assigned to generic vacancies.', 'enterprise.matching', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-11,QA_DIGEST:2061-Q1,VIDEO_QA_INDEX:SVID_20251105_150344@02:00', '23', '1', '23'),
    ('KB-ENT-012', 'ENT', '12', '合作企业类型', 'Communication and other', 'types of companies|companies typically work with|enterprise types|industries|partner types', 'types of companies|companies typically work with|enterprise types|industries|partner types', 'There is no fixed public list of companies. The relevant company type and industry depend on the expert''s research direction and the availability of a genuinely suitable partner.', 'enterprise.project_types', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-12,QA_DIGEST:enterprise-matching', NULL, '1', '24'),
    ('KB-ENT-013', 'ENT', '13', '常见研发需求', 'Communication and other', 'R&D gaps|research gaps|technical needs|common problems|product development', 'R&D gaps|research gaps|technical needs|common problems|product development', 'Potential enterprise needs may involve technical problem-solving, product development, research guidance or technology commercialisation. The specific need cannot be confirmed before an enterprise and project are identified.', 'enterprise.rnd_needs', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-13,QA_DIGEST:enterprise-matching', NULL, '1', '25'),
    ('KB-ENT-014', 'ENT', '14', '匹配后的企业披露', 'Communication and other', 'company profile|company address|matched company details|proposed technical work', 'company profile|company address|matched company details|proposed technical work', 'Once a potential match is identified, the company profile, website, address and proposed technical work should be provided for the expert''s review.', 'enterprise.partner_disclosure', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-14,QA_DIGEST:2109-Q2', NULL, '1', '26'),
    ('KB-ENT-015', 'ENT', '15', '企业匹配期限', 'Funding and timeline', 'timeline for matching|matching deadline|how long to match|matching time', 'timeline for matching|matching deadline|how long to match|matching time', 'There is no fixed enterprise-matching deadline. Timing depends on the expert''s research direction and the availability of a genuinely suitable enterprise.', 'enterprise.matching_timeline', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-15,QA_DIGEST:2061-Q3,VIDEO_QA_INDEX:SVID_20251126_210104@05:18', NULL, '1', '27'),
    ('KB-FEE-042', 'FEE', '42', '专家费用政策', 'Trust and compliance', 'fee|fees|charge|charges|cost|costs|money transfer', 'fee|fees|charge|charges|cost|costs|money transfer', 'We never charge experts any fees throughout the process.', 'fees.policy', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-42,QA_DIGEST:1095-Q5', '37', '1', '28'),
    ('KB-FUND-033', 'FUND', '33', '薪资待遇与政府科研经费', 'Funding and timeline', 'government funding|research funding|3 million|12 million|project funding|salary support|housing allowance', 'government funding|research funding|3 million|12 million|project funding|salary support|housing allowance', 'After a successful application, selected candidates may receive government research funding in the range of 3-12 million RMB, with enterprises providing personal salary support separately; full-time roles may also include additional housing allowance.', 'finance.government_funding,finance.enterprise_compensation,finance.additional_support', 'AUTO', 'APPROVED', 'MEDIUM', 'VERBATIM', 'QA_FACT_PROPOSAL:fact-33,ONLINE_QA:rule-8,QA_DIGEST:rule-8-salary,VIDEO_QA_INDEX:salary-funding-and-housing', '8', '1', '29'),
    ('KB-FUND-034', 'FUND', '34', '企业个人报酬', 'Funding and timeline', 'salary|compensation|remuneration|advisory compensation|paid role|personal compensation', 'salary|compensation|remuneration|advisory compensation|paid role|personal compensation', 'Personal compensation is provided separately by the matched enterprise under the agreed collaboration arrangement.', 'finance.enterprise_compensation', 'AUTO', 'APPROVED', 'MEDIUM', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-34,QA_DIGEST:1095-Q3,VIDEO_QA_INDEX:SVID_20251117_085959@10:06', NULL, '1', '30'),
    ('KB-FUND-035', 'FUND', '35', '报酬结构', 'Funding and timeline', 'compensation structure|retainer|hourly|project-based|payment method|payment schedule', 'compensation structure|retainer|hourly|project-based|payment method|payment schedule', 'There is no universal compensation model. The exact amount, payment method, deliverables and payment schedule are negotiated and included in the written agreement before any commitment is made.', 'finance.compensation_structure', 'AUTO', 'APPROVED', 'MEDIUM', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-35,QA_DIGEST:compensation-separation', NULL, '1', '31'),
    ('KB-FUND-036', 'FUND', '36', '其他可能支持', 'Funding and timeline', 'housing allowance|startup capital|additional funding|entrepreneurial support', 'housing allowance|startup capital|additional funding|entrepreneurial support', 'Full-time arrangements may include housing support. Entrepreneurial projects may be considered for start-up capital or subsequent project funding, subject to the applicable programme and review.', 'finance.additional_support', 'REVIEW', 'REVIEW', 'HIGH', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-36,QA_DIGEST:2077-Q1', NULL, '1', '32'),
    ('KB-GOV-004', 'GOV', '4', '项目组织层级', 'Trust and compliance', 'responsible government organization|government body|sponsoring institution|talent office|government agency|ministry of science and technology', 'responsible government organization|government body|sponsoring institution|talent office|government agency|ministry of science and technology', 'The programme is led at the national level by China''s Ministry of Science and Technology and implemented locally by local government talent offices.', 'governance.sponsor_level,governance.responsible_organization,governance.national_lead,governance.local_implementation', 'AUTO', 'APPROVED', 'MEDIUM', 'VERBATIM', 'QA_FACT_PROPOSAL:fact-04,QA_DIGEST:government-level,VIDEO_QA_INDEX:local-talent-office-implementation', '42', '1', '33'),
    ('KB-IP-039', 'IP', '39', '线上知识产权边界', 'Funding and timeline', 'intellectual property|IP rights|who owns IP|IP ownership|IP arising|publication rights', 'intellectual property|IP rights|who owns IP|IP ownership|IP arising|publication rights', 'Until a contract is signed, nothing you share with us transfers any rights; any final intellectual-property arrangements will be set out in the future written agreement.', 'ip.arrangements', 'AUTO', 'APPROVED', 'HIGH', 'VERBATIM', 'ONLINE_QA:rule-39', '39', '1', '34'),
    ('KB-OUTR-009', 'OUTR', '9', '联系信息来源', 'Communication and other', 'how did you find me|where did you get my email|contact source', 'how did you find me|where did you get my email|contact source', 'Potential candidates are identified through publicly available academic sources such as ORCID, research publications and university researcher profiles.', 'outreach.public_source', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-09', '29', '1', '35'),
    ('KB-PROG-001', 'PROG', '1', '项目内容介绍', 'Program and eligibility', 'what is this project|what is the program|about the programme|which programme', 'what is this project|what is the program|about the programme|which programme', 'This is a government-backed programme connecting experienced international experts with Chinese enterprises for research and technology collaboration. At the introductory stage, no relocation or commitment is required.', 'programme.purpose,programme.tracks', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-01,QA_DIGEST:overview,VIDEO_QA_INDEX:SVID_20251113_175509@04:30', '1', '1', '36'),
    ('KB-PROG-002', 'PROG', '2', '项目总览', 'Program overview', 'learn more|programme structure|programme tracks|typical duration|advisory project', 'learn more|programme structure|programme tracks|typical duration|advisory project', 'Two tracks:

Innovative Talent Scheme -- for senior researchers (PhD + notable institutional experience) to serve as a research consultant to a matched Chinese enterprise. You would guide the company''s R&D for 2-3 years, without leaving your current position. Most participants work remotely and visit China 1-2 times per year; all travel expenses are covered by us.

Entrepreneurial Talent Scheme -- for experts who wish to commercialize their research by establishing a venture in China. Remote involvement is possible, with 1-2 annual visits.', 'programme.structure,programme.tracks,programme.scope', 'AUTO', 'APPROVED', 'LOW', 'VERBATIM', 'QA_FACT_PROPOSAL:fact-02,ONLINE_QA:rule-24,QA_DIGEST:1095-Q1,VIDEO_QA_INDEX:SVID_20251117_085959@03:06', '24', '1', '37'),
    ('KB-PROG-003', 'PROG', '3', '项目公开状态', 'Program overview', 'official programme name|programme website|publicly listed|official notice|project name|public website|confidential programme', 'official programme name|programme website|publicly listed|official notice|project name|public website|confidential programme', 'The programme is confidential and therefore has no publicly disclosed official name and no public-facing official website.', 'programme.official_name,programme.public_visibility', 'AUTO', 'APPROVED', 'MEDIUM', 'VERBATIM', 'QA_FACT_PROPOSAL:fact-03,QA_DIGEST:programme-confidentiality,VIDEO_QA_INDEX:programme-public-status', '41', '1', '38'),
    ('KB-PROG-008', 'PROG', '8', '顾问名单公开政策', 'Trust and compliance', 'advisor list|adviser list|current advisors|participant list', 'advisor list|adviser list|current advisors|participant list', 'For privacy and confidentiality reasons, a current adviser list is not published.', 'programme.adviser_list_policy', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-08,QA_DIGEST:privacy-principle', NULL, '1', '39'),
    ('KB-ROLE-027', 'ROLE', '27', '合作角色', 'Role and work style', 'my role|position|consultant|adviser|co-entrepreneur', 'my role|position|consultant|adviser|co-entrepreneur', 'Depending on the agreed project, the expert may serve as a research or technical adviser to the matched enterprise, or participate as a co-entrepreneur in a technology venture.', 'role.type', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-27,QA_DIGEST:2077-Q1', '4', '1', '40'),
    ('KB-ROLE-028', 'ROLE', '28', '主要职责', 'Role and work style', 'responsibilities|duties|what would I do|my responsibilities|technical advisor', 'responsibilities|duties|what would I do|my responsibilities|technical advisor', 'Responsibilities depend on the project and may include technical guidance, research advice, problem-solving, product-development support or commercialisation guidance.', 'role.responsibilities', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-28,QA_DIGEST:role-summary,VIDEO_QA_INDEX:advisory-role-candidates', '5', '1', '41'),
    ('KB-ROLE-029', 'ROLE', '29', '交付物', 'Role and work style', 'deliverables|outputs|milestones|reports|expected work', 'deliverables|outputs|milestones|reports|expected work', 'There is no universal deliverables list. Expected outputs, milestones, reports and other deliverables are negotiated with the matched enterprise and recorded in the written agreement.', 'role.deliverables', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-29', NULL, '1', '42'),
    ('KB-WORK-030', 'WORK', '30', '全职、兼职及远程', 'Role and work style', 'full time|part time|remote|form of collaboration|technical consultant', 'full time|part time|remote|form of collaboration|technical consultant', 'Full-time, part-time and remote advisory arrangements may be possible. The applicable form depends on the enterprise, project scope and agreed workload.', 'work.remote_arrangement', 'AUTO', 'APPROVED', 'LOW', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-30,QA_DIGEST:2077-Q1,VIDEO_QA_INDEX:SVID_20251226_161329@07:00', '6', '1', '43'),
    ('KB-WORK-031', 'WORK', '31', '现有单位、搬迁及赴华安排', 'Role and work style', 'current affiliation|university affiliation|remain employed|relocate|move to China|visits|travel expenses|work location', 'current affiliation|university affiliation|remain employed|relocate|move to China|visits|travel expenses|work location', 'A typical remote advisory arrangement does not require relocation or a change to the expert''s current institutional affiliation. Short visits to China may be arranged where the project requires them, with travel support determined by the project arrangement.', 'work.affiliation,work.relocation,work.travel_arrangement', 'AUTO', 'APPROVED', 'MEDIUM', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-31,QA_DIGEST:2077-Q1,VIDEO_QA_INDEX:SVID_20251202_145933@03:12', '7', '1', '44'),
    ('KB-WORK-032', 'WORK', '32', '合作期限及投入时间', 'Role and work style', 'project duration|time commitment|weekly hours|monthly hours|how involved|duration for technical advisers', 'project duration|time commitment|weekly hours|monthly hours|how involved|duration for technical advisers', 'A research advisory project commonly runs for approximately two to three years. Exact weekly or monthly workload is flexible and must be agreed with the matched enterprise.', 'work.advisory_duration,work.time_commitment', 'REVIEW', 'REVIEW', 'MEDIUM', 'COMPOSE', 'QA_FACT_PROPOSAL:fact-32,QA_DIGEST:1095-Q1,VIDEO_QA_INDEX:SVID_20251215_160308@07:42', '40', '1', '45');

-- rag_phrase_group: 87 rows, UNIQUE(group_code, phrase).
INSERT INTO rag_phrase_group
    (group_code, phrase, sort_order)
VALUES
    ('AFFILIATION', 'affiliation', '1'),
    ('AFFILIATION', 'currently employed', '2'),
    ('AFFILIATION', 'current university', '3'),
    ('AFFILIATION', 'institutional requirements', '4'),
    ('COMPENSATION', 'compensation', '1'),
    ('COMPENSATION', 'remuneration', '2'),
    ('COMPENSATION', 'salary', '3'),
    ('COMPENSATION', 'paid', '4'),
    ('COMPENSATION_MENTION', 'compensation', '1'),
    ('COMPENSATION_STRUCTURE', 'compensation structure', '1'),
    ('COMPENSATION_STRUCTURE', 'payment method', '2'),
    ('COMPENSATION_STRUCTURE', 'payment schedule', '3'),
    ('COMPENSATION_STRUCTURE', 'hourly rate', '4'),
    ('COMPENSATION_STRUCTURE', 'retainer', '5'),
    ('COMPENSATION_STRUCTURE', 'project based payment', '6'),
    ('CONFIDENTIALITY', 'confidentiality', '1'),
    ('CONFIDENTIALITY', 'confidential', '2'),
    ('CONFIDENTIALITY', 'nda', '3'),
    ('CONTRACT_PARTY', 'contractual relationship', '1'),
    ('CONTRACT_PARTY', 'contracting party', '2'),
    ('CONTRACT_PARTY', 'contractual party', '3'),
    ('CONTRACT_PARTY', 'contract party', '4'),
    ('CONTRACT_PARTY', 'who signs', '5'),
    ('DETAIL_INQUIRY', 'nature of the offer', '1'),
    ('DETAIL_INQUIRY', 'details about the offer', '2'),
    ('DETAIL_INQUIRY', 'more details from you', '3'),
    ('DETAIL_INQUIRY', 'more details', '4'),
    ('DETAIL_INQUIRY', 'further details', '5'),
    ('DETAIL_INQUIRY', 'further information', '6'),
    ('DETAIL_INQUIRY', 'additional information', '7'),
    ('DETAIL_INQUIRY', 'interested in the offer', '8'),
    ('DETAIL_INQUIRY', 'learn more about the offer', '9'),
    ('DETAIL_INQUIRY', 'learning more about this opportunity', '10'),
    ('DETAIL_INQUIRY', 'specific programme', '11'),
    ('DETAIL_INQUIRY', 'specific program', '12'),
    ('DETAIL_INQUIRY', 'specific plan', '13'),
    ('DETAIL_INQUIRY', 'programme overview', '14'),
    ('DETAIL_INQUIRY', 'program overview', '15'),
    ('DETAIL_INQUIRY', 'tell me more', '16'),
    ('DETAIL_INQUIRY', 'how does the programme work', '17'),
    ('DETAIL_INQUIRY', 'how does the program work', '18'),
    ('DURATION', 'duration', '1'),
    ('DURATION', 'how long', '2'),
    ('DURATION', 'cooperation period', '3'),
    ('GOVERNMENT_FUNDING_MENTION', 'government funding', '1'),
    ('GOVERNMENT_ORGANIZATION', 'government organization', '1'),
    ('GOVERNMENT_ORGANIZATION', 'government organisation', '2'),
    ('GOVERNMENT_ORGANIZATION', 'government body', '3'),
    ('GOVERNMENT_ORGANIZATION', 'responsible organization', '4'),
    ('GOVERNMENT_ORGANIZATION', 'responsible organisation', '5'),
    ('IP', 'intellectual property', '1'),
    ('IP', 'ip rights', '2'),
    ('IP', 'ip ownership', '3'),
    ('IP', 'publication rights', '4'),
    ('NEXT_STEP', 'next step', '1'),
    ('NEXT_STEP', 'next steps', '2'),
    ('NEXT_STEP', 'what should i do', '3'),
    ('NEXT_STEP', 'what do you need from me', '4'),
    ('NEXT_STEP', 'what is required from me', '5'),
    ('NEXT_STEP', 'how can we proceed', '6'),
    ('NEXT_STEP', 'how should we proceed', '7'),
    ('NEXT_STEP', 'how would we cooperate', '8'),
    ('NEXT_STEP', 'how can we cooperate', '9'),
    ('NEXT_STEP', 'cooperation requirements', '10'),
    ('POSITIVE_INTENT', 'i am interested', '1'),
    ('POSITIVE_INTENT', 'i remain interested', '2'),
    ('POSITIVE_INTENT', 'willing to continue', '3'),
    ('POSITIVE_INTENT', 'would like to continue', '4'),
    ('POSITIVE_INTENT', 'happy to continue', '5'),
    ('POSITIVE_INTENT', 'ready to proceed', '6'),
    ('POSITIVE_INTENT', 'would like to proceed', '7'),
    ('PROGRAMME_NAME', 'official name', '1'),
    ('PROGRAMME_NAME', 'name of the national', '2'),
    ('PROGRAMME_NAME', 'programme name', '3'),
    ('PROGRAMME_NAME', 'program name', '4'),
    ('PROGRAMME_NAME', 'programme website', '5'),
    ('PROGRAMME_NAME', 'program website', '6'),
    ('RESPONSIBILITY', 'responsibilities', '1'),
    ('RESPONSIBILITY', 'responsibility', '2'),
    ('RESPONSIBILITY', 'duties', '3'),
    ('RESPONSIBILITY', 'technical advisor', '4'),
    ('VERIFICATION', 'proof of qingfei', '1'),
    ('VERIFICATION', 'government cooperation', '2'),
    ('VERIFICATION', 'supporting evidence', '3'),
    ('VERIFICATION', 'independent verification', '4'),
    ('VERIFICATION', 'talent office certificate', '5'),
    ('VERIFICATION', 'talent summit', '6');

-- rag_intent_coverage: 21 rows (group_code, coverage_key).
INSERT INTO rag_intent_coverage
    (group_code, coverage_key, sort_order)
VALUES
    ('AFFILIATION', 'work.affiliation', '1'),
    ('COMPENSATION', 'finance.enterprise_compensation', '1'),
    ('COMPENSATION_STRUCTURE', 'finance.compensation_structure', '1'),
    ('CONFIDENTIALITY', 'confidentiality.materials', '1'),
    ('CONTRACT_PARTY', 'contract.party', '1'),
    ('DETAIL_INQUIRY', 'programme.purpose', '1'),
    ('DETAIL_INQUIRY', 'programme.structure', '2'),
    ('DETAIL_INQUIRY', 'enterprise.matching', '3'),
    ('DETAIL_INQUIRY', 'role.type', '4'),
    ('DETAIL_INQUIRY', 'role.responsibilities', '5'),
    ('DETAIL_INQUIRY', 'finance.government_funding', '6'),
    ('DURATION', 'work.advisory_duration', '1'),
    ('GOVERNMENT_ORGANIZATION', 'governance.responsible_organization', '1'),
    ('GOVERNMENT_ORGANIZATION', 'governance.sponsor_level', '2'),
    ('IP', 'ip.arrangements', '1'),
    ('IP', 'confidentiality.materials', '2'),
    ('PROGRAMME_NAME', 'programme.official_name', '1'),
    ('PROGRAMME_NAME', 'programme.public_visibility', '2'),
    ('RESPONSIBILITY', 'role.responsibilities', '1'),
    ('VERIFICATION', 'company.verification_evidence', '1'),
    ('VERIFICATION', 'company.government_cooperation', '2');

-- rag_mandatory_rule: 6 rows; sort_order 15 = COMPENSATION -> KB-FUND-033 (D-3).
INSERT INTO rag_mandatory_rule
    (rule_code, match_groups, fact_codes, sort_order)
VALUES
    ('DETAIL_INQUIRY', 'DETAIL_INQUIRY', 'KB-PROG-002,KB-FUND-033', '10'),
    ('COMPENSATION', 'COMPENSATION', 'KB-FUND-033', '15'),
    ('PROGRAMME_NAME', 'PROGRAMME_NAME', 'KB-PROG-003', '20'),
    ('GOVERNMENT_ORG', 'GOVERNMENT_ORG', 'KB-GOV-004', '30'),
    ('PROGRAMME_NAME,GOVERNMENT_ORG', 'PROGRAMME_NAME,GOVERNMENT_ORG', 'KB-COMP-007', '40'),
    ('IP', 'IP', 'KB-IP-039,KB-CONF-036', '50');

-- rag_prefilter_exclusion: 4 rows (target_value stays single-valued).
INSERT INTO rag_prefilter_exclusion
    (rule_code, when_groups, unless_groups, target_type, target_value)
VALUES
    ('COMPENSATION_MENTION', 'COMPENSATION_MENTION', 'GOVERNMENT_FUNDING_MENTION', 'COVERAGE_KEY', 'finance.government_funding'),
    ('COMPENSATION_MENTION', 'COMPENSATION_MENTION', 'GOVERNMENT_FUNDING_MENTION', 'COVERAGE_KEY', 'finance.additional_support'),
    ('DETAIL_INQUIRY', 'DETAIL_INQUIRY', '', 'FACT_CODE', 'KB-FUND-034'),
    ('DETAIL_INQUIRY', 'DETAIL_INQUIRY', 'COMPENSATION_STRUCTURE', 'FACT_CODE', 'KB-FUND-035');

-- rag_kb_meta: singleton row (id = 1 enforced by CHECK).
INSERT INTO rag_kb_meta VALUES (1, 'e62421a42c432cf3', 45, NOW());

-- fingerprint e62421a42c432cf3;
