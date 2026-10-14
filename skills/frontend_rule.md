# ALOUTE — FRONTEND CONVENTION

> **Scope**
>
> File này chỉ quy định frontend của ALOUTE với:
>
> ```text
> Thymeleaf + Bootstrap + JavaScript
> ```
>
> File này **không sở hữu** API response shape, pagination contract, HTTP status, error code, JWT implementation hoặc business permission.
>
> Các contract đó phải được lấy từ file canonical tương ứng.

---

# 1. FRONTEND ARCHITECTURE & OWNERSHIP

Theo kế hoạch đồ án nhóm (`Nhom13_KeHoachCodeTest.docx`), **Phạm Minh Quân** chịu trách nhiệm chính về toàn bộ giao diện người dùng.

Hệ thống hỗ trợ 2 mô hình giao diện:
1. **Model A (Primary Course Deliverable — Chính thức nộp môn học)**:
   - **Thymeleaf SSR + Bootstrap 5 + Vanilla JS** (nằm trong `src/main/resources/templates` và `src/main/resources/static`).
   - Server-side rendered pages được render trực tiếp bởi các `PageController`.
   - JavaScript chỉ đóng vai trò enhancement tương tác (like, comment, voting, WebSocket realtime stomp).
2. **Model B (Extended REST Client — Mở rộng)**:
   - **Vue 3 SPA** (nằm trong thư mục `fe-vue`).
   - Hoạt động như một external client độc lập gọi REST API `/api/v1/*`.
   - Không được làm ảnh hưởng hoặc làm xáo trộn kiến trúc Thymeleaf SSR chính thức.

```text
Browser
   ↓
Spring MVC Page Controller (Quân phụ trách)
   ↓
ViewModel / Model
   ↓
Thymeleaf
   ↓
HTML + Bootstrap 5 + JavaScript enhancement
```

JavaScript chỉ tăng tương tác khi cần, không biến Thymeleaf template thành SPA giả lập.

---

# 2. SOURCE OF TRUTH REFERENCES

Frontend phải follow:

```text
API contract / pagination / error / HTTP
→ ALOUTE_SYSTEM_ARCHITECTURE.md

Authentication / JWT / current user / authorization enforcement
→ SPRING_SECURITY_CONVENTION.md

Business capability / visibility / role semantics
→ ALOUTE_DOMAIN_SYSTEM.md
```

Frontend không copy lại các bảng/rule đó.

---

# 3. RESOURCE FOLDER STRUCTURE

```text
src/main/resources/
├── templates/
│   ├── layouts/
│   │   ├── default.html
│   │   ├── auth.html
│   │   ├── settings.html
│   │   └── admin.html
│   │
│   ├── fragments/
│   │   ├── head.html
│   │   ├── navbar.html
│   │   ├── sidebar.html
│   │   ├── flash.html
│   │   ├── modal.html
│   │   └── pagination.html
│   │
│   ├── auth/
│   ├── feed/
│   ├── user/
│   ├── post/
│   ├── topic/
│   ├── poll/
│   ├── community/
│   ├── messaging/
│   ├── notification/
│   ├── moderation/
│   ├── settings/
│   └── error/
│
└── static/
    ├── css/
    │   ├── app.css
    │   ├── tokens.css
    │   ├── components/
    │   ├── features/
    │   └── pages/
    │
    ├── js/
    │   ├── core/
    │   │   ├── http-client.js
    │   │   ├── dom.js
    │   │   ├── form.js
    │   │   └── websocket-client.js
    │   ├── features/
    │   │   ├── post/
    │   │   ├── community/
    │   │   ├── messaging/
    │   │   └── notification/
    │   └── pages/
    │
    ├── images/
    └── vendor/
```

Không bắt buộc tạo folder chưa dùng.

---

# 4. TEMPLATE RESPONSIBILITY

Một Thymeleaf template chỉ chịu trách nhiệm presentation.

Template được phép:

- Render field từ ViewModel.
- Loop collection.
- Render fragment.
- Conditional UI.
- Build link/form từ model.
- Hiển thị permission/capability đã được backend tính cho UX.

Template không được:

- Query repository/database.
- Tự suy luận business permission phức tạp.
- Parse JWT.
- Gọi Firebase SDK.
- Chứa business calculation dài.
- Chứa hàng trăm dòng JavaScript inline.

---

# 5. PAGE VS FRAGMENT

