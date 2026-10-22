# ALOUTE — DOCKER & REVERSE PROXY CONVENTION

> **Scope**
>
> File này là source of truth cho **Docker, Docker Compose, container runtime, reverse proxy và deployment topology** của ALOUTE.
>
> Reverse proxy mặc định:
>
> ```text
> Nginx
> ```
>
> File này **không sở hữu**:
>
> ```text
> API response / pagination / HTTP status / ErrorCode
> → ALOUTE_SYSTEM_ARCHITECTURE.md
>
> Business rule / entity / WHO CAN DO WHAT
> → ALOUTE_DOMAIN_SYSTEM.md
>
> Spring Boot / JPA implementation
> → ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md
>
> JWT / Spring Security
> → SPRING_SECURITY_CONVENTION.md
>
> Thymeleaf / Bootstrap / browser JavaScript
> → ALOUTE_FRONTEND_CONVENTION.md
> ```

---

# 1. DEPLOYMENT MODEL

```text
Internet
   ↓
DNS
   ↓
80 / 443
   ↓
Nginx Reverse Proxy
   ↓
Spring Boot Container
   ↓
Primary Relational Database
```

Realtime:

```text
Browser
   ↓ WSS
Nginx
   ↓ WebSocket Upgrade
Spring Boot
```

Optional:

```text
Spring Boot
├── Firebase external integration
└── MongoDB nếu System Architecture xác nhận có use case
```

Production mặc định không expose Spring Boot trực tiếp ra Internet khi Nginx đã là public entry point.

---

# 2. CONTAINER RESPONSIBILITY

```text
nginx
→ TLS + public entry point + reverse proxy

app
→ Spring Boot application

db
→ primary relational database

mongo
→ optional MongoDB

backup
→ optional operational backup job
```

Không nhét:

```text
Nginx + Spring Boot + Database
```

vào cùng một container.

---

# 3. PROJECT DEPLOY FOLDER

```text
project-root/
├── Dockerfile
├── .dockerignore
│
├── deploy/
│   ├── compose/
│   │   ├── compose.dev.yml
│   │   └── compose.prod.yml
│   │
│   ├── nginx/
│   │   ├── nginx.conf
│   │   └── conf.d/
│   │       └── aloute.conf.template
│   │
│   ├── env/
│   │   └── .env.example
│   │
│   └── scripts/
│       ├── deploy.sh
│       ├── backup.sh
│       └── restore.sh
│
└── ...
```

Production `.env` không commit Git.

---

# 4. DOCKERFILE

Spring Boot production image ưu tiên multi-stage build:

```text
Build stage
→ JDK + Maven/Wrapper
→ build JAR

Runtime stage
→ JRE
→ run JAR
```

Concept:

```dockerfile
FROM eclipse-temurin:<java-version>-jdk AS build

WORKDIR /workspace
COPY . .
RUN ./mvnw clean package -DskipTests


FROM eclipse-temurin:<java-version>-jre

WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Exact Java version phải khớp project.

Không dùng JDK production runtime nếu chỉ cần JRE.

---

# 5. BUILD PIPELINE ALTERNATIVE

CI có thể build JAR trước rồi Docker chỉ đóng gói runtime:

```text
CI
↓
mvn test/package
↓
docker build runtime image
```

Project phải có một production workflow canonical.

Không để mỗi developer deploy theo một cách khác nhau.

---

# 6. .DOCKERIGNORE

Phải có `.dockerignore`.

Ví dụ:

```text
.git
.idea
.vscode
target
*.log
.env
.env.*
node_modules
```

Không đưa local secret/build cache không cần thiết vào Docker build context.

---

# 7. PRODUCTION IMAGE

Production image không chứa nếu không cần:

```text
.git
IDE metadata
local database
developer credentials
test output
source secret
```

Image là runtime artifact, không phải bản sao toàn bộ laptop developer.

---

# 8. NON-ROOT USER

Production app container ưu tiên chạy non-root.

Concept:

```dockerfile
RUN addgroup --system aloute \
 && adduser --system --ingroup aloute aloute

