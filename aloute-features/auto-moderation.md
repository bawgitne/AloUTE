# Lọc từ khóa và kiểm duyệt tự động

AutoMod kiểm tra post, comment, group chat và direct-message request trước khi publish. Hệ thống dựa trên rule/score, luôn có log và cơ chế appeal.

## Rule và risk

| Rule | Ví dụ | Risk |
| --- | --- | ---: |
| Exact/phrase keyword | Từ/cụm từ cấm | 10–80 |
| Prefix/suffix/contains | Biến thể của từ cấm | 20–60 |
| Safe regex | Điện thoại, spam invite, chuỗi lặp | 10–50 |
| Link rule | Denylist, URL rút gọn đáng ngờ | 30–80 |
| Mention spam | Quá giới hạn mention | 30 |
| Flood/repetition | Gửi nhiều nội dung giống nhau | 20–70 |
| Account risk | Account mới, lịch sử action hợp lệ | Tối đa 20 |

Dùng `max(severeRule)` cộng tín hiệu phụ có cap, tránh cộng mù quáng khiến câu dài vô hại bị phạt.

## Chuẩn hóa chống né filter

1. Unicode normalize NFKC và lowercase.
2. Giữ hai bản nguyên dấu/accent-folded cho tiếng Việt.
3. Xóa zero-width và chuẩn hóa khoảng trắng.
4. Giới hạn ký tự lặp (`đẹpppp` → `đẹpp`).
5. Map leetspeak có kiểm soát (`0→o`, `1→i`) như tín hiệu phụ.
6. Tokenize rồi chạy exact/phrase, wildcard và regex đã review.

Regex do Admin nhập phải giới hạn độ dài, có timeout và compile/test trước khi bật để chống ReDoS.

## Action theo điểm

| Score | Action |
| --- | --- |
| 0–29 | `ALLOW` |
| 30–49 | `WARN`, cho sửa hoặc vẫn gửi |
| 50–69 | `MASK_OR_REVIEW` |
| 70–84 | `QUARANTINE`, không public, báo moderator |
| 85–100 | `BLOCK`, từ chối và áp rate limit |

Hard rule nghiêm trọng đi thẳng `BLOCK`. Không tự động ban vĩnh viễn; đình chỉ dài hạn cần người kiểm duyệt xác nhận.

## Dữ liệu và quyền

- `ModerationRule`: scope, type, pattern, severity, action, enabled, version.
- `ContentScanResult`: target, normalized hash, matched rule IDs, score, action, engine version.
- `ModerationAlert`: priority, assigned manager, status.
- `ModerationAppeal`: lý do User, reviewer, decision, timestamps.
- Group Owner được tạo rule riêng nhưng không thể tắt global safety rule.
- User nhận lý do theo category, được sửa và appeal; không công khai toàn bộ denylist.
- Mọi quyết định thủ công lưu evidence snapshot và audit log.

## Kiểm thử

- Corpus tiếng Việt có dấu/không dấu, ký tự lặp, zero-width và leetspeak.
- False-positive cho từ chứa substring hợp lệ.
- Regex timeout/ReDoS, spam burst, mention flood và URL shortener.
- Rule versioning giữ được lý do của scan cũ.
- Group moderator không sửa được global rule.
- Appeal khôi phục đúng nội dung và cập nhật audit log.

