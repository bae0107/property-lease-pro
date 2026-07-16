# ============================================================
# 新模块接入清单（main-service 扩展所需配置变更）
# ============================================================

## 1. pom.xml 新增 openapi-generator execution 块
#    为每个业务模块的 external / internal yaml 各增加一个 <execution>
#    参照现有 iam-external / iam-internal 两个 execution 的模式复制即可
#    注意：每个 execution 必须有唯一的 <id>

# account-external
<execution>
  <id>generate-account-external</id>
  <configuration combine.self="override">
    <inputSpec>${project.basedir}/src/main/resources/openapi/account-external.yaml</inputSpec>
    <output>${project.build.directory}/generated-sources/openapi-account-external</output>
    <configOptions>
      <apiPackage>com.jugu.propertylease.main.api</apiPackage>
      <modelPackage>com.jugu.propertylease.main.api.model</modelPackage>
      <invokerPackage>com.jugu.propertylease.main.api</invokerPackage>
      <delegatePattern>true</delegatePattern>
      <openApiNullable>false</openApiNullable>
      <useJakartaEe>true</useJakartaEe>
      <useResponseEntity>false</useResponseEntity>
      <useSpringBoot3>true</useSpringBoot3>
      <useTags>true</useTags>
      <documentationProvider>springdoc</documentationProvider>
    </configOptions>
    <templateDirectory>${project.basedir}/src/main/resources/openapi-templates/JavaSpring</templateDirectory>
    <importMappings>
      <!-- 与 iam-external execution 相同的 importMappings -->
      <importMapping>ErrorResponse=com.jugu.propertylease.common.model.ErrorResponse</importMapping>
      <importMapping>PageRequest=com.jugu.propertylease.common.model.PageRequest</importMapping>
      <importMapping>PageResponse=com.jugu.propertylease.common.model.PageResponse</importMapping>
      <importMapping>QueryFilter=com.jugu.propertylease.common.model.QueryFilter</importMapping>
      <importMapping>StringFilter=com.jugu.propertylease.common.model.StringFilter</importMapping>
      <importMapping>IdsFilter=com.jugu.propertylease.common.model.IdsFilter</importMapping>
      <importMapping>EnumFilter=com.jugu.propertylease.common.model.EnumFilter</importMapping>
      <importMapping>ListViewMeta=com.jugu.propertylease.common.model.ListViewMeta</importMapping>
      <importMapping>ColumnMeta=com.jugu.propertylease.common.model.ColumnMeta</importMapping>
      <importMapping>FilterFieldMeta=com.jugu.propertylease.common.model.FilterFieldMeta</importMapping>
      <importMapping>FilterOption=com.jugu.propertylease.common.model.FilterOption</importMapping>
      <importMapping>BatchRequest=com.jugu.propertylease.common.model.BatchRequest</importMapping>
    </importMappings>
    <schemaMappings>
      <schemaMapping>ErrorResponse=com.jugu.propertylease.common.model.ErrorResponse</schemaMapping>
      <schemaMapping>PageRequest=com.jugu.propertylease.common.model.PageRequest</schemaMapping>
      <schemaMapping>PageResponse=com.jugu.propertylease.common.model.PageResponse</schemaMapping>
      <schemaMapping>QueryFilter=com.jugu.propertylease.common.model.QueryFilter</schemaMapping>
      <schemaMapping>StringFilter=com.jugu.propertylease.common.model.StringFilter</schemaMapping>
      <schemaMapping>IdsFilter=com.jugu.propertylease.common.model.IdsFilter</schemaMapping>
      <schemaMapping>EnumFilter=com.jugu.propertylease.common.model.EnumFilter</schemaMapping>
      <schemaMapping>ListViewMeta=com.jugu.propertylease.common.model.ListViewMeta</schemaMapping>
      <schemaMapping>ColumnMeta=com.jugu.propertylease.common.model.ColumnMeta</schemaMapping>
      <schemaMapping>FilterFieldMeta=com.jugu.propertylease.common.model.FilterFieldMeta</schemaMapping>
      <schemaMapping>FilterOption=com.jugu.propertylease.common.model.FilterOption</schemaMapping>
      <schemaMapping>BatchRequest=com.jugu.propertylease.common.model.BatchRequest</schemaMapping>
    </schemaMappings>
    <generateApis>true</generateApis>
    <generateModels>true</generateModels>
    <generateSupportingFiles>false</generateSupportingFiles>
    <generateApiTests>false</generateApiTests>
    <generateModelTests>false</generateModelTests>
    <generateApiDocumentation>false</generateApiDocumentation>
    <generateModelDocumentation>false</generateModelDocumentation>
    <generatorName>spring</generatorName>
  </configuration>
  <goals><goal>generate</goal></goals>
</execution>

