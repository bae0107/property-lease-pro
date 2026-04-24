package com.jugu.propertylease.main.iam.auth;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.iam.repo.IamRefreshTokenRepository;
import com.jugu.propertylease.main.iam.repo.model.RefreshTokenEntity;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 持久化服务（DB 版，token 明文仅返回给客户端，库内仅存 hash）。
 */
@Service
public class RefreshTokenService {

  private static final long REFRESH_TOKEN_TTL_SECONDS = 7L * 24 * 3600;

  private final IamRefreshTokenRepository refreshTokenRepository;

  public RefreshTokenService(IamRefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional
  public String create(Long userId, String username, String userType) {
    String rawToken = UUID.randomUUID().toString().replace("-", "");
    String hash = sha256(rawToken);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    insertToken(hash, userId, username, userType, now);

    return rawToken;
  }

  @Transactional(readOnly = true)
  public TokenPayload validate(String rawToken) {
    String hash = sha256(rawToken);
    IamRefreshToken token = refreshTokenRepository.findByTokenHash(hash)
        .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_INVALID",
            "refresh token 无效"));

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (token.getRevokedAt() != null || token.getReplacedByTokenHash() != null) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_REVOKED",
          "refresh token 已失效");
    }

    if (token.getExpiresAt().isBefore(now)) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_EXPIRED",
          "refresh token 已过期");
    }

    return new TokenPayload(token.getUserId(), token.getUsernameSnapshot(), token.getUserTypeSnapshot(), hash);
  }

  @Transactional
  public String rotate(String oldRawToken, Long userId, String username, String userType) {
    String oldHash = sha256(oldRawToken);
    String newRawToken = UUID.randomUUID().toString().replace("-", "");
    String newHash = sha256(newRawToken);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    int updated = refreshTokenRepository.revokeAndReplace(oldHash, newHash, now);

    if (updated == 0) {
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "IAM_AUTH_REFRESH_TOKEN_INVALID",
          "refresh token 无效");
    }

    insertToken(newHash, userId, username, userType, now);

    return newRawToken;
  }

  @Transactional
  public void revoke(String rawToken) {
    String hash = sha256(rawToken);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    refreshTokenRepository.revoke(hash, now);
  }

  private void insertToken(String hash, Long userId, String username, String userType, OffsetDateTime now) {
    refreshTokenRepository.insert(new RefreshTokenEntity(
        hash,
        userId,
        username,
        userType,
        now,
        now.plusSeconds(REFRESH_TOKEN_TTL_SECONDS),
        null,
        null,
        now,
        now));
  }

  private String sha256(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot hash refresh token", e);
    }
  }

  public record TokenPayload(Long userId, String username, String userType, String tokenHash) {
  }
}
