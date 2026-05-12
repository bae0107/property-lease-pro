package com.jugu.propertylease.main.iam.service.mapper;

import com.jugu.propertylease.main.api.model.DataScopeDimension;
import com.jugu.propertylease.main.api.model.DataScopeItem;
import com.jugu.propertylease.main.api.model.DataScopeType;
import com.jugu.propertylease.main.api.model.Role;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.User;
import com.jugu.propertylease.main.api.model.UserDataScope;
import com.jugu.propertylease.main.api.model.UserDetail;
import com.jugu.propertylease.main.api.model.UserStatus;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.jooq.tables.pojos.IamRole;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUser;
import com.jugu.propertylease.main.jooq.tables.pojos.IamUserDataScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户相关 jOOQ POJO → OpenAPI DTO 转换器。
 *
 * <p>不含任何业务逻辑，只负责字段映射。
 * 枚举转换使用 {@code fromValue} 静态方法，消除 "ALL"/"SPECIFIC" 等字符串字面量。
 */
@Component
public class UserDtoMapper {

    private final RoleDtoMapper roleDtoMapper;

    public UserDtoMapper(RoleDtoMapper roleDtoMapper) {
        this.roleDtoMapper = roleDtoMapper;
    }

    public UserDetail toUserDetail(IamUser user, List<IamRole> roles, List<IamUserDataScope> scopeRows) {
        List<Role> roleDtos = roles.stream().map(roleDtoMapper::toRole).toList();
        UserDataScope userDataScope = new UserDataScope().scopes(toDataScopeItems(scopeRows));

        User base = new User()
                .id(user.getId())
                .userName(user.getUserName())
                .realName(user.getRealName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .userType(UserType.fromValue(user.getUserType()))
                .sourceType(SourceType.fromValue(user.getSourceType()))
                .status(UserStatus.fromValue(user.getStatus()))
                .source(user.getSource())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .authVersion(user.getAuthVersion())
                .deletedAt(user.getDeletedAt())
                .roleNames(roleDtos.isEmpty()
                        ? "-"
                        : String.join(",", roleDtos.stream().map(Role::getName).toList()));

        return new UserDetail()
                .id(base.getId())
                .userName(base.getUserName())
                .realName(base.getRealName())
                .mobile(base.getMobile())
                .email(base.getEmail())
                .userType(base.getUserType())
                .sourceType(base.getSourceType())
                .status(base.getStatus())
                .source(base.getSource())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .authVersion(base.getAuthVersion())
                .deletedAt(base.getDeletedAt())
                .roleNames(base.getRoleNames())
                .roles(roleDtos)
                .dataScope(userDataScope);
    }

    /**
     * 将数据库行列表转换为 DataScopeItem 列表。
     *
     * <p>同一维度（dimension）下的多行记录：
     * <ul>
     *   <li>scopeType=ALL  → 合并为一条 DataScopeItem（resourceIds=null）</li>
     *   <li>scopeType=SPECIFIC → 合并 resourceId 列表</li>
     * </ul>
     *
     * <p>此方法被 UserReadService.getUserDataScopeItems 直接调用，
     * 访问级别为包级可见（package-private），不对外暴露为 public。
     */
    public  List<DataScopeItem> toDataScopeItems(List<IamUserDataScope> scopeRows) {
        // allMap：dimension → true（标记此维度有 ALL 记录）
        Map<String, Boolean> allMap = new LinkedHashMap<>();
        // specificMap：dimension → resourceId 列表
        Map<String, List<Long>> specificMap = new LinkedHashMap<>();

        for (IamUserDataScope ds : scopeRows) {
            String dim = ds.getScopeDimension();
            // 使用枚举比对，消除 "ALL" 字符串字面量
            if (DataScopeType.ALL == DataScopeType.fromValue(ds.getScopeType())) {
                allMap.put(dim, true);
            } else {
                specificMap.computeIfAbsent(dim, k -> new ArrayList<>());
                if (ds.getResourceId() != null) {
                    specificMap.get(dim).add(ds.getResourceId());
                }
            }
        }

        List<DataScopeItem> result = new ArrayList<>();
        for (String dim : unionKeys(allMap, specificMap)) {
            DataScopeItem item = new DataScopeItem()
                    .dimension(DataScopeDimension.fromValue(dim));
            if (Boolean.TRUE.equals(allMap.get(dim))) {
                item.scopeType(DataScopeType.ALL).resourceIds(null);
            } else {
                item.scopeType(DataScopeType.SPECIFIC)
                        .resourceIds(specificMap.getOrDefault(dim, List.of()));
            }
            result.add(item);
        }
        return result;
    }

    /** 合并两个 Map 的 key，保持 LinkedHashMap 插入顺序。 */
    private static List<String> unionKeys(Map<String, ?> first, Map<String, ?> second) {
        List<String> keys = new ArrayList<>(first.keySet());
        second.keySet().stream()
                .filter(k -> !keys.contains(k))
                .forEach(keys::add);
        return keys;
    }
}
