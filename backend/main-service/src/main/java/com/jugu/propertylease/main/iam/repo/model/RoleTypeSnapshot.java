package com.jugu.propertylease.main.iam.repo.model;

import com.jugu.propertylease.main.api.model.UserType;

/**
 * 角色校验用快照。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code id}：角色 ID</li>
 *   <li>{@code roleType}：角色适用的用户类型（来自 iam_role.role_type，存储的是 UserType 值，
 *       如 STAFF / CONTRACTOR，用于校验用户-角色类型是否匹配）。
 *       注意：此字段与 RoleType(BUILTIN/CUSTOM) 枚举无关，语义为"此角色归属哪种用户类型"。</li>
 *   <li>{@code name}：角色显示名称，用于错误信息拼装</li>
 * </ul>
 */
public record RoleTypeSnapshot(Long id, UserType roleType, String name) {

    /**
     * 判断本角色是否适用于指定用户类型。
     *
     * <p>替代原来的 {@code userBase.userType().getValue().equals(r.roleType())} 字符串比对。
     */
    public boolean matchesUserType(UserType userType) {
        return this.roleType == userType;
    }
}