## Page

Page là template đại diện một màn hình/route lớn.

Ví dụ:

```text
feed/index.html
user/profile.html
post/detail.html
community/detail.html
messaging/index.html
settings/profile.html
```

## Fragment

Fragment là phần UI được reuse.

Ví dụ:

```text
fragments/navbar.html
fragments/sidebar.html
post/_post-card.html
community/_member-row.html
messaging/_message-item.html
```

Không biến mọi `div` thành fragment.

Tách fragment khi:

- Có semantic UI unit rõ.
- Được reuse.
- Có khả năng thay đổi độc lập.
- Page khó đọc nếu giữ inline.

---

# 6. LAYOUT

Layout chứa app shell:

```text
head
navbar
left sidebar
main content
right sidebar nếu có
global modal area
global script include
```

Page không copy navbar/sidebar/footer nhiều lần.

Chọn một layout strategy và dùng thống nhất.

Không vừa dùng layout dialect kiểu này vừa copy-paste HTML shell ở page khác.

---

# 7. THYMELEAF MODEL RULE

Controller đưa vào view một model đủ dùng.

Ưu tiên:

```text
PostCardViewModel
ProfileHeaderViewModel
CommunityDetailViewModel
ConversationViewModel
```

thay vì đưa object graph entity khổng lồ.

Không để template phải đi sâu:

```text
post.user.profile.organization....
```

nếu UI chỉ cần:

```text
authorName
authorAvatarUrl
```

Backend/ViewModel mapper phải làm phẳng dữ liệu hợp lý.

---

# 8. ENTITY LEAKAGE

Không dùng JPA Entity làm UI contract mặc định.

Lý do:

- Lazy loading.
- Persistence field leakage.
- Template coupling.
- Security leakage.
- Khó đổi schema.
- Khó test view.

Page nhận ViewModel.

AJAX nhận Response DTO theo architecture contract.

---

# 9. BOOTSTRAP RESPONSIBILITY

Bootstrap dùng cho:

- Grid.
- Responsive utilities.
- Form.
- Modal.
- Dropdown.
- Navbar.
- Offcanvas.
- Tabs.
- Button.
- Alert.
- Spacing utilities.

Bootstrap không quyết định:

- Business state.
- Permission.
- API contract.
- Data source.
- Authentication.

Không sửa trực tiếp Bootstrap vendor file.

Override qua:

```text
static/css/app.css
static/css/components/
static/css/features/
```

---

# 10. CSS RULES

Global CSS chỉ chứa:

```text
design token
base typography
shared layout
small reusable utility
shared component skin
```

Feature CSS nằm gần feature:

```text
css/features/post.css
css/features/community.css
css/features/messaging.css
```

Page-only CSS:

```text
css/pages/settings.css
```

Tránh:

```text
!important
```

trừ trường hợp override third-party behavior có lý do.

Không dùng selector global mơ hồ gây leak giữa feature.

---

# 11. DESIGN TOKENS

Giá trị lặp lại phải centralize khi đủ ổn định.

Ví dụ:

```css
:root {
  --app-content-max-width: 720px;
  --app-sidebar-width: 280px;
  --app-radius: 12px;
  --app-space-sm: .5rem;
  --app-space-md: 1rem;
}
```

Không hardcode cùng một layout value ở hàng chục file.

---

# 12. JAVASCRIPT PHILOSOPHY

JavaScript dùng khi HTML server-rendered không đủ UX.

Ví dụ:

- Like/reaction without full reload.
- Infinite scroll.
- Dynamic comment/reply.
- Modal submit.
- Live notification.
- Chat.
- Preview upload.
- Client-side form UX.

Không dùng JS chỉ để render lại thứ Thymeleaf đã render tốt.

---

# 13. JAVASCRIPT MODULE BOUNDARY

Core reusable infrastructure:

```text
static/js/core/
```

Feature behavior:

```text
static/js/features/<feature>/
```

Page wiring:

```text
static/js/pages/
```

Ví dụ:

```text
core/http-client.js
features/post/post-like.js
features/post/post-composer.js
features/messaging/chat.js
pages/feed-page.js
```

Không tạo:

```text
app.js
```

5000 dòng chứa toàn bộ frontend.

---

# 14. HTTP CLIENT

Mọi AJAX/fetch đi qua shared client.

```text
Feature JS
   ↓
http-client.js
   ↓
Backend JSON API
```

