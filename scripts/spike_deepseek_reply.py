#!/usr/bin/env python3
"""Throwaway spike: generate one RAG-grounded expert-email reply with DeepSeek."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-v4-flash"

SAMPLE_INBOUND_EMAIL = """Dear LiLei,

                          Thank you for reaching out and for your interest in my research background.

                          I am open to exploring this collaboration and would be interested in discussing the opportunity further. Before proceeding, I would appreciate receiving more specific details regarding:

                          1. Remote Advisory Work: How is the remote advisory structured in practice (e.g., expected workload, frequency of consultations, reporting format)?

                          2. Financial Terms & Payment: What is the compensation structure for this position, and how are payments remitted?

                          3. China Trips: If travel to China is required, what is the expected duration of each stay?

                          Current Research Focus & Expertise:

                          To help you identify relevant industry matches, here is an overview of my current research areas:

                           Abiotic Stress Tolerance:

                           Heat tolerance in wheat

                           Drought tolerance in rice

                           Salinity tolerance in rice

                           Biotic Stress Resistance:

                           Yellow rust resistance in wheat

                           Bakanae disease resistance in rice

                           Blast disease resistance in rice

                           Fusarium wilt resistance in tomato

                           Cotton leafworm (Spodoptera littoralis) resistance in soybean

                          Current Methodology & Approach:

                          My ongoing studies focus on utilizing plant growth-promoting bacteria and fungi (PGPR/PGPF) to enhance plant resilience against both biotic and abiotic stresses. Additionally, I work on formulating eco-friendly biopesticides derived from plant extracts and bio-wastes that boost plant immunity and growth.

                          My research investigates these responses at both the physiological and molecular levels. Furthermore, I maintain full flexibility to address other environmental and biological stresses affecting crop production based on target company requirements.

                          I look forward to hearing from you with more details.
"""

# SAMPLE_INBOUND_EMAIL = """
# Hi LuKai,
#
# Thank you for your email. I am interested in the offer but I would need
# more details from you, especially around the nature of the offer. I am
# currently doing some computational drug discovery studies on Streptococcus,
# Clostridium difficile, mRNA and HCV.
#
# Thank you as I await your response. Regards.
# """


@dataclass(frozen=True)
class RagFact:
    """One production-shaped, independently retrievable fact chunk."""

    fact_id: str
    title: str
    category: str
    legacy_rule_id: int | None
    question_variants: tuple[str, ...]
    keywords: tuple[str, ...]
    answer: str
    coverage_keys: tuple[str, ...]
    reply_policy: str
    status: str
    risk_level: str
    render_mode: str
    source_refs: tuple[str, ...]
    enabled: bool = True

    @property
    def retrieval_text(self) -> str:
        return " | ".join(
            (
                self.title,
                *self.question_variants,
                *self.keywords,
                *self.coverage_keys,
                self.answer,
            )
        )

    def retrieval_record(self) -> dict[str, Any]:
        return {
            "fact_id": self.fact_id,
            "title": self.title,
            "category": self.category,
            "coverage_keys": list(self.coverage_keys),
            "reply_policy": self.reply_policy,
            "status": self.status,
            "risk_level": self.risk_level,
            "render_mode": self.render_mode,
            "retrieval_text": self.retrieval_text,
        }

    def generation_record(self) -> dict[str, Any]:
        return {
            "fact_id": self.fact_id,
            "title": self.title,
            "answer": self.answer,
            "coverage_keys": list(self.coverage_keys),
            "reply_policy": self.reply_policy,
            "status": self.status,
            "risk_level": self.risk_level,
            "render_mode": self.render_mode,
            "source_refs": list(self.source_refs),
        }


@dataclass(frozen=True)
class ProcessContext:
    """Structured workflow state supplied by the expert profile in production."""

    expert_reply_count: int = 1
    expert_tags: tuple[str, ...] = ()
    cv_status: str = "UNKNOWN"

    def prompt_record(self) -> dict[str, Any]:
        return {
            "expert_reply_count": self.expert_reply_count,
            "expert_tags": list(self.expert_tags),
            "cv_status": self.cv_status,
        }


def _fact(
    number: int,
    area: str,
    title: str,
    category: str,
    variants: str,
    answer: str,
    coverage: str,
    *,
    legacy_rule_id: int | None = None,
    reply_policy: str = "AUTO",
    status: str = "APPROVED",
    risk_level: str = "LOW",
    render_mode: str = "COMPOSE",
    sources: tuple[str, ...] = (),
    proposal_source: bool = True,
    enabled: bool = True,
) -> RagFact:
    phrases = tuple(item.strip() for item in variants.split("|") if item.strip())
    keys = tuple(item.strip() for item in coverage.split(",") if item.strip())
    effective_status = "DISABLED" if not enabled else status
    return RagFact(
        fact_id=f"KB-{area}-{number:03d}",
        title=title,
        category=category,
        legacy_rule_id=legacy_rule_id,
        question_variants=phrases,
        keywords=phrases,
        answer=answer,
        coverage_keys=keys,
        reply_policy=reply_policy,
        status=effective_status,
        risk_level=risk_level,
        render_mode=render_mode,
        source_refs=(
            *((f"QA_FACT_PROPOSAL:fact-{number:02d}",) if proposal_source else ()),
            *sources,
        ),
        enabled=enabled,
    )


