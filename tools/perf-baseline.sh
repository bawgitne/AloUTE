#!/usr/bin/env bash
# AloUTE SSR page latency benchmark: avg + P95 TTFB over N iterations per page.
# Usage: perf-baseline.sh [iterations] [outfile]
BASE=http://127.0.0.1:8080
N=${1:-20}
OUT=${2:-final-validation-evidence/perf-baseline.csv}
mkdir -p "$(dirname "$OUT")"

TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@aloute.local","password":"Admin@123"}' \
  -D - -o /dev/null | grep -i "set-cookie: aloute_access_token" | sed 's/.*aloute_access_token=\([^;]*\).*/\1/')

PAGES=(
  "login_page:/login"
  "feed:/feed"
  "post_detail:/posts/1"
  "search:/search?q=do"
  "profile:/users/admin"
  "community_list:/communities"
  "messaging:/messages"
  "notifications:/notifications"
  "topics:/topics/hoc-tap"
)

echo "page,http,ttfb_ms,total_ms,bytes" > "$OUT"
for entry in "${PAGES[@]}"; do
  name="${entry%%:*}"
  path="${entry#*:}"
  for i in $(seq 1 "$N"); do
    curl -s -o /dev/null -b "aloute_access_token=$TOKEN" \
      -w "${name},%{http_code},%{time_starttransfer},%{time_total},%{size_download}\n" \
      "$BASE$path" >> "$OUT" || true
  done
done

for i in $(seq 1 "$N"); do
  curl -s -o /dev/null -H "Authorization: Bearer $TOKEN" \
    -w "api_feed,%{http_code},%{time_starttransfer},%{time_total},%{size_download}\n" \
    "$BASE/api/posts?page=0&size=20" >> "$OUT" || true
  curl -s -o /dev/null -H "Authorization: Bearer $TOKEN" \
    -w "api_notifications,%{http_code},%{time_starttransfer},%{time_total},%{size_download}\n" \
    "$BASE/api/notifications?page=0&size=10" >> "$OUT" || true
done

node -e "
const fs=require('fs');
const rows=fs.readFileSync(process.argv[1],'utf8').trim().split('\n').slice(1).map(l=>l.split(','));
const by={};
for(const r of rows){ if(r.length<5) continue; const [p,http,ttfb,total,bytes]=[r[0],r[1],+r[2]*1000,+r[3]*1000,+r[4]]; (by[p]=by[p]||[]).push({http,ttfb,total,bytes}); }
console.log('page,n,http_codes,avg_ttfb_ms,p95_ttfb_ms,avg_total_ms,p95_total_ms,avg_bytes');
for(const [p,arr] of Object.entries(by)){
  const tt=arr.map(x=>x.ttfb).sort((a,b)=>a-b), ta=arr.map(x=>x.total).sort((a,b)=>a-b);
  const avg=a=>a.reduce((s,x)=>s+x,0)/a.length;
  const p95=a=>a[Math.min(a.length-1,Math.floor(a.length*0.95))];
  const codes={}; for(const x of arr) codes[x.http]=(codes[x.http]||0)+1;
  console.log([p,arr.length,JSON.stringify(codes),avg(tt).toFixed(1),p95(tt).toFixed(1),avg(ta).toFixed(1),p95(ta).toFixed(1),Math.round(avg(arr.map(x=>x.bytes)))].join(','));
}
" "$OUT"
