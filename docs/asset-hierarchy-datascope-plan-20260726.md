# 资产层级 + 数据权限配置闭环 执行计划

> 日期：2026-07-26
> 依据：`asset-hierarchy-datascope-spec-v1.md`（已确认：单元/楼层属性化；本轮只做配置闭环，不做业务强制过滤）

## 实施步骤

### Step 1 — changelog 018（数据模型）
新建 `018-create-area-store-tables.xml`（master 是 includeAll，自动拾取）：
- `area_info`：AreaId BIGINT 自增 PK、AreaName VARCHAR(64) NOT NULL、IsDeleted INT default 1、CreateTime/UpdateTime DATETIME(3)；
- `store_info`：StoreId BIGINT 自增 PK、AreaId BIGINT NOT NULL、StoreName VARCHAR(64) NOT NULL、IsDeleted、时间列；索引 idx_store_info_area(AreaId)；
- `room_info` addColumn `Unit VARCHAR(20)`；
- 沿用 016 约定：列名 camelCase、IsDeleted 1=未删除；jOOQ forcedType 已有 `(?i:PUBLIC\.(ROOM_INFO|BUILDING_INFO)\..*TIME)` 正则——**需确认是否覆盖 AREA_INFO/STORE_INFO，不覆盖则扩正则或给新表时间列显式 forcedType**；
- 注意 Liquibase schemaLocation 必须两行 URI 对（017 踩过坑）。

### Step 2 — propertymgr-external.yaml + 后端实现
1. yaml 追加：
   - tag `propertymgr-areas` / `propertymgr-stores`；
   - `POST /propertymgr/areas`（write）、`POST /propertymgr/areas/query`（read）、`PUT /propertymgr/areas/{id}`（write）；
   - `POST /propertymgr/stores`（write，areaId 不存在 404）、`POST /propertymgr/stores/query`（read，areaId/storeName 过滤）、`PUT /propertymgr/stores/{id}`（write）；
   - Area{areaId,areaName}、Store{storeId,areaId,storeName} + PageResult；
   - Room/CreateRoomRequest 加 `unit`；
   - 权限码：propertymgr:area:read/write、propertymgr:store:read/write；
2. `PropertymgrService`：
   - createArea / updateArea(404 AREA_NOT_FOUND) / queryAreas（名称模糊，IsDeleted=1）；
   - createStore（areaId 不存在 404 AREA_NOT_FOUND）/ updateStore（门店 404 STORE_NOT_FOUND、区域 404）/ queryStores（areaId+名称过滤）；
   - createBuilding 增加 storeId 存在性校验（404 STORE_NOT_FOUND）；
   - createRoom/queryRooms 带 Unit 列；
3. Delegate：PropertymgrAreasApiDelegateImpl、PropertymgrStoresApiDelegateImpl（仿 Buildings/Rooms）；Rooms delegate 映射 unit；
4. 编译 + jOOQ 重新生成（LiquibaseDatabase 自动读 018）。

### Step 3 — IAM data-scope 存在性校验
1. 新建 `propertymgr/api/AssetHierarchyQueryPort`：`Set<Long> findExistingAreaIds(Collection<Long>)`、`Set<Long> findExistingStoreIds(Collection<Long>)`；实现类走 DSLContext（IsDeleted=1）；
2. `UserMutationService.updateUserDataScope`：SPECIFIC scope 按维度调 port 校验 resourceIds 全存在，缺失 → 400（错误码沿用现有风格，如 IAM_DATA_SCOPE_RESOURCE_NOT_FOUND）；
3. UT：PropertymgrService 区域/门店（创建/查重路径/404 路径/查询过滤）、updateUserDataScope 存在性校验（全存在通过、缺一个 400）。

### Step 4 — 后端验证（reale2e 冒烟）
1. `mvn -pl main-service -am install -DskipTests -o` → `mvn -pl main-service -am test -o` 全绿；
2. 重启 main-service reale2e（TaskStop 后 taskkill 残留 java）；
3. curl/python 冒烟（staff01，先补权限——manifest 同步后 OPS_STAFF 重新全量授权）：
   - 建区域「华东区域」→ 建门店「上海一店」（StoreId 应=1，与 B101.StoreId 对齐验证）；
   - 建楼栋挂门店 1 → 建房间带单元；
   - 建角色（维度 STORE）→ 建用户绑角色 → PUT data-scope SPECIFIC 选门店 1 → 200；
   - 维度不匹配 400、resourceId 不存在 400 各验一次；
   - B101 详情/门店 1 对齐检查。

### Step 5 — 前端
1. 新增 `property-areas.js`（列表/创建/改名）、`property-stores.js`（区域过滤+名称模糊/创建选区域/编辑）；
2. `property-buildings.js`：创建弹窗 storeId 手输 → 门店下拉（stores/query pageSize 200）；
3. `property-rooms.js`：创建加单元输入、列表加单元列；
4. `iam-roles.js`：创建弹窗加「数据权限要求」下拉（null/AREA/STORE，提示不可改）、列表加「数据维度」列；
5. `iam-users.js`：操作列加「数据权限」（iam:user:scope:write 驱动）——弹窗 GET 用户详情 → AREA/STORE 两行（scopeType 下拉 ALL/SPECIFIC，SPECIFIC 显示区域/门店多选清单）→ PUT 覆写；
6. `app.js` 注册两新路由（房屋管理组，楼栋之前）；dashboard 入口同步。

### Step 6 — 联调 + 提交
- node --check 全部；python 按页面请求形态回归 16 页 + 新 2 页；
- 提交拆分：① 后端（changelog+yaml+service+port+UT）② 前端 ③ 无（文档随各自 commit）。

## 风险与注意

- jOOQ forcedType 正则可能不含新表时间列 → 编译期 OffsetDateTime 映射问题，Step 1 先查 jooq 配置；
- OPS_STAFF 权限需再次全量授权（新 4 权限码同步后）；
- 演示数据 StoreId=1 对齐依赖"第一个建的门店"——若库已有残留需先确认 store_info 为空；
- 前端 data-scope 弹窗的维度行渲染规则：角色要求维度必填；两维度都可配（后端允许 AREA+STORE 各一条）。
