# frontend/web Phase 2 plan：前端骨架 + 登录 + IAM 两页

> 日期：2026-07-26
> 对应 spec：`docs/frontend-web-dev-spec-v1.md` §4.1–4.3
> 范围：静态骨架 + api/auth/ui 公共层 + 登录/登出 + 工作台 + 系统-用户 + 系统-角色（验证权限驱动菜单）

## 技术形态

- 纯静态、无构建：`frontend/web/index.html` + `css/app.css` + `js/**`，原生 ES Module（`type="module"`），现代 Chrome 可用即可；
- 本地托管：`cd frontend/web && python -m http.server 3000`，与 gateway CORS（允许 localhost:3000）匹配；
- hash 路由 SPA-lite：`#/login`、`#/dashboard`、`#/iam/users`、`#/iam/roles`；
- API 走 gateway：`http://localhost:8080/api/main-service`（config.js 单点配置）。

## 文件结构

```
frontend/web/
  index.html            # 外壳：<div id="app"> + <script type="module" src="js/app.js">
  css/app.css           # 极简：flex 布局、表格、表单、模态框、toast
  js/
    common/config.js    # API_BASE 常量
    common/api.js       # fetch 封装：自动 Bearer；401→清 token 跳登录；403/业务错 toast(message+traceId)
    common/auth.js      # token/refreshToken 存 localStorage；JWT payload base64 解码；hasPerm(code)
    common/ui.js        # el() DOM helper、table、分页条、modal 表单、toast
    app.js              # 路由表 + 布局（侧栏菜单按权限过滤）+ 未登录守卫
    pages/login.js      # 账号密码登录
    pages/dashboard.js  # 当前用户信息（sub/userId/权限数）+ 模块入口
    pages/iam-users.js  # 用户列表/创建 STAFF/分配角色/启停/重置密码
    pages/iam-roles.js  # 角色列表/创建/分配权限/删除
```

## API 映射（已核对 iam-external.yaml）

| 功能 | 端点 | 请求体 |
|---|---|---|
| 登录 | POST /auth/login/password | {username, password} → {accessToken, refreshToken, userId, userType} |
| 登出 | POST /auth/logout | {refreshToken} |
| 用户列表 | POST /iam/users/query | PageRequest {pageNo,pageSize} |
| 创建用户 | POST /iam/users | {userType:STAFF, username, password(≥8位含小写+数字), mobile, realName?, roleIds[]} |
| 分配角色 | PUT /iam/users/{id}/roles | {roleIds[]}（全量替换） |
| 启停 | PUT /iam/users/{id}/status | {status: ACTIVE/INACTIVE} |
| 重置密码 | PUT /iam/users/{id}/password | {password} |
| 角色列表 | POST /iam/roles/query | PageRequest |
| 角色详情 | GET /iam/roles/{id} | → RoleDetail（含 permissions[]） |
| 创建角色 | POST /iam/roles | {name, code, description?} |
| 分配权限 | PUT /iam/roles/{id}/permissions | {permissionIds[]}（全量替换） |
| 删除角色 | POST /iam/roles/batch-delete | {ids[]}（BUILTIN/有绑定的禁删） |
| 权限列表 | POST /iam/permissions/query | PageRequest（pageSize 调大全量拉取，前端按 resource 分组） |

## 菜单权限映射（Phase 2 部分）

| 菜单 | 路由 | 需要权限 |
|---|---|---|
| 工作台 | #/dashboard | 登录即可 |
| 系统-用户 | #/iam/users | iam:user:read |
| 系统-角色 | #/iam/roles | iam:role:read |

按钮级显隐：创建用户 iam:user:create、分配角色/启停 iam:user:write、重置密码 iam:user:password:reset、创建角色/分配权限/删除 iam:role:write。

## 验证

1. 起 gateway(8080) + main-service(reale2e, 8081) + 静态服务器(3000)；
2. iam_admin 登录 → 菜单含用户/角色 → 创建角色/用户全流程（复用 OPS_STAFF/staff01 场景）；
3. staff01 登录 → 菜单只剩工作台（iam 只读权限有 → 两菜单可见但无写按钮）；
4. 直接访问 #/iam/users 无 token → 跳登录；token 过期 401 → 自动跳登录；
5. 登出 → 回登录页。
