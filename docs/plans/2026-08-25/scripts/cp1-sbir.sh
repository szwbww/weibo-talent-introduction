#!/usr/bin/env bash
# CP-1：SBIR Awards API 实测（一次跑完连通性 + 字段 + 填充率 + 分页 + 限流）
# 用法：在能直连公网的终端里执行  bash cp1-sbir.sh
# 输出整段贴回给 Claude 即可。不改任何仓库文件，只写 /tmp。

set -u
H='https://api.www.sbir.gov/public/api/awards'
echo "===== 0. 连通性（先确认能不能出去）====="
code=$(curl -sS -o /tmp/sbir_probe.json -w '%{http_code}' --max-time 25 "$H?year=2024&rows=1&format=json" 2>&1)
echo "HTTP_STATUS = $code"
if [ "$code" != "200" ]; then
  echo ">>> 直连未触达。分层定位："
  ip=$(nslookup api.www.sbir.gov 2>/dev/null | awk '/^Address: /{a=$2} END{print a}')
  echo "--- DNS 解析到 : ${ip:-未知}"
  case "$ip" in
    198.18.*|198.19.*)
      echo "    ^^^ 198.18.0.0/15 是 RFC 2544 保留段，公网不存在此 IP。"
      echo "    这是本机代理工具（Clash / Surge / Stash 等）的 fake-ip 模式在接管解析。"
      echo "    TCP「成功」连的是本地代理，不是 sbir.gov；TLS 被掐说明该域名被规则判成了 DIRECT。" ;;
    *) echo "    （非 fake-ip 段，可能是真实解析）" ;;
  esac
  echo "--- TCP ---"; (nc -vz -w 5 api.www.sbir.gov 443 2>&1) || true
  echo "--- 代理环境变量 ---"; env | grep -i proxy || echo "(无，说明走 TUN/增强模式接管，不靠 http_proxy)"
  echo "--- 本机在听的常见代理端口 ---"
  lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null | grep -E ':(7890|7891|1080|1087|8888|8889|6152|6153|9090) ' || echo "(未发现)"

  echo
  echo ">>> 自动尝试：显式走本地 HTTP 代理重试"
  ok=""
  for port in 7890 1087 8888 6152 7891 1080 8889; do
    c=$(curl -x "http://127.0.0.1:$port" -sS -o /dev/null -w '%{http_code}' --max-time 15 \
        "$H?year=2024&rows=1&format=json" 2>/dev/null)
    printf '    127.0.0.1:%-5s => %s\n' "$port" "${c:-无响应}"
    if [ "$c" = "200" ]; then ok="$port"; break; fi
  done

  echo
  echo ">>> 对照：同一路径下其他境外站点是否正常"
  for u in "https://api.openalex.org/works?per-page=1" "https://api.crossref.org/works?rows=1"; do
    c=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "$u" 2>/dev/null)
    printf '    直连 %-45s => %s\n' "${u:0:45}" "${c:-000}"
  done

  if [ -n "$ok" ]; then
    echo
    echo ">>> 找到可用代理端口 $ok。用下面这条命令重跑完整 CP-1："
    echo "    ALL_PROXY=http://127.0.0.1:$ok bash $0"
    exit 2
  fi

  echo
  echo ">>> 仍未触达。处理办法（任选其一）："
  echo "    1) 在代理工具里给 sbir.gov 加一条规则走代理节点（推荐）："
  echo "       Clash 规则示例:  - DOMAIN-SUFFIX,sbir.gov,<你的节点或策略组>"
  echo "    2) 临时切「全局模式」后重跑本脚本"
  echo "    3) 若知道代理端口 P: ALL_PROXY=http://127.0.0.1:P bash $0"
  echo ">>> 注意：本次仍未触达服务端，【不得】据此判定 SBIR API 不可用。"
  exit 1
fi

echo; echo "===== 1. 顶层结构 ====="
curl -sS --max-time 30 "$H?year=2024&rows=5&format=json" -o /tmp/sbir5.json
python3 -c "
import json;d=json.load(open('/tmp/sbir5.json'))
print('顶层类型:',type(d).__name__)
print('键/长度:', list(d)[:20] if isinstance(d,dict) else len(d))"