USER aloute
```

Không chạy root mặc định chỉ vì cấu hình dễ hơn.

---

# 9. IMAGE TAG

Production không dùng `latest` làm source of truth.

Dùng immutable tag:

```text
aloute:<version>
aloute:<git-sha>
```

Ví dụ:

```text
aloute:a12bc34
```

Một immutable tag đã publish không được overwrite thành image khác.

---

# 10. DOCKER COMPOSE

Compose mô tả runtime topology, không mô tả business rule.

Canonical services:

```text
nginx
app
db
```

Optional:

```text
mongo
backup
```

Không chạy PostgreSQL + MySQL + SQL Server cùng lúc chỉ vì tech stack liệt kê cả ba.

Primary relational DB phải theo `ALOUTE_SYSTEM_ARCHITECTURE.md`.

---

# 11. DEV VS PROD

Khuyến nghị:

```text
compose.dev.yml
compose.prod.yml
```

Dev có thể:

- expose app port;
- expose DB port localhost;
- chạy app bằng IDE;
- chỉ chạy DB bằng Docker.

Prod:

- chỉ Nginx public;
- app internal;
- DB internal;
- persistent volume;
- healthcheck;
- restart policy;
- production env.

---

# 12. PORT EXPOSURE

Production public:

```text
80
443
```

qua Nginx.

Spring Boot:

```text
8080
```

chỉ internal Docker network.

Database:

```text
5432 / 3306 / 1433
```

không public Internet.

MongoDB:

```text
27017
```

không public Internet.

---

# 13. EXPOSE VS PORTS

Production concept:

```yaml
services:
  nginx:
    ports:
      - "80:80"
      - "443:443"

  app:
    expose:
      - "8080"
```

Không publish:

```yaml
app:
  ports:
    - "8080:8080"
```

trong production nếu không có lý do vận hành rõ.

---

# 14. DOCKER NETWORK

Service giao tiếp bằng Docker service name.

Đúng:

```text
app:8080
db:5432
```

Sai:

```text
172.18.0.5
```

Không hardcode container IP.

---

# 15. LOCALHOST RULE

Trong app container:

```text
localhost
```

là chính app container.

Nếu DB là container khác:

Sai:

```text
localhost:5432
```

Đúng:

```text
db:5432
```

---

# 16. NETWORK SEGMENTATION

Cho đồ án/VPS đơn giản có thể dùng:

```text
internal network
```

chứa:

```text
nginx
app
db
```

Nếu cần chặt hơn:

```text
public network
internal network
```

nhưng không over-engineer nếu chưa có requirement.

---

# 17. ENVIRONMENT VARIABLES

Runtime config inject qua environment.

Catalog thực tế của ALOUTE (khớp `deploy/env/.env.example` + `docker-compose.yml`, verify 2026-10-20):

```text
SPRING_PROFILES_ACTIVE              # dev | prod
ALOUTE_DOMAIN
ALOUTE_APP_BASE_URL

MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD / MYSQL_PORT

ALOUTE_JWT_SECRET                   # Base64 >= 32 bytes, bắt buộc ở prod (fail-closed)
ALOUTE_JWT_EXPIRATION_SECONDS
ALOUTE_REGISTRATION_OTP_SECRET      # HMAC key cho registration OTP, bắt buộc ở prod

ALOUTE_FIREBASE_ENABLED / ALOUTE_FIREBASE_BUCKET / ALOUTE_FIREBASE_CREDENTIALS_PATH

