package com.jugu.propertylease.gateway.security;

import com.jugu.propertylease.gateway.config.GatewayProperties;
import com.jugu.propertylease.main.client.internal.api.InternalAuthApiClient;
import com.jugu.propertylease.main.client.internal.model.AuthVersionCheckResult;
import com.jugu.propertylease.security.filter.reactive.UserTokenVersionChecker;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 调用 main-service 内部接口校验用户 token 的 authVersion 是否最新。
 */
@Component
public class MainServiceUserTokenVersionChecker implements UserTokenVersionChecker {

  private static final Logger log = LoggerFactory.getLogger(MainServiceUserTokenVersionChecker.class);

  private final GatewayProperties.AuthVersionProperties authVersionProperties;
  private final InternalAuthApiClient internalAuthApiClient;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public MainServiceUserTokenVersionChecker(GatewayProperties gatewayProperties,
      InternalAuthApiClient internalAuthApiClient) {
    this.authVersionProperties = gatewayProperties.getAuthVersion();
    this.internalAuthApiClient = internalAuthApiClient;
  }

  @Override
  public boolean isCurrent(Long userId, Integer authVersion) {
    if (!authVersionProperties.isEnabled()) {
      return true;
    }
    if (userId == null || userId <= 0 || authVersion == null || authVersion < 0) {
      return false;
    }
    String key = userId + ":" + authVersion;
    long now = System.currentTimeMillis();
    CacheEntry cached = cache.get(key);
    if (cached != null && cached.expireAtMillis() > now) {
      return cached.current();
    }

    boolean current = queryMainService(userId, authVersion);
    long expireAt = now + authVersionProperties.getCacheTtlSeconds() * 1000L;
    cache.put(key, new CacheEntry(current, expireAt));
    return current;
  }

  private boolean queryMainService(Long userId, Integer authVersion) {
    try {
      AuthVersionCheckResult body = internalAuthApiClient.checkAuthVersion(userId, authVersion);
      return Boolean.TRUE.equals(body.getCurrent());
    } catch (Exception ex) {
      log.warn("authVersion check request error, userId={}, authVersion={}, failOpen={}",
          userId, authVersion, authVersionProperties.isFailOpen(), ex);
      return authVersionProperties.isFailOpen();
    }
  }

  private record CacheEntry(boolean current, long expireAtMillis) {
  }
}
