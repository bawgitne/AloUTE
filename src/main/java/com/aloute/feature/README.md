# HƯỚNG DẪN KIẾN TRÚC PACKAGE-BY-FEATURE

Thư mục `feature/` tổ chức code backend theo từng module nghiệp vụ (Package-by-Feature) theo quy định tại `skills/backend_rule.md`.

## 1. Cấu trúc chuẩn của một Feature

Khi bạn nhận một module (ví dụ: `post`, `user`, `community`, `messaging`), hãy tuân thủ cấu trúc sau:

```text
feature/<ten-feature>/
├── controller/
│   ├── api/            # @RestController trả JSON (/api/...)
│   └── page/           # @Controller trả Thymeleaf view
├── dto/
│   ├── request/        # DTO nhận từ client (@Valid, KHÔNG nhận authorId)
│   └── response/       # DTO trả về cho client / ViewModel cho Thymeleaf
├── entity/             # JPA Entities kế thừa BaseEntity
├── repository/         # Spring Data JPA Repositories
├── service/            # Business Service Interface & Impl (@Transactional)
└── mapper/             # Chuyển đổi Entity <-> DTO
```

## 2. Quy tắc luồng dữ liệu (Dependency Direction)

```text
Controller  →  Service  →  Repository
```

- **Controller**: Chỉ xử lý request HTTP/View, validate DTO, lấy authenticated user qua `@CurrentUser` và gọi Service. KHÔNG gọi Repository trực tiếp.
- **Service**: Chứa toàn bộ business rules, kiểm tra quyền hạn (WHO CAN DO WHAT theo `skills/domain_system.md`), phân định `@Transactional`.
- **Repository**: Chỉ truy vấn DB, KHÔNG điều phối business.
- **Entity**: KHÔNG trả trực tiếp ra API, luôn map sang DTO.
- **ApiResponse**: API Controller luôn bọc response trong `ApiResponse<T>` chuẩn từ `com.aloute.common.api.ApiResponse`.
