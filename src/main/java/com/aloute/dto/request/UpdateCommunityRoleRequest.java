package com.aloute.dto.request;

import com.aloute.entity.CommunityRole;
import jakarta.validation.constraints.NotNull;

public record UpdateCommunityRoleRequest(@NotNull CommunityRole role) {}
