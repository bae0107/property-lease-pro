package com.jugu.propertylease.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jugu.propertylease.security.exception.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JwtTokenParserTest {

  // HMAC-SHA256 requires >= 256-bit key (32 bytes)
  private static final String SECRET = "test-secret-key-at-least-32-bytes!!";
  private static final String OTHER_SECRET = "other-secret-key-at-least-32bytes!";

  private JwtTokenParser parser;
  private ServiceTokenGenerator generator;

  @BeforeEach
  void setUp() {
    parser = new JwtTokenParser();
    generator = new ServiceTokenGenerator();
  }

  // ─────────────────────────────────────────────
  //  parseServiceToken — happy path
  // ─────────────────────────────────────────────

  @Nested
  class ParseServiceToken {

    @Test
    void validToken_withUserContext_parsesCorrectly() {
      String token = generator.generate("gateway", 42L, List.of("order:read", "order:write"),
          SECRET, 300);

      ServiceTokenPayload payload = parser.parseServiceToken(token, SECRET);

      assertThat(payload.serviceName()).isEqualTo("gateway");
      assertThat(payload.userId()).isEqualTo(42L);
      assertThat(payload.permissions()).containsExactlyInAnyOrder("order:read", "order:write");
      assertThat(payload.exp()).isGreaterThan(0);
    }

    @Test
    void validToken_systemCall_nullUserIdAndEmptyPermissions() {
      String token = generator.generate("billing-service", null, List.of(), SECRET, 300);

      ServiceTokenPayload payload = parser.parseServiceToken(token, SECRET);

      assertThat(payload.serviceName()).isEqualTo("billing-service");
      assertThat(payload.userId()).isNull();
      assertThat(payload.permissions()).isEmpty();
    }

    @Test
    void expiredToken_throwsTokenExpired() {
      SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
      // Build a token already expired
      String expiredToken = Jwts.builder()
          .subject("test-service")
          .issuedAt(new Date(System.currentTimeMillis() - 10_000))
          .expiration(new Date(System.currentTimeMillis() - 5_000))
          .signWith(key)
          .compact();

      assertThatThrownBy(() -> parser.parseServiceToken(expiredToken, SECRET))
          .isInstanceOf(InvalidTokenException.class)
          .satisfies(ex -> assertThat(((InvalidTokenException) ex).getErrorCode())
              .isEqualTo(InvalidTokenException.TOKEN_EXPIRED));
    }

    @Test
    void wrongSignature_throwsTokenInvalid() {
      String token = generator.generate("gateway", 1L, List.of(), SECRET, 300);

      assertThatThrownBy(() -> parser.parseServiceToken(token, OTHER_SECRET))
          .isInstanceOf(InvalidTokenException.class)
          .satisfies(ex -> assertThat(((InvalidTokenException) ex).getErrorCode())
              .isEqualTo(InvalidTokenException.TOKEN_INVALID));
    }

    @Test
    void malformedToken_throwsTokenMalformed() {
      assertThatThrownBy(() -> parser.parseServiceToken("not.a.jwt", SECRET))
          .isInstanceOf(InvalidTokenException.class)
          .satisfies(ex -> assertThat(((InvalidTokenException) ex).getErrorCode())
              .isEqualTo(InvalidTokenException.TOKEN_MALFORMED));
    }

    @Test
    void emptyToken_throwsTokenMalformed() {
      assertThatThrownBy(() -> parser.parseServiceToken("", SECRET))
          .isInstanceOf(InvalidTokenException.class);
    }
  }

  // ─────────────────────────────────────────────
  //  parseUserToken — happy path
  // ─────────────────────────────────────────────

  @Nested
  class ParseUserToken {

    @Test
    void validUserToken_parsesCorrectly() {
      SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
      String token = Jwts.builder()
          .subject("user@example.com")
          .claim("userId", 99L)
          .claim("permissions", List.of("device:read"))
          .issuedAt(new Date())
          .expiration(new Date(System.currentTimeMillis() + 300_000))
          .signWith(key)
          .compact();

      UserTokenPayload payload = parser.parseUserToken(token, SECRET);

      assertThat(payload.username()).isEqualTo("user@example.com");
      assertThat(payload.userId()).isEqualTo(99L);
      assertThat(payload.permissions()).containsExactly("device:read");
    }

    @Test
    void expiredUserToken_throwsTokenExpired() {
      SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
      String token = Jwts.builder()
          .subject("user@example.com")
          .issuedAt(new Date(System.currentTimeMillis() - 10_000))
          .expiration(new Date(System.currentTimeMillis() - 5_000))
          .signWith(key)
          .compact();

      assertThatThrownBy(() -> parser.parseUserToken(token, SECRET))
          .isInstanceOf(InvalidTokenException.class)
          .satisfies(ex -> assertThat(((InvalidTokenException) ex).getErrorCode())
              .isEqualTo(InvalidTokenException.TOKEN_EXPIRED));
    }
  }
}
