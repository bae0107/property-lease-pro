package com.jugu.propertylease.main.iam.auth;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * main-service 内部认证版本校验接口。
 */
@RestController
@RequestMapping("/internal/v1/auth/version")
public class InternalAuthVersionController {

  private final AuthVersionService authVersionService;

  public InternalAuthVersionController(AuthVersionService authVersionService) {
    this.authVersionService = authVersionService;
  }

  @GetMapping("/check")
  public AuthVersionCheckResponse check(@RequestParam("userId") Long userId,
      @RequestParam("authVersion") Integer authVersion) {
    if (userId == null || userId <= 0 || authVersion == null || authVersion < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "userId and authVersion must be positive/non-negative");
    }

    Integer currentVersion = authVersionService.getCurrentAuthVersion(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    boolean current = Objects.equals(currentVersion, authVersion);
    return new AuthVersionCheckResponse(userId, authVersion, currentVersion, current);
  }

  public record AuthVersionCheckResponse(Long userId,
                                         Integer tokenAuthVersion,
                                         Integer currentAuthVersion,
                                         boolean current) {
  }
}
