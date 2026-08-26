#!/usr/bin/env bash
# 定性 403：是路径问题、缺 key、还是 API 已关闭
# 在【生产服务器】上跑：bash cp1-forbidden.sh
set -u
UA='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36'

show () { # $1=label $2=url
  printf '\n--- %s\n    %s\n' "$1" "$2"
  curl -sS -D /tmp/h.txt -o /tmp/b.txt -w '    HTTP %{http_code}  (%{time_total}s)\n' --max-time 20 \
       -H "User-Agent: $UA" "$2" 2>&1 | tail -1
  echo "    响应头关键项:"
  grep -iE '^(server|via|x-cache|x-amz|x-amzn|x-cache-hits|content-type|www-authenticate|x-error)' /tmp/h.txt \
    | sed 's/^/      /' | head -8
  echo "    响应体前 200 字节:"
  head -c 200 /tmp/b.txt | sed 's/^/      /'; echo
}

echo "===== A. 路径变体（判断是不是路径不对）====="
show "文档给的路径"          "https://api.www.sbir.gov/public/api/awards?year=2024&rows=1&format=json"
show "去掉查询参数"          "https://api.www.sbir.gov/public/api/awards"
show "裸主机根路径"          "https://api.www.sbir.gov/"
show "故意写错的路径（对照）" "https://api.www.sbir.gov/this/path/does/not/exist/xyz123"
show "旧式路径"              "https://www.sbir.gov/api/awards.json?year=2024&rows=1"
show "solicitation 端点"     "https://api.www.sbir.gov/public/api/solicitations?rows=1"

echo
echo "===== B. 主站是否正常（区分整站封禁 vs 仅 API）====="
show "主站首页"              "https://www.sbir.gov/"
show "API 文档页"            "https://www.sbir.gov/api"

echo
echo "===== 判读 ====="
cat <<'TXT'
  故意写错的路径也返回 {"message":"Forbidden"}
      → 不是路径问题，是整个 API 被挡（WAF / 缺 key / 已关闭）
  故意写错的路径返回 "Missing Authentication Token" 而正确路径返回 Forbidden
      → 路径存在但被拒，最可能是缺 API key 或 WAF 规则
  响应头含 x-amzn-errortype 或 x-amz-apigw-id
      → 确认是 AWS API Gateway 在拒，不是 CloudFront WAF
  响应头含 x-amzn-waf-* 或 server: CloudFront 且无 apigw 标识
      → 是 WAF 层拦截，可能是地域/信誉封禁
  主站 200 而 API 全 403
      → 仅 API 被关闭或收紧，对应「维护中」公告，路径 C 有了正当依据
  主站也 403
      → 整站对该出口拒绝
TXT
