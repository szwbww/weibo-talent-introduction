#!/usr/bin/env python3
"""Regenerate the RAG deterministic-layer parity corpus (plan 02, T5).

Reads inbound email texts, evaluates the deterministic retrieval functions of
`scripts/spike_deepseek_reply.py` with the D-3 patch applied (COMPENSATION
mandatory rule -> KB-FUND-033, mirroring the V112 `rag_mandatory_rule` row at
sort_order 15), and writes `src/test/resources/rag-parity/fixtures.json`.

`RagPrefilterParityTest` reads that file and asserts the Kotlin deterministic
layer (plan 02 T1-T4) produces exactly the recorded outputs for every case:
    prefilter fact_code list (ordered, <= 18)
    mandatory fact_code list (ordered, D-3 applied)
    requested coverage keys (ordered)

Determinism contract: this script is a pure function of the spike knowledge
base and the case texts below; rerunning it MUST leave
`git diff --stat src/test/resources/rag-parity/fixtures.json` empty.

Corpus contract (plan T5 / child brief / controller ruling):
- >= 20 real inbound emails exported from historical `mail_record` INBOUND
  bodies (de-identified). ENVIRONMENT-BLOCKED (controller-approved): the
  historical data is not reachable here (dev MySQL absent; leftover containers
  carry no mail bodies; a scratch Flyway chain reproduces only the schema --
  mail bodies are runtime user data never seeded by migrations). Texts are NOT
  fabricated to pad the count. The available real/realistic letters are used:
  spike `SAMPLE_INBOUND_EMAIL` (counts as real per brief), mockup sample A
  (只问报酬) and mockup sample B (日本教授完整样例, de-identified) from
  `docs/mockups/rag-knowledge-base.html` DATA.samples.
- The 8 constructed scenarios of the plan's 实测基线 table: rows 1-7 use the
  plan's verbatim letter fragments; row 8's email IS the real mockup sample B
  (machine-verified to reproduce the plan table: detail=是, mandatory
  [KB-PROG-002, KB-FUND-033, KB-PROG-003, KB-GOV-004, KB-COMP-007, KB-IP-039,
  KB-CONF-036], requested_coverage_keys=16, KB-FUND-033 命中, KB-FUND-036 未命中;
  prefilter candidates = 14 -- the plan prose "预筛候选 18 条" is stale, the plan
  authors' own mockup trace also records 14, and 18 is unreachable with this
  corpus; see the child execution report O-note).
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import spike_deepseek_reply as spike  # noqa: E402  (script-relative import)
import export_rag_kb_sql as export  # noqa: E402  (same-script-directory import)

OUTPUT = REPO_ROOT / "src/test/resources/rag-parity/fixtures.json"

# ---------------------------------------------------------------------------
# Real / realistic inbound letters (de-identified: names, emails and
# organization names removed -- they do not participate in phrase matching).
# (id, label, email)
# ---------------------------------------------------------------------------
MOCKUP_SAMPLE_A = "Could you tell me the compensation for this advisory role?"

MOCKUP_SAMPLE_B_DEIDENTIFIED = (
    "Dear Colleague,\n\n"
    "Thank you for reaching out and for your interest in my research.\n\n"
    "I may be interested in learning more about this opportunity. Before\n"
    "discussing my current research in detail, could you please provide some\n"
    "additional information about the programme, including:\n\n"
    "- The official name of the national-level talent programme\n"
    "- The government organization responsible for the programme\n"
    "- The expected contractual relationship (with the organizing team, the\n"
    "  partner company, or another organization)\n"
    "- Typical responsibilities, duration, and compensation for technical\n"
    "  advisors\n"
    "- Any requirements regarding affiliation, intellectual property,\n"
    "  confidentiality, or registration under the programme\n\n"
    "As I am currently employed by a Japanese national university and involved\n"
    "in several publicly funded and industry-collaborative research projects, I\n"
    "would first need to confirm the relevant institutional requirements.\n\n"
    "Thank you, and I look forward to hearing from you."
)

REAL_INBOUND_EMAILS: tuple[tuple[str, str, str], ...] = (
    (
        "real-spike-sample",
        "spike SAMPLE_INBOUND_EMAIL —— 真实日本植物学教授来信（brief：计入 real 池）",
        spike.SAMPLE_INBOUND_EMAIL.strip(),
    ),
    (
        "real-mockup-compensation-only",
        "mockup rag-knowledge-base.html 样例 A · 只问报酬（真实来信改写）",
        MOCKUP_SAMPLE_A,
    ),
    (
        "real-mockup-japanese-professor-full",
        "mockup rag-knowledge-base.html 样例 B · 日本教授问全套细节（脱敏；"
        "即实测基线 row 8 的来信文本，机器校验与计划表逐字一致）",
        MOCKUP_SAMPLE_B_DEIDENTIFIED,
    ),
)

# ---------------------------------------------------------------------------
# The constructed scenarios of the plan's 实测基线 table, rows 1-7 verbatim
# (row 8's letter is the real mockup sample B above; the D-3 registration test
# asserts the full 8-row table against the fixture cases by id).
# ---------------------------------------------------------------------------
SCENARIOS: tuple[tuple[str, str, str], ...] = (
    (
        "scenario-1-compensation",
        "实测基线 row1: the compensation for this advisory role",
        "the compensation for this advisory role",
    ),
    (
        "scenario-2-salary",
        "实测基线 row2: the salary for this advisory role",
        "the salary for this advisory role",
    ),
    (
        "scenario-3-remuneration",
        "实测基线 row3: What remuneration is offered",
        "What remuneration is offered",
    ),
    (
        "scenario-4-detail-compensation",
        "实测基线 row4: more details from you, including the compensation",
        "more details from you, including the compensation",
    ),
    (
        "scenario-5-compensation-government-funding",
        "实测基线 row5: compensation + government funding",
        "What is the compensation, and is there any government funding",
    ),
    (
        "scenario-6-compensation-structure",
        "实测基线 row6: the compensation structure and payment schedule",
        "the compensation structure and payment schedule",
    ),
    (
        "scenario-7-official-name",
        "实测基线 row7: the official name of the programme (完全不问钱)",
        "the official name of the programme",
    ),
)

# ---------------------------------------------------------------------------
# Corpus: identical content to the V112 machine-generated seed, serialized in
# the same row order the repositories read at startup (plan 01: phrase groups
# and intent coverage ORDER BY group_code, sort_order; mandatory rules ORDER BY
# sort_order; exclusions ORDER BY rule_code, target_value; facts in fact_code
# order with sort_order = code-order ordinal). Kotlin's parity test rebuilds
# RagCorpusSnapshot from this section, so it MUST stay byte-equivalent to what
# the DB rows would load as.
# ---------------------------------------------------------------------------
def _group_phrases() -> dict[str, tuple[str, ...]]:
    """group_code -> phrases, derived exactly as export_rag_kb_sql.main()."""
    phrases: dict[str, list[str]] = {}
    for name, entry in zip(export._INTENT_GROUP_NAMES, spike._INTENT_COVERAGE):
        phrases.setdefault(name, []).extend(entry[0])
    for name, group_phrases in export._EXTRA_PHRASE_GROUPS:
        phrases.setdefault(name, []).extend(group_phrases)
    return {code: tuple(items) for code, items in phrases.items()}


_GROUP_PHRASES = _group_phrases()


def _split_csv(value: str) -> list[str]:
    """Mirror of the repository's splitCsv: ',' split, trim, drop empties."""
    return [item.strip() for item in value.split(",") if item.strip()]