# ── 其余模块照此复制，修改以下两处 ──────────────────────────────────────
# account-internal   → inputSpec: account-internal.yaml   output: openapi-account-internal
# meter-external     → inputSpec: meter-external.yaml      output: openapi-meter-external
# meter-internal     → inputSpec: meter-internal.yaml      output: openapi-meter-internal
# contract-external  → inputSpec: contract-external.yaml   output: openapi-contract-external
# contract-internal  → inputSpec: contract-internal.yaml   output: openapi-contract-internal
# settlement-external→ inputSpec: settlement-external.yaml output: openapi-settlement-external
# settlement-internal→ inputSpec: settlement-internal.yaml output: openapi-settlement-internal
# schedule-external  → inputSpec: schedule-external.yaml   output: openapi-schedule-external

## 2. build-helper-maven-plugin：新增 generated-sources 目录
#    在现有 <sources> 块中追加：

<source>${project.build.directory}/generated-sources/openapi-account-external/src/main/java</source>
<source>${project.build.directory}/generated-sources/openapi-account-internal/src/main/java</source>
<source>${project.build.directory}/generated-sources/openapi-meter-external/src/main/java</source>
<source>${project.build.directory}/generated-sources/openapi-meter-internal/src/main/java</source>
<source>${project.build.directory}/generated-sources/openapi-contract-external/src/main/java</source>
<source>${project.build.directory}/generated-sources/openapi-contract-internal/src/main/java</source>
<source>${project.build.directory}/generated-sources/openapi-settlement-external/src/main/java</source>
<source>${project.build.directory}/generated-sources/openapi-settlement-internal/src/main/java</source>
<source>${project.build.directory}/generated-sources/openapi-schedule-external/src/main/java</source>

## 3. iam-manifest-maven-plugin：openapiDir 已配置为整个 openapi 目录
#    新增 yaml 文件放入 src/main/resources/openapi/ 后会自动被扫描
#    无需额外配置

## 4. application.yml 新增配置项
#    在现有配置后追加：

scheduling:
  enabled: true   # 控制 @Scheduled 是否生效（测试环境可设 false）

# Spring 调度线程池（避免 Cron 任务阻塞）
spring:
  task:
    scheduling:
      pool:
        size: 5
      thread-name-prefix: schedule-

# 新模块内部调用配置（同进程暂不需要，预留微服务化配置项）
internal:
  contract:
    base-url: http://localhost:${server.port}  # 微服务化后改为服务注册地址
  billing:
    base-url: http://billing-service:8080

## 5. MainServiceApplication.java 新增注解
#    @EnableScheduling（开启 @Scheduled 支持）

@SpringBootApplication
@EnableScheduling   // ← 新增
public class MainServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MainServiceApplication.class, args);
    }
}

## 6. Liquibase 文件命名与放置
#    放置路径：src/main/resources/db/changelog/changes/
#    文件按序号命名，master yaml 使用 includeAll 自动加载：
#
#    004-create-account-tables.xml
#    005-create-meter-tables.xml
#    006-create-contract-tables.xml
#    007-create-settlement-tables.xml
#    008-create-schedule-tables.xml
#
#    db.changelog-master.yaml 已配置 includeAll，无需修改。

## 7. 权限码汇总（iam-manifest-maven-plugin 自动从 yaml 扫描 x-required-permission）
#    以下权限码将在启动时自动同步到 iam_permission 表：
#
#    account:account:read
#    account:transaction:read
#    account:top-up:write
#    meter:reading:write
#    meter:reading:read
#    meter:settlement:read
#    meter:price:read
#    meter:price:write
#    contract:contract:write
#    contract:contract:read
#    contract:room:write
#    contract:tenancy:checkin
#    contract:tenancy:read
#    contract:tenancy:checkout
#    settlement:settlement:read
#    settlement:settlement:write
#    schedule:task:trigger
#    schedule:task:read

## 8. 推荐开发顺序（按依赖从底层到上层）
#
#    Step 1: account 模块（无外部依赖）
#            → Liquibase → jOOQ codegen → Repository → Service → Port → Delegate → 测试
#
#    Step 2: meter 模块（依赖 account Port、contract QueryPort）
#            → 先实现 meter 的 Repository / Service
#            → ContractQueryPort 提供 stub 实现用于 meter 本地测试
#
#    Step 3: contract 模块（依赖 account / meter / iam / asset / enterprise）
#            → ContractLifecycleService → ContractRoomService
#            → CheckInService（跨模块编排）
#            → CheckoutService + CallbackService
#            → RentBillService（ContractScheduleTrigger 实现）
#
#    Step 4: settlement 模块（依赖 account / meter / contract QueryPort）
#            → TenantCheckoutSettlementService
#            → ContractTerminationSettlementService
#
#    Step 5: schedule 模块（依赖 meter / contract 的 ScheduleTrigger）
#            → ScheduleTaskLogService → ScheduleTaskRunner → Delegate
#
#    Step 6: 集成测试（入住 → 日结 → 退宿 → 结算 完整链路）