`http-client.js` chịu trách nhiệm browser-side transport concern:

- JSON serialization/deserialization.
- Common headers không thuộc business.
- Request cancellation khi cần.
- Normalized transport failure.
- Credentials theo security strategy.
- Trace/request ID propagation nếu dùng.

Nó **không định nghĩa lại**:

```text
ApiResponse fields
pagination fields
HTTP status meaning
error code catalog
```

Những contract đó lấy từ `ALOUTE_SYSTEM_ARCHITECTURE.md`.

---

# 15. API CONSUMPTION RULE

Feature JavaScript không parse response theo các shape tự phát.

Sai:

```text
post.js hiểu response kiểu A
community.js hiểu kiểu B
chat.js hiểu kiểu C
```

Đúng:

```text
Tất cả consume canonical backend contract.
```

Nếu backend contract thay đổi, sửa transport/parser layer hoặc DTO usage có chủ đích; không sửa ngẫu nhiên từng feature.

---

# 16. PAGINATION UI

Frontend được phép quy định **UI behavior** của pagination, không được định nghĩa API pagination contract.

Pagination component chịu trách nhiệm:

- Previous/next UI.
- Active page.
- Disable khi không còn page.
- Preserve query/filter phù hợp.
- Accessible label.
- Loading state.

Tên query field, zero/one-based, response metadata:

```text
follow ALOUTE_SYSTEM_ARCHITECTURE.md
```

Không lặp lại chúng trong file này.

---

# 17. FORM STRUCTURE

Form có 4 state tách biệt:

```text
input state
client UX validation
server validation error
submit state
```

Client validation chỉ cải thiện UX.

Backend vẫn là authority.

Field error render gần field.

Form-level error render ở vùng chung.

Không dùng toast thay cho mọi validation error.

---

# 18. SUBMIT RULE

Khi submit async:

- Disable double submit.
- Hiển thị progress khi cần.
- Restore state khi fail.
- Không xóa UI trước khi destructive request thực sự thành công trừ khi optimistic behavior đã được thiết kế.
- Server response/error interpretation theo architecture contract.

---

# 19. ERROR UX

Frontend phân biệt:

```text
field error
form/business error
page load error
temporary transport error
unexpected error
```

Không hiển thị tất cả thành:

```text
Something went wrong
```

Không hardcode business handling dựa vào message text.

Dùng stable backend error code khi UI cần phân nhánh.

Catalog/error meaning vẫn thuộc architecture/domain, không copy vào đây.

---

# 20. LOADING / EMPTY / ERROR

List/page async phải có UX cho:

```text
loading
empty
success
error
```

Không để blank screen.

Ví dụ feed:

```text
initial loading
empty feed
loaded feed
failed load + retry
loading-more
no-more-data
```

---

# 21. DOM RULE

Ưu tiên DOM scoped theo feature/component.

Không query toàn document bằng selector generic nếu có thể scope trong container.

Dùng:

```text
data-* attributes
stable class hooks
event delegation
```

cho JS behavior.

Không dựa vào text hiển thị để xác định business identity.

Resource ID có thể nằm trong safe `data-*` attribute khi browser cần gửi action.

---

# 22. INLINE SCRIPT

Tránh JavaScript lớn trong template:

```html
<script>
  // 300 lines
</script>
```

Template chỉ nên bootstrap data nhỏ hoặc include module.

Feature logic nằm trong static JS file.

Không inject raw user content vào executable script.

---

# 23. DATA BOOTSTRAP

Nếu page cần initial JS data:

- Ưu tiên data attribute cho giá trị nhỏ.
- Có thể dùng JSON script block đã serialize an toàn cho object cần thiết.
- Không serialize cả entity graph.
- Không expose field nhạy cảm.
- Không tự nhét JWT vào JS-readable variable.

---

# 24. AUTH UI

Frontend có thể:

```text
show/hide
enable/disable
redirect UX
```

dựa trên capability/current-user info backend cung cấp.

Frontend không enforce security.

Không tự parse JWT để quyết định quyền.

Không lưu/copy global role rule riêng.

Authentication transport và JWT strategy theo:

```text
SPRING_SECURITY_CONVENTION.md
```

---

# 25. PERMISSION UI

Ưu tiên backend/ViewModel cung cấp semantic capability:

```text
canEdit
canDelete
canModerate
canReply
```

thay vì template tự viết condition phức tạp.