echo; echo "===== 2. 第一条记录的字段全集 ====="
python3 -c "
import json;d=json.load(open('/tmp/sbir5.json'))
r=d[0] if isinstance(d,list) else d.get('results',d.get('data',[]))[0]
print('\n'.join(sorted(r.keys())))
print('---- 是否存在 pi_title:', 'pi_title' in r)"

echo; echo "===== 3. 关键字段填充率（200 条）====="
curl -sS --max-time 60 "$H?year=2024&rows=200&format=json" -o /tmp/sbir200.json
python3 - <<'PY'
import json
d=json.load(open('/tmp/sbir200.json'))
rows=d if isinstance(d,list) else d.get('results',d.get('data',[]))
keys=['pi_name','pi_email','poc_title','poc_email','firm','research_area_keywords',
      'abstract','number_employees','company_url','award_year','phase','program','ri_name']
print(f'总条数 {len(rows)}')
for k in keys:
    n=sum(1 for r in rows if str(r.get(k) or '').strip())
    print(f'{k:26s} {n:4d}/{len(rows)} = {n*100//max(len(rows),1)}%')
PY

echo; echo "===== 4. pi_email vs poc_email + poc_title 样本 ====="
python3 - <<'PY'
import json
d=json.load(open('/tmp/sbir200.json'))
rows=d if isinstance(d,list) else d.get('results',d.get('data',[]))
both=[r for r in rows if (r.get('pi_email') or '').strip() and (r.get('poc_email') or '').strip()]
same=sum(1 for r in both if r['pi_email'].strip().lower()==r['poc_email'].strip().lower())
print(f'两者都有值 {len(both)} 条，相同 {same} 条')
for r in both[:8]:
    print('  PI:',r['pi_email'],'| POC:',r['poc_email'],'| poc_title:',r.get('poc_title'),'| firm:',r.get('firm'))
PY

echo; echo "===== 5. firm 与 keywords 能不能过生产分阈值（关键）====="
python3 - <<'PY'
import json,re
d=json.load(open('/tmp/sbir200.json'))
rows=d if isinstance(d,list) else d.get('results',d.get('data',[]))
COMPANY=['inc','incorporated','ltd','limited','gmbh','corp','corporation','company','pharma','biotech','medtech']
ROLE=['r d','research and development','rd','product','process','design','manufacturing','engineer']
THEME=['product','engineering','manufacturing','production']
def hit(text,terms):
    t=re.sub(r'[^a-z0-9]+',' ',(text or '').lower())
    return any(f' {w} ' in f' {t} ' for w in terms)
c=sum(1 for r in rows if hit(r.get('firm'),COMPANY))
th=sum(1 for r in rows if hit(r.get('research_area_keywords'),THEME))
ro=sum(1 for r in rows if hit(r.get('firm'),ROLE))
both=sum(1 for r in rows if hit(r.get('firm'),ROLE) and hit(r.get('research_area_keywords'),THEME))
n=len(rows)
print(f'firm 命中公司形态词(+15)     : {c}/{n} = {c*100//max(n,1)}%')
print(f'firm 命中研发岗位词(+35)     : {ro}/{n} = {ro*100//max(n,1)}%')
print(f'keywords 命中生产主题词(+20) : {th}/{n} = {th*100//max(n,1)}%')
print(f'岗位+主题同时命中(=55 过阈值): {both}/{n} = {both*100//max(n,1)}%   <<< 这个数字决定路径 B 是否必要')
PY

echo; echo "===== 6. 分页 ====="
curl -sS --max-time 30 "$H?year=2024&rows=5&start=5&format=json" -o /tmp/sbir_p2.json
python3 -c "
import json
f=lambda d: d if isinstance(d,list) else d.get('results',d.get('data',[]))
a=f(json.load(open('/tmp/sbir5.json'))); b=f(json.load(open('/tmp/sbir_p2.json')))
ka=[r.get('agency_tracking_number') for r in a]; kb=[r.get('agency_tracking_number') for r in b]
print('page1',ka); print('page2',kb)
print('重叠' if set(ka)&set(kb) else '无重叠 → 分页正常')"

echo; echo "===== 7. 限流（10 连击）====="
for i in $(seq 1 10); do
  curl -sS -o /dev/null -w "%{http_code} " --max-time 15 "$H?year=2024&rows=1&format=json"
done; echo; echo "全部完成。"