# V1 integrated corpus. The proposal supplies the canonical 48-fact skeleton;
# the digest and video index contribute corroboration and conflict provenance.
# Raw source documents are deliberately not sent to the answer model.
RAG_KNOWLEDGE_BASE: tuple[RagFact, ...] = (
    _fact(1, "PROG", "项目内容介绍", "Program and eligibility", "what is this project|what is the program|about the programme|which programme", "This is a government-backed programme connecting experienced international experts with Chinese enterprises for research and technology collaboration. At the introductory stage, no relocation or commitment is required.", "programme.purpose,programme.tracks", legacy_rule_id=1, sources=("QA_DIGEST:overview", "VIDEO_QA_INDEX:SVID_20251113_175509@04:30")),
    _fact(2, "PROG", "项目总览", "Program overview", "learn more|programme structure|programme tracks|typical duration|advisory project", "Two tracks:\n\nInnovative Talent Scheme -- for senior researchers (PhD + notable institutional experience) to serve as a research consultant to a matched Chinese enterprise. You would guide the company's R&D for 2-3 years, without leaving your current position. Most participants work remotely and visit China 1-2 times per year; all travel expenses are covered by us.\n\nEntrepreneurial Talent Scheme -- for experts who wish to commercialize their research by establishing a venture in China. Remote involvement is possible, with 1-2 annual visits.", "programme.structure,programme.tracks,programme.scope", legacy_rule_id=24, render_mode="VERBATIM", sources=("ONLINE_QA:rule-24", "QA_DIGEST:1095-Q1", "VIDEO_QA_INDEX:SVID_20251117_085959@03:06")),
    _fact(3, "PROG", "项目公开状态", "Program overview", "official programme name|programme website|publicly listed|official notice|project name|public website|confidential programme", "The programme is confidential and therefore has no publicly disclosed official name and no public-facing official website.", "programme.official_name,programme.public_visibility", legacy_rule_id=41, risk_level="MEDIUM", render_mode="VERBATIM", sources=("QA_DIGEST:programme-confidentiality", "VIDEO_QA_INDEX:programme-public-status")),
    _fact(4, "GOV", "项目组织层级", "Trust and compliance", "responsible government organization|government body|sponsoring institution|talent office|government agency|ministry of science and technology", "The programme is led at the national level by China's Ministry of Science and Technology and implemented locally by local government talent offices.", "governance.sponsor_level,governance.responsible_organization,governance.national_lead,governance.local_implementation", legacy_rule_id=42, risk_level="MEDIUM", render_mode="VERBATIM", sources=("QA_DIGEST:government-level", "VIDEO_QA_INDEX:local-talent-office-implementation")),
    _fact(5, "COMP", "公司法定身份", "Trust and compliance", "company name|legal name|registered location|registered address", "Our registered company name is Jiangsu Qingfei Talent Technology Co., Ltd. (江苏清飞人才科技有限公司), registered in Nanjing, China.", "company.legal_name,company.registered_location", legacy_rule_id=35, risk_level="MEDIUM"),
    _fact(6, "COMP", "公司官方网站", "Trust and compliance", "company website|your website|official company website|company site", "Our official company website is https://www.qingfeitalent.com.", "company.official_website", risk_level="MEDIUM"),
    _fact(7, "COMP", "清飞与政府人才办合作证明", "Trust and compliance", "verify|proof|registration information|certificate|government cooperation|talent summit|policy documents", "Qingfei cooperates with local government talent offices in multiple regions. We can provide supporting evidence of these working relationships, including company registration information; supporting documents, policy materials and relevant certificates from local talent offices; records of official talent activities; and materials relating to government talent summits.", "company.verification_evidence,company.government_cooperation", legacy_rule_id=18, risk_level="MEDIUM", render_mode="VERBATIM", sources=("QA_DIGEST:2143-Q3", "QA_DIGEST:talent-office-certificates-and-summits", "VIDEO_QA_INDEX:official-talent-activity-records")),
    _fact(8, "PROG", "顾问名单公开政策", "Trust and compliance", "advisor list|adviser list|current advisors|participant list", "For privacy and confidentiality reasons, a current adviser list is not published.", "programme.adviser_list_policy", sources=("QA_DIGEST:privacy-principle",)),
    _fact(9, "OUTR", "联系信息来源", "Communication and other", "how did you find me|where did you get my email|contact source", "Potential candidates are identified through publicly available academic sources such as ORCID, research publications and university researcher profiles.", "outreach.public_source", legacy_rule_id=29),
    _fact(10, "AGCY", "我方服务范围", "Trust and compliance", "your role|mediator|middleman|what do you provide|why work with you", "Our team supports initial review, enterprise matching, application preparation, submission coordination and subsequent administrative assistance. Experts are not charged for these services.", "agency.service_scope", legacy_rule_id=11, sources=("QA_DIGEST:application-process", "VIDEO_QA_INDEX:SVID_20251217_160358@05:30")),
    _fact(11, "ENT", "企业匹配原则", "Communication and other", "matching process|how do you match|matched enterprise|research matching", "Enterprise matching is based on the expert's research background and the specific technical needs of potential Chinese partners; experts are not assigned to generic vacancies.", "enterprise.matching", legacy_rule_id=23, sources=("QA_DIGEST:2061-Q1", "VIDEO_QA_INDEX:SVID_20251105_150344@02:00")),
    _fact(12, "ENT", "合作企业类型", "Communication and other", "types of companies|companies typically work with|enterprise types|industries|partner types", "There is no fixed public list of companies. The relevant company type and industry depend on the expert's research direction and the availability of a genuinely suitable partner.", "enterprise.project_types", sources=("QA_DIGEST:enterprise-matching",)),
    _fact(13, "ENT", "常见研发需求", "Communication and other", "R&D gaps|research gaps|technical needs|common problems|product development", "Potential enterprise needs may involve technical problem-solving, product development, research guidance or technology commercialisation. The specific need cannot be confirmed before an enterprise and project are identified.", "enterprise.rnd_needs", sources=("QA_DIGEST:enterprise-matching",)),
    _fact(14, "ENT", "匹配后的企业披露", "Communication and other", "company profile|company address|matched company details|proposed technical work", "Once a potential match is identified, the company profile, website, address and proposed technical work should be provided for the expert's review.", "enterprise.partner_disclosure", sources=("QA_DIGEST:2109-Q2",)),
    _fact(15, "ENT", "企业匹配期限", "Funding and timeline", "timeline for matching|matching deadline|how long to match|matching time", "There is no fixed enterprise-matching deadline. Timing depends on the expert's research direction and the availability of a genuinely suitable enterprise.", "enterprise.matching_timeline", sources=("QA_DIGEST:2061-Q3", "VIDEO_QA_INDEX:SVID_20251126_210104@05:18")),
    _fact(16, "APP", "个人或团队申报", "Program and eligibility", "apply individually|apply jointly|as a team|with my team|research partner", "Candidates may apply individually or jointly and may participate with relevant research or entrepreneurial partners.", "application.format", legacy_rule_id=2, sources=("QA_DIGEST:1095-track-description", "VIDEO_QA_INDEX:SVID_20251110_160339@09:30")),
    _fact(17, "APP", "申报条件与材料（停用）", "Program and eligibility", "criteria|qualification|eligible|requirements", "Applicants should hold the title of associate professor or above, have outstanding research achievements and be able to contribute to industrial services and scientific and technological innovation.", "researcher.selection", legacy_rule_id=3, enabled=False, risk_level="HIGH", sources=("QA_DIGEST:eligibility-conflict", "VIDEO_QA_INDEX:eligibility-needs-manual-review")),
    _fact(18, "APP", "初审材料", "Program and eligibility", "send CV|initial materials|what should I provide|documents needed|provide my CV", "At the initial stage, a CV is sufficient for eligibility review and enterprise matching. Additional supporting materials may be requested later if the application proceeds.", "application.required_materials", legacy_rule_id=33, sources=("QA_DIGEST:staged-material-rule", "VIDEO_QA_INDEX:SVID_20251215_100710@01:36")),
    _fact(19, "APP", "后续支持材料", "Program and eligibility", "patent certificate|publication list|supporting documents|additional materials", "Patent information, publication lists, education records and other supporting evidence may be requested later according to application requirements. Original identity documents are not required for initial review.", "application.supporting_materials", reply_policy="REVIEW", status="REVIEW", risk_level="HIGH", sources=("QA_DIGEST:1095-Q4", "VIDEO_QA_INDEX:SVID_20251222_150048@08:48")),
    _fact(20, "APP", "申请步骤", "Funding and timeline", "application process|next steps|procedure|what happens next", "After initial materials are received, the team conducts an eligibility review, begins enterprise matching, prepares application documents with the matched enterprise and submits the completed materials for review.", "application.steps", legacy_rule_id=9, sources=("QA_DIGEST:1095-Q6", "VIDEO_QA_INDEX:SVID_20251217_160358@05:30")),
    _fact(21, "APP", "完整申请周期", "Funding and timeline", "full cycle|application timeline|how long does the process take", "The complete matching, application-preparation, submission and review process generally takes approximately six months or longer.", "application.timeline", sources=("QA_DIGEST:2061-Q3",)),
    _fact(22, "APP", "申报及公布窗口", "Funding and timeline", "deadline|when to apply|submission deadline|submission window|when results", "Materials are usually submitted around March-May, with results announced in November-December.\n\nThe exact submission deadline should be confirmed according to the latest project notice before application.", "application.submission_window", legacy_rule_id=10, status="REVIEW", risk_level="HIGH", sources=("ONLINE_QA:rule-10", "QA_DIGEST:2077-Q4", "VIDEO_QA_INDEX:SVID_20251222_083119@07:06")),
    _fact(23, "APP", "成功率及再次申报", "Funding and timeline", "success rate|chance|probability|not selected|apply again", "This is a national-level project with an approximate success rate of about 10%. Competition is strong. If you are not selected in the first year, you may apply again in a subsequent cycle.", "application.success_rate", legacy_rule_id=16, reply_policy="REVIEW", status="REVIEW", risk_level="HIGH", sources=("ONLINE_QA:rule-16", "QA_DIGEST:1095-Q2")),
    _fact(25, "APP", "确认视频", "Process actions", "confirmation video|VCR|passport video|record a video|identity verification", "Any identity-verification requirement, including a confirmation video, is handled by an operator only after clear interest and before formal submission. The exact requirement and safeguards must be explained before materials are requested.", "application.identity_verification", legacy_rule_id=13, reply_policy="REVIEW", status="REVIEW", risk_level="HIGH", sources=("QA_DIGEST:1095-Q11", "VIDEO_QA_INDEX:video-requirement-needs-manual-review")),
    _fact(26, "APP", "入选后流程", "Process actions", "after selection|what happens after selected|onboarding|finalise project scope", "After selection, the researcher and matched enterprise finalise the project scope and written agreement. Visits, onboarding and administrative support are then arranged according to the agreed collaboration.", "application.after_selection", legacy_rule_id=15, sources=("QA_DIGEST:2061-Q4", "VIDEO_QA_INDEX:SVID_20251105_150344@04:42")),
    _fact(27, "ROLE", "合作角色", "Role and work style", "my role|position|consultant|adviser|co-entrepreneur", "Depending on the agreed project, the expert may serve as a research or technical adviser to the matched enterprise, or participate as a co-entrepreneur in a technology venture.", "role.type", legacy_rule_id=4, sources=("QA_DIGEST:2077-Q1",)),
    _fact(28, "ROLE", "主要职责", "Role and work style", "responsibilities|duties|what would I do|my responsibilities|technical advisor", "Responsibilities depend on the project and may include technical guidance, research advice, problem-solving, product-development support or commercialisation guidance.", "role.responsibilities", legacy_rule_id=5, sources=("QA_DIGEST:role-summary", "VIDEO_QA_INDEX:advisory-role-candidates")),
    _fact(29, "ROLE", "交付物", "Role and work style", "deliverables|outputs|milestones|reports|expected work", "There is no universal deliverables list. Expected outputs, milestones, reports and other deliverables are negotiated with the matched enterprise and recorded in the written agreement.", "role.deliverables"),
    _fact(30, "WORK", "全职、兼职及远程", "Role and work style", "full time|part time|remote|form of collaboration|technical consultant", "Full-time, part-time and remote advisory arrangements may be possible. The applicable form depends on the enterprise, project scope and agreed workload.", "work.remote_arrangement", legacy_rule_id=6, sources=("QA_DIGEST:2077-Q1", "VIDEO_QA_INDEX:SVID_20251226_161329@07:00")),
    _fact(31, "WORK", "现有单位、搬迁及赴华安排", "Role and work style", "current affiliation|university affiliation|remain employed|relocate|move to China|visits|travel expenses|work location", "A typical remote advisory arrangement does not require relocation or a change to the expert's current institutional affiliation. Short visits to China may be arranged where the project requires them, with travel support determined by the project arrangement.", "work.affiliation,work.relocation,work.travel_arrangement", legacy_rule_id=7, risk_level="MEDIUM", sources=("QA_DIGEST:2077-Q1", "VIDEO_QA_INDEX:SVID_20251202_145933@03:12")),
    _fact(32, "WORK", "合作期限及投入时间", "Role and work style", "project duration|time commitment|weekly hours|monthly hours|how involved|duration for technical advisers", "A research advisory project commonly runs for approximately two to three years. Exact weekly or monthly workload is flexible and must be agreed with the matched enterprise.", "work.advisory_duration,work.time_commitment", legacy_rule_id=40, reply_policy="REVIEW", status="REVIEW", risk_level="MEDIUM", sources=("QA_DIGEST:1095-Q1", "VIDEO_QA_INDEX:SVID_20251215_160308@07:42")),
    _fact(33, "FUND", "薪资待遇与政府科研经费", "Funding and timeline", "government funding|research funding|3 million|12 million|project funding|salary support|housing allowance", "After a successful application, selected candidates may receive government research funding in the range of 3-12 million RMB, with enterprises providing personal salary support separately; full-time roles may also include additional housing allowance.", "finance.government_funding,finance.enterprise_compensation,finance.additional_support", legacy_rule_id=8, risk_level="MEDIUM", render_mode="VERBATIM", sources=("ONLINE_QA:rule-8", "QA_DIGEST:rule-8-salary", "VIDEO_QA_INDEX:salary-funding-and-housing")),
    _fact(34, "FUND", "企业个人报酬", "Funding and timeline", "salary|compensation|remuneration|advisory compensation|paid role|personal compensation", "Personal compensation is provided separately by the matched enterprise under the agreed collaboration arrangement.", "finance.enterprise_compensation", risk_level="MEDIUM", sources=("QA_DIGEST:1095-Q3", "VIDEO_QA_INDEX:SVID_20251117_085959@10:06")),
    _fact(35, "FUND", "报酬结构", "Funding and timeline", "compensation structure|retainer|hourly|project-based|payment method|payment schedule", "There is no universal compensation model. The exact amount, payment method, deliverables and payment schedule are negotiated and included in the written agreement before any commitment is made.", "finance.compensation_structure", risk_level="MEDIUM", sources=("QA_DIGEST:compensation-separation",)),
    _fact(36, "FUND", "其他可能支持", "Funding and timeline", "housing allowance|startup capital|additional funding|entrepreneurial support", "Full-time arrangements may include housing support. Entrepreneurial projects may be considered for start-up capital or subsequent project funding, subject to the applicable programme and review.", "finance.additional_support", reply_policy="REVIEW", status="REVIEW", risk_level="HIGH", sources=("QA_DIGEST:2077-Q1",)),
    _fact(37, "CONT", "合同及签约主体", "Funding and timeline", "contract terms|contractual relationship|written agreement|formal agreement|who signs|contracting party|partner company", "After selection, the expected arrangement is a written agreement directly between the expert and the matched enterprise, not Qingfei Tech Talent Team. The exact legal relationship, contract type and full terms must be confirmed for the specific project and reviewed before any commitment.", "contract.party,contract.terms", legacy_rule_id=38, reply_policy="REVIEW", status="REVIEW", risk_level="HIGH", sources=("QA_DIGEST:2061-Q4", "VIDEO_QA_INDEX:SVID_20251215_160308@08:18-CONFLICT")),
    _fact(39, "IP", "线上知识产权边界", "Funding and timeline", "intellectual property|IP rights|who owns IP|IP ownership|IP arising|publication rights", "Until a contract is signed, nothing you share with us transfers any rights; any final intellectual-property arrangements will be set out in the future written agreement.", "ip.arrangements", legacy_rule_id=39, risk_level="HIGH", render_mode="VERBATIM", sources=("ONLINE_QA:rule-39",), proposal_source=False),
    _fact(36, "CONF", "线上申请材料保密", "Trust and compliance", "materials confidential|keep my documents|redaction|application privacy|confidentiality", "Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction.", "confidentiality.materials", legacy_rule_id=36, risk_level="MEDIUM", render_mode="VERBATIM", sources=("ONLINE_QA:rule-36",), proposal_source=False),
    _fact(42, "FEE", "专家费用政策", "Trust and compliance", "fee|fees|charge|charges|cost|costs|money transfer", "We never charge experts any fees throughout the process.", "fees.policy", legacy_rule_id=37, sources=("QA_DIGEST:1095-Q5",)),
    _fact(43, "APP", "护照及敏感证件顾虑", "Process actions", "share passport|passport privacy|identity document|sensitive documents", "No passport or original identity document is required for initial review. If identity verification becomes necessary later, an operator must explain the requirement, safeguards and permitted redactions before requesting anything.", "application.sensitive_documents", legacy_rule_id=32, reply_policy="REVIEW", status="REVIEW", risk_level="HIGH", sources=("QA_DIGEST:staged-material-rule", "VIDEO_QA_INDEX:passport-video-needs-manual-review")),
    _fact(44, "COMM", "会议安排", "Communication and other", "meeting|Zoom|Teams|Webex|schedule a call|time zone", "Zoom, Teams or Webex may be used. A typical introductory call lasts approximately 15–20 minutes and is arranged according to the expert's time zone.", "communication.meeting", legacy_rule_id=21, sources=("QA_DIGEST:2094-Q2",)),
    _fact(45, "COMM", "仅邮件联系", "Communication and other", "email only|not on LinkedIn|no social media|contact me by email", "Understood. Communication may continue by email only.", "communication.email_only", legacy_rule_id=22, sources=("QA_DIGEST:1095-Q8",)),
    _fact(46, "AGCY", "多代理及材料保护", "Trust and compliance", "other agency|duplicate agency|protect my rights|material misuse", "Duplicate applications should be avoided. Where authorisation verification is required, the expert should be informed of its exact purpose before providing it. No guarantee of selection or subsidy payment should be made.", "application.multi_agency_protection", legacy_rule_id=19, reply_policy="REVIEW", status="REVIEW", risk_level="HIGH", sources=("QA_DIGEST:2143-Q1-Q2", "VIDEO_QA_INDEX:SVID_20251226_161329@11:36")),
    _fact(47, "COMM", "项目敏感性", "Trust and compliance", "sensitive programme|legitimacy|security concern|trust", "Caution is understandable. Communication may continue by email, and available company-registration and talent-office documentation may be provided for independent verification before the expert proceeds.", "", legacy_rule_id=20, risk_level="MEDIUM", sources=("QA_DIGEST:2285-Q1-Q2", "VIDEO_QA_INDEX:SVID_20251222_083119@02:36")),
    _fact(48, "COMM", "退休专家答复", "Communication and other", "retired|I am retired|no longer working", "Thank you for letting us know. We will not continue discussing participation. A referral may be requested only where appropriate and without pressure.", "communication.retired", legacy_rule_id=12),
)

