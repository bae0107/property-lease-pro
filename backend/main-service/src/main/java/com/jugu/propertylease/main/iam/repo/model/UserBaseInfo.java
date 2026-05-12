package com.jugu.propertylease.main.iam.repo.model;

import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UserType;

/**
 * 用于业务前置校验的用户轻量快照。
 *
 * <p>只携带 userType 和 sourceType 两个字段，
 * 避免为校验而加载整个 IamUser POJO（含 realName / mobile / email 等无关字段）。
 *
 * <p>字段使用枚举类型而非 String，Service 层可直接做类型安全比较，
 * 不再需要 userType.getValue().equals(...) 这种字符串比对。
 */
public record UserBaseInfo(UserType userType, SourceType sourceType) {

    /** 是否为可修改的普通用户类型（STAFF 或 CONTRACTOR）。*/
    public boolean isMutableType() {
        return userType == UserType.STAFF || userType == UserType.CONTRACTOR;
    }

    /** 是否为内置用户（BUILTIN 来源，禁止修改）。*/
    public boolean isBuiltin() {
        return sourceType == SourceType.BUILTIN;
    }
}
