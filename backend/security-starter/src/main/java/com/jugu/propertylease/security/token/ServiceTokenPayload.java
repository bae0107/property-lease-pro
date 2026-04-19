package com.jugu.propertylease.security.token;

import java.util.List;

/**
 * Service JWT 解析结果，所有微服务的 ServletServiceJwtFilter 使用。
 *
 * @param serviceName 来自 JWT sub（如 "gateway"、"billing-service"）
 * @param userId      来自 JWT claims["userId"]，可为 null（null = 系统调用，无用户上下文）
 * @param permissions 来自 JWT claims["permissions"]，系统调用时为空列表
 * @param exp         Unix 时间戳（秒）
 */
public record ServiceTokenPayload(
    String serviceName,
    Long userId,
    List<String> permissions,
    long exp
) {

}
