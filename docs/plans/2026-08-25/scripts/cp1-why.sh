#!/usr/bin/env bash
# CP-1 补充诊断：区分「规则没生效」与「sbir.gov 拒绝代理出口 IP」
# 用法：bash cp1-why.sh    约 30 秒
set -u
UA='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36'

probe () { # $1=url $2=说明 $3...=额外 curl 参数
  local url="$1" label="$2"; shift 2
  local out; out=$(curl -sS -o /dev/null -w '%{http_code}|%{remote_ip}|%{time_total}s' --max-time 20 "$@" "$url" 2>&1)
  printf '  %-34s %s\n' "$label" "$out"
}

echo "===== A. 同域名的不同主机（判断是不是只有 api 子域被挡）====="
probe "https://www.sbir.gov/"                                  "www.sbir.gov 主站"
probe "https://api.www.sbir.gov/public/api/awards?year=2024&rows=1" "api.www.sbir.gov"
probe "https://www.sbir.gov/api"                               "www.sbir.gov/api 文档页"

echo
echo "===== B. 带浏览器请求头（判断是不是 UA / 指纹拦截）====="
probe "https://api.www.sbir.gov/public/api/awards?year=2024&rows=1" "默认 curl UA"
probe "https://api.www.sbir.gov/public/api/awards?year=2024&rows=1" "浏览器 UA" \
      -H "User-Agent: $UA" -H 'Accept: application/json,text/html;q=0.9' -H 'Accept-Language: en-US,en;q=0.9'
probe "https://api.www.sbir.gov/public/api/awards?year=2024&rows=1" "浏览器 UA + HTTP/1.1" \
      --http1.1 -H "User-Agent: $UA" -H 'Accept: application/json'

echo
echo "===== C. 对照：其他美国 .gov 站点是否也被挡 ====="
probe "https://api.nasa.gov/"            "api.nasa.gov"
probe "https://www.usaspending.gov/"     "usaspending.gov"
probe "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/einfo.fcgi" "ncbi.nlm.nih.gov（仓库在用）"

echo
echo "===== D. TLS 握手失败在哪一步 ====="
curl -sSv --max-time 20 'https://api.www.sbir.gov/public/api/awards?year=2024&rows=1' -o /dev/null 2>&1 \
  | grep -E 'Trying|Connected|TLS|SSL|ALPN|CApath|error|subject|issuer' | head -20

echo
echo "===== 判读 ====="
cat <<'TXT'
  A 中主站 200、api 000        → 只有 api 子域被挡，试试改用主站的数据下载页
  B 中浏览器 UA 变成 200       → 是 UA/指纹拦截，SbirDataSource 需要设 User-Agent
  B 中三行都是 000             → 与请求头无关，是 IP 层面被拒
  C 中其他 .gov 也是 000       → 代理出口 IP 段被美国政府站点整体拦截，换节点或走方案二
  C 中其他 .gov 正常、只有 sbir → sbir.gov 单独拦截该出口 IP，换节点重试
TXT