ALOUTE_MAIL_HOST / ALOUTE_MAIL_PORT / ALOUTE_MAIL_USERNAME /
ALOUTE_MAIL_PASSWORD / ALOUTE_MAIL_FROM   # để trống HOST = simulated delivery
```

JWT-specific property thuộc Security Convention.

Không hardcode production config vào Docker image.

---

# 18. .ENV

Repository chỉ commit:

```text
.env.example   (tại deploy/env/.env.example)
```

Không commit:

```text
.env
.env.prod
production secret
```

`.env.example` chỉ chứa placeholder (xem catalog §17); secret thật
(`MYSQL_PASSWORD`, `ALOUTE_JWT_SECRET`, `ALOUTE_REGISTRATION_OTP_SECRET`,
`ALOUTE_MAIL_PASSWORD`) phải để rỗng trong example.

---

# 19. SECRET

Không đặt secret trong:

```text
Dockerfile ENV
Git-tracked Compose
Nginx config committed plaintext
Java source
Shell script committed
```

Với VPS đồ án có thể dùng protected `.env`.

Nếu infrastructure hỗ trợ:

```text
CI secret
Docker secret
secret manager
```

thì dùng mechanism phù hợp.

---

# 20. PERSISTENT VOLUME

Database phải có persistent storage.

Ví dụ:

```yaml
volumes:
  db-data:
```

Recreate container không được làm mất DB.

Không dùng writable container layer làm nơi lưu database duy nhất.

---

# 21. VOLUME OWNERSHIP

Persistent candidate:

```text
database data
certificate data nếu local
backup
local upload nếu architecture dùng local storage
```

Nếu media canonical nằm Firebase:

```text
không tạo local media volume để duplicate vô nghĩa.
```

---

# 22. BIND MOUNT VS NAMED VOLUME

Dev:

```text
bind mount
```

có thể phù hợp.

Prod:

```text
named volume
```

hoặc managed persistent storage.

Không mount toàn bộ source code vào production app container.

---

# 23. DATABASE SCHEMA

Docker/Compose không sở hữu schema evolution.

Schema migration follow:

```text
ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md
```

Không tạo hai source of truth:

```text
random init.sql
+
Flyway/Liquibase migration
```

mà không phân định trách nhiệm.

---

# 24. START ORDER

`depends_on` chỉ giúp startup ordering.

Không đồng nghĩa:

```text
container started
=
service ready
```

Dependency cần healthcheck hoặc retry phù hợp.

---

# 25. HEALTHCHECK

Service quan trọng nên có healthcheck có meaning.

Có thể dùng Spring Boot Actuator:

```text
/actuator/health
```

nếu project bật Actuator.

Không expose sensitive actuator endpoint public.

---

# 26. ACTUATOR EXPOSURE

Production chỉ expose endpoint thật sự cần.

Không public:

```text
/env
/beans
/configprops
/heapdump
```

Security của endpoint thuộc Security Convention.

---

# 27. RESTART POLICY

Production có thể dùng:

```yaml
restart: unless-stopped
```

hoặc policy tương đương.

Restart loop không phải cách chữa application crash.

Nếu restart liên tục:

```text
đọc log
tìm root cause
```

---

# 28. GRACEFUL SHUTDOWN

Deploy/restart phải cho Spring Boot cơ hội shutdown graceful.

Không dùng:

```text
kill -9
```

làm deploy workflow mặc định.

Mục tiêu:

```text
stop receiving new work
finish current request khi có thể
close resources
exit
```

---

# 29. RESOURCE LIMIT

VPS phải kiểm soát:

```text
CPU
memory
disk
```

của:

```text
app
database
nginx
```

Không set JVM heap lớn hơn memory container/host thực tế.

---

# 30. APPLICATION LOG

Application container log:

```text
stdout
stderr
```

là mặc định.

Không ghi production log chỉ vào file ephemeral bên trong container.

---

# 31. LOG ROTATION

Docker/runtime log phải có rotation.

Không để log tăng vô hạn đến đầy disk.

Có thể giới hạn:

```text
max-size
max-file
```

theo runtime logging driver.

---

# 32. NGINX RESPONSIBILITY

Nginx chịu trách nhiệm:

```text
public HTTP/HTTPS entry point
TLS termination
reverse proxy
forwarded headers
WebSocket Upgrade
transport-level request size/timeouts
basic infrastructure protection
```

Nginx không chịu trách nhiệm:

```text
business permission
community role
post ownership
API ErrorCode
JWT business claim semantics
```

---

# 33. SERVER NAME

Production config phải dùng domain thật:

```nginx
server_name aloute.example.com;
```

Không dùng wildcard/default host như canonical virtual host nếu domain đã biết.

Có thể có default server riêng để reject unknown host.

---

# 34. HTTP TO HTTPS

Production:

```text
HTTP :80
↓ redirect
HTTPS :443
```

TLS terminate ở Nginx.

Spring Boot phía private Docker network có thể dùng HTTP.

---

# 35. TLS

Canonical single-host topology:

```text
Client
↓ HTTPS
Nginx
↓ HTTP private Docker network
Spring Boot
```

Không commit TLS private key.

Certificate có thể dùng:

```text
Let's Encrypt
provider certificate
manual certificate
```

nhưng project phải có renewal/deploy procedure rõ.

---

# 36. BASIC REVERSE PROXY

Concept:

```nginx
location / {
    proxy_pass http://app:8080;
}
```

Trong Nginx container không dùng:

```text
localhost:8080
```

để gọi app container khác.

Dùng service DNS:

```text
app:8080
```

---

# 37. PROXY HEADERS

Nginx phải forward proxy context cần thiết.

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
```

