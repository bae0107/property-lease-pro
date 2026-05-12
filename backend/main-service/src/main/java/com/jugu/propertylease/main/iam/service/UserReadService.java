package com.jugu.propertylease.main.iam.service;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.iam.repo.IamUserReadRepository;
import com.jugu.propertylease.main.iam.service.mapper.UserDtoMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户读取聚合服务。
 */
@Service
public class UserReadService {

    private final IamUserReadRepository userReadRepository;
    private final UserDtoMapper userDtoMapper;

    public UserReadService(IamUserReadRepository userReadRepository, UserDtoMapper userDtoMapper) {
        this.userReadRepository = userReadRepository;
        this.userDtoMapper = userDtoMapper;
    }

    /**
     * 查询用户详情（含角色列表和数据权限）。
     *
     * @throws BusinessException 404 IAM_USER_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public UserDetail getUserDetail(Long userId) {
        var user = userReadRepository.findActiveUserById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "IAM_USER_NOT_FOUND", "用户不存在"));

        var roles = userReadRepository.findRolesByUserId(userId);
        var scopeRows = userReadRepository.findDataScopesByUserId(userId);

        return userDtoMapper.toUserDetail(user, roles, scopeRows);
    }

    /**
     * 查询用户数据权限汇总（供 API 层直接返回）。
     */
    @Transactional(readOnly = true)
    public UserDataScope getUserDataScope(Long userId) {
        // 存在性由调用方（Controller/Service）保证，此处只负责组装
        var scopeRows = userReadRepository.findDataScopesByUserId(userId);
        return new UserDataScope().scopes(userDtoMapper.toDataScopeItems(scopeRows));
    }

    /**
     * 查询用户数据权限条目列表（供 UserMutationService 内部校验使用）。
     *
     * <p>updateUserRoles 变更角色后需要验证现有数据权限维度与新角色是否仍匹配，
     * 此方法提供所需的轻量数据，不加载完整 UserDetail。
     */
    @Transactional(readOnly = true)
    public List<DataScopeItem> getUserDataScopeItems(Long userId) {
        var scopeRows = userReadRepository.findDataScopesByUserId(userId);
        return userDtoMapper.toDataScopeItems(scopeRows);
    }
}
