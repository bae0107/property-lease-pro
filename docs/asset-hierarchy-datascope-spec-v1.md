# 资产层级 + 数据权限配置闭环 spec v1（待确认）

> 日期：2026-07-26
> 范围：`backend/main-service`（propertymgr 主数据 + IAM 闭环）+ `frontend/web`（对应页面）
> 前置共识（2026-07-26 与用户确认）：
> - 层级：区域(AREA) > 门店(STORE) > 楼栋(BUILDING) > 单元(UNIT) > 楼层(LEVEL) > 房间(ROOM)
> - **单元/楼层属性化**：不做独立表，作为房间上的字段；房间仍直挂楼栋
> - **本轮只做配置闭环**：区域/门店主数据 + 角色维度 + 用户数据权限配置全部打通，但**业务查询不按 data-scope 过滤**（强制过滤单独立项）

## 1. 背景与现状差距

IAM 侧早已预留数据权限模型（001 changelog + iam-external.yaml）：

- `iam_role.required_data_scope_dimension`：角色声明绑定用户时要求的维度（AREA/STORE/NULL），创建后不可改；
- `iam_user_data_scope`：user_id + dimension(AREA/STORE) + scope_type(ALL/SPECIFIC) + resource_id；
- `GET/PUT /iam/users/{id}/data-scope` 已上线（权限码 `iam:user:read` / `iam:user:scope:write`）；
- 表注释写明「resource_id 存 area_id/store_id（来自 main-service 其他模块）」——**但区域/门店实体从未建设**。

资产侧现状：

- `building_info`：BuildingId(VARCHAR PK)、**StoreId(BIGINT 裸 ID，无门店实体可指)**、BuildingName；
- `room_info`：RoomId(BIGINT 自增 PK)、BuildingId、Level(VARCHAR)、RoomNum、LivingNum、RoomStatus；
- 无区域/门店/单元任何实体。

前端现状：角色创建无维度下拉；用户管理无数据权限入口；楼栋创建的 storeId 靠手输。

## 2. 数据模型设计（changelog 018）

沿用 016 的历史约定：表名 snake_case、**列名 camelCase**、`IsDeleted INT 1=未删除`、`CreateTime/UpdateTime DATETIME(3)`。

### 2.1 area_info（区域）

| 列 | 类型 | 说明 |
|---|---|---|
| AreaId | BIGINT 自增 PK | IAM data-scope resource_id 引用此列 |
| AreaName | VARCHAR(64) NOT NULL | 区域名称（不强制唯一，演示从简） |
| IsDeleted | INT default 1 | 1=未删除 |
| CreateTime / UpdateTime | DATETIME(3) | |

### 2.2 store_info（门店）

| 列 | 类型 | 说明 |
|---|---|---|
| StoreId | BIGINT 自增 PK | `building_info.StoreId` 逻辑外键指向此列（不加 DB 外键，沿用现有风格） |
| AreaId | BIGINT NOT NULL | 所属区域（area_info.AreaId，逻辑外键） |
| StoreName | VARCHAR(64) NOT NULL | 门店名称 |
| IsDeleted | INT default 1 | |
| CreateTime / UpdateTime | DATETIME(3) | |

### 2.3 room_info 加列

- `Unit VARCHAR(20)`：单元（属性化，不做实体）；`Level` 保留原样（楼层属性）。

### 2.4 building_info 不动

StoreId 列已存在，由 Service 层校验其必须指向存在的 store_info（见 §3.3）。

## 3. API 设计（propertymgr-external.yaml 追加）

### 3.1 区域

| 端点 | 说明 | 权限码 |
|---|---|---|
| `POST /propertymgr/areas` | 创建 `{areaName}` → 201 Area | `propertymgr:area:write` |
| `POST /propertymgr/areas/query` | 分页（PageRequest + areaName 模糊） | `propertymgr:area:read` |
| `PUT /propertymgr/areas/{id}` | 改名 `{areaName}`；404 不存在 | `propertymgr:area:write` |

Area 模型：`{areaId, areaName}`。

### 3.2 门店

| 端点 | 说明 | 权限码 |
|---|---|---|
| `POST /propertymgr/stores` | 创建 `{areaId, storeName}`；areaId 不存在 404 | `propertymgr:store:write` |
| `POST /propertymgr/stores/query` | 分页（PageRequest + areaId / storeName 模糊过滤） | `propertymgr:store:read` |
| `PUT /propertymgr/stores/{id}` | 改 `{areaId, storeName}`；门店/区域不存在 404 | `propertymgr:store:write` |

Store 模型：`{storeId, areaId, storeName}`。

