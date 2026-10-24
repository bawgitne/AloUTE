#!/usr/bin/env bash
# AloUTE SSR page latency benchmark: TTFB vs total response time over N
# iterations per page. Statistics are computed over 2xx responses only; error
# responses are counted per status code so they can never silently drag the
# timings. 2 discarded warm-up requests per page absorb JIT/connection noise.
# Usage: perf-baseline.sh [iterations] [outfile]
BASE=http://127.0.0.1:8080
N=${1:-20}
OUT=${2:-final-validation-evidence/perf-baseline.csv}
mkdir -p "$(dirname "$OUT")"

TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@aloute.local","password":"Admin@123"}' \
  -D - -o /dev/null | grep -i "set-cookie: aloute_access_token" | sed 's/.*aloute_access_token=\([^;]*\).*/\1/')
if [ -z "$TOKEN" ]; then
  echo "ERROR: login failed or aloute_access_token cookie missing — is the app running on $BASE?" >&2
  exit 1
fi

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
  # warm-up (discarded): prime JIT, connection pool and lazy statics.
  # login_page is measured WITHOUT the auth cookie: with a session the app
  # 302-redirects to /feed, which would report an error row instead of the
  # actual anonymous login page render.
  if [ "$name" = "login_page" ]; then
    curl -s -o /dev/null "$BASE$path" || true
    curl -s -o /dev/null "$BASE$path" || true
    for i in $(seq 1 "$N"); do
      curl -s -o /dev/null \
        -w "${name},%{http_code},%{time_starttransfer},%{time_total},%{size_download}\n" \
        "$BASE$path" >> "$OUT" || true
    done
    continue
  fi
  curl -s -o /dev/null -b "aloute_access_token=$TOKEN" "$BASE$path" || true
  curl -s -o /dev/null -b "aloute_access_token=$TOKEN" "$BASE$path" || true
  for i in $(seq 1 "$N"); do
    curl -s -o /dev/null -b "aloute_access_token=$TOKEN" \
      -w "${name},%{http_code},%{time_starttransfer},%{time_total},%{size_download}\n" \
      "$BASE$path" >> "$OUT" || true
  done
done

for i in $(seq 1 "$N"); do
  curl -s -o /dev/null -b "aloute_access_token=$TOKEN" \
    -w "api_feed,%{http_code},%{time_starttransfer},%{time_total},%{size_download}\n" \
    "$BASE/api/posts?page=0&size=20" >> "$OUT" || true
  curl -s -o /dev/null -b "aloute_access_token=$TOKEN" \
    -w "api_notifications,%{http_code},%{time_starttransfer},%{time_total},%{size_download}\n" \
    "$BASE/api/notifications?page=0&size=10" >> "$OUT" || true
  curl -s -o /dev/null -b "aloute_access_token=$TOKEN" \
    -w "api_conversation_messages,%{http_code},%{time_starttransfer},%{time_total},%{size_download}\n" \
    "$BASE/api/conversations/1/messages?page=0&size=30" >> "$OUT" || true
done

node -e "
const fs=require('fs');
const rows=fs.readFileSync(process.argv[1],'utf8').trim().split('\n').slice(1).map(l=>l.split(','));
const by={};
for(const r of rows){ if(r.length<5) continue; const [p,http,ttfb,total,bytes]=[r[0],r[1],+r[2]*1000,+r[3]*1000,+r[4]]; (by[p]=by[p]||[]).push({http,ttfb,total,bytes}); }
// nearest-rank percentile on an ascending array: ceil(p*n)-1
const pct=(a,p)=>a[Math.max(0,Math.min(a.length-1,Math.ceil(p*a.length)-1))];
console.log('page,n,http_codes,err_n,ok_n,p50_ttfb_ms,p95_ttfb_ms,p50_total_ms,p95_total_ms,avg_ok_bytes');
for(const [p,arr] of Object.entries(by)){
  const codes={}; for(const x of arr) codes[x.http]=(codes[x.http]||0)+1;
  const ok=arr.filter(x=>x.http.startsWith('2'));
  const err=arr.length-ok.length;
  if(ok.length===0){
    console.log([p,arr.length,JSON.stringify(codes),err,0,'-','-','-','-', '-'].join(','));
    continue;
  }
  const tt=ok.map(x=>x.ttfb).sort((a,b)=>a-b), ta=ok.map(x=>x.total).sort((a,b)=>a-b);
  const avgBytes=Math.round(ok.reduce((s,x)=>s+x.bytes,0)/ok.length);
  console.log([p,arr.length,JSON.stringify(codes),err,ok.length,pct(tt,0.5).toFixed(1),pct(tt,0.95).toFixed(1),pct(ta,0.5).toFixed(1),pct(ta,0.95).toFixed(1),avgBytes].join(','));
}
" "$OUT"
