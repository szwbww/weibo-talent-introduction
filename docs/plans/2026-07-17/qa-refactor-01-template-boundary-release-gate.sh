#!/usr/bin/env bash
# QA 重构 01 — V78 发布门禁（I-2 / T1）
# 用法:
#   部署前: ./qa-refactor-01-template-boundary-release-gate.sh pre
#   部署后: ./qa-refactor-01-template-boundary-release-gate.sh post
#
# 环境变量（与 application.yml 一致）:
#   DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD
# 或完整 JDBC: DB_URL=jdbc:mysql://host:3306/talent_introduction?...

set -euo pipefail

PHASE="${1:-}"
if [[ "$PHASE" != "pre" && "$PHASE" != "post" ]]; then
  echo "usage: $0 pre|post" >&2
  exit 2
fi

if [[ -n "${DB_URL:-}" ]]; then
  # jdbc:mysql://host:port/db?params
  REST="${DB_URL#jdbc:mysql://}"
  HOST_PORT="${REST%%/*}"
  DB_NAME="${REST#*/}"
  DB_NAME="${DB_NAME%%\?*}"
  DB_HOST="${HOST_PORT%%:*}"
  DB_PORT="${HOST_PORT#*:}"
  DB_PORT="${DB_PORT:-3306}"
else
  DB_HOST="${DB_HOST:-127.0.0.1}"
  DB_PORT="${DB_PORT:-3306}"
  DB_NAME="${DB_NAME:-talent_introduction}"
fi
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"

mysql_query() {
  local sql="$1"
  if [[ -n "$DB_PASSWORD" ]]; then
    MYSQL_PWD="$DB_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -N -e "$sql" "$DB_NAME"
  else
    mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -N -e "$sql" "$DB_NAME"
  fi
}

TS="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "=== qa-refactor-01 release gate ($PHASE) @ $TS ==="
echo "database: ${DB_HOST}:${DB_PORT}/${DB_NAME}"

QA_VARIANTS="$(mysql_query "SELECT COUNT(*) FROM content_variant WHERE owner_type='QA_RULE';")"
QA_BLOCKS="$(mysql_query "SELECT COUNT(*) FROM mail_compose_template_block WHERE block_type='QA_RULE';")"
DANGLING="$(mysql_query "SELECT COUNT(*) FROM mail_compose_template_block b LEFT JOIN qa_rule q ON q.id=b.ref_id WHERE b.block_type='QA_RULE' AND q.id IS NULL;")"

echo "qa_rule_variants=$QA_VARIANTS"
echo "qa_rule_blocks=$QA_BLOCKS"
echo "dangling_qa_rule_refs=$DANGLING"

if [[ "$PHASE" == "pre" ]]; then
  echo "expected: qa_rule_variants=0 qa_rule_blocks=1 dangling_qa_rule_refs=0"
  if [[ "$QA_VARIANTS" != "0" || "$QA_BLOCKS" != "1" || "$DANGLING" != "0" ]]; then
    echo "FAIL: pre-deploy gate not satisfied; stop release (do not pick arbitrary QA variants)." >&2
    exit 1
  fi
  echo "PASS: pre-deploy gate"
  exit 0
fi

echo "expected: qa_rule_blocks=0"
if [[ "$QA_BLOCKS" != "0" ]]; then
  echo "FAIL: post-V78 QA_RULE blocks remain; rollback application before Phase 2." >&2
  exit 1
fi
echo "PASS: post-deploy gate"
exit 0
