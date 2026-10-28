# 07 — Báo cáo hiệu năng

- Ngày chạy: 2026-10-28
- Môi trường: local `http://127.0.0.1:8082`, Spring Boot 3.2.5, H2 in-memory, Java 21, Chrome 153 headless, không throttling mạng
- Bằng chứng: `test-results/quan-e2e-acceptance/cross-run-2/results.json` (PERF-01..03)

## 1. Tải trang Feed (PERF-01)

Đo 3 lần tải `/feed` bằng phiên user, lấy median. LCP quan sát bằng `PerformanceObserver` cài trước khi navigation.

| Lần | DCL (ms) | Load (ms) | DOM interactive (ms) | Response start (ms) | LCP (ms) | Transfer document (bytes) | Resource transfer (bytes) | Số resource | Request ngoài |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 129 | 143 | 124 | 85 | 168 | 14.906 | 377 | 18 | 4 |
| 2 | 125 | 129 | 121 | 78 | 140 | 14.906 | 377 | 18 | 4 |
| 3 | 118 | 133 | 114 | 76 | 156 | 14.906 | 377 | 18 | 4 |
| **Median** | **125** | 133 | 121 | 78 | 156 | 14.906 | 377 | 18 | 4 |

Ngân sách đặt trước: DCL < 3.000 ms, load < 5.000 ms → PASS với biên rất lớn.

Diễn giải: HTML SSR nhẹ (~14,9 KB), CSS/JS cục bộ nhỏ (377 bytes transfer do cache trong phiên đo), 4 request CDN (Bootstrap CSS/JS, Bootstrap Icons, Google Fonts). Trong điều kiện mạng thật, CDN là yếu tố chi phối và chưa được đo.

## 2. Độ trễ API (PERF-02)

6 mẫu mỗi endpoint, đo bằng `context.request` (không tính render):

| Endpoint | p50 (ms) | p95 (ms) | Mẫu (ms) |
|---|---|---|---|
| `GET /api/feed?page=0&size=10` | 28 | 29 | 27, 27, 28, 28, 29 |
| `GET /api/notifications?filter=ALL&page=0&size=20` | 8 | 9 | 8, 8, 8, 9, 9 |
| `GET /api/notifications/unread-count` | 7 | 8 | 7, 7, 7, 7, 8 |

Ngân sách p95 < 800 ms → PASS. Lưu ý: H2 in-memory và dữ liệu seed nhỏ; số liệu không đại diện cho PostgreSQL/tải thật.

## 3. Tài nguyên tĩnh (PERF-03)

| Thuộc tính | Giá trị |
|---|---|
| URL | `/css/app.css` |
| HTTP | 200 |
| Content-Type | `text/css` |
| Kích thước | 60.424 bytes |
| Cache-Control | `max-age=43200` (12 giờ) |
| ETag | không có |

Không có nén được kiểm tra ở tầng server (không bật compression trong môi trường dev). Chưa đo TTFB có CDN, chưa đo trên 3G/4G throttling.

## 4. Rủi ro hiệu năng đã quan sát

1. **CDN external** — 4 request ra ngoài; nếu chặn CDN, icon/font có thể mất, layout xô lệch. Không thể xác minh production nếu không kiểm soát mạng.
2. **Không có ETag cho CSS** — trình duyệt vẫn cache 12h nhưng không revalidate hiệu quả; khi deploy asset mới, người dùng có thể giữ bản cũ tới 12h.
3. **Không có hash/version trong URL asset** — cache dài hạn an toàn cần đổi tên tệp theo nội dung (backend/config, ngoài phạm vi).
4. **Dữ liệu seed nhỏ** — feed 10 bài; chưa đo N+1 hoặc tải 1000 bài.

## 5. Kết luận

Trong môi trường local, hiệu năng đạt ngân sách đặt ra (DCL ~125 ms, API p95 ≤ 29 ms). Đây là **baseline local**, không phải cam kết hiệu năng production. Cần đo lại với PostgreSQL, dữ liệu lớn, mạng throttled và CDN thật trước khi chốt SLO.
