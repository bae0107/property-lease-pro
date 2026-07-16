# 全项目差距评估（本轮：翻了真正的 pom.xml，发现两个致命缺口）

## 一、结论先说：这轮之前，项目其实**编译不过**，而且原因跟之前几轮猜的列名细节无关

翻了一遍你上传包里真实存在的 `pom.xml`（之前几轮我都只看了 `pom-additions.xml` 这种"补丁说明"，
没看过真正的 pom），发现两个比"列名对不对"严重得多的问题：

### 1. `014-create-contract-tables.xml` / `015-create-occupancy-tables.xml` 根本不存在

HANDOFF.md 把这两个文件列在"已完成"清单里，但翻遍这次上传的所有压缩包（`files__1_.zip`
及其嵌套的 `step0-code.zip`），**这两个文件从来没有真正出现过**。没有它们，
`CONTRACT` / `CONTRACT_ROOM` / `CONTRACT_CHARGE_RULE` / `ROOM_ASSIGNMENT` / `STAY` /
`TRANSFER_RECORD` 这些表在数据库里都不存在，jOOQ codegen 连 POJO 都生成不出来——
不是"编译报错"，是**编译这一步之前，代码生成这一步就直接失败**。

**已修复**：本轮据 `ContractRepository`/`JooqContractRepository`/`ContractLifecycleService`
和已有的 `JooqOccupancyRepository`（这个是真实存在的旧代码，权威来源）里实际引用的列名，
重建了这两个文件。**过程中还真发现了一处我自己之前的推测错误**：`transfer_record` 表我原来
猜的列名是 `operator_id`/`created_at`，但 `JooqOccupancyRepository.insertTransferRecord`
实际用的是 `transferred_by`/`transfer_at`——已经改过来对齐了。

⚠️ 这两个文件还需要你**手动加进 Liquibase 主 changelog 的 include 列表**（比如
`db.changelog-master.xml`），这个文件没在上传包里出现过，我这边加不了。

### 2. `pom.xml` 里完全没有 `customer-external` 这个 execution

`pom-additions.xml` 里写的"追加到 pom.xml"的说明，**从来没有真正被应用进 pom.xml**。
结果就是 `customer-external.yaml` 根本不会被 openapi-generator 处理，
`CustomerEnterprisesApiDelegateImpl`/`CustomerEmployeesApiDelegateImpl` 引用的生成接口
不存在，customer 模块编译不过。

**已修复**：在 `pom.xml` 里补上了 `customer-external` execution（照抄 `contract-external`
那段的写法），同时发现 `add-generated-sources` 那个 execution 里的 `<source>` 白名单
也漏了 `openapi-customer-external` 这一条——一并补上了，不然就算 execution 加了，
生成的代码也不会被注册成编译源码目录。

## 二、现在的完成度评估

好消息是：**修完这两个致命问题之后，八个模块（iam / customer / propertymgr / accounting /
metering / schedule / contract / occupancy）在"每一层都有代码"这个意义上已经齐了**——
repo 层、service 层、Port/Delegate 层、DB migration、OpenAPI yaml，没有再发现完全空缺的模块。
剩下的问题性质上都是"细节需要核对"，不是"整块东西没写"：

| 类别 | 具体问题 | 严重程度 |
|---|---|---|
| DB | 014/015 需要手动加入主 changelog include 列表 | 🔴 阻塞编译，但一行配置 |
| DB | contract/occupancy 表列名是推测重建，未跟你核对过 | 🟡 建议 review，编译报错会直接指出来 |
| 配置 | `contract-internal` execution 是 v4 遗留死配置（没人实现），建议清理但不阻塞 | 🟢 可选 |
| 业务 | `applyFullReturn` 里 SETTLING→COMPLETED 是同步立即执行（因为 settleFullReturn 是 stub） | 🟡 等接入真实 billing-service 再改 |
| 业务 | 到期提醒通知是纯日志 stub（无 NotificationPort，但 pom.xml 里其实已经有阿里云短信 SDK 依赖，具备接入条件） | 🟡 看你们要不要现在做 |
| 业务 | `findContractsDueBy` 的"活跃合同"状态集合是推测值 | 🟢 一行常量可改 |
| API | `contract-external.yaml` 是我自行设计的（没有真实 v5 spec），`chargeType`/`payerType`/`paymentMode` 未定 enum | 🟡 建议 review |
| API | `/contract/rooms/query` 是内存分页非数据库分页 | 🟢 影响很小 |

## 三、下一步建议（按优先级）

1. **把 `014`/`015` 加进主 changelog**，找到 `db.changelog-master.xml`（或等效文件）加两行 include。
2. **本地跑一次 `mvn generate-sources`**，这一步之前是直接失败的，现在应该能跑通到"jOOQ 生成 POJO"
   这一步了。如果列名有出入，报错会很直接（找不到列/字段），照着我重建的 xml 改。
3. 编译 Java 代码，解决剩余的类型不匹配问题（如果有的话，多半是 API model 字段名的小出入）。
4. Review `contract-external.yaml`（尤其是 enum 字段）和 `applyFullReturn`/到期提醒 这两个业务假设。
5. 走一遍 HANDOFF.md 第五节的集成测试链路。

这次改动涉及 `pom.xml`（项目根目录）和两个新的 changelog 文件，一并打包在下面的 zip 里，
`pom.xml` 放在 zip 的 `root/pom.xml` 路径下（不在 `src/` 下面），提醒自己合并时放到项目根目录，
不要误放进 src 树里。
