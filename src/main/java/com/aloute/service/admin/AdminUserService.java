package com.aloute.service.admin;

import com.aloute.dto.response.admin.AdminUserDetailResponse;
import com.aloute.dto.response.admin.AdminUserSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {

    Page<AdminUserSummaryResponse> getUsers(String query, String role, Boolean active, Pageable pageable);

    AdminUserDetailResponse getUserDetail(Long userId);
}
