package com.jugu.propertylease.security.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.jugu.propertylease.security.constants.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceTokenGeneratorTest {

  private static final String SECRET = "test-secret-key-at-least-32-bytes!!";

  private ServiceTokenGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new ServiceTokenGenerator();
  }

  @Test
  void generate_withUserContext_producesValidJwt() {
    String token = generator.generate("gateway", 42L, List.of("order:read"), SECRET, 300);

    assertThat(token).isNotBlank();
    Claims claims = parseClaims(token);
    assertThat(claims.getSubject()).isEqualTo("gateway");
    assertThat(((Number) claims.get(SecurityConstants.CLAIM_USER_ID)).longValue()).isEqualTo(42L);
    assertThat(claims.get(SecurityConstants.CLAIM_PERMISSIONS)).isNotNull();
  }

  @Test
  void generate_withNullUserId_omitsUserIdClaim() {
    String token = generator.generate("billing-service", null, List.of(), SECRET, 300);

    Claims claims = parseClaims(token);
    assertThat(claims.getSubject()).isEqualTo("billing-service");
    // userId claim must be absent (not null), per spec: key不存在 ↔ 系统调用
    assertThat(claims.containsKey(SecurityConstants.CLAIM_USER_ID)).isFalse();
  }

  @Test
  void generate_withEmptyPermissions_omitsPermissionsClaim() {
    String token = generator.generate("gateway", 1L, List.of(), SECRET, 300);

    Claims claims = parseClaims(token);
    assertThat(claims.containsKey(SecurityConstants.CLAIM_PERMISSIONS)).isFalse();
  }

  @Test
  void generate_withNullPermissions_omitsPermissionsClaim() {
    String token = generator.generate("gateway", 1L, null, SECRET, 300);

    Claims claims = parseClaims(token);
    assertThat(claims.containsKey(SecurityConstants.CLAIM_PERMISSIONS)).isFalse();
  }

  @Test
  void generate_expiration_isWithinExpectedRange() {
    long before = System.currentTimeMillis();
    String token = generator.generate("gateway", 1L, List.of(), SECRET, 300);
    long after = System.currentTimeMillis();

    Claims claims = parseClaims(token);
    Date exp = claims.getExpiration();

    long expMs = exp.getTime();
    assertThat(expMs).isBetween(before + 299_000, after + 301_000);
  }

  @Test
  void generate_eachCallProducesDifferentToken() throws InterruptedException {
    // iat/exp at second granularity—wait 1 second to guarantee different tokens
    String t1 = generator.generate("gateway", 1L, List.of(), SECRET, 300);
    Thread.sleep(1100);
    String t2 = generator.generate("gateway", 1L, List.of(), SECRET, 300);

    assertThat(t1).isNotEqualTo(t2);
  }

  @Test
  void generate_withMultiplePermissions_allPresent() {
    List<String> perms = List.of("order:read", "order:write", "device:command");
    String token = generator.generate("gateway", 1L, perms, SECRET, 300);

    // round-trip via parser
    JwtTokenParser parser = new JwtTokenParser();
    ServiceTokenPayload payload = parser.parseServiceToken(token, SECRET);
    assertThat(payload.permissions()).containsExactlyInAnyOrderElementsOf(perms);
  }

  // ─── helper ───

  private Claims parseClaims(String token) {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    return Jwts.parser().verifyWith(key).build()
        .parseSignedClaims(token).getPayload();
  }
}
