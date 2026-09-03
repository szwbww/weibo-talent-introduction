#!/usr/bin/env python3
"""Regenerate the machine-produced seed section of V112 (plan 01, T1).

The canonical corpus lives in `scripts/spike_deepseek_reply.py`
(`RAG_KNOWLEDGE_BASE` plus the `_XXX_PHRASES` / `_INTENT_COVERAGE`
constants). This script derives every seed row from that single source and
prints the SQL INSERT section on stdout -- 禁止手抄 (no hand-copied facts).

Typical use (T1):
    python3 scripts/export_rag_kb_sql.py >> src/main/resources/db/migration/V112__create_rag_knowledge_base.sql

The printed section covers, in order:
  1. `rag_fact`          -- 45 rows, columns fixed as in the V112 DDL.
  2. `rag_phrase_group`  -- phrase rows of the intent groups plus the
                            POSITIVE_INTENT / NEXT_STEP / COMPENSATION_MENTION /
                            GOVERNMENT_FUNDING_MENTION matcher groups.
  3. `rag_intent_coverage`   -- group_code -> coverage_key rows (21).
  4. `rag_mandatory_rule`    -- 6 rows incl. the D-3 COMPENSATION row (15).
  5. `rag_prefilter_exclusion` -- 4 rows (the T2 "3 行" heading is a typo).
  6. `rag_kb_meta`       -- singleton row carrying the G-2 corpus fingerprint.

The G-2 fingerprint is printed as the final trailer line and is the *same*
canonical serialization `RagKnowledgeBase.load()` re-computes from the DB at
startup; the Kotlin implementation is the single source of truth for the
algorithm (plan G-2, A1 amendment) and this script mirrors it byte-for-byte.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import spike_deepseek_reply as spike  # noqa: E402  (script-relative import)

# --- Intent groups (order mirrors spike._INTENT_COVERAGE entry order) -------
# Entry 8 (the inline "compensation|remuneration|salary|paid" phrases) is the
# COMPENSATION group: it backs both the finance.enterprise_compensation
# coverage key and the D-3 mandatory rule.
_INTENT_GROUP_NAMES: tuple[str, ...] = (
    "DETAIL_INQUIRY",
    "PROGRAMME_NAME",
    "GOVERNMENT_ORGANIZATION",
    "VERIFICATION",
    "CONTRACT_PARTY",
    "RESPONSIBILITY",
    "DURATION",
    "COMPENSATION",
    "COMPENSATION_STRUCTURE",
    "AFFILIATION",
    "IP",
    "CONFIDENTIALITY",
)

# Matcher-only phrase groups consumed by plan 02 (I-8 step 3 exclusions and
# I-12 CV gating). Not part of _INTENT_COVERAGE, hence listed here.
_EXTRA_PHRASE_GROUPS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("POSITIVE_INTENT", spike._POSITIVE_INTENT_PHRASES),
    ("NEXT_STEP", spike._NEXT_STEP_PHRASES),
    ("COMPENSATION_MENTION", ("compensation",)),
    ("GOVERNMENT_FUNDING_MENTION", ("government funding",)),
)

# Plan 01 T2 (rows exactly as specified; the "（3 行）" heading is a typo,
# A-1 / the child brief count 4 rows).
# Columns: rule_code, when_groups, unless_groups, target_type, target_value.
# when-groups semantics (plan 02 I-8 step 3): all when groups hit and no
# unless group hits -> drop the target.
_EXCLUSION_ROWS: tuple[tuple[str, str, str, str, str], ...] = (
    ("COMPENSATION_MENTION", "COMPENSATION_MENTION", "GOVERNMENT_FUNDING_MENTION",
     "COVERAGE_KEY", "finance.government_funding"),
    ("COMPENSATION_MENTION", "COMPENSATION_MENTION", "GOVERNMENT_FUNDING_MENTION",
     "COVERAGE_KEY", "finance.additional_support"),
    ("DETAIL_INQUIRY", "DETAIL_INQUIRY", "", "FACT_CODE", "KB-FUND-034"),
    ("DETAIL_INQUIRY", "DETAIL_INQUIRY", "COMPENSATION_STRUCTURE",
     "FACT_CODE", "KB-FUND-035"),
)

# Plan 01 T2 / master D-3. sort_order 15 = COMPENSATION -> KB-FUND-033.
# Columns: match_groups, fact_codes, sort_order. rule_code mirrors match_groups
# (row 40 is the any-of rule, literal per T2).
_MANDATORY_ROWS: tuple[tuple[str, str, int], ...] = (
    ("DETAIL_INQUIRY", "KB-PROG-002,KB-FUND-033", 10),
    ("COMPENSATION", "KB-FUND-033", 15),
    ("PROGRAMME_NAME", "KB-PROG-003", 20),
    ("GOVERNMENT_ORG", "KB-GOV-004", 30),
    ("PROGRAMME_NAME,GOVERNMENT_ORG", "KB-COMP-007", 40),
    ("IP", "KB-IP-039,KB-CONF-036", 50),
)

# G-2 corpus fingerprint over the current corpus. Value fixed by plan G-2 (A1
# amendment: e62421a42c432cf3); the algorithm is defined (and implemented) in
# `RagKnowledgeBase.fingerprintOf` -- this script mirrors it byte-for-byte.
FINGERPRINT = "e62421a42c432cf3"


def _sql(value: object) -> str:
    """Render a seed value as a MySQL string literal (or NULL)."""
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def _fact_rows() -> list[tuple[str, ...]]:
    """45 rag_fact rows: (fact_code, area, seq, title, category,
    question_variants, keywords, answer, coverage_keys, reply_policy, status,
    risk_level, render_mode, source_refs, legacy_rule_id, enabled, sort_order).

    Rows are emitted in `fact_code` ascending order and `sort_order` = 1..45 in
    that same order (stable corpus ordinal). The G-2 canonical serialization
    (`RagKnowledgeBase.fingerprintOf`) serializes rows sorted by fact_code and
    reads `sort_order` from the DB, so the seeded column must equal the
    code-order ordinal for the Kotlin and Python fingerprints to agree.

    I-4 separators: variants/keywords are '|'-joined, coverage_keys and
    source_refs are ','-joined -- the exact texts Kotlin re-reads from the DB.
    """
    rows: list[tuple[str, ...]] = []
    for sort_order, fact in enumerate(
        sorted(spike.RAG_KNOWLEDGE_BASE, key=lambda f: f.fact_id), start=1
    ):
        area, seq_text = fact.fact_id.split("-")[1], fact.fact_id.split("-")[2]
        rows.append((
            fact.fact_id,
            area,
            str(int(seq_text)),
            fact.title,
            fact.category,
            "|".join(fact.question_variants),
            "|".join(fact.keywords),
            fact.answer,
            ",".join(fact.coverage_keys),
            fact.reply_policy,
            fact.status,
            fact.risk_level,
            fact.render_mode,
            ",".join(fact.source_refs),
            None if fact.legacy_rule_id is None else str(fact.legacy_rule_id),
            "1" if fact.enabled else "0",
            str(sort_order),
        ))
    return rows


def _corpus_fingerprint() -> str:
    """G-2 fingerprint: SHA-256 (first 16 hex chars) over the canonical corpus
    text. 45 rag_fact rows ordered by fact_code; each row serializes its V112
    data columns (CREATE TABLE order, excluding id/audit columns) joined with
    '|' (legacy_rule_id NULL as empty string, enabled as 1/0, seq and
    sort_order as plain integers); rows joined with '\n'. Byte-identical to the
    Kotlin `RagKnowledgeBase.fingerprintOf` (verified by RagKnowledgeBaseTest).
    """
    canonical = "\n".join(
        "|".join("" if value is None else str(value) for value in row)
        for row in _fact_rows()
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


def _emit_multi_insert(table: str, columns: tuple[str, ...],
                       rows: list[tuple[str, ...]]) -> list[str]:
    lines = [f"INSERT INTO {table}\n    ({', '.join(columns)})\nVALUES"]
    for index, row in enumerate(rows):
        suffix = "," if index < len(rows) - 1 else ";"
        lines.append("    (" + ", ".join(_sql(value) for value in row) + ")" + suffix)
    lines.append("")
    return lines


def main() -> int:
    fingerprint = _corpus_fingerprint()
    if fingerprint != FINGERPRINT:
        print(
            f"fatal: corpus fingerprint {fingerprint} != plan constant {FINGERPRINT}",
            file=sys.stderr,
        )
        return 1

    lines: list[str] = []
    lines.append("-- =====================================================================")
    lines.append("-- Machine-generated corpus seed -- do not hand-edit.")
    lines.append("-- Regenerate with:  python3 scripts/export_rag_kb_sql.py")
    lines.append("-- Source of truth:   scripts/spike_deepseek_reply.py RAG_KNOWLEDGE_BASE")
    lines.append("-- =====================================================================")

    lines.append("-- rag_fact: 45 rows (fact_code ascending), fact_code UNIQUE,")
    lines.append("-- '|'/',' separators per I-4, sort_order = code-order ordinal 1..45,")
    lines.append("-- legacy_rule_id read-only (G-4).")
    fact_rows = _fact_rows()
    if len(fact_rows) != 45:
        print(f"fatal: expected 45 rag_fact rows, got {len(fact_rows)}", file=sys.stderr)
        return 1
    lines += _emit_multi_insert(
        "rag_fact",
        ("fact_code", "area", "seq", "title", "category", "question_variants",
         "keywords", "answer", "coverage_keys", "reply_policy", "status",
         "risk_level", "render_mode", "source_refs", "legacy_rule_id",
         "enabled", "sort_order"),
        fact_rows,
    )

    phrase_rows: list[tuple[str, str, str]] = []
    for name, entry in zip(_INTENT_GROUP_NAMES, spike._INTENT_COVERAGE):
        for sort_order, phrase in enumerate(entry[0], start=1):
            phrase_rows.append((name, phrase, str(sort_order)))
    for name, phrases in _EXTRA_PHRASE_GROUPS:
        for sort_order, phrase in enumerate(phrases, start=1):
            phrase_rows.append((name, phrase, str(sort_order)))
    phrase_rows.sort(key=lambda row: (row[0], int(row[2])))
    lines.append(f"-- rag_phrase_group: {len(phrase_rows)} rows, UNIQUE(group_code, phrase).")
    lines += _emit_multi_insert(
        "rag_phrase_group",
        ("group_code", "phrase", "sort_order"),
        phrase_rows,
    )

    coverage_rows: list[tuple[str, str, str]] = []
    for name, entry in zip(_INTENT_GROUP_NAMES, spike._INTENT_COVERAGE):
        for sort_order, key in enumerate(entry[1], start=1):
            coverage_rows.append((name, key, str(sort_order)))
    coverage_rows.sort(key=lambda row: (row[0], int(row[2])))
    lines.append(f"-- rag_intent_coverage: {len(coverage_rows)} rows (group_code, coverage_key).")
    lines += _emit_multi_insert(
        "rag_intent_coverage",
        ("group_code", "coverage_key", "sort_order"),
        coverage_rows,
    )

    lines.append("-- rag_mandatory_rule: 6 rows; sort_order 15 = COMPENSATION -> KB-FUND-033 (D-3).")
    lines += _emit_multi_insert(
        "rag_mandatory_rule",
        ("rule_code", "match_groups", "fact_codes", "sort_order"),
        [(groups, groups, codes, str(order)) for groups, codes, order in _MANDATORY_ROWS],
    )

    lines.append("-- rag_prefilter_exclusion: 4 rows (target_value stays single-valued).")
    lines += _emit_multi_insert(
        "rag_prefilter_exclusion",
        ("rule_code", "when_groups", "unless_groups", "target_type", "target_value"),
        _EXCLUSION_ROWS,
    )

    lines.append("-- rag_kb_meta: singleton row (id = 1 enforced by CHECK).")
    lines.append(f"INSERT INTO rag_kb_meta VALUES (1, {_sql(fingerprint)}, 45, NOW());")
    lines.append("")
    lines.append(f"-- fingerprint {fingerprint};")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
