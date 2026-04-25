package com.jugu.propertylease.main.iam.repo.model;

import java.time.OffsetDateTime;

public record RefreshTokenEntity(
    String tokenHash,
    Long userId,
    String usernameSnapshot,
    String userTypeSnapshot,
    OffsetDateTime issuedAt,
    OffsetDateTime expiresAt,
    OffsetDateTime revokedAt,
    String replacedByTokenHash,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
