package com.jugu.propertylease.security.token;

import com.jugu.propertylease.security.constants.SecurityConstants;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;

/**
 * Service JWT 生成器（纯 Java，无 Spring 依赖），可直接 new 实例单测。
 *
 * <p>每次调用重新生成，不缓存。
 * userId=null 和 permissions=空列表时，对应 Claims key 不写入（而非写 null/空）， 与 {@link JwtTokenParser}
 * 的解析语义对称：key 不存在 ↔ 系统调用/无权限。
 */
public class ServiceTokenGenerator {

  /**
   * 生成 Service JWT。
   *
   * @param serviceName 当前服务名，写入 sub 字段
   * @param userId      当前用户 ID，为 null 时表示系统调用（不写入 Claims）
   * @param permissions 当前用户权限列表，为空时不写入 Claims
   * @param secret      HS256 密钥原文
   * @param expSeconds  有效期（秒）
   * @return compact JWT 字符串
   */
  public String generate(String serviceName,
      Long userId,
      List<String> permissions,
      String secret,
      int expSeconds) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    long nowMillis = System.currentTimeMillis();

    var builder = Jwts.builder()
        .subject(serviceName)
        .issuedAt(new Date(nowMillis))
        .expiration(new Date(nowMillis + (long) expSeconds * 1000));

    if (userId != null) {
      builder.claim(SecurityConstants.CLAIM_USER_ID, userId);
    }
    if (permissions != null && !permissions.isEmpty()) {
      builder.claim(SecurityConstants.CLAIM_PERMISSIONS, permissions);
    }

    return builder.signWith(key).compact();
  }
}