RETRIEVAL_SYSTEM_PROMPT = """You are the semantic retrieval stage of a RAG system.

Select only fact chunks that directly answer the inbound expert's requests.
Return one valid JSON object:
{
  "fact_ids": ["KB-..."],
  "unresolved_topics": ["short topic"]
}

Rules:
1. Select at most 14 fact IDs and use only IDs present in the candidate list.
2. Prefer atomic facts that answer an explicit request. Do not select promotional
   funding, housing, startup, meeting, or document-request facts unless asked.
3. REVIEW chunks may be selected, but they remain conditional and must not be
   converted into confirmed claims.
4. If the exact name, organization, contract type, amount, or requirement is not
   confirmed, select the relevant REVIEW chunk and mark the topic unresolved.
5. Do not draft the email.
"""


SYSTEM_PROMPT = """You draft careful English replies to overseas academic experts
using retrieved RAG fact chunks.

Return one valid JSON object with exactly these top-level fields:
{
  "draft": "the complete email body",
  "coverage": [
    {
      "topic": "short topic name",
      "status": "ANSWERED or PENDING_CONFIRMATION",
      "evidence": "short description of the approved fact used, or what is missing"
    }
  ],
  "warnings": ["short warning"]
}

Rules:
1. Write one coherent, diplomatic email, not a mechanical list of facts.
2. Acknowledge only the interests or concerns actually expressed in the inbound
   email; do not carry concerns over from another example.
   Do not restate, summarize, paraphrase, or acknowledge the expert's questions,
   research topics, organisms, diseases, technologies, or project names. Do not
   use phrases such as "I understand you would like", "You mentioned", or
   "particularly in relation to your research". After an optional one-sentence
   thank-you, answer directly.
3. Answer every requested topic in the same order as the inbound email.
   Treat PROCESS CONTEXT as authoritative workflow metadata. Do not infer whether
   a CV was received from the email text.
4. Treat RETRIEVED FACT CHUNKS as the only factual authority. Do not use general
   knowledge or facts that are absent from the retrieved chunks.
5. Use a concise, professional, empathetic tone suitable for communication
   with a university professor.
6. Do not introduce funding amounts, housing allowances, startup support,
   document requests, meetings, or other details unless they directly answer
   the expert's question and are necessary for clarity.
   A request for more details, further information, or the nature of the offer
   requires a useful overview that includes personal compensation and available
   government research-funding information, including supported amounts.
7. Distinguish confirmed general arrangements from matters that depend on
   enterprise matching, a future written agreement, or institutional review.
8. A chunk with status REVIEW is not a confirmed claim. Phrase it conditionally,
   state that the exact point requires confirmation, and add a warning.
9. If an exact official name, organization, amount, condition, or requirement
   is not supported by an APPROVED chunk, say it requires confirmation or that
   supporting documentation will be provided. Do not speculate.
10. Do not conflate government R&D funding with personal compensation.
11. Do not claim that the expert must resign, relocate, transfer affiliation,
    assign intellectual property, or disclose confidential research unless an
    approved fact explicitly says so.
12. Use the expert's name or title only when it is unambiguous in the inbound
    email; otherwise use "Dear Professor,". Sign as Wu Wei, Customer Care
    Officer, Qingfei Tech Talent Team, China.
13. Do not use placeholders, internal labels, citations, or Markdown fences,
    except for the supplied render_token values. These internal tokens are
    replaced deterministically by Python before the draft is shown.
14. Coverage evidence must name the supporting fact ID(s). Inside the draft,
    fact IDs may appear only as supplied render_token values.
15. The coverage array must contain only the topics requested in the inbound
    email and answered in the draft, in the same order. Do not force topics from
    a previous email or example.
16. When PROCESS CONTEXT confirms a second-or-later expert reply, CV status is
    MISSING, the expert expresses willingness to continue, and asks about next
    steps or cooperation requirements, use the light-material fact to request
    only the CV. Explain briefly that it supports preliminary eligibility review
    and enterprise matching. Do not request a passport, degree certificate,
    employment certificate, confidential research details, or other supporting
    documents. If CV status is RECEIVED or UNKNOWN, do not request a CV.
17. For every retrieved chunk whose render_mode is VERBATIM, place its supplied
    render_token exactly once as a separate paragraph. Do not write, summarize,
    paraphrase, merge, translate, qualify, or repeat that fact yourself.
18. For a request about programme details, a specific plan, further
    information, or the nature of the offer, include the VERBATIM project-
    overview token and salary-and-government-funding token. Place the project
    overview token before the salary token as separate paragraphs.
19. If the expert asks for either the programme name or responsible government
    organization, also include the VERBATIM Qingfei-government-cooperation
    evidence token. Place it immediately after the name and/or organization
    tokens so that the supporting evidence is easy to verify.
20. MANDATORY FACT IDS are hard requirements. If a mandatory chunk is absent
    from RETRIEVED FACT CHUNKS, do not silently replace it with general wording.
21. For any intellectual-property question, place the online IP-boundary token
    first and the application-material-confidentiality token immediately after
    it. Do not add other IP or confidentiality claims.
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a one-shot grounded reply through DeepSeek."
    )
    parser.add_argument(
        "--input",
        type=Path,
        help="Optional inbound-email text file; otherwise SAMPLE_INBOUND_EMAIL is used.",
    )
    parser.add_argument(
        "--model",
        default=os.getenv("DEEPSEEK_MODEL", DEFAULT_MODEL),
        help=f"DeepSeek model (default: {DEFAULT_MODEL})",
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL),
        help=f"DeepSeek API base URL (default: {DEFAULT_BASE_URL})",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=120.0,
        help="HTTP timeout in seconds (default: 120)",
    )
    parser.add_argument(
        "--dump-prompt",
        action="store_true",
        help="Print the prefilter, retrieval prompt and generation prompt without API calls.",
    )
    parser.add_argument(
        "--dump-kb",
        action="store_true",
        help="Print the integrated production-shaped RAG knowledge base as JSON.",
    )
    parser.add_argument(
        "--expert-reply-count",
        type=int,
        default=1,
        help="Number of inbound replies received from the expert (default: 1).",
    )
    parser.add_argument(
        "--expert-tags",
        default="",
        help="Comma-separated structured expert tags.",
    )
    parser.add_argument(
        "--cv-status",
        choices=("MISSING", "RECEIVED", "UNKNOWN"),
        default="UNKNOWN",
        help="Structured CV material status (default: UNKNOWN).",
    )
    return parser.parse_args()


def read_text(path: Path, label: str) -> str:
    try:
        text = path.expanduser().resolve().read_text(encoding="utf-8").strip()
    except OSError as exc:
        raise SystemExit(f"Unable to read {label} file {path}: {exc}") from exc
    if not text:
        raise SystemExit(f"{label.capitalize()} file is empty: {path}")
    return text


_TOKEN_RE = re.compile(r"[a-z0-9]+")
_DETAIL_INQUIRY_PHRASES = (
    "nature of the offer",
    "details about the offer",
    "more details from you",
    "more details",
    "further details",
    "further information",
    "additional information",
    "interested in the offer",
    "learn more about the offer",
    "learning more about this opportunity",
    "specific programme",
    "specific program",
    "specific plan",
    "programme overview",
    "program overview",
    "tell me more",
    "how does the programme work",
    "how does the program work",
)
_PROGRAMME_NAME_PHRASES = (
    "official name",
    "name of the national",
    "programme name",
    "program name",
    "programme website",
    "program website",
)
_GOVERNMENT_ORGANIZATION_PHRASES = (
    "government organization",
    "government organisation",
    "government body",
    "responsible organization",
    "responsible organisation",
)
_IP_PHRASES = (
    "intellectual property",
    "ip rights",
    "ip ownership",
    "publication rights",
)
_COMPENSATION_STRUCTURE_PHRASES = (
    "compensation structure",
    "payment method",
    "payment schedule",
    "hourly rate",
    "retainer",
    "project based payment",
)
_INTENT_COVERAGE: tuple[tuple[tuple[str, ...], tuple[str, ...]], ...] = (
    (_DETAIL_INQUIRY_PHRASES, ("programme.purpose", "programme.structure", "enterprise.matching", "role.type", "role.responsibilities", "finance.government_funding")),
    (_PROGRAMME_NAME_PHRASES, ("programme.official_name", "programme.public_visibility")),
    (_GOVERNMENT_ORGANIZATION_PHRASES, ("governance.responsible_organization", "governance.sponsor_level")),
    (("proof of qingfei", "government cooperation", "supporting evidence", "independent verification", "talent office certificate", "talent summit"), ("company.verification_evidence", "company.government_cooperation")),
    (("contractual relationship", "contracting party", "contractual party", "contract party", "who signs"), ("contract.party",)),
    (("responsibilities", "responsibility", "duties", "technical advisor"), ("role.responsibilities",)),
    (("duration", "how long", "cooperation period"), ("work.advisory_duration",)),
    (("compensation", "remuneration", "salary", "paid"), ("finance.enterprise_compensation",)),
    (_COMPENSATION_STRUCTURE_PHRASES, ("finance.compensation_structure",)),
    (("affiliation", "currently employed", "current university", "institutional requirements"), ("work.affiliation",)),
    (_IP_PHRASES, ("ip.arrangements", "confidentiality.materials")),
    (("confidentiality", "confidential", "nda"), ("confidentiality.materials",)),
)

_POSITIVE_INTENT_PHRASES = (
    "i am interested",
    "i remain interested",
    "willing to continue",
    "would like to continue",
    "happy to continue",
    "ready to proceed",
    "would like to proceed",
)
_NEXT_STEP_PHRASES = (
    "next step",
    "next steps",
    "what should i do",
    "what do you need from me",
    "what is required from me",
    "how can we proceed",
    "how should we proceed",
    "how would we cooperate",
    "how can we cooperate",
    "cooperation requirements",
)


def _normalized(value: str) -> str:
    return " " + " ".join(_TOKEN_RE.findall(value.lower())) + " "


def _contains_any(inbound_email: str, phrases: tuple[str, ...]) -> bool:
    normalized = _normalized(inbound_email)
    return any(_normalized(phrase) in normalized for phrase in phrases)


def is_detail_inquiry(inbound_email: str) -> bool:
    return _contains_any(inbound_email, _DETAIL_INQUIRY_PHRASES)


def mandatory_fact_ids(inbound_email: str) -> tuple[str, ...]:
    """Return hard-required chunks in their required email order."""
    required: list[str] = []
    asks_name = _contains_any(inbound_email, _PROGRAMME_NAME_PHRASES)
    asks_organization = _contains_any(
        inbound_email,
        _GOVERNMENT_ORGANIZATION_PHRASES,
    )
    if is_detail_inquiry(inbound_email):
        required.extend(("KB-PROG-002", "KB-FUND-033"))
    if asks_name:
        required.append("KB-PROG-003")
    if asks_organization:
        required.append("KB-GOV-004")
    if asks_name or asks_organization:
        required.append("KB-COMP-007")
    if _contains_any(inbound_email, _IP_PHRASES):
        required.extend(("KB-IP-039", "KB-CONF-036"))
    return tuple(dict.fromkeys(required))


def should_request_cv(inbound_email: str, process_context: ProcessContext) -> bool:
    normalized = _normalized(inbound_email)
    tags = {tag.strip().upper() for tag in process_context.expert_tags}
    positive_intent = "WILLING_TO_CONTINUE" in tags or any(
        _normalized(phrase) in normalized for phrase in _POSITIVE_INTENT_PHRASES
    )
    asks_next_step = any(
        _normalized(phrase) in normalized for phrase in _NEXT_STEP_PHRASES
    )
    return (
        process_context.expert_reply_count >= 2
        and process_context.cv_status.upper() == "MISSING"
        and positive_intent
        and asks_next_step
    )


def requested_coverage_keys(
    inbound_email: str,
    process_context: ProcessContext | None = None,
) -> tuple[str, ...]:
    normalized = _normalized(inbound_email)
    requested: list[str] = []
    for phrases, coverage_keys in _INTENT_COVERAGE:
        if any(_normalized(phrase) in normalized for phrase in phrases):
            requested.extend(coverage_keys)
    if should_request_cv(inbound_email, process_context or ProcessContext()):
        requested.append("application.required_materials")
    return tuple(dict.fromkeys(requested))


def _lexical_score(query: str, fact: RagFact, requested: set[str]) -> float:
    query_normalized = _normalized(query)
    query_tokens = set(_TOKEN_RE.findall(query.lower()))
    fact_tokens = set(_TOKEN_RE.findall(fact.retrieval_text.lower()))
    overlap = len(query_tokens & fact_tokens)
    phrase_hits = sum(
        1 for phrase in fact.question_variants if _normalized(phrase) in query_normalized
    )
    coverage_hits = len(requested & set(fact.coverage_keys))
    return coverage_hits * 100.0 + phrase_hits * 12.0 + overlap


def prefilter_facts(
    inbound_email: str,
    *,
    limit: int = 18,
    knowledge_base: tuple[RagFact, ...] = RAG_KNOWLEDGE_BASE,
    process_context: ProcessContext | None = None,
) -> list[RagFact]:
    requested = set(requested_coverage_keys(inbound_email, process_context))
    ranked = sorted(
        (fact for fact in knowledge_base if fact.enabled and fact.status != "DISABLED"),
        key=lambda fact: (-_lexical_score(inbound_email, fact, requested), fact.fact_id),
    )
    if requested:
        selected = [fact for fact in ranked if requested & set(fact.coverage_keys)]
    else:
        selected = [fact for fact in ranked if _lexical_score(inbound_email, fact, requested) >= 2]

    normalized = _normalized(inbound_email)
    if " compensation " in normalized and " government funding " not in normalized:
        selected = [
            fact
            for fact in selected
            if not set(fact.coverage_keys)
            & {"finance.government_funding", "finance.additional_support"}
        ]
    if is_detail_inquiry(inbound_email):
        selected = [
            fact
            for fact in selected
            if fact.fact_id != "KB-FUND-034"
            and not (
                fact.fact_id == "KB-FUND-035"
                and not _contains_any(
                    inbound_email,
                    _COMPENSATION_STRUCTURE_PHRASES,
                )
            )
        ]

    enabled_by_id = {
        fact.fact_id: fact
        for fact in knowledge_base
        if fact.enabled and fact.status != "DISABLED"
    }
    mandatory = [
        enabled_by_id[fact_id]
        for fact_id in mandatory_fact_ids(inbound_email)
        if fact_id in enabled_by_id
    ]
    mandatory_ids = {fact.fact_id for fact in mandatory}
    return (mandatory + [fact for fact in selected if fact.fact_id not in mandatory_ids])[:limit]


def build_retrieval_prompt(
    inbound_email: str,
    candidates: list[RagFact],
    process_context: ProcessContext | None = None,
) -> str:
    records = [fact.retrieval_record() for fact in candidates]
    context = process_context or ProcessContext()
    return f"""PROCESS CONTEXT