Spring phải được config xử lý forwarded header phù hợp (`server.forward-headers-strategy: native` với `internal-proxies` giới hạn ở các dải IP private: 10/8, 172.16/12, 192.168/16, 127/8).

Không trust forwarded header từ Internet nếu app có đường truy cập bypass proxy. Nginx phải luôn overwrite `X-Forwarded-Proto $scheme` và `X-Forwarded-Host $host` để vô hiệu hóa header giả mạo từ client. Database container (MySQL) không được publish port 3306 ra host interfaces trong production/compose để ngăn chặn database exposure.

---

# 38. CLIENT IP

Application không tin random:

```text
X-Forwarded-For
```

do client tự gửi.

Client IP chỉ đáng tin khi request bắt buộc đi qua trusted proxy topology.

---

# 39. AUTH TRANSPORT PASS-THROUGH

Nginx không tự sửa authentication transport một cách tùy tiện.

Phải preserve theo architecture:

```text
Authorization
Cookie
Set-Cookie
```

Chi tiết JWT/cookie thuộc:

```text
SPRING_SECURITY_CONVENTION.md
```

---

# 40. REQUEST BODY LIMIT

Nginx có thể dùng:

```nginx
client_max_body_size
```

nhưng đây chỉ là infrastructure guard.

Application vẫn validate file/request theo business rule.

Infrastructure limit nên tương thích application media limit để backend còn trả lỗi canonical khi phù hợp.

---

# 41. PROXY TIMEOUT

Không tăng timeout cực lớn chỉ để che:

```text
slow SQL
deadlock
external service hang
bad upload flow
```

WebSocket và HTTP request thường có thể cần timeout khác nhau.

---

# 42. WEBSOCKET PROXY

Concept:

