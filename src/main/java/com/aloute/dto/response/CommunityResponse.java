package com.aloute.dto.response;

import com.aloute.entity.CommunityRole;

public record CommunityResponse(Long id, Long ownerId, String name, String slug,
                                String description, String avatarUrl, String coverUrl,
                                boolean isPrivate, long memberCount, long postCount,
                                CommunityRole currentUserRole, Boolean isOwner) {}