<process_context>
{json.dumps(context.prompt_record(), ensure_ascii=False, indent=2)}
</process_context>

INBOUND EMAIL
<inbound_email>
{inbound_email.strip()}
</inbound_email>

CANDIDATE FACT CHUNKS
<candidate_chunks>
{json.dumps(records, ensure_ascii=False, indent=2)}
</candidate_chunks>

Select the minimum sufficient fact IDs now.
"""


def fact_render_token(fact_id: str) -> str:
    return f"{{{{FACT:{fact_id}}}}}"


def render_verbatim_facts(draft: str, retrieved_facts: list[RagFact]) -> str:
    """Resolve VERBATIM facts deterministically, inserting omitted tokens."""
    verbatim_facts = [
        fact for fact in retrieved_facts if fact.render_mode == "VERBATIM"
    ]
    if not verbatim_facts:
        return draft

    rendered = draft.strip()
    tokens = [fact_render_token(fact.fact_id) for fact in verbatim_facts]

    for token in tokens:
        first = rendered.find(token)
        if first >= 0:
            tail_start = first + len(token)
            rendered = rendered[:tail_start] + rendered[tail_start:].replace(token, "")

    for index, token in enumerate(tokens):
        if token in rendered:
            continue
        previous = next(
            (candidate for candidate in reversed(tokens[:index]) if candidate in rendered),
            None,
        )
        if previous is not None:
            insert_at = rendered.find(previous) + len(previous)
            rendered = rendered[:insert_at] + "\n\n" + token + rendered[insert_at:]
            continue
        following = next(
            (candidate for candidate in tokens[index + 1:] if candidate in rendered),
            None,
        )
        if following is not None:
            insert_at = rendered.find(following)
            rendered = rendered[:insert_at] + token + "\n\n" + rendered[insert_at:]
            continue
        first_paragraph_end = rendered.find("\n\n")
        if first_paragraph_end >= 0:
            insert_at = first_paragraph_end + 2
            rendered = rendered[:insert_at] + token + "\n\n" + rendered[insert_at:]
        else:
            rendered = token + "\n\n" + rendered

    for fact, token in zip(verbatim_facts, tokens):
        rendered = rendered.replace(token, fact.answer)
    return rendered.strip()


def build_generation_prompt(
    *,
    retrieved_facts: list[RagFact],
    inbound_email: str,
    process_context: ProcessContext | None = None,
) -> str:
    records = []
    for fact in retrieved_facts:
        record = fact.generation_record()
        if fact.render_mode == "VERBATIM":
            record.pop("answer", None)
            record["render_token"] = fact_render_token(fact.fact_id)
            record["render_instruction"] = (
                "Place render_token exactly once as its own paragraph; "
                "do not restate or paraphrase this fact."
            )
        records.append(record)
    context = process_context or ProcessContext()
    return f"""PROCESS CONTEXT
