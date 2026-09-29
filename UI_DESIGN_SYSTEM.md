# AloUTE UI Design System

## Design Concept

AloUTE uses the direction **Modern University Social Network**.

The interface represents a digital campus community: professional, friendly, and student-oriented. The design should support sharing information, connecting students, and viewing community activity through a clear information hierarchy.

Core principles:

- Clean academic identity.
- Social interaction layout without copying commercial social networks.
- Modular components suitable for Thymeleaf + Bootstrap implementation.
- Prioritize readability and user actions over visual effects.

## Layout Rules

Main desktop layout:

```
Navbar
-----------------------------
Left Panel | Feed Area | Right Panel
Profile    | Posts     | Community
Navigation | Activity | Information
```

Components:

- Navbar: global navigation, search, account actions.
- Left panel: user profile summary and navigation shortcuts.
- Center feed: primary activity stream and content cards.
- Right panel: community information, suggestions, and supporting content.

## Color Direction

Primary direction:

- Dark professional background.
- Neutral surfaces for cards and content areas.
- Accent color for important actions and university identity.

Usage rules:

- Maintain strong contrast between text and background.
- Use accent colors only for actions, states, and highlights.
- Avoid excessive gradients or decorative effects.

## Typography

Rules:

- Use clear sans-serif fonts.
- Prioritize readable body text.
- Use consistent heading hierarchy.
- Keep text density suitable for student information consumption.

## Component Rules

Reusable components:

- Navigation bar.
- User profile card.
- Post card.
- Comment/interaction area.
- Information card.
- Form card.
- Modal/dialog when required.

Component principles:

- Consistent spacing.
- Clear primary action.
- Minimal visual complexity.
- Reusable with Bootstrap components.

## Responsive Principles

Desktop:

- Three-column social layout.

Tablet:

- Reduce side panels.
- Keep feed as the main focus.

Mobile:

- Single-column content flow.
- Collapse navigation.
- Preserve important actions.

## Accessibility Principles

- Maintain readable color contrast.
- Use meaningful labels for forms and buttons.
- Support keyboard navigation where applicable.
- Do not rely only on color to communicate status.
- Keep interaction states visible.


## Word Week 1 coverage and evidence — 28/09/2026

Owner: **Phạm Minh Quân**. Source: [Word hiện hành](Nhom13_KeHoachCodeTest.docx), Week 1: **21/09/2026 – 26/09/2026**.
Bộ wireframe vector 10 màn hình chuẩn theo định hướng **Dark Professional University Social Network** đã được thiết lập đầy đủ tại [`docs/ui/wireframes/`](docs/ui/wireframes/README.md).

| Word screen area | Design evidence (Week 1 Wireframes) | Implementation & Template status |
|---|---|---|
| Login/Register | [`01-login.svg`](docs/ui/wireframes/01-login.svg), [`02-register.svg`](docs/ui/wireframes/02-register.svg) (Dark theme auth cards, validation, single-account notice) | Static `auth/login.html` và `auth/register.html` đã có; runtime auth pending backend JWT |
| Home Feed | [`03-home-feed.svg`](docs/ui/wireframes/03-home-feed.svg) (3 cột chuẩn: Mini-profile/Nav, Composer, Media post, Feed states, Campus info) | Static `feed/index.html` đã có; dynamic feed ranking pending backend contract |
| Profile | [`04-profile.svg`](docs/ui/wireframes/04-profile.svg) (Cover, Avatar, Bio, Follower stats, điều khiển thực tế `Follow`/`Message` & `Edit Profile`, tabs) | Static `user/profile.html` đã có; mutation/privacy pending backend |
| Create/View Post | [`05-create-post.svg`](docs/ui/wireframes/05-create-post.svg), [`06-post-detail.svg`](docs/ui/wireframes/06-post-detail.svg) (Composer modal, topic, media dropzone, poll, owner edit/delete & report, nested replies) | Static `post/create.html` và `post/detail.html` đã có; post mutation pending backend |
| Search | [`07-search.svg`](docs/ui/wireframes/07-search.svg) (Thanh tìm kiếm, 4 tabs People/Posts/Communities/Topics, highlight từ khóa, empty state card) | Navbar có affordance input; template kết quả tìm kiếm đầy đủ thuộc phạm vi Week 3 Q3-02 |
| Community | [`08-community.svg`](docs/ui/wireframes/08-community.svg) (Cover, Badge, stats, nút Join/Leave, role Member/Moderator/Admin, filtered feed) | Khung giao diện thông tin hiện có; template màn hình Community riêng thuộc Week 3 Q3-02 |
| Messaging | [`09-messaging.svg`](docs/ui/wireframes/09-messaging.svg) (Layout 2 cột 1-1, tìm kiếm hội thoại, danh sách chat, active chat history, composer) | Template chức năng thuộc phạm vi Week 3 Q3-03; chờ STOMP/HTTP handoff từ Bằng |
| Notification | [`10-notification.svg`](docs/ui/wireframes/10-notification.svg) (Danh sách 4 loại Like/Reply/Follow/Community, read/unread states, mark all as read, empty state) | Template chức năng thuộc phạm vi Week 3 Q3-03; chờ STOMP/HTTP handoff từ Bằng |

Template paths resolve under [src/main/resources/templates](src/main/resources/templates); exact file inventory is in [FE_INTEGRATION_PLAN.md](FE_INTEGRATION_PLAN.md). Toàn bộ 10 wireframe SVG đã cung cấp đầy đủ bằng chứng thiết kế (Design Evidence) theo đúng ngôn ngữ Dark Theme (`#0B0F19` canvas, `#131B2A` card, `#2563EB` accent).

**Ranh giới trạng thái (Evidence Distinction)**:
- *Wireframe / Mockup Artifact*: Đã có 10/10 file SVG tại `docs/ui/wireframes/` làm bằng chứng thiết kế Tuần 1.
- *Production Frontend*: 6 trang tĩnh cơ bản thuộc phạm vi Tuần 2; các trang Search/Community/Messaging/Notification thuộc phạm vi tích hợp Tuần 3.
- *Backend & Runtime*: Chờ bàn giao contract và implementation từ các thành viên phụ trách backend (Khôi, Ngọc, Bằng). Wireframe tồn tại **không** đồng nghĩa với tính năng đã được hiện thực hóa ở runtime.
