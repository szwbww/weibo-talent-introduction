#!/usr/bin/env bash
# 诊断"深度发现"资格淘汰原因分布
# 用法: ES_PASSWORD=你的密码 ./scripts/diagnose-reject-reasons.sh
# 可选覆盖: ES_BASE_URL / ES_USERNAME / ES_RAW_INDEX_NAME
set -euo pipefail

ES_BASE_URL="${ES_BASE_URL:-https://es-fcxvip4d.public.tencentelasticsearch.com:9200}"
ES_USERNAME="${ES_USERNAME:-elastic}"
ES_PASSWORD="${ES_PASSWORD:?请设置 ES_PASSWORD 环境变量}"
RAW="${ES_RAW_INDEX_NAME:-orcid_info}"
AUTH="-u ${ES_USERNAME}:${ES_PASSWORD}"

echo "===== ① PASSED vs REJECTED 总量 (RAW=${RAW}) ====="
curl -s $AUTH "${ES_BASE_URL}/${RAW}/_search" \
  -H 'Content-Type: application/json' -d '{
  "size":0,
  "aggs":{"by_result":{"terms":{"field":"filterResult","size":10}}}
}' | python3 -c 'import sys,json;d=json.load(sys.stdin);[print(f"{b[\"key\"]:>10}: {b[\"doc_count\"]}") for b in d["aggregations"]["by_result"]["buckets"]]'

echo
echo "===== ② REJECTED 的拒绝原因 Top20 (filterRejectReason 原值) ====="
curl -s $AUTH "${ES_BASE_URL}/${RAW}/_search" \
  -H 'Content-Type: application/json' -d '{
  "size":0,
  "query":{"term":{"filterResult":"REJECTED"}},
  "aggs":{"reasons":{"terms":{"field":"filterRejectReason","size":20}}}
}' | python3 -c 'import sys,json;d=json.load(sys.stdin);bs=d["aggregations"]["reasons"]["buckets"];[print(f"{b[\"doc_count\"]:>8}  {b[\"key\"]}") for b in bs] or (bs or print("(无桶: 字段可能非 keyword 或为空)"))'

echo
echo "提示: filterRejectReason 是多原因用 \"; \" 拼接的整串。若想按单个原因(MISSING_ORCID 等)统计,"
echo "      用下面这条带 include 的近似统计(逐个原因 count):"
echo
for R in MISSING_ORCID CHINESE_NATIONALITY INVALID_EMAIL_FORMAT DISPOSABLE_EMAIL NO_DOCTORAL_DEGREE AGE_EXCEEDED H_INDEX_TOO_LOW CITATION_COUNT_TOO_LOW INACTIVE; do
  N=$(curl -s $AUTH "${ES_BASE_URL}/${RAW}/_count" \
    -H 'Content-Type: application/json' -d "{
    \"query\":{\"bool\":{\"must\":[
      {\"term\":{\"filterResult\":\"REJECTED\"}},
      {\"wildcard\":{\"filterRejectReason\":\"*${R}*\"}}
    ]}}}" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("count","?"))')
  printf "  %-24s %s\n" "$R" "$N"
done