def _group_hit(email: str, group_code: str) -> bool:
    normalized = spike._normalized(email)
    return any(
        spike._normalized(phrase) in normalized
        for phrase in _GROUP_PHRASES.get(group_code, ())
    )


def _enabled_fact_ids() -> set[str]:
    return {
        fact.fact_id
        for fact in spike.RAG_KNOWLEDGE_BASE
        if fact.enabled and fact.status != "DISABLED"
    }


_ENABLED_FACT_IDS = _enabled_fact_ids()

# rag_prefilter_exclusion rows (rule_code, when_groups, unless_groups,
# target_type, target_value) -- export._EXCLUSION_ROWS is the V112 authority.
_EXCLUSION_TARGETS: tuple[tuple[str, str, str, str, str], ...] = export._EXCLUSION_ROWS


def mandatory_ids_d3(email: str) -> list[str]:
    """6-rule mandatory evaluation incl. D-3 (mirror of RagMandatoryResolver).

    Rows = export._MANDATORY_ROWS (sort_order 10/15/20/30/40/50; 15 is the D-3
    COMPENSATION -> KB-FUND-033 row). match_groups any-of; fact codes appended
    in row order; first-occurrence dedupe (I-9); enabled-only (I-2).

    Seed-data compensation (see RagMandatoryResolver KDoc): V112 mandatory
    rules 30/40 reference the match group `GOVERNMENT_ORG`, which is not a
    rag_phrase_group code (the group is GOVERNMENT_ORGANIZATION); normalize it
    so org-asking emails still force KB-GOV-004 / KB-COMP-007 like the spike.
    """
    ordered: list[str] = []
    for groups_csv, codes_csv, _order in sorted(
        export._MANDATORY_ROWS, key=lambda row: row[2]
    ):
        if any(
            _group_hit(email, _normalize_group_code(group))
            for group in _split_csv(groups_csv)
        ):
            for code in _split_csv(codes_csv):
                if code not in ordered:
                    ordered.append(code)
    return [code for code in ordered if code in _ENABLED_FACT_IDS]


