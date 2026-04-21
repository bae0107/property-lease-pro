package com.jugu.propertylease.main.iam.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认空实现，后续可替换为真正的版本递增 + 事件发布实现。
 */
@Component
public class NoopAuthVersionService implements AuthVersionService {

  private static final Logger log = LoggerFactory.getLogger(NoopAuthVersionService.class);

  @Override
  public void bumpAuthVersion(Long userId, String reason) {
    log.info("authVersion bump skipped in noop service: userId={} reason={}", userId, reason);
  }
}

