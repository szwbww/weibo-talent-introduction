#!/usr/bin/env bash
# 05A 产出物：institutionType 真实分布盘点（A5a-7）
# 用法：
#   export ES_URL='https://es-fcxvip4d.public.tencentelasticsearch.com:9200'
#   export ES_AUTH='elastic:你的密码'
#   bash institution-type-distribution.sh
set -u
: "${ES_URL:?请先 export ES_URL}"
: "${ES_AUTH:?请先 export ES_AUTH}"
IDX="${IDX:-orcid_info_candidate}"

q () { curl -sS -u "$ES_AUTH" -H 'Content-Type: application/json' --max-time 60 "$ES_URL/$IDX/_search" -d "$1"; }

echo "索引：$IDX"
echo
echo "===== ① 覆盖率：多少文档已经有 institutionType ====="
q '{"size":0,"aggs":{
     "has":     {"filter":{"exists":{"field":"institutionType"}}},
     "missing": {"missing":{"field":"institutionType"}},
     "has_email":{"filter":{"bool":{"filter":[{"exists":{"field":"institutionType"}},{"exists":{"field":"email"}}]}}}
   }}' | python3 -c "
import sys,json;d=json.load(sys.stdin)
t=d['hits']['total']['value'];a=d['aggregations']
h=a['has']['doc_count'];m=a['missing']['doc_count'];he=a['has_email']['doc_count']
print(f'  文档总数        {t:>8,}')
print(f'  有 institutionType {h:>8,}  ({h*100//max(t,1)}%)')
print(f'    其中有邮箱     {he:>8,}')
print(f'  缺失            {m:>8,}  ({m*100//max(t,1)}%)')
print()
if h < 500: print('  ⚠ 样本不足 500，分布不可信。先跑「补充学术数据（OpenAlex）」，见脚本末尾说明。')
"

echo
echo "===== ② 类型分布（仅统计已有该字段的文档）====="
q '{"size":0,"query":{"bool":{"filter":[{"exists":{"field":"institutionType"}}]}},
    "aggs":{"t":{"terms":{"field":"institutionType","size":20}}}}' | python3 -c "
import sys,json;d=json.load(sys.stdin)
b=d['aggregations']['t']['buckets'];tot=sum(x['doc_count'] for x in b) or 1
for x in b: print(f\"  {x['key']:<14}{x['doc_count']:>8,}  {x['doc_count']*100//tot:>3}%\")
"

echo
echo "===== ③ 交叉表：机构类型 × 当前分类结果（05B 的核心输入）====="
q '{"size":0,"query":{"bool":{"filter":[{"exists":{"field":"institutionType"}}]}},
    "aggs":{"t":{"terms":{"field":"institutionType","size":20},
      "aggs":{"cls":{"terms":{"field":"expertClassification.type","size":10,"missing":"(无分类)"}}}}}}' \
| python3 -c "
import sys,json;d=json.load(sys.stdin)
for x in d['aggregations']['t']['buckets']:
    print(f\"\\n  {x['key']}  共 {x['doc_count']:,}\")
    for c in x['cls']['buckets']:
        print(f\"      {c['key']:<16}{c['doc_count']:>8,}  {c['doc_count']*100//max(x['doc_count'],1):>3}%\")
"

echo
echo "===== ④ 同上，但只看有邮箱的（真正会发信的那批）====="
q '{"size":0,"query":{"bool":{"filter":[{"exists":{"field":"institutionType"}},{"exists":{"field":"email"}}]}},
    "aggs":{"t":{"terms":{"field":"institutionType","size":20},
      "aggs":{"cls":{"terms":{"field":"expertClassification.type","size":10,"missing":"(无分类)"}}}}}}' \
| python3 -c "
import sys,json;d=json.load(sys.stdin)
for x in d['aggregations']['t']['buckets']:
    print(f\"\\n  {x['key']}  共 {x['doc_count']:,}\")
    for c in x['cls']['buckets']:
        print(f\"      {c['key']:<16}{c['doc_count']:>8,}  {c['doc_count']*100//max(x['doc_count'],1):>3}%\")
"

echo
echo "===== ⑤ company 的样本机构名（人工核对 type 判得准不准）====="
q '{"size":15,"_source":["institution","institutionType","expertClassification.type","email"],
    "query":{"bool":{"filter":[{"term":{"institutionType":"company"}}]}}}' | python3 -c "
import sys,json;d=json.load(sys.stdin)
for h in d['hits']['hits']:
    s=h['_source'];c=(s.get('expertClassification') or {}).get('type','(无)')
    print(f\"  {c:<16}{(s.get('institution') or '')[:52]}\")
"
echo
echo "===== ⑥ healthcare 的样本机构名 ====="
q '{"size":15,"_source":["institution","institutionType","expertClassification.type"],
    "query":{"bool":{"filter":[{"term":{"institutionType":"healthcare"}}]}}}' | python3 -c "
import sys,json;d=json.load(sys.stdin)
for h in d['hits']['hits']:
    s=h['_source'];c=(s.get('expertClassification') or {}).get('type','(无)')
    print(f\"  {c:<16}{(s.get('institution') or '')[:52]}\")
"
