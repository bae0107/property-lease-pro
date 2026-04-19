package com.jugu.propertylease.security.token;

import java.util.List;

/**
 * User JWT 解析结果，仅 Gateway 的 ReactiveUserJwtFilter 使用，微服务内网不直接接触此类型。
 *
 * @param username    来自 JWT sub（登录标识，如邮箱/手机号）
 * @param userId      来自 JWT claims["userId"]
 * @param permissions 来自 JWT claims["permissions"]
 * @param exp         Unix 时间戳（秒）
 */
public record UserTokenPayload(
    String username,
    Long userId,
    List<String> permissions,
    long exp
) {

}
