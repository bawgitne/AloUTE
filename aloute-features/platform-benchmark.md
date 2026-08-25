# Đối chiếu nền tảng và hướng cải tiến

ALOUTE học các pattern sản phẩm đã được kiểm chứng nhưng tự thiết kế dữ liệu, nghiệp vụ, UI và mã nguồn; không sao chép thương hiệu, asset hoặc giao diện pixel-perfect.

## Feature map

| Nguồn | Pattern học hỏi | Cách ALOUTE triển khai/cải tiến |
| --- | --- | --- |
| Facebook | Feed, audience từng bài, activity log, Privacy Checkup, public/private group | Feed có điểm giải thích được; “Vì sao thấy bài này?”; Privacy Checkup dạng wizard |
| Zalo | Chat nhanh, quyền trưởng/phó nhóm, nhóm announcement, cộng đồng chú trọng privacy | Permission matrix; chế độ chỉ admin được chat; form onboarding; ẩn member list |
| Telegram | Online/last seen theo privacy, typing, multi-device, invite link, message expiry | Presence nhiều tab; last seen gần đúng; link mời hết hạn/giới hạn lượt; tin tự xóa |
| Signal | Message request, typing/read receipt tùy chọn, block, disappearing message | Request inbox chống spam; privacy đối ứng; safety tips và session control |
| Discord | Role/permission, onboarding questions, forum/tag, AutoMod keyword/regex/spam | Dynamic group form; topic/tag; rule engine có action và moderator alert |

## Cải tiến riêng của ALOUTE

1. Recommendation có score breakdown và feedback “ít nội dung kiểu này hơn”.
2. Group onboarding bằng form động, rule/permission cấu hình ngay khi tạo.
3. AutoMod nhiều tầng: keyword, regex, spam heuristic, quarantine và appeal.
4. Security Center hợp nhất session, login alert, 2FA và Privacy Checkup.
5. Realtime tôn trọng privacy: typing, read receipt và online đều có tùy chọn.

## Những gì chủ động không làm

- Không clone toàn bộ Facebook; chỉ làm các luồng có thể demo sâu.
- Không tuyên bố end-to-end encryption nếu server vẫn đọc/lưu nội dung message.
- Không làm video call/livestream trong core scope.
- Không gọi heuristic là AI khi chưa có mô hình ML và dữ liệu đánh giá.
- Không dùng nhiều database chỉ để tăng số công nghệ.

## Nguồn chính thức

- [Facebook privacy settings và Privacy Checkup](https://www.facebook.com/help/193677450678703/)
- [Facebook login alerts và Security Checkup](https://www.facebook.com/help/1674717642789671)
- [Facebook private-group joining](https://www.facebook.com/help/ipad-app/401492893195007)
- [Zalo group messaging permissions](https://help.zalo.me/huong-dan/chuyen-muc/tro-chuyen-nhom/tai-sao-ban-khong-the-nhan-tin-trong-mot-so-nhom-zalo/)
- [Zalo community privacy](https://help.zalo.me/huong-dan/chuyen-muc/tro-chuyen-nhom/tro-chuyen-cong-dong-them-ket-noi-them-quyen-rieng-tu/)
- [Telegram privacy, online/last seen và typing](https://telegram.org/faq)
- [Signal read receipts](https://support.signal.org/hc/en-us/articles/360007059812-Read-Receipts)
- [Signal message request và safety](https://support.signal.org/hc/en-us/articles/9932632052378-How-to-protect-yourself-on-Signal)
- [Discord AutoMod](https://support.discord.com/hc/en-us/articles/4421269296535-AutoMod-FAQ)
- [Discord Community Onboarding](https://support.discord.com/hc/en-us/articles/11074987197975-Community-Onboarding-FAQ)
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)

