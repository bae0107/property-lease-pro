package com.jugu.propertylease.main.iam.repo.model;

import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UserType;

/**
 * 软删除前读取的用户快照，用于：
 * <ul>
 *   <li>校验是否允许删除（BUILTIN / SYSTEM 类型禁止）</li>
 *   <li>记录被墓碑化前的原始登录标识（userName / mobile / email）</li>
 * </ul>
 *
 * <p>字段使用枚举类型，消除 Service 层的字符串比对。
 */
public record UserDeleteSnapshot(
        String userName,
        String mobile,
        String email,
        SourceType sourceType,
        UserType userType
) {

    /** 是否为内置或系统用户（禁止删除）。 */
    public boolean isDeletionForbidden() {
        return sourceType == SourceType.BUILTIN || userType == UserType.SYSTEM;
    }
}
