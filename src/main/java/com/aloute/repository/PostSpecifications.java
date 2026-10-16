package com.aloute.repository;

import com.aloute.entity.Bookmark;
import com.aloute.entity.Community;
import com.aloute.entity.Post;
import com.aloute.entity.PostVisibility;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Canonical Post & Bookmark query specifications enforcing visibility and privacy invariants.
 * Owned by Ngọc (Repository, Entity, Post, Community) and Khôi (Security/Authorization).
 */
public final class PostSpecifications {

    private PostSpecifications() {}

    public static Specification<Post> visibleTo(Long viewerId,
                                                Collection<Long> blockedUserIds,
                                                Collection<Long> followingUserIds,
                                                Collection<Long> userCommunityIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Post must not be soft-deleted
            predicates.add(cb.isNull(root.get("deletedAt")));

            // 2. Block check: post author must not be blocked by viewer, nor author has blocked viewer
            if (blockedUserIds != null && !blockedUserIds.isEmpty()) {
                predicates.add(cb.not(root.get("user").get("id").in(blockedUserIds)));
            }

            // 3. Community check (using explicit LEFT JOIN to prevent implicit inner-join dropping standalone posts):
            // Either post has no community, or community is not private,
            // or (viewer is member of that private community)
            Join<Post, Community> communityJoin = root.join("community", JoinType.LEFT);
            Predicate noCommunity = cb.isNull(root.get("community"));
            Predicate publicCommunity = cb.isFalse(communityJoin.get("privateCommunity"));
            Predicate communityAllowed = cb.or(noCommunity, publicCommunity);
            if (userCommunityIds != null && !userCommunityIds.isEmpty()) {
                Predicate memberOfPrivateCommunity = cb.and(
                        cb.isTrue(communityJoin.get("privateCommunity")),
                        communityJoin.get("id").in(userCommunityIds)
                );
                communityAllowed = cb.or(communityAllowed, memberOfPrivateCommunity);
            }
            predicates.add(communityAllowed);

            // 4. Post visibility & author privacy:
            if (viewerId != null) {
                Predicate isAuthor = cb.equal(root.get("user").get("id"), viewerId);

                // Author account privacy:
                Predicate authorPublic = cb.isFalse(root.get("user").get("privateAccount"));
                Predicate followingAuthor = (followingUserIds != null && !followingUserIds.isEmpty())
                        ? root.get("user").get("id").in(followingUserIds)
                        : cb.disjunction();
                Predicate authorPrivacySatisfied = cb.or(authorPublic, followingAuthor);

                // Post visibility enum:
                Predicate isPublicVisibility = cb.equal(root.get("visibility"), PostVisibility.PUBLIC);
                Predicate isFollowersVisibility = cb.and(
                        cb.equal(root.get("visibility"), PostVisibility.FOLLOWERS),
                        followingAuthor
                );

                // Canonical: No durable Mention entity exists in schema.
                // Non-owner MENTIONED reads fail closed under canonical policy.
                Predicate nonAuthorAllowed = cb.and(
                        authorPrivacySatisfied,
                        cb.or(isPublicVisibility, isFollowersVisibility)
                );

                predicates.add(cb.or(isAuthor, nonAuthorAllowed));
            } else {
                // Guest viewer:
                predicates.add(cb.equal(root.get("visibility"), PostVisibility.PUBLIC));
                predicates.add(cb.isFalse(root.get("user").get("privateAccount")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Bookmark> bookmarkVisibleForUser(Long userId,
                                                                 Collection<Long> blockedUserIds,
                                                                 Collection<Long> followingUserIds,
                                                                 Collection<Long> userCommunityIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Bookmark must belong to user
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            // Join the bookmarked post
            Join<Bookmark, Post> postJoin = root.join("post", JoinType.INNER);

            // 1. Post must not be soft-deleted
            predicates.add(cb.isNull(postJoin.get("deletedAt")));

            // 2. Block check: post author must not be in blockedUserIds
            if (blockedUserIds != null && !blockedUserIds.isEmpty()) {
                predicates.add(cb.not(postJoin.get("user").get("id").in(blockedUserIds)));
            }

            // 3. Community check (using explicit LEFT JOIN on community)
            Join<Post, Community> communityJoin = postJoin.join("community", JoinType.LEFT);
            Predicate noCommunity = cb.isNull(postJoin.get("community"));
            Predicate publicCommunity = cb.isFalse(communityJoin.get("privateCommunity"));
            Predicate communityAllowed = cb.or(noCommunity, publicCommunity);
            if (userCommunityIds != null && !userCommunityIds.isEmpty()) {
                Predicate memberOfPrivateCommunity = cb.and(
                        cb.isTrue(communityJoin.get("privateCommunity")),
                        communityJoin.get("id").in(userCommunityIds)
                );
                communityAllowed = cb.or(communityAllowed, memberOfPrivateCommunity);
            }
            predicates.add(communityAllowed);

            // 4. Post visibility & author privacy:
            Predicate isAuthor = cb.equal(postJoin.get("user").get("id"), userId);

            Predicate authorPublic = cb.isFalse(postJoin.get("user").get("privateAccount"));
            Predicate followingAuthor = (followingUserIds != null && !followingUserIds.isEmpty())
                    ? postJoin.get("user").get("id").in(followingUserIds)
                    : cb.disjunction();
            Predicate authorPrivacySatisfied = cb.or(authorPublic, followingAuthor);

            Predicate isPublicVisibility = cb.equal(postJoin.get("visibility"), PostVisibility.PUBLIC);
            Predicate isFollowersVisibility = cb.and(
                    cb.equal(postJoin.get("visibility"), PostVisibility.FOLLOWERS),
                    followingAuthor
            );

            // Non-owner MENTIONED reads fail closed
            Predicate nonAuthorAllowed = cb.and(
                    authorPrivacySatisfied,
                    cb.or(isPublicVisibility, isFollowersVisibility)
            );

            predicates.add(cb.or(isAuthor, nonAuthorAllowed));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