```nginx
location /ws/ {
    proxy_pass http://app:8080;

    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";

    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Exact path phải lấy từ application config.

Nginx chỉ forward socket; authorization vẫn do backend.

---

# 43. WSS

Nếu website chạy HTTPS:

```text
browser public WebSocket
→ wss://
```

Không dùng insecure:

```text
ws://
```

từ HTTPS page.

---

# 44. STATIC FILE

Default cho ALOUTE:

```text
Spring Boot serve Thymeleaf + static resources
Nginx reverse proxy toàn request
```

Không bắt buộc tách static asset sang Nginx ở đồ án này.

Nếu sau này Nginx serve static trực tiếp, build pipeline phải có một source artifact rõ.

---

# 45. ERROR PASS-THROUGH

Mặc định Nginx không thay application/API error response.

Không bật kiểu:

```text
proxy_intercept_errors
```

cho toàn app rồi biến canonical JSON error thành HTML Nginx page.

Application response contract thuộc System Architecture.

---

# 46. CORS

Với Thymeleaf same-origin, không mở CORS wildcard chỉ để chạy được.

Không để Spring và Nginx cùng set CORS khác nhau.

Application/security config là owner chính của CORS semantics.

---

# 47. CSRF

Reverse proxy không disable/bypass CSRF.

Credential/CSRF strategy thuộc Security Convention.

Nginx chỉ transport request đúng.

---

# 48. COMPRESSION

Nginx có thể gzip text:

```text
HTML
CSS
JavaScript
JSON
```

Không cố compress lại media đã nén như:

```text
JPEG
PNG
MP4
ZIP
```

một cách vô ích.

---

# 49. PROXY CACHE

Default:

```text
không proxy-cache authenticated application response.
```

Không cache mù:

```text
feed
message
notification
private profile
authenticated API
```

nếu chưa có cache isolation strategy.

---

# 50. RATE LIMIT

Nginx có thể có infrastructure-level rate limit cho endpoint nhạy cảm nếu project thật sự cần.

Ví dụ candidate:

```text
login
register
upload
```

Không dùng Nginx rate limit thay business/security rule.

Không invent limit ngẫu nhiên chưa có requirement.

---

# 51. NGINX CONFIG STRUCTURE

```text
deploy/nginx/
├── nginx.conf
└── conf.d/
    └── aloute.conf.template
```

`nginx.conf`:

```text
global runtime config
```

`aloute.conf`:

```text
ALOUTE virtual host (rendered from aloute.conf.template via render-nginx.sh into writable ephemeral /etc/nginx/conf.d)
TLS
proxy rules
WebSocket route
```

---

# 52. NGINX VALIDATION

Trước reload/deploy:

```text
nginx -t
```

Config invalid không được đưa vào production.

---

# 53. NGINX LOG

Access log có thể chứa:

```text
time
method
path
status
response time
client IP
user agent
```

Không log:

```text
Authorization token
Cookie content
password
request body
```

---

# 54. DATABASE EXPOSURE

Production topology:

```text
Internet
  X