Ví dụ:

```html
<button th:if="${post.canDelete}">
  Delete
</button>
```

Nhưng request delete vẫn phải được backend authorize lại.

Nguồn business permission:

```text
ALOUTE_DOMAIN_SYSTEM.md
```

Cách backend enforce:

```text
SPRING_SECURITY_CONVENTION.md
```

---

# 26. WEBSOCKET CLIENT

WebSocket client dùng cho realtime feature được system architecture cho phép.

Một connection lifecycle phải có owner rõ.

Ví dụ:

```text
websocket-client.js
```

chịu trách nhiệm:

```text
connect
disconnect
reconnect
subscribe
unsubscribe
connection state
```

Feature module chịu trách nhiệm domain event của nó.

Không để mỗi message item tự mở socket.

Transport/use-case boundary theo:

```text
ALOUTE_SYSTEM_ARCHITECTURE.md
```

Security handshake/authentication theo:

```text
SPRING_SECURITY_CONVENTION.md
```

---

# 26.1. PUSH NOTIFICATION & SERVICE WORKER CLIENT (FCM)

Ngoài realtime in-app bằng WebSocket, ALOUTE hỗ trợ background Web Push khi người dùng đóng tab hoặc chuyển sang ứng dụng khác:

1. **Service Worker Root (`static/firebase-messaging-sw.js`)**:
   - Chịu trách nhiệm background push event (`messaging.onBackgroundMessage(...)`).
   - Hiển thị Web Notification popup thông qua browser Notification API.
   - Bắt sự kiện `notificationclick` để focus hoặc mở tab `/notifications` hoặc chi tiết bài viết/tin nhắn.

2. **Feature Client (`static/js/features/fcm-notification.js`)**:
   - Yêu cầu cấp quyền `Notification.requestPermission()`.
   - Lấy FCM Token qua `getToken(messaging, { vapidKey: ... })`.
   - Đăng ký token lên backend qua `POST /api/notifications/fcm-token`.
   - Lắng nghe foreground FCM push nếu socket tạm ngắt (`onMessage(messaging, ...)`).
   - Huỷ đăng ký token qua `DELETE /api/notifications/fcm-token` khi người dùng đăng xuất.

---

# 27. CHAT UI

Chat page có thể chia:

```text
ConversationList
ConversationHeader
MessageList
MessageComposer
MessageItem
```

Ở Thymeleaf, các unit có thể là fragments hoặc HTML containers + JS modules.

Load history bằng HTTP/page mechanism theo architecture.

Realtime delivery bằng WebSocket theo architecture.

Không để socket event tự mutate DOM ở 20 chỗ; tập trung xử lý tại messaging module.

---

# 28. INFINITE SCROLL

Nếu feature dùng infinite scroll:

- Chỉ một request load-more đang chạy.
- Không append duplicate item.
- Có end-of-list state.
- Có retry.
- Giữ stable resource ID.
- Cân nhắc scroll restoration.

Pagination/cursor field lấy từ canonical architecture contract.

---

# 29. OPTIMISTIC UI

Chỉ dùng khi:

- Action dễ rollback.
- Failure rate thấp.
- UX được lợi rõ.
- Conflict semantics rõ.

Phù hợp có thể là:

```text
like/reaction
```

Không mặc định optimistic destructive action.

Fail phải rollback/reconcile.

---

# 30. MEDIA UI

Browser chịu trách nhiệm UX:

- Preview.
- Progress.
- File type/size pre-check cho UX.
- Remove preview.
- Upload state.

Server vẫn validate thật.

Firebase/storage implementation không thuộc frontend rule.

Frontend chỉ dùng upload flow mà backend/system architecture expose.

---

# 31. RESPONSIVE RULE

ALOUTE phải usable ở:

```text
mobile
tablet
desktop
```

Ưu tiên Bootstrap grid/flex utilities trước khi custom layout.

Không hardcode desktop width cho core social feed.

Feed content phải có max-width hợp lý và co giãn.

Sidebar có behavior responsive rõ.

---

# 32. ACCESSIBILITY

Dùng semantic HTML:

```text
button cho action
a cho navigation
label cho input
nav cho navigation region
main cho main content
```

Interactive control phải keyboard usable.

Modal phải hỗ trợ focus/escape theo Bootstrap behavior.

Icon-only button phải có accessible name.

Image cần `alt` phù hợp.

