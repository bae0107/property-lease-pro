# Main-Service v5 文档对齐与本次落地记录

> 日期：2026-07-16  
> 范围：只关注 `main-service`；最新依据文档为 `docs/temp/main-service-dev-spec-v5.md`。

## 1. 最新文档识别

`docs/temp/main-service-dev-spec-v5.md` 是当前 main-service 的最新开发规格：版本号为 v5.0，且明确修正 v4 的 Port Interface / internal yaml 重复设计问题。本文后续只以 v5 文档为准。

## 2. 文档与代码现状差异

### 已基本落地

- `main-service` 已按 v5 模块划分出现 `iam / propertymgr / customer / contract / occupancy / metering / accounting / schedule` 包。
- `contract / metering / accounting` 等模块已开始提供 `{module}/api` Port Interface，方向符合 v5 “进程内 Java Port Interface” 原则。
- `customer` 模块已有企业与员工的基础查询/维护能力雏形。

### 主要 gap

1. **编译卫生 gap**：部分开发过程中遗留的聚合草稿文件与正式拆分文件并存，造成同包同名类型重复；部分聚合文件内还包含多个 `public` 顶层类型或重复 `package` 声明，直接阻塞 main-service 编译。
2. **Port DTO 组织 gap**：`accounting.api.model` 中多个跨模块 DTO 被写在同一个 Java 文件中，但它们需要被其他包公开引用，必须拆成一类型一文件。
3. **API 契约 gap**：v5 文档指出对外 API 与真实跨进程 internal API 才使用 OpenAPI；当前代码里仍可见旧设计痕迹，需要后续逐步核对生成 delegate 与 Port 的边界。
4. **业务闭环 gap**：合同签约、入住、日结、退宿、换宿、退房链路已有框架，但仍需要逐流程补齐仓储方法、状态幂等、异常补偿与回调一致性。
5. **外部服务 stub gap**：billing-service / device-service 当前按 v5 可 stub 占位，但 stub 行为需要固化契约测试，避免后续替换 HTTP client 时破坏业务语义。

## 3. 可实施的小步骤

优先选择不会扩大业务改动面、但能解除后续验证阻塞的小步骤：

1. 删除 `contract.api.ContractPorts`、`metering.api.MeteringPorts`、`accounting.service.AccountingPortImpls` 这类草稿聚合文件，保留已经拆分好的正式类型文件。
2. 将 `accounting.api.model.AccountingPortModels` 拆成每个 `public record` 一个独立文件。
3. 给 `AccountingCommandPort` / `AccountingQueryPort` 补充 model 包导入，保持 Port Interface 对 DTO 的公开引用可解析。
4. 运行 main-service 编译验证；若环境无法解析 Maven BOM，则记录为环境限制，后续在可访问 Maven 仓库的环境复跑。

## 4. 本次已实现

已完成上述 1-3 步，目标是先恢复 main-service 的 Java 类型组织与 Port DTO 公开边界，减少与 v5 文档中“Port Interface 是模块间契约”的偏差。

## 5. 后续建议

下一步建议聚焦一条纵向业务链路（建议从 `contract.confirmContract -> accounting.createEnterpriseSignBill -> accounting payment callback -> contract READY_FOR_CHECK_IN` 开始），为该链路补齐：

- 状态迁移幂等测试；
- 对 `AssetCommandPort` 锁房/释放失败的回滚策略；
- accounting bill 与 deposit ledger 的一致性检查；
- v5 文档对应章节的验收清单。
