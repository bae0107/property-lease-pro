package com.jugu.propertylease.security.token;

import com.jugu.propertylease.security.constants.SecurityConstants;
import com.jugu.propertylease.security.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;

/**
 * JWT 解析与校验（纯 Java，无 Spring 依赖），可直接 new 实例单测。
 *
 * <p>secret 每次作为参数传入，不在构造器持有，便于测试多密钥场景。
 */
public class JwtTokenParser {

  /**
   * 解析并验证 User JWT。
   *
   * @param token  Bearer token 字符串（不含 "Bearer " 前缀）
   * @param secret HS256 密钥原文
   * @return 解析后的 {@link UserTokenPayload}
   * @throws InvalidTokenException Token 过期、签名不匹配、格式错误
   */
  public UserTokenPayload parseUserToken(String token, String secret) {
    Claims claims = parseClaims(token, secret);
    String username = claims.getSubject();
    Long userId = extractUserId(claims);
    List<String> permissions = extractPermissions(claims);
    long exp = claims.getExpiration().getTime() / 1000;
    return new UserTokenPayload(username, userId, permissions, exp);
  }

  /**
   * 解析并验证 Service JWT。
   *
   * @param token  X-Service-Token 字符串
   * @param secret HS256 密钥原文
   * @return 解析后的 {@link ServiceTokenPayload}
   * @throws InvalidTokenException Token 过期、签名不匹配、格式错误
   */
  public ServiceTokenPayload parseServiceToken(String token, String secret) {
    Claims claims = parseClaims(token, secret);
    String serviceName = claims.getSubject();
    Long userId = extractUserId(claims);
    List<String> permissions = extractPermissions(claims);
    long exp = claims.getExpiration().getTime() / 1000;
    return new ServiceTokenPayload(serviceName, userId, permissions, exp);
  }

  // ===== Private helpers =====

  private Claims parseClaims(String token, String secret) {
    SecretKey key = buildKey(secret);
    try {
      return Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException e) {
      throw new InvalidTokenException(InvalidTokenException.TOKEN_EXPIRED,
          "Token has expired", e);
    } catch (SignatureException e) {
      throw new InvalidTokenException(InvalidTokenException.TOKEN_INVALID,
          "Token signature validation failed", e);
    } catch (MalformedJwtException e) {
      throw new InvalidTokenException(InvalidTokenException.TOKEN_MALFORMED,
          "Token is malformed", e);
    } catch (JwtException e) {
      throw new InvalidTokenException(InvalidTokenException.TOKEN_MALFORMED,
          "Token parsing failed: " + e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      // jjwt 在 token 为 null 或空字符串时抛出此异常（早于 JwtException 层）
      throw new InvalidTokenException(InvalidTokenException.TOKEN_MALFORMED,
          "Token is null or empty", e);
    }
  }

  /**
   * 安全提取 userId：jjwt 将数字反序列化为 Integer 或 Long，统一转为 Long。
   */
  private Long extractUserId(Claims claims) {
    Object raw = claims.get(SecurityConstants.CLAIM_USER_ID);
    if (raw == null) {
      return null;
    }
    return ((Number) raw).longValue();
  }

  /**
   * 提取 permissions 列表，key 不存在时返回空列表（不抛异常）。
   */
  @SuppressWarnings("unchecked")
  private List<String> extractPermissions(Claims claims) {
    Object raw = claims.get(SecurityConstants.CLAIM_PERMISSIONS);
    if (raw == null) {
      return Collections.emptyList();
    }
    if (raw instanceof List<?> list) {
      return list.stream()
          .map(Object::toString)
          .collect(Collectors.toUnmodifiableList());
    }
    return Collections.emptyList();
  }

  private SecretKey buildKey(String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
}
