package com.jugu.propertylease.main.iam.auth;

import com.jugu.propertylease.main.iam.repo.IamAuthVersionRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultAuthVersionService implements AuthVersionService {

  private static final Logger log = LoggerFactory.getLogger(DefaultAuthVersionService.class);

  private final IamAuthVersionRepository authVersionRepository;

  public DefaultAuthVersionService(IamAuthVersionRepository authVersionRepository) {
    this.authVersionRepository = authVersionRepository;
  }

  @Override
  public void bumpAuthVersion(Long userId, String reason) {
    int affected = authVersionRepository.bumpAuthVersion(userId, OffsetDateTime.now());
    if (affected == 0) {
      log.warn("skip authVersion bump, user not found or already deleted: userId={} reason={}",
          userId, reason);
      return;
    }
    log.info("authVersion bumped for userId={} reason={}", userId, reason);
  }

  @Override
  public Optional<Integer> getCurrentAuthVersion(Long userId) {
    return authVersionRepository.findCurrentAuthVersion(userId);
  }
}
