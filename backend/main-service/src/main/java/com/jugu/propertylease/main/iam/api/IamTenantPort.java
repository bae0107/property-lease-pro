package com.jugu.propertylease.main.iam.api;

/**
 * IAM 模块对外暴露的 TENANT 用户管理 Port（进程内 Port Interface，供 occupancy 模块注入调用）。
 *
 * <p>与 {@code /internal/v1/users}（HTTP 接口，供 billing-service/device-service 使用）是两套不同机制，
 * 语义也不同：HTTP 接口是"预创建"（重复则报错），本接口是"幂等查找或创建"。
 */
public interface IamTenantPort {

    /**
     * 员工办理入住时调用：确保该手机号对应的 TENANT 用户存在并可用。
     *
     * <p>逻辑：
     * <ol>
     *   <li>按 mobile 查找未删除的 TENANT 用户，找到则直接返回其 ID（幂等）</li>
     *   <li>未找到则创建新用户（userName = "tenant_{mobile}"，写入 realName）</li>
     * </ol>
     *
     * @param mobile   员工手机号（去重/登录唯一键）
     * @param realName 真实姓名（来自 customer.employee.name）
     * @return iam_user.id
     */
    Long createOrEnableTenantUser(String mobile, String realName);

    /**
     * 员工退宿后调用：软删除 IAM 用户，注销小程序登录能力。
     *
     * <p>内部调用 {@code UserLifecycleService.softDeleteUser(iamUserId, null, reason)}，
     * operatorUserId 传 null 表示系统自动操作。
     *
     * @param iamUserId iam_user.id
     */
    void disableTenantUser(Long iamUserId);
}
