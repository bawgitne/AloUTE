package com.aloute.dto.response;

import com.aloute.entity.CommunityRole;
import java.time.Instant;

public record CommunityMembershipResponse(Long communityId, UserSummary user,
                                          CommunityRole role, Instant joinedAt) {}