def _normalize_group_code(code: str) -> str:
    """V112 seed-data compensation: GOVERNMENT_ORG -> GOVERNMENT_ORGANIZATION
    (the actual rag_phrase_group code); identity otherwise."""
    return "GOVERNMENT_ORGANIZATION" if code == "GOVERNMENT_ORG" else code


def requested_coverage_keys_db_order(
    email: str, process_context: spike.ProcessContext | None = None
) -> list[str]:
    """Requested keys in DB load order (group_code ascending) -- mirror of
    RagPrefilterService.requestedCoverageKeys, which iterates the snapshot rows
    exactly as rag_intent_coverage loads (ORDER BY group_code, sort_order).

    NOTE (recorded in the execution report): the spike's own function iterates
    its private _INTENT_COVERAGE constant order instead. The two agree for every
    email except one that hits both the IP and CONFIDENTIALITY phrase groups,
    whose shared key confidentiality.materials then occupies a different
    dedupe-keep-first position in the ORDERED list (identical key SET; the key
    order has no downstream effect -- prefilter only intersects the set). The DB
    row order is the machine-derived authority (plan 01 load order), so the
    fixtures are computed in that order and Kotlin matches them exactly.
    """
    groups = sorted(
        (name, entry)
        for name, entry in zip(export._INTENT_GROUP_NAMES, spike._INTENT_COVERAGE)
    )
    ordered: list[str] = []
    for name, entry in groups:
        if _group_hit(email, name):
            for key in entry[1]:
                if key not in ordered:
                    ordered.append(key)
    if spike.should_request_cv(email, process_context or spike.ProcessContext()):
        ordered.append("application.required_materials")
    return ordered


def prefilter_d3(
    email: str,
    *,
    limit: int = 18,
    knowledge_base: tuple[spike.RagFact, ...] = spike.RAG_KNOWLEDGE_BASE,
    process_context: spike.ProcessContext | None = None,
) -> list[spike.RagFact]:
    """spike.prefilter_facts with the D-3 patch: mandatory via the 6-rule
    evaluation (mandatory_ids_d3) and requested via the DB-order computation,
    keeping the script's five-step order (I-8) verbatim.
    """
    requested = set(requested_coverage_keys_db_order(email, process_context))
    ranked = sorted(
        (fact for fact in knowledge_base if fact.enabled and fact.status != "DISABLED"),
        key=lambda fact: (
            -spike._lexical_score(email, fact, requested),
            fact.fact_id,
        ),
    )
    if requested:
        selected = [fact for fact in ranked if requested & set(fact.coverage_keys)]
    else:
        selected = [
            fact
            for fact in ranked
            if spike._lexical_score(email, fact, requested) >= 2
        ]

    matched = {code for code in _GROUP_PHRASES if _group_hit(email, code)}
    selected = [
        fact
        for fact in selected
        if not any(
            when_groups
            and all(group in matched for group in _split_csv(when_groups))
            and not any(group in matched for group in _split_csv(unless_groups))
            and (
                (target_type == "FACT_CODE" and fact.fact_id == target_value)
                or (
                    target_type == "COVERAGE_KEY"
                    and target_value in set(fact.coverage_keys)
                )
            )
            for _rule_code, when_groups, unless_groups, target_type, target_value in _EXCLUSION_TARGETS
        )
    ]

    enabled_by_id = {
        fact.fact_id: fact
        for fact in knowledge_base
        if fact.enabled and fact.status != "DISABLED"
    }
    mandatory = [
        enabled_by_id[fact_id]
        for fact_id in mandatory_ids_d3(email)
        if fact_id in enabled_by_id
    ]
    mandatory_ids = {fact.fact_id for fact in mandatory}
    return (
        mandatory + [fact for fact in selected if fact.fact_id not in mandatory_ids]
    )[:limit]


