package com.jugu.propertylease.main.iam.repo;

import com.jugu.propertylease.main.jooq.tables.pojos.IamRefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface IamRefreshTokenRepository {

  void insert(String tokenHash, Long userId, String usernameSnapshot, String userTypeSnapshot,
      OffsetDateTime issuedAt, OffsetDateTime expiresAt, OffsetDateTime revokedAt,
      String replacedByTokenHash, OffsetDateTime createdAt, OffsetDateTime updatedAt);

  Optional<IamRefreshToken> findByTokenHash(String tokenHash);

  int revokeAndReplace(String tokenHash, String replacedByTokenHash, OffsetDateTime now);

  void revoke(String tokenHash, OffsetDateTime now);
}