Database
```

DB chỉ accessible từ:

```text
app
authorized backup/maintenance path
```

Không mở DB public chỉ để dùng GUI dễ hơn.

---

# 55. DATABASE ADMIN ACCESS

Nếu cần admin DB trên VPS:

Ưu tiên:

```text
SSH tunnel
VPN/private network
temporary local-only bind
```

Không public DB port dài hạn.

---

# 56. MONGODB

Nếu dùng MongoDB:

- internal network;
- authentication;
- persistent storage;
- không public 27017;
- source of truth phải theo Architecture.

Nếu System Architecture chưa xác nhận use case:

```text
không chạy MongoDB container.
```

---

# 57. HOST FIREWALL

Production tối thiểu thường chỉ cần public:

```text
80
443
```

SSH:

```text
22
```

nên hạn chế nguồn nếu môi trường cho phép.

Không public:

```text
8080
database ports
27017
```

---

# 58. DNS

DNS:

```text
domain
→ VPS/load balancer public address
```

DNS không trỏ vào Docker container IP.

Container IP là internal detail.

---

# 59. DEPLOY ENVIRONMENTS

```text
development
test
staging nếu có
production
```

Domain/config khác nhau qua environment, không hardcode vào business code.

Production container phải chạy đúng Spring profile production.

Cloud Run dùng request-based billing chỉ cấp CPU đầy đủ trong khi xử lý request.
Luồng registration OTP tuân theo `domain_system.md` §60.3: gửi sau commit và hoàn tất
lần thử SMTP trước khi trả response, không phụ thuộc thread nền sau response.
Không cần bật CPU always allocated hoặc minimum instances > 0 cho luồng OTP này.
Không áp dụng bảo đảm này cho password reset: luồng đó hiện vẫn dùng `emailExecutor`.

---

# 60. VPS DIRECTORY

Khuyến nghị:

```text
/opt/aloute/
├── compose.yml
├── .env
├── nginx/
├── backup/
└── scripts/
```

Không deploy production vào random folder kiểu:

```text
/root/project-final-final/
```

---

# 61. FILE PERMISSION

Không dùng:

```text
chmod 777
```

để chữa permission.

Secret và TLS private key phải có permission hạn chế.

Fix owner/group đúng.

---

# 62. SSH DEPLOY

Nếu deploy bằng SSH:

- dùng SSH key;
- không commit private key;
- không nhét password vào script;
- hạn chế quyền user;
- không dùng root cho mọi thao tác nếu không cần.

Docker access phải được coi là quyền rất mạnh trên host.

---

# 63. BACKUP

Database phải có:

```text
backup command
backup destination
retention
restore procedure
```

Backup chưa test restore thì chưa được coi là backup đáng tin cậy.

---

# 64. BACKUP STORAGE

Không chỉ giữ backup trên cùng một volume/disk duy nhất nếu mục tiêu là chống disk failure.

Với đồ án có thể đơn giản hơn, nhưng phải document limitation.

---

# 65. RESTORE

Restore procedure phải biết:

```text
target DB
migration compatibility
application stop/write strategy nếu cần
restore command
health verification
```

Không đợi tới lúc mất database mới tìm cách restore.

---

# 66. DEPLOY FLOW

Canonical:

```text
pull source / obtain artifact
↓
run tests
↓
build JAR/image
↓
tag immutable image
↓
backup nếu migration rủi ro
↓
run migration/start application
↓
healthcheck
↓
verify Nginx/HTTPS
↓
verify critical flow
↓
verify WebSocket nếu liên quan
```

---

# 67. MIGRATION DURING DEPLOY

Schema migration theo backend convention.

Không để nhiều container cùng chạy destructive migration nếu tooling/process không đảm bảo safe.

Single-instance đồ án có thể dùng migration at startup nếu team hiểu failure behavior.

---

# 68. ROLLBACK

Rollback cần:

```text
previous immutable app image
known compose/deploy command
compatible database state
```

Không gọi là rollback-ready nếu image trước đã bị overwrite bởi `latest`.

---

# 69. POST-DEPLOY VERIFY

Sau deploy kiểm tra:

```text
DNS
HTTPS
Nginx
app health
DB connectivity
login page
critical API/page
static assets
WebSocket nếu có thay đổi realtime
logs
```

Không chỉ nhìn:

```text
docker ps
```

rồi coi deploy thành công.

---

# 70. COMPOSE SERVICE NAMING

Dùng:

```text
nginx
app
db
mongo
```

Không dùng:

```text
container1
backend-final
backend-new2
```

Không bắt buộc `container_name`; Compose service DNS thường đã đủ.

---

# 71. DATABASE IMAGE

DB image phải pin version.

Không dùng production canonical:

```text
postgres:latest
mysql:latest
mssql:latest
```

Major upgrade cần procedure tương thích persistent volume/data.

---

# 72. BASE IMAGE

Dùng official/reputable base image.

Pin version hợp lý:

```text
Java runtime
Nginx
Database
```

Không chọn random image không rõ nguồn.

---

# 73. TIMEZONE

Infrastructure/application nên dùng timezone strategy nhất quán.

Khuyến nghị persistence/system timestamp:

```text
UTC
```

Frontend format theo user/local context.

Không phụ thuộc implicit VPS timezone cho business timestamp.

---

# 74. WEBSOCKET SCALE WARNING

Một app instance:

```text
WebSocket đơn giản.
```

Nếu scale nhiều app instances phải xem lại:

```text
shared broker
event propagation
presence state
sticky session nếu cần
```

Không nghĩ rằng chỉ tăng replica là realtime tự scale đúng.

---

# 75. LOCAL DEVELOPMENT

Dev không bắt buộc chạy full production topology.

Hợp lệ:

```text
DB bằng Docker
+
Spring Boot từ IDE
```

hoặc:

```text
full Compose stack
```

Nhưng production behavior quan trọng như:

```text
Nginx
WSS
forwarded headers
```

phải được test trước demo/deploy.

---

# 76. TESTCONTAINERS

Backend integration test có thể dùng Testcontainers khi cần database semantics gần production.

Không bắt buộc.

Nếu production dùng PostgreSQL, critical query/constraint có thể khác H2; test engine phù hợp khi cần.

---

# 77. CI/CD MINIMUM

```text
checkout
↓
test
↓
build
↓
build image
↓
tag
↓
publish/deploy
↓
health verification
```

Không deploy khi critical tests fail.

---

# 78. DISK MANAGEMENT

Phải theo dõi:

```text
Docker image
build cache
container log
DB volume
backup
```

Không chạy:

```text
docker system prune -a --volumes
```

mù trên production.

Persistent DB volume không được prune tùy tiện.

---

# 79. FAILURE TRIAGE

Khi ALOUTE không vào được:

```text
1. DNS resolve?
2. VPS reachable?
3. 80/443 open?
4. Nginx running?
5. TLS valid?
6. Nginx resolve/reach app?
7. App healthy?
8. App reach DB?
9. Migration success?
10. Firebase dependency okay?
11. WebSocket Upgrade okay?
```

Debug từ ngoài vào trong.

Không chỉnh ngẫu nhiên nhiều layer cùng lúc.

---

# 80. PRODUCTION COMPOSE CONCEPT

```yaml
services:
  nginx:
    image: nginx:<pinned-version>
    ports:
      - "80:80"
      - "443:443"
    networks:
      - internal

  app:
    image: aloute:<immutable-version>
    env_file:
      - .env
    expose:
      - "8080"
    networks:
      - internal
    restart: unless-stopped

  db:
    image: <chosen-db>:<pinned-version>
    env_file:
      - .env
    volumes:
      - db-data:/database-data
    networks:
      - internal
    restart: unless-stopped