Không dùng màu là tín hiệu duy nhất cho trạng thái.

---

# 33. XSS / HTML RENDERING

Không render raw user HTML nếu không có sanitization policy.

Ưu tiên escaped Thymeleaf text:

```text
th:text
```

Cẩn thận với unescaped HTML.

Không dùng user-provided content làm executable JS/template.

Security sanitization implementation phải ở backend/shared boundary phù hợp; frontend không tự tin dữ liệu chỉ vì đã render một lần.

---

# 34. STATIC ASSET RULE

Không sửa file trong:

```text
static/vendor/
```

Custom code nằm ngoài vendor.

Asset naming phải có meaning.

Không commit build artifact/cache không cần thiết.

Image lớn phải resize/compress phù hợp với UI use case.

---

# 35. TEMPLATE NAMING

Page:

```text
index.html
detail.html
create.html
edit.html
```

chỉ dùng khi đã nằm trong folder feature rõ.

Ví dụ:

```text
community/detail.html
post/detail.html
settings/profile.html
```

Fragment có thể prefix `_`:

```text
_post-card.html
_member-row.html
_message-item.html
```

Chọn convention này và giữ thống nhất.

---

# 36. CSS CLASS NAMING

Không phụ thuộc hoàn toàn vào Bootstrap class cho semantic hook JS.

Có thể có:

```text
post-card
post-card__header
message-list
conversation-item
```

JS hook ưu tiên `data-*` hoặc class riêng có mục đích rõ.

Không dùng class kiểu:

```text
abc
box2
item-x
```

---

# 37. JAVASCRIPT NAMING

Function là động từ:

```text
loadMorePosts
submitPost
toggleReaction
connectChatSocket
renderMessage
```

Boolean:

```text
isLoading
hasNext
canDelete
isConnected
```

Không dùng:

```text
data2
flag1
doThing
temp
```

ngoài scope rất nhỏ.

---

# 38. PAGE-SPECIFIC SCRIPT

Mỗi page chỉ load JS cần thiết khi có thể.

Không bắt toàn bộ website load chat/infinite-scroll/admin logic ở mọi page.

Shared bundle chỉ chứa core nhỏ.

Feature bundle/module chứa behavior riêng.

---

# 39. PERFORMANCE

Ưu tiên:

- Server render initial content.
- Lazy load media.
- Pagination/infinite scroll cho list lớn.
- Không append hàng nghìn DOM node.
- Không fetch duplicate.
- Debounce search input.
- Cancel stale request khi search nhanh.
- Không load library lớn chỉ để dùng một utility nhỏ.

Measure trước khi optimize sâu.

---

# 40. SEARCH UI

Search UX có thể giữ:

```text
query
filter
sort
page/cursor state
```

Nếu trạng thái cần share/bookmark, đưa vào URL theo page design.

Không đưa toàn bộ search state vào hidden JS global singleton.

Backend search semantics thuộc domain/system, không được frontend tự định nghĩa.

---

# 41. URL AS UI STATE

Các state cần:

```text
refresh
back/forward
bookmark
share
```

nên cân nhắc nằm trong URL:

```text
search
filter
sort
tab
page
```

Không nhét object business lớn vào query string.

---

# 42. FLASH MESSAGE VS AJAX MESSAGE

Full page redirect:

```text
RedirectAttributes / flash message
```

có thể dùng cho success/error UX.

AJAX:

```text
inline state / toast / modal feedback
```

theo context.

Không dùng toast cho mọi field validation.

---

# 43. FRONTEND TESTING

Tối thiểu test/verify:

```text
critical Thymeleaf rendering
important form validation presentation
permission-based visibility UX
AJAX success/failure state
infinite scroll duplication
chat realtime behavior
responsive core pages
keyboard/accessibility critical controls
```

Backend security vẫn phải có test riêng; UI hide/show test không thay thế security test.

---

# 44. FRONTEND REVIEW CHECKLIST

- Template có query DB hoặc business logic không?
- Entity có leak trực tiếp ra template/API không?
- Layout/fragment có bị copy-paste không?
- JavaScript có nằm đúng feature không?
- Có God JS file không?
- AJAX có đi qua shared HTTP client không?
- Có tự invent response/error/pagination contract không?
- Có parse JWT ở browser không?
- Có tin UI permission như security boundary không?
- Form có loading/double-submit handling không?
- List có loading/empty/error state không?
- WebSocket có một lifecycle owner rõ không?
- Có XSS risk từ raw HTML không?
- UI responsive và keyboard usable không?
- Có vendor file bị sửa trực tiếp không?

