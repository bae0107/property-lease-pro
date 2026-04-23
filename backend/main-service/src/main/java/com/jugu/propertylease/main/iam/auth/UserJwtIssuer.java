package com.jugu.propertylease.main.iam.auth;

import com.jugu.propertylease.security.constants.SecurityConstants;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * User JWT 签发器（仅用于外部客户端登录态 token）。
 */
@Component
public class UserJwtIssuer {

  public String issue(Long userId,
      String username,
      List<String> permissions,
      String secret,
      int expirationSeconds) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    long nowMillis = System.currentTimeMillis();

    var builder = Jwts.builder()
        .subject(username)
        .issuedAt(new Date(nowMillis))
        .expiration(new Date(nowMillis + (long) expirationSeconds * 1000))
        .claim(SecurityConstants.CLAIM_USER_ID, userId);

    if (permissions != null && !permissions.isEmpty()) {
      builder.claim(SecurityConstants.CLAIM_PERMISSIONS, permissions);
    }

    return builder.signWith(key).compact();
  }
}
