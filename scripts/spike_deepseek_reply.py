#!/usr/bin/env python3
"""Scenario-routed RAG reply generation with DeepSeek."""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import _spike_deepseek_reply_core as base


SAMPLE_INBOUND_EMAIL = base.SAMPLE_INBOUND_EMAIL


@dataclass(frozen=True)
class ReplyContext:
    expert_reply_count: int = 1
    expert_tags: tuple[str, ...] = ()
    cv_status: str = "UNKNOWN"
    materials_received_this_reply: bool = False
    expert_name: str = ""
    sender_name: str = "LiLei"
    sender_title: str = "Customer Care Officer"
    team_name: str = "Qingfei Tech Talent Team"
    country: str = "China"

    def prompt_record(self) -> dict[str, Any]:
        return asdict(self)


APPROVED_REPLY_FACTS: tuple[base.RagFact, ...] = (
    base._fact(
        49,
        "PAY",
        "采纳回复：个人报酬与付款安排",
        "Funding and timeline",
        "financial terms|compensation structure|payments remitted|currency|payment schedule|remittance method",
        "Personal compensation is provided separately by the matched enterprise. At this preliminary stage, there is no fixed compensation or payment model because the project scope and enterprise have not yet been confirmed.\n\nThe exact amount, currency, payment schedule and remittance method will be negotiated with the enterprise and specified in the written agreement. You will be able to review these terms before deciding whether to proceed.",
        "finance.enterprise_compensation,finance.compensation_structure,finance.payment_method",
        render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:financial-terms",),
        proposal_source=False,
    ),
    base._fact(
        50,
        "TRAVEL",
        "采纳回复：访华频次与时长",
        "Role and work style",
        "china trips|travel to china|duration of each stay|visit duration|one week",
        "Most remote advisers visit China once or twice per year when required by the project. A typical visit lasts approximately one week, although the exact duration is arranged according to the project schedule and mutual availability. Related travel expenses can be covered under the project arrangement.",
        "work.travel_arrangement,work.visit_duration",
        render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:china-visits",),
        proposal_source=False,
    ),
    base._fact(
        51,
        "MATCH",
        "采纳回复：已提供研究信息后的企业匹配",
        "Communication and other",
        "identify relevant industry matches|research information provided|potential match|company profile|proposed technical needs",
        "We will use the research information you provided to identify and match you with suitable Chinese companies. Once a potential match is found, we will share the company’s profile, website, location and proposed technical needs for your review.",
        "enterprise.matching,enterprise.partner_disclosure",
        render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:enterprise-matching",),
        proposal_source=False,
    ),
    base._fact(
        52,
        "WORK",
        "采纳回复：远程顾问合作概述",
        "Role and work style",
        "remote advisory work|part-time advisory|remain in current position|relocation",
        "The cooperation is normally structured as a part-time, remote technical advisory arrangement, commonly lasting two to three years. You may remain in your current position and are not required to relocate.",
        "work.remote_arrangement,work.advisory_duration,work.affiliation,work.relocation",
        render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:remote-advisory-work",),
        proposal_source=False,
    ),
    base._fact(
        53,
        "FUND",
        "采纳回复：政府科研经费",
        "Funding and timeline",
        "government research funding|3-12 million|3–12 million|approved research and development activities",
        "If the application is successful, selected candidates may also receive government research funding of approximately RMB 3–12 million for approved research and development activities. This project funding is separate from personal compensation.",
        "finance.government_funding",
        render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:government-research-funding",),
        proposal_source=False,
    ),
    base._fact(
        54,
        "FEE",
        "采纳回复：清飞不收费",
        "Trust and compliance",
        "service fees|application fees|does qingfei charge|no fees",
        "Qingfei does not charge experts any service or application fees.",
        "fees.policy",
        render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:no-fee-policy",),
        proposal_source=False,
    ),
    base._fact(
        55,
        "WORK",
        "采纳回复：工作量、交付物与职责",
        "Role and work style",
        "expected workload|frequency of consultations|reporting format|working time|technical milestones|deliverables",
        "There is no universal workload or reporting format. The consultation frequency, expected working time, technical milestones, reports and other deliverables are discussed with the matched enterprise and recorded in a written agreement before you make any commitment. Typical responsibilities may include technical guidance, research advice, problem-solving and product-development support.",
        "work.time_commitment,role.responsibilities,role.deliverables",
        render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:remote-advisory-work",),
        proposal_source=False,
    ),
)