---

# 45. CORE RULES

1. Thymeleaf là view engine, không phải business layer.
2. Bootstrap là UI toolkit, không phải architecture.
3. JavaScript dùng progressive enhancement.
4. Page render từ ViewModel, không mặc định dùng Entity.
5. AJAX đi qua shared HTTP client.
6. Frontend không định nghĩa API contract.
7. Frontend không định nghĩa HTTP status.
8. Frontend không định nghĩa pagination fields.
9. Frontend không định nghĩa JWT/security implementation.
10. Frontend permission chỉ phục vụ UX.
11. WebSocket client có lifecycle owner rõ.
12. Không tạo SPA ngầm trong Thymeleaf.
13. Không tạo God template/God JS.
14. Server vẫn là business/security authority.
15. Mọi cross-cutting contract phải reference file canonical, không copy.

---

# 46. ADMIN DASHBOARD & INLINE MODERATION FRONTEND CONVENTIONS

## 46.1. Cấu trúc Backoffice Template
1. **Layout chuyên biệt:**
   - `templates/layouts/admin.html`: Giao diện quản trị riêng biệt với Sidebar cố định, Topbar điều hướng, Breadcrumbs, badge hiển thị số lượng Report đang chờ xử lý (`pendingReports`).
2. **Hệ thống màn hình Admin:**
   - `templates/admin/overview.html`: Thẻ KPI nhanh (`asOf`), biểu đồ tăng trưởng bài viết/người dùng, danh sách các báo cáo vi phạm cần duyệt gấp.
   - `templates/admin/users/list.html` & `detail.html`: Bảng tra cứu tài khoản kèm bộ lọc trạng thái, chi tiết hồ sơ quản trị, lịch sử vi phạm.
   - `templates/admin/posts/list.html` & `detail.html`: Bảng kiểm soát bài viết, xem trước nội dung, modal gỡ bài (`POST_REMOVE`) và khôi phục (`POST_RESTORE`).
   - `templates/admin/reports/list.html` & `detail.html`: Hàng đợi duyệt báo cáo phân theo tab (`PENDING`, `REVIEWED`, `RESOLVED`, `REJECTED`), chi tiết vi phạm kèm đối tượng liên quan.
   - `templates/admin/moderation-actions/list.html`: Nhật ký kiểm duyệt (Audit Log) chỉ đọc, không có nút xóa/sửa.

## 46.2. Inline Admin Controls trên Giao diện Người dùng
Khi Quản trị viên (`SYSTEM_ADMIN`) lướt web như người dùng thông thường, giao diện hiển thị thêm các công cụ đặc quyền thông qua `sec:authorize="hasRole('SYSTEM_ADMIN')"`:
1. **Trên thẻ bài viết (`_post-card.html`) & Trang chi tiết (`post/detail.html`):**
   - Menu 3 chấm (`...`) bổ sung khối thao tác quản trị màu đỏ:
     - 🗑️ **Gỡ bài viết này (Admin)**: Mở modal xác nhận kèm ô nhập lý do gỡ bài.
     - 🚫 **Khóa tài khoản tác giả (Ban User)**: Mở modal đình chỉ tài khoản tác giả.
2. **Trên trang cá nhân (`user/profile.html`):**
   - Hiển thị **Admin Toolbar** phía trên đầu hồ sơ: Nút Khóa/Mở khóa tài khoản (`Ban/Unban`), Nút Hạn chế tương tác (cấm đăng bài/bình luận có thời hạn).
3. **Quy tắc UX bắt buộc:**
   - **Không Optimistic Update:** Đối với mọi thao tác gỡ bài, xóa nội dung, khóa tài khoản, UI **bắt buộc** phải chờ phản hồi HTTP `2xx` từ máy chủ mới tiến hành xóa phần tử khỏi DOM hoặc cập nhật trạng thái.
   - **Xác nhận & Lý do:** Mọi hành động can thiệp phải hiển thị Confirmation Modal, bắt buộc nhập lý do xử lý (`reasonDetail`, tối thiểu 10 ký tự).
   - **Chống Submit trùng:** Vô hiệu hóa nút bấm và hiển thị trạng thái đang xử lý (spinner) trong khi request đang in-flight.

