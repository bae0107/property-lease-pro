//package com.jugu.propertylease.main.iam.service;
//
//import com.jugu.propertylease.common.exception.BusinessException;
//import com.jugu.propertylease.main.iam.repository.IamRepository;
//import com.jugu.propertylease.main.iam.repository.mapper.UserEntity;
//import com.jugu.propertylease.main.iam.security.JwtTokenProvider;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.ValueOperations;
//import org.springframework.http.HttpStatus;
//import org.springframework.security.crypto.password.PasswordEncoder;
//
//import java.util.Map;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
/// **
// * AuthenticationService 单元测试
// */
//@ExtendWith(MockitoExtension.class)
//class AuthenticationServiceTest {
//
//    @Mock
//    private IamRepository iamRepository;
//
//    @Mock
//    private PasswordEncoder passwordEncoder;
//
//    @Mock
//    private JwtTokenProvider jwtTokenProvider;
//
//    @Mock
//    private RedisTemplate<String, String> redisTemplate;
//
//    @Mock
//    private ValueOperations<String, String> valueOperations;
//
//    @InjectMocks
//    private AuthenticationService authenticationService;
//
//    private UserEntity testUser;
//
//    @BeforeEach
//    void setUp() {
//        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
//
//        testUser = new UserEntity();
//        testUser.setId(1L);
//        testUser.setUsername("testuser");
//        testUser.setPasswordHash("$2a$10$hashedPassword");
//        testUser.setUserType("STAFF");
//        testUser.setStatus("ACTIVE");
//        testUser.setLoginFailureCount(0);
//    }
//
//    @Test
//    void testAuthenticate_Success() {
//        // Arrange
//        String username = "testuser";
//        String password = "password123";
//        String loginIp = "192.168.1.1";
//
//        when(iamRepository.findUserByUsername(username)).thenReturn(Optional.of(testUser));
//        when(passwordEncoder.matches(password, testUser.getPasswordHash())).thenReturn(true);
//        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("mockAccessToken");
//        when(valueOperations.increment(anyString(), anyLong())).thenReturn(1L);
//
//        // Act
//        Map<String, Object> result = authenticationService.authenticate(username, password, loginIp);
//
//        // Assert
//        assertNotNull(result);
//        assertEquals("mockAccessToken", result.get("accessToken"));
//        assertEquals("Bearer", result.get("tokenType"));
//        assertEquals(1800, result.get("expiresIn"));
//
//        verify(iamRepository).resetLoginFailureCount(testUser.getId());
//        verify(iamRepository).updateUserLoginInfo(testUser.getId(), loginIp);
//    }
//
//    @Test
//    void testAuthenticate_UserNotFound() {
//        // Arrange
//        String username = "notfound";
//        String password = "password123";
//
//        when(iamRepository.findUserByUsername(username)).thenReturn(Optional.empty());
//        when(valueOperations.increment(anyString(), anyLong())).thenReturn(1L);
//
//        // Act & Assert
//        BusinessException exception = assertThrows(BusinessException.class, () -> {
//            authenticationService.authenticate(username, password, "192.168.1.1");
//        });
//
//        assertEquals("AUTH_USER_NOT_FOUND", exception.getErrorCode());
//    }
//
//    @Test
//    void testAuthenticate_InvalidPassword() {
//        // Arrange
//        String username = "testuser";
//        String password = "wrongpassword";
//
//        when(iamRepository.findUserByUsername(username)).thenReturn(Optional.of(testUser));
//        when(passwordEncoder.matches(password, testUser.getPasswordHash())).thenReturn(false);
//        when(valueOperations.increment(anyString(), anyLong())).thenReturn(1L);
//
//        // Act & Assert
//        BusinessException exception = assertThrows(BusinessException.class, () -> {
//            authenticationService.authenticate(username, password, "192.168.1.1");
//        });
//
//        assertEquals("AUTH_INVALID_CREDENTIALS", exception.getErrorCode());
//        verify(iamRepository).incrementLoginFailureCount(testUser.getId());
//    }
//
//    @Test
//    void testAuthenticate_UserDisabled() {
//        // Arrange
//        testUser.setStatus("DISABLED");
//
//        when(iamRepository.findUserByUsername("testuser")).thenReturn(Optional.of(testUser));
//        when(valueOperations.increment(anyString(), anyLong())).thenReturn(1L);
//
//        // Act & Assert
//        BusinessException exception = assertThrows(BusinessException.class, () -> {
//            authenticationService.authenticate("testuser", "password123", "192.168.1.1");
//        });
//
//        assertEquals("AUTH_USER_DISABLED", exception.getErrorCode());
//    }
//
//    @Test
//    void testAuthenticate_TooManyFailures() {
//        // Arrange
//        when(valueOperations.increment(anyString(), anyLong())).thenReturn(6L); // 超过 5 次
//
//        // Act & Assert
//        BusinessException exception = assertThrows(BusinessException.class, () -> {
//            authenticationService.authenticate("testuser", "password123", "192.168.1.1");
//        });
//
//        assertEquals("AUTH_TOO_MANY_FAILURES", exception.getErrorCode());
//    }
//}