def _corpus_section() -> dict[str, object]:
    """Serialized corpus rows in repository ORDER BY order (see docstring)."""
    fact_rows = export._fact_rows()
    if len(fact_rows) != 45:
        raise SystemExit(f"fatal: expected 45 rag_fact rows, got {len(fact_rows)}")
    facts = [
        {
            "factCode": row[0],
            "area": row[1],
            "seq": int(row[2]),
            "title": row[3],
            "category": row[4],
            "questionVariants": row[5],
            "keywords": row[6],
            "answer": row[7],
            "coverageKeys": row[8],
            "replyPolicy": row[9],
            "status": row[10],
            "riskLevel": row[11],
            "renderMode": row[12],
            "sourceRefs": row[13],
            "legacyRuleId": None if row[14] is None else int(row[14]),
            "enabled": row[15] == "1",
            "sortOrder": int(row[16]),
        }
        for row in fact_rows
    ]

    phrase_rows = [
        (name, phrase, order)
        for name, entry in zip(export._INTENT_GROUP_NAMES, spike._INTENT_COVERAGE)
        for order, phrase in enumerate(entry[0], start=1)
    ]
    for name, phrases in export._EXTRA_PHRASE_GROUPS:
        phrase_rows.extend(
            (name, phrase, order) for order, phrase in enumerate(phrases, start=1)
        )
    phrase_rows.sort(key=lambda row: (row[0], row[2]))

    coverage_rows = [
        (name, key, order)
        for name, entry in zip(export._INTENT_GROUP_NAMES, spike._INTENT_COVERAGE)
        for order, key in enumerate(entry[1], start=1)
    ]
    coverage_rows.sort(key=lambda row: (row[0], row[2]))

    mandatory_rows = sorted(
        [
            {
                "ruleCode": groups,
                "matchGroups": groups,
                "factCodes": codes,
                "sortOrder": order,
            }
            for groups, codes, order in export._MANDATORY_ROWS
        ],
        key=lambda row: row["sortOrder"],
    )

    exclusion_rows = sorted(
        [
            {
                "ruleCode": rule_code,
                "whenGroups": when_groups,
                "unlessGroups": unless_groups,
                "targetType": target_type,
                "targetValue": target_value,
            }
            for rule_code, when_groups, unless_groups, target_type, target_value in export._EXCLUSION_ROWS
        ],
        key=lambda row: (row["ruleCode"], row["targetValue"]),
    )

    return {
        "fingerprint": export.FINGERPRINT,
        "facts": facts,
        "phraseGroups": [
            {"groupCode": code, "phrase": phrase, "sortOrder": order}
            for code, phrase, order in phrase_rows
        ],
        "intentCoverage": [
            {"groupCode": code, "coverageKey": key, "sortOrder": order}
            for code, key, order in coverage_rows
        ],
        "mandatoryRules": mandatory_rows,
        "exclusions": exclusion_rows,
    }


def _evaluate_case(case_id: str, kind: str, label: str, email: str) -> dict[str, object]:
    return {
        "id": case_id,
        "kind": kind,
        "label": label,
        "email": email,
        "expected": {
            "mandatory": mandatory_ids_d3(email),
            "requested": requested_coverage_keys_db_order(email),
            "prefilter": [fact.fact_id for fact in prefilter_d3(email)],
        },
    }


def main() -> int:
    fingerprint = export._corpus_fingerprint()
    if fingerprint != export.FINGERPRINT:
        print(
            f"fatal: corpus fingerprint {fingerprint} != plan constant {export.FINGERPRINT}",
            file=sys.stderr,
        )
        return 1

    cases: list[dict[str, object]] = [
        _evaluate_case(case_id, "real", label, email)
        for case_id, label, email in REAL_INBOUND_EMAILS
    ]
    cases.extend(
        _evaluate_case(case_id, "constructed", label, email)
        for case_id, label, email in SCENARIOS
    )

    document = {
        "generatedBy": "scripts/dump_rag_parity_fixtures.py",
        "note": (
            "Parity corpus for plan 02 (RagPrefilterParityTest). Expected outputs are "
            "the spike's deterministic functions with the D-3 COMPENSATION mandatory "
            "rule applied; requested keys are computed in DB load order (execution "
            "report O-note: spike private-constant order is not representable in the "
            "DB rows; identical key set, ordered-list corner only for IP+CONFIDENTIALITY "
            "co-hits). Deterministic: rerunning this script must not change this file."
        ),
        "corpus": _corpus_section(),
        "cases": cases,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"wrote {OUTPUT}: {len(cases)} cases "
        f"({len(REAL_INBOUND_EMAILS)} real / {len(SCENARIOS)} constructed)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
