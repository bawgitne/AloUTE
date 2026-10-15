package com.aloute.service.admin;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class AdminSortHelper {

    public static final Set<String> POST_SORT_FIELDS = Set.of("createdAt", "id", "updatedAt");
    public static final Set<String> USER_SORT_FIELDS = Set.of("createdAt", "id", "username", "email", "updatedAt");
    public static final Set<String> REPORT_SORT_FIELDS = Set.of("createdAt", "id", "status", "reviewedAt");
    public static final Set<String> AUDIT_SORT_FIELDS = Set.of("createdAt", "id", "actionType");

    private AdminSortHelper() {}

    public static Pageable createPageable(int page, int size, String sort, Set<String> allowedFields, String defaultField) {
        if (page < 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Page index must not be less than zero");
        }
        if (size < 1 || size > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Page size must be between 1 and 100");
        }
        Sort validatedSort = parseSort(sort, allowedFields, defaultField);
        return PageRequest.of(page, size, validatedSort);
    }

    public static Sort parseSort(String sort, Set<String> allowedFields, String defaultField) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, defaultField);
        }
        String[] parts = sort.split(",", -1);
        if (parts.length < 1 || parts.length > 2) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid sort format. Expected format: property[,asc|desc]");
        }
        String property = parts[0].trim();
        if (property.isEmpty() || !allowedFields.contains(property)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid sort property: '" + property + "'. Allowed: " + allowedFields);
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            String dirStr = parts[1].trim();
            if ("asc".equalsIgnoreCase(dirStr)) {
                direction = Sort.Direction.ASC;
            } else if ("desc".equalsIgnoreCase(dirStr)) {
                direction = Sort.Direction.DESC;
            } else {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid sort direction: '" + dirStr + "'. Allowed: asc, desc");
            }
        }
        return Sort.by(direction, property);
    }
}