APPROVED_FOLLOWUP_FACTS = (
    base._fact(56, "ROLE", "采纳回复：顾问职责", "Role and work style",
        "advisory responsibilities|collaboration scope|advisor duties",
        "Typical responsibilities may include technical guidance, research advice, problem-solving and product-development support. The consultation frequency, expected working time, technical milestones, reports and other deliverables will be discussed with the matched enterprise and recorded in a written agreement before you make any commitment.",
        "role.responsibilities,role.deliverables,work.time_commitment", render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:2026-09-10-advisory-responsibilities",), proposal_source=False),
    base._fact(57, "APP", "采纳回复：资格概述及待确认条件", "Program and eligibility",
        "eligibility requirements|eligibility criteria|who is eligible",
        "The innovative-talent track is intended for senior researchers with a PhD and notable institutional experience. The detailed eligibility requirements for the applicable scheme still need to be confirmed.",
        "application.eligibility_overview", render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:2026-09-10-eligibility",), proposal_source=False),
    base._fact(58, "APP", "采纳回复：申请流程介绍", "Funding and timeline",
        "application process|application procedure|application steps",
        "The application process involves an eligibility review, enterprise matching, preparation of application documents with the matched enterprise, and submission for review.",
        "application.steps", render_mode="VERBATIM",
        sources=("USER_APPROVED_REPLY:2026-09-10-application-process",), proposal_source=False),
    base._fact(59, "PROG", "项目对外公开状态", "Program overview",
        "programme website|program website|public website|publicly listed|public-facing website",
        "The programme does not have a public-facing official website.",
        "programme.public_visibility", render_mode="VERBATIM", risk_level="MEDIUM",
        sources=("USER_CONFIRMED_FACT:programme-public-status",), proposal_source=False),
)

V2_KNOWLEDGE_BASE: tuple[base.RagFact, ...] = (
    *base.RAG_KNOWLEDGE_BASE,
    *APPROVED_REPLY_FACTS,
    *APPROVED_FOLLOWUP_FACTS,
)


_GENERAL_OVERVIEW_PHRASES = (
    "overall nature of the programme",
    "overall nature of the program",
    "nature of the offer",
    "programme overview",
    "program overview",
    "what is this programme",
    "what is this program",
    "how does the programme work",
    "how does the program work",
    "tell me more about the programme",
    "tell me more about the program",
    "know more about this programme",
    "know more about this program",
)
_REMOTE_ARRANGEMENT_PHRASES = (
    "remote advisory",
    "part-time advisory",
    "remain in current position",
    "relocation",
)
_WORKLOAD_DETAIL_PHRASES = (
    "expected workload",
    "frequency of consultations",
    "reporting format",
    "working time",
    "technical milestones",
    "deliverables",
)
_REMOTE_DETAIL_PHRASES = _REMOTE_ARRANGEMENT_PHRASES + _WORKLOAD_DETAIL_PHRASES
_FINANCE_DETAIL_PHRASES = (
    "funding mechanism",
    "remuneration structure",
    "financial terms",
    "compensation structure",
    "payments remitted",
    "payment remitted",
    "payment method",
    "payment schedule",
    "remittance method",
    "currency",
)
_TRAVEL_DETAIL_PHRASES = (
    "china trips",
    "travel to china",
    "duration of each stay",
    "visit duration",
    "each visit",
)
_RESEARCH_FOR_MATCHING_PHRASES = (
    "my current research interests",
    "my research expertise",
    "identify relevant industry matches",
    "identify suitable enterprises",
    "current research focus",
    "overview of my current research areas",
    "research information",
)
_NAME_PHRASES = (
    "official name",
    "programme name",
    "program name",
    "project name",
    "name of the initiative",
)
_PUBLIC_STATUS_PHRASES = (
    "programme website",
    "program website",
    "public website",
    "publicly listed",
    "public-facing website",
)
_ORGANIZATION_PHRASES = (
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
_ROLE_PHRASES = ("advisory responsibilities", "advisor duties", "collaboration scope")
_PROCESS_PHRASES = ("application process", "application procedure", "application steps")
_ELIGIBILITY_PHRASES = ("eligibility requirements", "eligibility criteria", "who is eligible")


def overview_requested(inbound_email: str) -> bool:
    return _has(inbound_email, _GENERAL_OVERVIEW_PHRASES) or (
        _has(inbound_email, ("learn more", "further details", "more information"))
        and _has(inbound_email, ("national talent programme", "national talent program"))
        and not _has(inbound_email, _NAME_PHRASES + _ORGANIZATION_PHRASES)
    )


def _has(inbound_email: str, phrases: tuple[str, ...]) -> bool:
    return base._contains_any(inbound_email, phrases)


def classify_request(inbound_email: str) -> str:
    if overview_requested(inbound_email):
        return "GENERAL_OVERVIEW"
    if any(
        _has(inbound_email, phrases)
        for phrases in (
            _REMOTE_DETAIL_PHRASES,
            _FINANCE_DETAIL_PHRASES,
            _TRAVEL_DETAIL_PHRASES,
        )
    ):
        return "SPECIFIC_QUESTIONS"
    if _has(inbound_email, _GENERAL_OVERVIEW_PHRASES):
        return "GENERAL_OVERVIEW"
    return "TARGETED_QUESTION"


def research_information_provided(inbound_email: str) -> bool:
    return _has(inbound_email, _RESEARCH_FOR_MATCHING_PHRASES)


def apply_fact_compatibility(fact_ids: list[str]) -> list[str]:
    """Remove facts whose approved wording duplicates a selected overview."""
    unique_ids = list(dict.fromkeys(fact_ids))
    if "KB-PROG-002" in unique_ids:
        unique_ids = [
            fact_id
            for fact_id in unique_ids
            if fact_id not in {"KB-PROG-001", "KB-WORK-052"}
        ]
    if "KB-FUND-033" in unique_ids:
        unique_ids = [fact_id for fact_id in unique_ids if fact_id != "KB-FUND-053"]
    if "KB-PROG-003" in unique_ids:
        unique_ids = [fact_id for fact_id in unique_ids if fact_id != "KB-GOV-004"]
    exclusions = {
        "KB-PAY-049": {"KB-FUND-034"},
        "KB-MATCH-051": {"KB-ENT-011", "KB-ENT-014"},
        "KB-WORK-055": {"KB-ROLE-028", "KB-ROLE-029", "KB-WORK-032", "KB-ROLE-056"},
        "KB-ROLE-056": {"KB-ROLE-028", "KB-ROLE-029", "KB-WORK-032"},
        "KB-APP-058": {"KB-APP-020"},
    }
    for selected_id, excluded in exclusions.items():
        if selected_id in unique_ids:
            unique_ids = [fact_id for fact_id in unique_ids if fact_id not in excluded]
    lead_order = (
        ("KB-PROG-003", "KB-COMP-007", "KB-PROG-002")
        if "KB-PROG-003" in unique_ids
        else ("KB-PROG-002",)
    )
    prioritized = [fact_id for fact_id in lead_order if fact_id in unique_ids]
    unique_ids = prioritized + [fact_id for fact_id in unique_ids if fact_id not in prioritized]
    return unique_ids


def mandatory_fact_ids(inbound_email: str) -> tuple[str, ...]:
    required: list[str] = []
    mode = classify_request(inbound_email)
    asks_name = _has(inbound_email, _NAME_PHRASES)
    asks_organization = _has(inbound_email, _ORGANIZATION_PHRASES)
    asks_public_status = _has(inbound_email, _PUBLIC_STATUS_PHRASES)

    if mode == "GENERAL_OVERVIEW":
        required.append("KB-PROG-002")
        if not _has(inbound_email, _FINANCE_DETAIL_PHRASES):
            required.append("KB-FUND-033")
    if asks_name:
        required.append("KB-PROG-003")
    if asks_organization:
        required.append("KB-GOV-004")
    if asks_name or asks_organization:
        required.append("KB-COMP-007")
    if asks_public_status:
        required.append("KB-PROG-059")
    if _has(inbound_email, _REMOTE_ARRANGEMENT_PHRASES):
        required.append("KB-WORK-052")
    if _has(inbound_email, _WORKLOAD_DETAIL_PHRASES):
        required.append("KB-WORK-055")
    if _has(inbound_email, _FINANCE_DETAIL_PHRASES):
        required.extend(("KB-PAY-049", "KB-FUND-053", "KB-FEE-054"))
    if _has(inbound_email, _TRAVEL_DETAIL_PHRASES):
        required.append("KB-TRAVEL-050")
    if research_information_provided(inbound_email):
        required.append("KB-MATCH-051")
    if _has(inbound_email, _IP_PHRASES):
        required.extend(("KB-IP-039", "KB-CONF-036"))
    if _has(inbound_email, _ROLE_PHRASES) and not _has(inbound_email, _WORKLOAD_DETAIL_PHRASES):
        required.append("KB-ROLE-056")
    if _has(inbound_email, _PROCESS_PHRASES):
        required.append("KB-APP-058")
    if _has(inbound_email, _ELIGIBILITY_PHRASES):
        required.append("KB-APP-057")
    return tuple(apply_fact_compatibility(required))


def requested_coverage_keys(
    inbound_email: str,
    process_context: ReplyContext | None = None,
) -> tuple[str, ...]:
    requested: list[str] = []
    mappings = (
        (_ROLE_PHRASES, ("role.responsibilities", "role.deliverables")),
        (_PROCESS_PHRASES, ("application.steps",)),
        (_ELIGIBILITY_PHRASES, ("application.eligibility_overview", "application.eligibility_details")),
        (_NAME_PHRASES, ("programme.official_name",)),
        (_PUBLIC_STATUS_PHRASES, ("programme.public_visibility",)),
        (_ORGANIZATION_PHRASES, ("governance.responsible_organization", "governance.sponsor_level")),
        (("contractual relationship", "contracting party", "contract party", "who signs"), ("contract.party",)),
        (_REMOTE_ARRANGEMENT_PHRASES, ("work.remote_arrangement", "work.advisory_duration", "work.affiliation", "work.relocation")),
        (_WORKLOAD_DETAIL_PHRASES, ("work.time_commitment", "role.responsibilities", "role.deliverables")),
        (_FINANCE_DETAIL_PHRASES, ("finance.enterprise_compensation", "finance.compensation_structure", "finance.payment_method", "finance.government_funding", "fees.policy")),
        (_TRAVEL_DETAIL_PHRASES, ("work.travel_arrangement", "work.visit_duration")),
        (_IP_PHRASES, ("ip.arrangements", "confidentiality.materials")),
        (("confidentiality", "confidential", "nda"), ("confidentiality.materials",)),
        (("affiliation", "currently employed", "current university"), ("work.affiliation",)),
    )
    if classify_request(inbound_email) == "GENERAL_OVERVIEW":
        requested.extend(
            (
                "programme.structure",
                "programme.tracks",
                "programme.scope",
                "finance.government_funding",
            )
        )
    for phrases, coverage_keys in mappings:
        if _has(inbound_email, phrases):
            requested.extend(coverage_keys)
    if research_information_provided(inbound_email):
        requested.extend(("enterprise.matching", "enterprise.partner_disclosure"))
    context = process_context or ReplyContext()
    if base.should_request_cv(inbound_email, context):
        requested.append("application.required_materials")
    return tuple(dict.fromkeys(requested))


def prefilter_facts(
    inbound_email: str,
    *,
    limit: int = 18,
    process_context: ReplyContext | None = None,
) -> list[base.RagFact]:
    requested = set(requested_coverage_keys(inbound_email, process_context))
    ranked = sorted(
        (fact for fact in V2_KNOWLEDGE_BASE if fact.enabled and fact.status != "DISABLED"),
        key=lambda fact: (-base._lexical_score(inbound_email, fact, requested), fact.fact_id),
    )
    selected = (
        [fact for fact in ranked if requested & set(fact.coverage_keys)]
        if requested
        else [fact for fact in ranked if base._lexical_score(inbound_email, fact, requested) >= 2]
    )

    suppressed: set[str] = set()
    context = process_context or ReplyContext()
    if not base.should_request_cv(inbound_email, context):
        suppressed.add("KB-APP-018")
    if context.expert_reply_count <= 1:
        suppressed.update({"KB-COMM-044", "KB-APP-019", "KB-APP-025", "KB-APP-043"})
    if not _has(inbound_email, ("list of companies", "company list", "types of companies", "partner types")):
        suppressed.add("KB-ENT-012")
    if not _has(inbound_email, _WORKLOAD_DETAIL_PHRASES):
        suppressed.add("KB-WORK-055")
    if not _has(inbound_email, _ROLE_PHRASES):
        suppressed.add("KB-ROLE-056")
    if _has(inbound_email, _REMOTE_DETAIL_PHRASES):
        suppressed.update(
            {
                "KB-ROLE-028",
                "KB-ROLE-029",
                "KB-WORK-030",
                "KB-WORK-031",
                "KB-WORK-032",
            }
        )
    if _has(inbound_email, _FINANCE_DETAIL_PHRASES):
        suppressed.update(
            {
                "KB-FUND-033",
                "KB-FUND-034",
                "KB-FUND-035",
                "KB-FUND-036",
                "KB-FEE-042",
            }
        )
    if _has(inbound_email, _TRAVEL_DETAIL_PHRASES):
        suppressed.update({"KB-WORK-030", "KB-WORK-031", "KB-WORK-032"})
    if research_information_provided(inbound_email):
        suppressed.update({"KB-ENT-011", "KB-ENT-014"})
    selected = [fact for fact in selected if fact.fact_id not in suppressed]

    enabled_by_id = {
        fact.fact_id: fact
        for fact in V2_KNOWLEDGE_BASE
        if fact.enabled and fact.status != "DISABLED"
    }
    mandatory = [
        enabled_by_id[fact_id]
        for fact_id in mandatory_fact_ids(inbound_email)
        if fact_id in enabled_by_id
    ]
    mandatory_ids = {fact.fact_id for fact in mandatory}
    combined = mandatory + [fact for fact in selected if fact.fact_id not in mandatory_ids]
    compatible_ids = apply_fact_compatibility([fact.fact_id for fact in combined])
    return [enabled_by_id[fact_id] for fact_id in compatible_ids][:limit]


SYSTEM_PROMPT = """You draft careful English replies to overseas academic experts using retrieved RAG facts.

Return one valid JSON object with exactly these top-level fields:
{
  "draft": "complete internal email draft",
  "coverage": [{"topic": "short topic", "status": "ANSWERED or PENDING_CONFIRMATION", "evidence": "fact IDs"}],
  "warnings": ["short warning"]
}

Rules:
1. REPLY MODE is authoritative. GENERAL_OVERVIEW may include the programme overview. SPECIFIC_QUESTIONS must answer only the concrete questions and must not add the general programme overview.
2. Courtesy wording such as "more details", "further information", or "I look forward to hearing from you" never changes SPECIFIC_QUESTIONS into GENERAL_OVERVIEW.
3. Fixed lead order overrides inbound question order: when KB-PROG-003 is retrieved, put it first under "Programme name"; put KB-COMP-007 immediately after it under "Qingfei and government talent offices"; then put KB-PROG-002, when retrieved, under "Programme overview". Without KB-PROG-003, KB-PROG-002 remains first. Use concise numbered headings for multiple topics and put only the corresponding token in each fixed lead section.
4. After the salutation, start directly with the first section. No opening thanks, enthusiasm, or acknowledgement of interest/research. Only when materials_received_this_reply is true may you add one short acknowledgement of the received materials. Historical cv_status=RECEIVED alone is insufficient. Never repeat, summarize, classify, or paraphrase their research topics, organisms, crops, diseases, methods, technologies, or project names.
5. Retrieved facts are the only factual authority. Do not add general knowledge.
6. For every VERBATIM fact, place its render_token exactly once as a separate paragraph. Do not write or paraphrase that fact yourself. Python replaces the token before display.
7. For specific financial questions, keep personal compensation, negotiated payment terms, government research funding, and the no-fee policy distinct. Do not mention housing or startup support unless explicitly requested.
8. If research information was provided for matching, state only that it will be used for matching and what enterprise information will be supplied later. Do not repeat the research.
9. REVIEW facts are conditional and require a warning. Do not convert them into confirmed claims.
10. Use the expert_name from PROCESS CONTEXT when non-empty; otherwise use "Dear Professor,". Use the sender fields from PROCESS CONTEXT for the signature.
11. Prefer the neutral closing "Please let us know if you have any further questions." Avoid promotional or presumptive closings.
12. Do not use placeholders other than supplied render_token values. Fact IDs may appear in the draft only inside those tokens.
13. Coverage must follow the inbound question order and name the supporting fact IDs.
14. Avoid overlapping facts. When KB-PROG-002 is present, omit KB-PROG-001 and KB-WORK-052 because the overview already covers programme purpose, remote work, duration, affiliation, and relocation. KB-WORK-055 may still be used when workload, reporting, milestones, deliverables, or responsibilities are explicitly requested. When KB-FUND-033 is present, omit KB-FUND-053.
15. For expert_reply_count <= 1, do not request a CV or other materials, invite/schedule a meeting, list meeting platforms, or ask about availability. A willingness to discuss by email or online is not an instruction to schedule. Still answer application-process and eligibility questions.
16. In later replies, request only a CV if CV_REQUEST_ALLOWED is true; otherwise do not request materials. Never infer receipt of a CV from research text.
17. Use KB-MATCH-051 for matching when research information is provided. Do not add generic-vacancy or no-company-list wording unless the expert explicitly asks about those topics. Keep responsibilities in their own section, not repeated in matching.
18. Keep personal compensation, government funding and fees in one financial section without repeating facts. Use KB-APP-058 and KB-APP-057 together in the application section when both are retrieved. Detailed eligibility criteria remain PENDING_CONFIRMATION; do not invent thresholds or use disabled facts.
19. KB-PROG-003 is the authoritative programme-name fact. Do not state that the programme lacks an official name. It already contains the government organization information, so do not repeat KB-GOV-004 with it. Use KB-PROG-059 only when the expert asks about a public website or public listing.
"""


def build_generation_prompt(
    *,
    retrieved_facts: list[base.RagFact],
    inbound_email: str,
    process_context: ReplyContext | None = None,
) -> str:
    records: list[dict[str, Any]] = []
    for fact in retrieved_facts:
        record = fact.generation_record()
        if fact.render_mode == "VERBATIM":
            record.pop("answer", None)
            record["render_token"] = base.fact_render_token(fact.fact_id)
            record["render_instruction"] = "Place this token exactly once as its own paragraph."
        records.append(record)
    context = process_context or ReplyContext()
    return f"""REPLY MODE
<reply_mode>{classify_request(inbound_email)}</reply_mode>

PROCESS CONTEXT
<process_context>
{json.dumps(context.prompt_record(), ensure_ascii=False, indent=2)}
</process_context>

CV_REQUEST_ALLOWED
{json.dumps(base.should_request_cv(inbound_email, context))}

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

Generate the JSON result now.
"""


def retrieve_with_deepseek(
    *,
    api_key: str,
    base_url: str,
    model: str,
    inbound_email: str,
    timeout: float,
    process_context: ReplyContext,
) -> tuple[list[base.RagFact], dict[str, Any], dict[str, Any]]:
    candidates = prefilter_facts(inbound_email, process_context=process_context)
    result, api_response = base.call_deepseek_json(
        api_key=api_key,
        base_url=base_url,
        model=model,
        system_prompt=base.RETRIEVAL_SYSTEM_PROMPT,
        user_prompt=base.build_retrieval_prompt(inbound_email, candidates, process_context),
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
    selected_ids = hard_required + [fact_id for fact_id in selected_ids if fact_id not in hard_required]
    required = set(requested_coverage_keys(inbound_email, process_context))
    for fact in candidates:
        if required & set(fact.coverage_keys) and fact.fact_id not in selected_ids:
            selected_ids.append(fact.fact_id)
    if not selected_ids:
        selected_ids = [fact.fact_id for fact in candidates[:14]]
    selected_ids = apply_fact_compatibility(selected_ids)
    return [candidate_by_id[fact_id] for fact_id in selected_ids[:18]], result, api_response


def finalize_draft(
    draft: str,
    retrieved_facts: list[base.RagFact],
    process_context: ReplyContext,
) -> str:
    # Place trust-critical programme facts structurally, independent of model ordering.
    body = re.sub(r"^Dear[^\n]*,\s*", "", draft.strip(), count=1, flags=re.IGNORECASE)
    if not process_context.materials_received_this_reply:
        opening = re.compile(
            r"^(?:Thank you\b|Thanks\b|We are (?:glad|pleased|happy)\b|"
            r"I am (?:glad|pleased|happy)\b|I understand\b)[^\n]*(?:\n\s*|$)",
            flags=re.IGNORECASE,
        )
        while opening.match(body):
            body = opening.sub("", body, count=1).lstrip()
    facts_by_id = {fact.fact_id: fact for fact in retrieved_facts}
    if "KB-PROG-003" in facts_by_id:
        lead_specs = (
            ("KB-PROG-003", "Programme name"),
            ("KB-COMP-007", "Qingfei and government talent offices"),
            ("KB-PROG-002", "Programme overview"),
        )
    else:
        lead_specs = (("KB-PROG-002", "National talent programme"),)
    selected_leads = [
        (facts_by_id[fact_id], heading)
        for fact_id, heading in lead_specs
        if fact_id in facts_by_id
    ]
    if selected_leads:
        for fact, _ in selected_leads:
            token = base.fact_render_token(fact.fact_id)
            body = body.replace(fact.answer, token)
            body = re.sub(
                r"(?im)^(?:#{1,6}\s*)?(?:\d+[.)]\s*)?(?:\*\*)?[^\n]+"
                r"(?:\*\*)?\s*\n\s*(?=" + re.escape(token) + r")",
                "", body,
            )
        for fact, _ in selected_leads:
            body = body.replace(base.fact_render_token(fact.fact_id), "").strip()
        acknowledgement = ""
        if process_context.materials_received_this_reply:
            match = re.match(r"^(?:Thank you|Thanks)[^\n]*(?:\n\s*|$)", body, flags=re.IGNORECASE)
            if match:
                acknowledgement = match.group().strip() + "\n\n"
                body = body[match.end():].lstrip()
        lead_body = "\n\n".join(
            f"{index}. {heading}\n\n{base.fact_render_token(fact.fact_id)}"
            for index, (fact, heading) in enumerate(selected_leads, start=1)
        )
        body = acknowledgement + lead_body + ("\n\n" + body if body else "")
        number = 0

        def renumber(match: re.Match[str]) -> str:
            nonlocal number
            number += 1
            return f"{number}. {match.group(1)}"
        body = re.sub(r"(?m)^\d+[.)]\s+([^\n]+)$", renumber, body)
    rendered = base.render_verbatim_facts(body, retrieved_facts)
    rendered = re.sub(r"\n[ \t]*\n(?:[ \t]*\n)+", "\n\n", rendered.strip())

    salutation = (
        f"Dear Professor {process_context.expert_name.strip()},"
        if process_context.expert_name.strip()
        else "Dear Professor,"
    )
    if re.match(r"^Dear[^\n]*,", rendered, flags=re.IGNORECASE):
        rendered = re.sub(r"^Dear[^\n]*,", salutation, rendered, count=1, flags=re.IGNORECASE)
    else:
        rendered = salutation + "\n\n" + rendered

    signature = (
        "Best regards,\n"
        f"{process_context.sender_name}, {process_context.sender_title} "
        f"{process_context.team_name} {process_context.country}"
    )
    signoff_pattern = re.compile(
        r"\n\n(?:Best|Kind) regards,.*\Z",
        flags=re.IGNORECASE | re.DOTALL,
    )
    if signoff_pattern.search(rendered):
        rendered = signoff_pattern.sub("\n\n" + signature, rendered)
    else:
        rendered = rendered.rstrip() + "\n\n" + signature
    return re.sub(r"\n[ \t]*\n(?:[ \t]*\n)+", "\n\n", rendered).strip()


def print_fact_audit(
    *,
    retrieved_facts: list[base.RagFact],
    inbound_email: str,
    process_context: ReplyContext,
) -> None:
    print("\n=== MATCHED FACTS ===")
    if retrieved_facts:
        for fact in retrieved_facts:
            coverage = ", ".join(fact.coverage_keys) or "none"
            print(f"- {fact.fact_id} — {fact.title} — {coverage} — {fact.render_mode}")
    else:
        print("- None")

    covered = {key for fact in retrieved_facts for key in fact.coverage_keys}
    missing = [
        key
        for key in requested_coverage_keys(inbound_email, process_context)
        if key not in covered
    ]
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
    for key in missing:
        print(f"- {key} — no retrieved supporting fact")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate a scenario-routed RAG reply through DeepSeek.")
    parser.add_argument("--input", type=Path)
    parser.add_argument("--model", default=os.getenv("DEEPSEEK_MODEL", base.DEFAULT_MODEL))
    parser.add_argument("--base-url", default=os.getenv("DEEPSEEK_BASE_URL", base.DEFAULT_BASE_URL))
    parser.add_argument("--timeout", type=float, default=120.0)
    parser.add_argument("--dump-prompt", action="store_true")
    parser.add_argument("--dump-kb", action="store_true")
    parser.add_argument("--expert-reply-count", type=int, default=1)
    parser.add_argument("--expert-tags", default="")
    parser.add_argument("--cv-status", choices=("MISSING", "RECEIVED", "UNKNOWN"), default="UNKNOWN")
    parser.add_argument("--materials-received-this-reply", action="store_true",
                        help="Confirm that materials were actually received in this reply (allows a short acknowledgement).")
    parser.add_argument("--expert-name", default=os.getenv("EXPERT_NAME", ""))
    parser.add_argument("--sender-name", default=os.getenv("SENDER_NAME", "LiLei"))
    parser.add_argument("--sender-title", default=os.getenv("SENDER_TITLE", "Customer Care Officer"))
    parser.add_argument("--team-name", default=os.getenv("TEAM_NAME", "Qingfei Tech Talent Team"))
    parser.add_argument("--country", default=os.getenv("SENDER_COUNTRY", "China"))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    inbound_email = base.read_text(args.input, "inbound email") if args.input else SAMPLE_INBOUND_EMAIL.strip()
    context = ReplyContext(
        expert_reply_count=max(0, args.expert_reply_count),
        expert_tags=tuple(tag.strip().upper() for tag in args.expert_tags.split(",") if tag.strip()),
        cv_status=args.cv_status,
        materials_received_this_reply=args.materials_received_this_reply,
        expert_name=args.expert_name,
        sender_name=args.sender_name,
        sender_title=args.sender_title,
        team_name=args.team_name,
        country=args.country,
    )

    if args.dump_kb:
        print(json.dumps([fact.generation_record() for fact in V2_KNOWLEDGE_BASE], ensure_ascii=False, indent=2))
        return

    candidates = prefilter_facts(inbound_email, process_context=context)
    if args.dump_prompt:
        print("=== REPLY MODE ===")
        print(classify_request(inbound_email))
        print("\n=== PREFILTERED FACT IDS ===")
        print("\n".join(fact.fact_id for fact in candidates))
        print("\n=== GENERATION SYSTEM PROMPT ===")
        print(SYSTEM_PROMPT.strip())
        print("\n=== GENERATION USER PROMPT ===")
        print(build_generation_prompt(retrieved_facts=candidates, inbound_email=inbound_email, process_context=context))
        return

    api_key = os.getenv("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("DEEPSEEK_API_KEY is not set.")

    retrieved_facts, retrieval_result, retrieval_api_response = retrieve_with_deepseek(
        api_key=api_key,
        base_url=args.base_url,
        model=args.model,
        inbound_email=inbound_email,
        timeout=args.timeout,
        process_context=context,
    )
    result, api_response = base.call_deepseek_json(
        api_key=api_key,
        base_url=args.base_url,
        model=args.model,
        system_prompt=SYSTEM_PROMPT,
        user_prompt=build_generation_prompt(
            retrieved_facts=retrieved_facts,
            inbound_email=inbound_email,
            process_context=context,
        ),
        timeout=args.timeout,
        temperature=0.2,
        max_tokens=3000,
    )
    draft = result.get("draft")
    if not isinstance(draft, str):
        raise SystemExit("DeepSeek generation JSON does not contain a string field named 'draft'.")
    result["draft"] = finalize_draft(draft, retrieved_facts, context)
    violations = base.verbatim_violations(result["draft"], retrieved_facts)
    if violations:
        raise SystemExit("Renderer omitted VERBATIM fact(s): " + ", ".join(violations))

    print("=== GENERATED DRAFT ===")
    print(result["draft"])
    print_fact_audit(
        retrieved_facts=retrieved_facts,
        inbound_email=inbound_email,
        process_context=context,
    )
    print("\n=== WARNINGS ===")
    warnings = result.get("warnings", [])
    local = base.local_warnings(result["draft"])
    combined = [str(item) for item in warnings if str(item).strip()] if isinstance(warnings, list) else []
    combined.extend(local)
    if combined:
        for warning in dict.fromkeys(combined):
            print(f"- {warning}")
    else:
        print("- None")

    generation_usage = api_response.get("usage", {})
    retrieval_usage = retrieval_api_response.get("usage", {})
    if isinstance(generation_usage, dict) and generation_usage:
        print("\n=== GENERATION TOKEN USAGE ===")
        print(f"total={generation_usage.get('total_tokens', '?')}")
    if isinstance(retrieval_usage, dict) and retrieval_usage:
        print("\n=== RETRIEVAL TOKEN USAGE ===")
        print(f"total={retrieval_usage.get('total_tokens', '?')}")


if __name__ == "__main__":
    main()