### 3.3 既有模型增量

- Room 模型与 `POST /propertymgr/rooms` 请求加 `unit`（可选）；房间查询结果展示单元。
- `POST /propertymgr/buildings`：storeId 必须存在于 store_info，否则 404 `STORE_NOT_FOUND`（行为变更加强，原裸 ID 随便填）。

**不做删除**：区域/门店/楼栋的删除涉及引用检查（门店下有楼栋、区域下有门店），本轮非目标。

## 4. IAM 闭环补齐

### 4.1 SPECIFIC resourceIds 存在性校验（新增）

现状：`PUT /iam/users/{id}/data-scope` 只校验「维度与角色要求匹配」，不校验 resource_id 是否真实存在。

新增：scope_type=SPECIFIC 时，按维度校验 resourceIds 全部存在（AREA → area_info、STORE → store_info），任一不存在 400。跨模块访问通过新增 port（`AssetHierarchyQueryPort`，propertymgr 实现，iam 注入，沿用 AssetQueryPort 模式），避免 iam 直接依赖 propertymgr 内部。

### 4.2 其余 IAM 行为不变

- 角色创建已支持 `requiredDataScopeDimension`（后端零改动，只补前端下拉）；
- 角色绑定用户时的维度匹配校验已存在；
- UserDetail 已返回 dataScope 字段。

## 5. 前端设计（frontend/web）

### 5.1 新增两页（菜单「房屋管理」组，排在楼栋之前）

- **区域管理** `/property/areas`：列表（名称模糊）/ 创建 / 改名；
- **门店管理** `/property/stores`：列表（区域下拉过滤 + 名称模糊）/ 创建（选区域）/ 编辑。

权限：对应 read/write 权限码驱动菜单与按钮。

### 5.2 既有页面修改

- **楼栋管理**：创建弹窗的「门店 ID 手输」改为门店下拉（数据源 `/propertymgr/stores/query` pageSize 200）；列表可加列显示门店 ID；
- **房间管理**：创建弹窗加「单元」输入；列表加「单元」列；
- **角色管理**：
  - 创建角色弹窗加「数据权限要求」下拉：无要求(null) / 区域(AREA) / 门店(STORE)，提示"创建后不可改"；
  - 列表加「数据维度」列（Role 模型已返回 requiredDataScopeDimension）；
- **用户管理**：
  - 列表操作列加「数据权限」按钮（`iam:user:scope:write` 驱动）；
  - 弹窗：先 `GET /iam/users/{id}` 取角色与现有 dataScope，按 AREA/STORE 两个维度各渲染一行：范围类型下拉（ALL/SPECIFIC）+ SPECIFIC 时显示对应多选清单（区域清单 / 门店清单，来源 §3.1/3.2 查询接口，pageSize 200）；用户角色要求的维度必须填（前端校验 + 后端兜底）；提交 `PUT /iam/users/{id}/data-scope` 全量覆写；
  - 用户详情/列表可展示 dataScope 摘要（如 "AREA:SPECIFIC(2) STORE:ALL"）。

## 6. 演示数据对齐

现有演示数据 `B101.StoreId=1`。实施后用 API 依次建 1 个区域（AreaId=1）→ 1 个门店（StoreId=1，挂在区域 1 下），自增 ID 正好与 B101 对齐，存量数据无需迁移。spec 验证步骤包含此对齐检查。

权限清单同步后（新增 4 个 propertymgr 权限码），需给 OPS_STAFF 角色补勾（走角色管理页或 API）；`iam:user:scope:write` 检查是否已在 OPS_STAFF / IAM_ADMIN 权限内，缺则补。

## 7. 非目标（本轮明确不做）

- 业务查询的 data-scope 强制过滤（合同/账务/房屋查询不按用户范围过滤）——单独立项；
- 单元/楼层实体化；
- 区域/门店/楼栋删除；
- 门店的更多档案字段（地址、电话等）；
- TENANT/CONTRACTOR 侧的数据权限。

## 8. 验证

1. `mvn -pl main-service -am test -o` 全绿（新增 PropertymgrService 区域/门店 UT + data-scope 存在性校验 UT）；
2. reale2e 链路 curl 冒烟：建区域 → 建门店 → 建楼栋（挂门店）→ 建房间（带单元）→ 建角色（维度=STORE）→ 建用户绑角色 → 配 data-scope（SPECIFIC 选门店）→ 维度不匹配/资源不存在两种 400 各验证一次；
3. 前端 16 页接口形态回归 + 新增 2 页验证；
4. B101 与新 StoreId=1 对齐检查。