<process_context>
{json.dumps(context.prompt_record(), ensure_ascii=False, indent=2)}
</process_context>

RETRIEVED FACT CHUNKS
<retrieved_chunks>
{json.dumps(records, ensure_ascii=False, indent=2)}
</retrieved_chunks>

MANDATORY FACT IDS
<mandatory_fact_ids>
{json.dumps(list(mandatory_fact_ids(inbound_email)), ensure_ascii=False, indent=2)}
</mandatory_fact_ids>

INBOUND EMAIL
<inbound_email>
{inbound_email.strip()}
</inbound_email>

Generate the JSON result now. Before finalizing, verify that every requested
topic appears in coverage, coverage evidence names fact IDs, and the draft
contains no unsupported fact or unrelated promotional detail.
"""


def resolve_endpoint(base_url: str) -> str:
    normalized = base_url.rstrip("/")
    if normalized.endswith("/chat/completions"):
        return normalized
    return f"{normalized}/chat/completions"


def call_deepseek_json(
    *,
    api_key: str,
    base_url: str,
    model: str,
    system_prompt: str,
    user_prompt: str,
    timeout: float,
    temperature: float,
    max_tokens: int,
) -> tuple[dict[str, Any], dict[str, Any]]:
    endpoint = resolve_endpoint(base_url)
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "thinking": {"type": "disabled"},
        "temperature": temperature,
        "max_tokens": max_tokens,
        "response_format": {"type": "json_object"},
        "stream": False,
    }
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response_body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"DeepSeek HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise SystemExit(f"DeepSeek request failed: {exc.reason}") from exc
    except TimeoutError as exc:
        raise SystemExit(f"DeepSeek request timed out after {timeout:g}s") from exc

    try:
        api_response = json.loads(response_body)
        content = api_response["choices"][0]["message"]["content"]
        result = json.loads(content)
    except (json.JSONDecodeError, KeyError, IndexError, TypeError) as exc:
        raise SystemExit(
            "DeepSeek returned an unexpected response:\n" + response_body
        ) from exc

    if not isinstance(result, dict):
        raise SystemExit("DeepSeek JSON response is not an object.")
    return result, api_response


def retrieve_with_deepseek(
    *,
    api_key: str,
    base_url: str,
    model: str,
    inbound_email: str,
    timeout: float,
    process_context: ProcessContext | None = None,
    call_json: Any = call_deepseek_json,
) -> tuple[list[RagFact], dict[str, Any], dict[str, Any]]:
    candidates = prefilter_facts(
        inbound_email,
        process_context=process_context,
    )
    result, api_response = call_json(
        api_key=api_key,
        base_url=base_url,
        model=model,
        system_prompt=RETRIEVAL_SYSTEM_PROMPT,
        user_prompt=build_retrieval_prompt(
            inbound_email,
            candidates,
            process_context,
        ),
        timeout=timeout,
        temperature=0.0,
        max_tokens=900,
    )
    candidate_by_id = {fact.fact_id: fact for fact in candidates}
    model_ids = result.get("fact_ids", [])
    selected_ids = [
        value
        for value in model_ids
        if isinstance(value, str) and value in candidate_by_id
    ] if isinstance(model_ids, list) else []

    hard_required = [
        fact_id
        for fact_id in mandatory_fact_ids(inbound_email)
        if fact_id in candidate_by_id
    ]
    selected_ids = hard_required + [
        fact_id for fact_id in selected_ids if fact_id not in hard_required
    ]

    required = set(requested_coverage_keys(inbound_email, process_context))
    for fact in candidates:
        if required & set(fact.coverage_keys) and fact.fact_id not in selected_ids:
            selected_ids.append(fact.fact_id)
    if not selected_ids:
        selected_ids = [fact.fact_id for fact in candidates[:12]]
    selected = [candidate_by_id[fact_id] for fact_id in selected_ids[:14]]
    return selected, result, api_response


def local_warnings(draft: str) -> list[str]:
    checks = {
        "start-up capital": "contains unsolicited startup support",
        "startup capital": "contains unsolicited startup support",
        "[": "contains a possible unresolved placeholder",
        "]": "contains a possible unresolved placeholder",
        "i understand you": "restates or paraphrases the inbound email",
        "you mentioned": "restates or paraphrases the inbound email",
        "particularly in relation to your research": "restates or paraphrases the inbound email",
    }
    lowered = draft.lower()
    return sorted({message for needle, message in checks.items() if needle in lowered})


def verbatim_violations(draft: str, retrieved_facts: list[RagFact]) -> list[str]:
    """Return VERBATIM fact IDs whose approved answer is absent from the draft."""
    return [
        fact.fact_id
        for fact in retrieved_facts
        if fact.render_mode == "VERBATIM" and fact.answer not in draft
    ]


def print_fact_audit(
    *,
    retrieved_facts: list[RagFact],
    retrieval_result: dict[str, Any],
    inbound_email: str,
    process_context: ProcessContext | None = None,
) -> None:
    print("\n=== MATCHED FACTS ===")
    if retrieved_facts:
        for fact in retrieved_facts:
            coverage = ", ".join(fact.coverage_keys) or "none"
            print(
                f"- {fact.fact_id} — {fact.title} — {coverage}"
                f" — {fact.render_mode}"
            )
    else:
        print("- None")

    required = requested_coverage_keys(inbound_email, process_context)
    covered = {
        key
        for fact in retrieved_facts
        for key in fact.coverage_keys
    }
    missing = [key for key in required if key not in covered]
    retrieved_ids = {fact.fact_id for fact in retrieved_facts}
    missing_mandatory = [
        fact_id
        for fact_id in mandatory_fact_ids(inbound_email)
        if fact_id not in retrieved_ids
    ]
    print("\n=== MISSING FACTS ===")
    if not missing and not missing_mandatory:
        print("- None")
        return
    for fact_id in missing_mandatory:
        print(f"- {fact_id} — mandatory fact not retrieved")
    for coverage_key in missing:
        print(f"- {coverage_key} — no retrieved supporting fact")


def print_result(
    result: dict[str, Any],
    api_response: dict[str, Any],
    *,
    retrieved_facts: list[RagFact] | None = None,
    retrieval_result: dict[str, Any] | None = None,
    inbound_email: str = "",
    process_context: ProcessContext | None = None,
) -> None:
    draft_value = result.get("draft")
    if not isinstance(draft_value, str):
        raise SystemExit("DeepSeek generation JSON does not contain a string field named 'draft'.")
    draft = draft_value.strip()
    print("=== GENERATED DRAFT ===")
    print(draft)

    if retrieved_facts is not None:
        print_fact_audit(
            retrieved_facts=retrieved_facts,
            retrieval_result=retrieval_result or {},
            inbound_email=inbound_email,
            process_context=process_context,
        )

    print("\n=== COVERAGE AUDIT ===")
    coverage = result.get("coverage", [])
    if isinstance(coverage, list):
        for item in coverage:
            if not isinstance(item, dict):
                continue
            topic = item.get("topic", "unknown")
            status = item.get("status", "unknown")
            evidence = item.get("evidence", "")
            print(f"- {topic}: {status} — {evidence}")
    else:
        print("- Model did not return a coverage list.")

    warnings = []
    model_warnings = result.get("warnings", [])
    if isinstance(model_warnings, list):
        warnings.extend(str(item) for item in model_warnings if str(item).strip())
    warnings.extend(local_warnings(draft))
    print("\n=== WARNINGS ===")
    if warnings:
        for warning in dict.fromkeys(warnings):
            print(f"- {warning}")
    else:
        print("- None")

    usage = api_response.get("usage", {})
    if isinstance(usage, dict) and usage:
        print("\n=== TOKEN USAGE ===")
        print(
            f"prompt={usage.get('prompt_tokens', '?')} "
            f"completion={usage.get('completion_tokens', '?')} "
            f"total={usage.get('total_tokens', '?')}"
        )


def main() -> None:
    args = parse_args()
    inbound_email = (
        read_text(args.input, "inbound email") if args.input else SAMPLE_INBOUND_EMAIL.strip()
    )
    process_context = ProcessContext(
        expert_reply_count=max(0, args.expert_reply_count),
        expert_tags=tuple(
            tag.strip().upper()
            for tag in args.expert_tags.split(",")
            if tag.strip()
        ),
        cv_status=args.cv_status,
    )

    if args.dump_kb:
        records = []
        for fact in RAG_KNOWLEDGE_BASE:
            record = asdict(fact)
            record["retrieval_text"] = fact.retrieval_text
            record["embedding_text"] = fact.retrieval_text
            records.append(record)
        print(json.dumps(records, ensure_ascii=False, indent=2))
        return

    candidates = prefilter_facts(
        inbound_email,
        process_context=process_context,
    )

    if args.dump_prompt:
        print("=== PREFILTERED FACT IDS ===")
        print("\n".join(fact.fact_id for fact in candidates))
        print("\n=== RETRIEVAL SYSTEM PROMPT ===")
        print(RETRIEVAL_SYSTEM_PROMPT.strip())
        print("\n=== RETRIEVAL USER PROMPT ===")
        print(build_retrieval_prompt(inbound_email, candidates, process_context))
        print("\n=== GENERATION SYSTEM PROMPT ===")
        print(SYSTEM_PROMPT.strip())
        print("\n=== GENERATION USER PROMPT (prefilter fallback) ===")
        print(build_generation_prompt(
            retrieved_facts=candidates[:14],
            inbound_email=inbound_email,
            process_context=process_context,
        ))
        print("\n=== DRAFT STATUS ===")
        print("No draft generated: --dump-prompt is a no-API inspection mode. Run without --dump-prompt to call DeepSeek and generate a new draft.")
        return

    api_key = os.getenv("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        raise SystemExit(
            "DEEPSEEK_API_KEY is not set. Example:\n"
            "  export DEEPSEEK_API_KEY='your-key'"
        )

    retrieved_facts, retrieval_result, retrieval_api_response = retrieve_with_deepseek(
        api_key=api_key,
        base_url=args.base_url,
        model=args.model,
        inbound_email=inbound_email,
        timeout=args.timeout,
        process_context=process_context,
    )
    retrieval_usage = retrieval_api_response.get("usage", {})

    result, api_response = call_deepseek_json(
        api_key=api_key,
        base_url=args.base_url,
        model=args.model,
        system_prompt=SYSTEM_PROMPT,
        user_prompt=build_generation_prompt(
            retrieved_facts=retrieved_facts,
            inbound_email=inbound_email,
            process_context=process_context,
        ),
        timeout=args.timeout,
        temperature=0.2,
        max_tokens=2600,
    )
    draft = result.get("draft")
    if not isinstance(draft, str):
        raise SystemExit(
            "DeepSeek generation JSON does not contain a string field named 'draft'."
        )
    rendered_draft = render_verbatim_facts(draft, retrieved_facts)
    result["draft"] = rendered_draft
    violations = verbatim_violations(rendered_draft, retrieved_facts)
    if violations:
        print_fact_audit(
            retrieved_facts=retrieved_facts,
            retrieval_result=retrieval_result,
            inbound_email=inbound_email,
            process_context=process_context,
        )
        raise SystemExit(
            "DeepSeek omitted mandatory VERBATIM fact(s): " + ", ".join(violations)
        )
    print_result(
        result,
        api_response,
        retrieved_facts=retrieved_facts,
        retrieval_result=retrieval_result,
        inbound_email=inbound_email,
        process_context=process_context,
    )
    if isinstance(retrieval_usage, dict) and retrieval_usage:
        print("\n=== RETRIEVAL TOKEN USAGE ===")
        print(f"total={retrieval_usage.get('total_tokens', '?')}")


if __name__ == "__main__":
    main()