networks:
  internal:

volumes:
  db-data:
```

`/database-data` chỉ là placeholder.

Actual DB data path phải theo database engine được project chọn.

---

# 81. NGINX CONFIG CONCEPT

```nginx
server {
    listen 443 ssl;
    server_name aloute.example.com;

    location / {
        proxy_pass http://app:8080;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://app:8080;

        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Đây là architecture example, không phải production config copy-paste hoàn chỉnh.

Domain, certificate path và WebSocket endpoint phải dùng config thật.

---

# 82. SECURITY HEADER OWNERSHIP

Một response security header chỉ có một owner chính.

Không để:

```text
Spring Security
+
Nginx
```

set cùng header với giá trị khác nhau.

Header phụ thuộc application/security semantics ưu tiên cấu hình trong Security Convention.

Transport-specific header có thể thuộc Nginx.

---

# 83. UNKNOWN HOST

Production có thể có default Nginx server để reject request với hostname không hợp lệ.

Không phục vụ application dưới arbitrary Host nếu domain production đã xác định.

---

# 84. MAINTENANCE MODE

Nếu cần maintenance page:

- có procedure enable/disable;
- không intercept API vô thời hạn;
- không biến thành hidden branch khó debug.

Không cần xây maintenance architecture nếu đồ án không cần.

---

# 85. COMMON ANTI-PATTERNS

Không:

```text
Expose DB public
Expose Spring Boot public cạnh Nginx
Commit .env
Use latest as production version
Run everything in one container
Hardcode container IP
Use localhost between containers
Store DB only inside container layer
Trust depends_on as readiness
Put business authorization in Nginx
Replace all app errors with Nginx HTML
Disable security just to make proxy work
Mount source into production
Run root unnecessarily
Ignore log rotation
Prune production volume blindly
```

---

# 86. REVIEW CHECKLIST — DOCKER

- Dockerfile multi-stage/runtime gọn chưa?
- Base image pinned chưa?
- `.dockerignore` có chưa?
- Secret có bị copy vào image không?
- App có chạy root không cần thiết không?
- Image tag immutable không?
- DB có persistent volume không?
- App/DB có public port production không?
- Network có dùng service DNS không?
- Healthcheck có ý nghĩa không?
- Restart policy hợp lý không?

---

# 87. REVIEW CHECKLIST — NGINX

- Domain đúng chưa?
- HTTP → HTTPS chưa?
- TLS certificate đúng chưa?
- `proxy_pass` dùng service name chưa?
- Forwarded headers đúng chưa?
- WebSocket Upgrade đúng chưa?
- Có vô tình intercept JSON error không?
- Upload/body limit có hợp lý không?
- Proxy timeout có đang che lỗi app không?
- Access log có leak credential không?

---

# 88. REVIEW CHECKLIST — VPS

- Firewall chỉ mở port cần thiết?
- SSH dùng key?
- Production secret permission ổn?
- Disk còn đủ?
- Docker log rotation?
- Database backup có?
- Restore procedure có?
- Certificate renewal có?
- Previous image rollback còn không?
- Post-deploy health verification có?

---

# 89. DOCUMENT OWNERSHIP

```text
ALOUTE_DOMAIN_SYSTEM.md
→ business

ALOUTE_SYSTEM_ARCHITECTURE.md
→ application/system contracts

ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md
→ Spring Boot backend implementation

SPRING_SECURITY_CONVENTION.md
→ security implementation

ALOUTE_FRONTEND_CONVENTION.md
→ frontend implementation

ALOUTE_DOCKER_REVERSE_PROXY_CONVENTION.md
→ container + reverse proxy + deployment runtime
```

Không copy rule Docker/Nginx này sang các file khác.

Các file khác chỉ reference.

---

# 90. CORE RULES

1. Production dùng Nginx làm public entry point.
2. Spring Boot ở private Docker network.
3. Database không public Internet.
4. MongoDB không public Internet.
5. Một responsibility chính mỗi container.
6. Service gọi nhau bằng service name.
7. Không hardcode container IP.
8. Không dùng `localhost` giữa containers.
9. Image production dùng immutable tag.
10. Không dùng `latest` làm source of truth.
11. Không commit secret.
12. `.env` production không nằm Git.
13. Database có persistent volume.
14. Container recreate không được làm mất dữ liệu.
15. `depends_on` không phải readiness.
16. Healthcheck phải có meaning.
17. App production ưu tiên non-root.
18. Nginx terminate TLS.
19. HTTP redirect HTTPS.
20. Forward proxy headers đúng.
21. WebSocket forward Upgrade đúng.
22. HTTPS site dùng WSS.
23. Nginx không chứa business authorization.
24. Nginx không thay Spring Security.
25. Nginx không định nghĩa API semantics.
26. Nginx không intercept canonical JSON error mặc định.
27. Auth header/cookie pass-through.
28. Infrastructure upload limit không thay business validation.
29. App log stdout/stderr.
30. Docker log phải rotate.
31. DB phải có backup.
32. Backup phải có restore procedure.
33. Deploy phải health-check.
34. Rollback cần previous immutable image.
35. Migration thuộc backend convention.
36. Compose không duplicate schema source.
37. Không mount source vào production.
38. Không mở internal port chỉ để tiện debug.
39. Không prune production volume tùy tiện.
40. Docker/reverse-proxy rule chỉ có owner ở file này.
