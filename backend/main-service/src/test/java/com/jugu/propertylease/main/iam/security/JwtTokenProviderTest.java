//package com.jugu.propertylease.main.iam.security;
//
//import com.jugu.propertylease.main.iam.repository.mapper.UserEntity;
//import io.jsonwebtoken.ExpiredJwtException;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.security.Keys;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import javax.crypto.SecretKey;
//import java.nio.charset.StandardCharsets;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.util.Date;
//
//import static org.junit.jupiter.api.Assertions.*;
//
/// **
// * JwtTokenProvider 单元测试
// */
//class JwtTokenProviderTest {
//
//    private JwtTokenProvider jwtTokenProvider;
//    private SecretKey testSecretKey;
//    private UserEntity testUser;
//
//    @BeforeEach
//    void setUp() {
//        // 使用测试密钥
//        testSecretKey = Keys.hmacShaKeyFor("test-secret-key-for-unit-testing-12345".getBytes(StandardCharsets.UTF_8));
//
//        testUser = new UserEntity();
//        testUser.setId(1L);
//        testUser.setUsername("testuser");
//        testUser.setUserType("STAFF");
//        testUser.setDisplayName("Test User");
//
//        // 手动创建 provider（绕过 Spring）
//        jwtTokenProvider = new TestableJwtTokenProvider(testSecretKey, 1800);
//    }
//
//    @Test
//    void testGenerateAccessToken() {
//        // Act
//        String token = jwtTokenProvider.generateAccessToken(testUser);
//
//        // Assert
//        assertNotNull(token);
//        assertTrue(token.length() > 0);
//    }
//
//    @Test
//    void testGetUsernameFromToken() {
//        // Arrange
//        String token = jwtTokenProvider.generateAccessToken(testUser);
//
//        // Act
//        String username = jwtTokenProvider.getUsernameFromToken(token);
//
//        // Assert
//        assertEquals("testuser", username);
//    }
//
//    @Test
//    void testGetUserIdFromToken() {
//        // Arrange
//        String token = jwtTokenProvider.generateAccessToken(testUser);
//
//        // Act
//        Long userId = jwtTokenProvider.getUserIdFromToken(token);
//
//        // Assert
//        assertEquals(1L, userId);
//    }
//
//    @Test
//    void testValidateToken_Valid() {
//        // Arrange
//        String token = jwtTokenProvider.generateAccessToken(testUser);
//
//        // Act
//        boolean isValid = jwtTokenProvider.validateToken(token);
//
//        // Assert
//        assertTrue(isValid);
//    }
//
//    @Test
//    void testValidateToken_Invalid() {
//        // Arrange
//        String invalidToken = "invalid.token.here";
//
//        // Act
//        boolean isValid = jwtTokenProvider.validateToken(invalidToken);
//
//        // Assert
//        assertFalse(isValid);
//    }
//
//    @Test
//    void testIsTokenExpired() {
//        // Arrange - 创建一个已过期的 token
//        ExpiredJwtTokenProvider expiredProvider = new ExpiredJwtTokenProvider(testSecretKey);
//        String expiredToken = expiredProvider.generateAccessToken(testUser);
//
//        // Act
//        boolean isExpired = jwtTokenProvider.isTokenExpired(expiredToken);
//
//        // Assert
//        assertTrue(isExpired);
//    }
//
//    @Test
//    void testGetTokenExpiration() {
//        // Arrange
//        String token = jwtTokenProvider.generateAccessToken(testUser);
//
//        // Act
//        LocalDateTime expiration = jwtTokenProvider.getTokenExpiration(token);
//
//        // Assert
//        assertNotNull(expiration);
//        assertTrue(expiration.isAfter(LocalDateTime.now()));
//    }
//
//    // 可测试的 JwtTokenProvider 子类
//    static class TestableJwtTokenProvider extends JwtTokenProvider {
//        public TestableJwtTokenProvider(SecretKey secretKey, long expirationSeconds) {
//            super(secretKey, expirationSeconds);
//        }
//    }
//
//    // 已过期的 Token Provider
//    static class ExpiredJwtTokenProvider extends JwtTokenProvider {
//        public ExpiredJwtTokenProvider(SecretKey secretKey) {
//            super(secretKey, -1000); // 负数表示已过期
//        }
//    }
//}
