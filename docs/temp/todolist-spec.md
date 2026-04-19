# 非功能性能力 TODO List

> 版本：v1.0 | 生成时间：2026-01-18  
> 基于系统架构 Spec v10.0 + Security-Starter Spec v4.0 + Gateway Spec v2.0  
> 技术栈：Spring Boot 3.2.0 / Spring Cloud 2023.0.0 / Java 17 / Redis / Resilience4j

---

## 一、已覆盖的非功能性能力 ✅

| 能力域        | 实现状态 | 关键组件                                   |
|------------|------|----------------------------------------|
| **安全性**    | ✅ 完善 | JWT 双 Token、Zero-Trust、RBAC、数据权限、审计日志  |
| **可观测性**   | ✅ 完善 | TraceId 全链路、MDC 日志、Actuator、Prometheus |
| **错误处理**   | ✅ 完善 | 统一错误响应、异常体系收敛、Feign 异常处理               |
| **API 治理** | ✅ 完善 | OpenAPI 契约、同源原则、客户端自动生成                |

---

## 二、缺失能力与实施计划

### P0 - 必须实现（核心高可用能力）

#### 1. 限流与熔断

**目标：** 防止雪崩效应，保护下游服务

```markdown
- [ ] **Gateway 限流增强** (gateway 模块)
  - [ ] 实现 `RateLimitFilter` 多维度支持
    - [ ] IP 维度限流（当前已实现）
    - [ ] 用户维度限流（从 JWT 解析 userId）
    - [ ] 服务维度限流（按目标服务配置）
  - [ ] 配置化限流参数
    ```yaml
    gateway:
      rate-limit:
        enabled: true
        default-capacity: 100
        default-refill-rate: 20
        rules:
          - path-pattern: /api/iam/**
            capacity: 50
            refill-rate: 10
          - path-pattern: /api/order/**
            capacity: 200
            refill-rate: 50
    ```
  - [ ] 限流 Metrics 暴露（Prometheus）
  - [ ] 单元测试：并发限流测试

- [ ] **微服务熔断** (microservice-starter-parent 统一管理)
  - [ ] 添加 Resilience4j 依赖
    ```xml
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>2.2.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-micrometer</artifactId>
    </dependency>
    ```
  - [ ] 实现 `ServiceTokenClientInterceptor` 重试 + 熔断
    - [ ] Retry 配置（3 次，指数退避）
    - [ ] CircuitBreaker 配置（失败率>50% 打开，5s 后半开）
    - [ ] TimeLimiter 配置（超时 3s）
  - [ ] 配置模板
    ```yaml
    resilience4j:
      circuitbreaker:
        configs:
          default:
            slidingWindowSize: 10
            failureRateThreshold: 50
            waitDurationInOpenState: 5000
            permittedNumberOfCallsInHalfOpenState: 3
        instances:
          billing-service:
            baseConfig: default
      retry:
        configs:
          default:
            maxAttempts: 3
            waitDuration: 1000
            enableExponentialBackoff: true
            exponentialBackoffMultiplier: 2
      timelimiter:
        configs:
          default:
            timeoutDuration: 3s
    ```
  - [ ] 集成测试：模拟下游失败验证熔断

- [ ] **隔离舱模式** (P1 可选)
  - [ ] ThreadPoolBulkhead 配置（服务间调用线程池隔离）
  - [ ] 监控面板集成 Grafana Dashboard
```

**验收标准：**

- Gateway 限流 QPS 可控，超限返回 429
- 下游服务失败时熔断器自动打开，快速失败
- Prometheus 可查询限流/熔断指标

---

#### 2. Outbox 最终一致性

**目标：** 跨服务事务最终一致性，避免分布式事务

```markdown
- [ ] **数据库设计** (common 或各服务独立)
  - [ ] Liquibase changelog 创建 `outbox_events` 表
    ```sql
    CREATE TABLE outbox_events (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        event_id VARCHAR(64) UNIQUE NOT NULL,      -- UUID 防重
        event_type VARCHAR(100) NOT NULL,           -- 事件类型
        aggregate_type VARCHAR(100) NOT NULL,       -- 聚合根类型
        aggregate_id VARCHAR(100) NOT NULL,         -- 聚合根 ID
        payload JSON NOT NULL,                      -- 事件载荷
        status VARCHAR(20) DEFAULT 'PENDING',       -- PENDING/SENT/FAILED
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        sent_at DATETIME NULL,                      -- 发送成功时间
        error_message TEXT NULL,                    -- 失败原因
        retry_count INT DEFAULT 0                   -- 重试次数
    );
    CREATE INDEX idx_status_created ON outbox_events(status, created_at);
    ```

- [ ] **OutboxEventPublisher** (common 模块)
  - [ ] 定义 `OutboxEvent` 基类
    ```java
    public record OutboxEvent(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Object payload
    ) {}
    ```
  - [ ] 实现 `OutboxEventRepository` 接口（jOOQ 实现）
  - [ ] 业务 Service 注入 Repository，在 `@Transactional` 内保存事件
  - [ ] 单元测试：事务回滚时事件不保存

- [ ] **OutboxEventSender** (定时任务，每服务独立实现)
  - [ ] `@Scheduled(fixedDelay = 5000)` 扫描 PENDING 事件
  - [ ] 批量处理（每次最多 100 条）
  - [ ] 调用目标服务 `/internal` 接口
  - [ ] 成功 → SENT，失败 → 重试计数（最多 3 次）→ FAILED
  - [ ] 告警：FAILED 事件超过阈值发送邮件/钉钉
  - [ ] 集成测试：定时任务自动发送事件

- [ ] **幂等性保障** (接收方)
  - [ ] `/internal` 接口必须检查 `event_id` 唯一约束
  - [ ] 使用 `INSERT IGNORE` 或 `ON DUPLICATE KEY UPDATE` 防重
  - [ ] 示例：
    ```java
    // 接收方 DelegateImpl
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (!outboxEventRepository.existsByEventId(event.getEventId())) {
            // 处理业务逻辑
        }
    }
    ```

- [ ] **清理策略** (P1 可选)
  - [ ] 定时清理 30 天前的 SENT 事件
  - [ ] FAILED 事件保留 90 天供排查
```

**验收标准：**

- 业务事务提交后 5s 内事件发出
- 重复事件自动去重
- FAILED 事件可人工介入处理

---

#### 3. 缓存抽象

**目标：** 统一缓存使用规范，防护缓存异常

```markdown
- [ ] **缓存配置** (microservice-starter-parent)
  - [ ] 添加 Redis 依赖（可选，按需引入）
    ```xml
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    ```
  - [ ] 自动装配 `CacheManager`
    ```java
    @AutoConfiguration
    @ConditionalOnClass(RedisConnectionFactory.class)
    public class CacheAutoConfiguration {
        @Bean
        public CacheManager cacheManager(RedisConnectionFactory factory) {
            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))              // 默认 TTL 30 分钟
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()));
            
            return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(getDefaultCacheConfigs())
                .build();
        }
    }
    ```

- [ ] **缓存注解封装** (security-starter 或 common)
  - [ ] 定义 `@CacheResult` 封装（带异常降级）
    ```java
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Cacheable(cacheResolver = "fallbackCacheResolver")  // 缓存失败不抛异常
    public @interface CacheResult {
        String value();        // cacheName
        String key();          // SpEL 表达式
        long ttlMinutes() default 30;
    }
    ```
  - [ ] 实现 `FallbackCacheResolver`（缓存异常时返回 null，不中断业务）

- [ ] **缓存工具类** (可选)
  - [ ] `CacheService` 接口
    ```java
    public interface CacheService {
        <T> T get(String cacheName, String key, Class<T> type);
        void put(String cacheName, String key, Object value);
        void evict(String cacheName, String key);
        void clear(String cacheName);
    }
    ```
  - [ ] Redis 实现（支持手动操作场景）

- [ ] **缓存防护** (P1)
  - [ ] 缓存穿透：布隆过滤器（可选）
  - [ ] 缓存击穿：互斥锁（`synchronized` + 双重检查）
  - [ ] 缓存雪崩：随机 TTL（±5 分钟）

- [ ] **监控** (P1)
  - [ ] Cache Metrics（命中率、响应时间）
  - [ ] Redis 连接池监控
```

**验收标准：**

- `@CacheResult` 注解透明使用
- Redis 故障时业务不中断（降级查 DB）
- 缓存命中率 > 80%

---

#### 4. 健康检查增强

**目标：** K8s 探针就绪检查，依赖服务可达性验证

```markdown
- [ ] **自定义 HealthIndicator** (各微服务)
  - [ ] `DatabaseHealthIndicator`（检查连接池状态）
    ```java
    @Component
    public class DatabaseHealthIndicator implements HealthIndicator {
        private final DataSource dataSource;
        
        @Override
        public Health health() {
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(1)) {
                    return Health.up().build();
                }
            } catch (SQLException e) {
                return Health.down(e).build();
            }
            return Health.down().build();
        }
    }
    ```
  
  - [ ] `RedisHealthIndicator`（如有 Redis）
  - [ ] `DownstreamServiceHealthIndicator`（检查下游服务 HTTP 可达性）

- [ ] **Readiness/Liveness 探针分离** (Gateway + 微服务)
  - [ ] Liveness：应用存活（基础检查）
  - [ ] Readiness：依赖就绪（DB、Redis、下游服务）
  - [ ] K8s 探针配置示例：
    ```yaml
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
      initialDelaySeconds: 30
      periodSeconds: 10
    
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 30
      periodSeconds: 10
    ```

- [ ] **健康检查分组** (Spring Boot 3.2+)
  ```yaml
  management:
    health:
      group:
        liveness:
          include: ping
        readiness:
          include: db, redis, downstream
  ```

```

**验收标准：**
- K8s Pod 启动时 Readiness 检查通过才接入流量
- DB/Redis 不可用时 Readiness 返回 DOWN
- Liveness 始终反映应用进程状态

---

### P1 - 重要实现（运维与稳定性）

#### 5. 重试机制

**目标：** 网络抖动自动恢复，幂等性保障

```markdown
- [ ] **ServiceTokenClientInterceptor 重试逻辑**
  - [ ] 仅对 5xx 错误重试（4xx 不重试）
  - [ ] 指数退避：1s → 2s → 4s
  - [ ] 最大重试 3 次
  - [ ] 配置化：
    ```yaml
    client:
      retry:
        max-attempts: 3
        initial-interval: 1000
        multiplier: 2
        retryable-status-codes: 502,503,504
    ```

- [ ] **幂等性 Token 生成工具**
  - [ ] `IdempotencyKeyGenerator` 基于请求指纹
  - [ ] 接收方校验幂等 Key（Redis 原子操作）
  - [ ] 示例：
    ```java
    String idempotencyKey = IdempotencyKeyGenerator.generate(method, args);
    if (!redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", Duration.ofHours(1))) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_REQUEST", "请勿重复提交");
    }
    ```
```

**验收标准：**

- 网络抖动自动重试成功
- 重复请求被拦截

---

#### 6. 日志增强

**目标：** 结构化日志便于 ELK 分析，敏感数据脱敏

```markdown
- [ ] **结构化日志配置** (logback-spring.xml)
  - [ ] JSON 格式输出（便于 ELK 收集）
    ```xml
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${SERVICE_NAME}","env":"${SPRING_PROFILES_ACTIVE}"}</customFields>
        </encoder>
    </appender>
    ```
  
- [ ] **敏感字段脱敏** (common 模块)
  - [ ] 实现 `SensitiveDataMaskingConverter`
    ```java
    @Converter
    public class SensitiveDataMaskingConverter implements DynamicFieldConverter<Object, String> {
        @Override
        public String convert(Object source) {
            if (source instanceof String str) {
                // 手机号：138****1234
                // 身份证：110101********1234
                // 银行卡：6222 **** **** 1234
            }
        }
    }
    ```
  - [ ] 注解标记需脱敏字段
    ```java
    @Sensitive(type = SensitiveType.PHONE)
    private String phone;
    ```

- [ ] **日志级别动态调整**
  - [ ] 暴露 `/actuator/loggers` 端点
  - [ ] 配置权限控制（仅管理员可修改）
  ```

POST /actuator/loggers/com.jugu.propertylease
{ "configuredLevel": "DEBUG" }

  ```
```

**验收标准：**

- ELK 可直接解析 JSON 日志
- 日志中无明文敏感信息
- 生产环境可动态调日志级别

---

#### 7. 配置刷新

**目标：** 配置变更无需重启服务

```markdown
- [ ] **文件监听实现** (common 模块)
  - [ ] 实现 `FileWatchingConfigurationRefresher`
    ```java
    @Component
    @ConditionalOnProperty(name = "config.refresh.enabled", havingValue = "true")
    public class FileWatchingConfigurationRefresher {
        
        private final WatchService watchService;
        private final Path configPath;
        
        @PostConstruct
        public void start() throws IOException {
            // 监听 application.yml 变化
            Executors.newSingleThreadExecutor().submit(this::watchLoop);
        }
        
        private void watchLoop() {
            while (!Thread.interrupted()) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context().toString().contains("application.yml")) {
                        refreshConfiguration();
                    }
                }
                key.reset();
            }
        }
        
        private void refreshConfiguration() {
            // 发布 ContextRefreshedEvent 或调用 RefreshScope
        }
    }
    ```

- [ ] **配置刷新作用域**
  - [ ] 标记需刷新的 Bean
    ```java
    @Component
    @RefreshScope  // 自研注解，模拟 Spring Cloud
    public class RateLimitProperties {
        // 配置变更时重新注入
    }
    ```
  - [ ] 实现 `@RefreshScope` 代理逻辑（CGLIB 动态代理）

- [ ] **配置变更审计** (可选)
  - [ ] 记录变更历史（谁、何时、改了什么）
  - [ ] 配置差异对比工具（Git diff 风格）
```

**验收标准：**

- K8s ConfigMap 更新后 1min 内生效
- 限流/熔断等配置可动态调整

---

#### 8. 安全加固

**目标：** 消除安全隐患

```markdown
- [ ] **CSRF 显式禁用**
  - [ ] Gateway（WebFlux）：
    ```java
    http.csrf(csrf -> csrf.disable());
    ```
  - [ ] 微服务（Servlet）：
    ```java
    http.csrf(csrf -> csrf.disable());
    ```

- [ ] **请求体大小限制**
  - [ ] Gateway：
    ```yaml
    spring:
      codec:
        max-in-memory-size: 1MB
    ```
  - [ ] 微服务：
    ```yaml
    server:
      tomcat:
        max-http-form-post-size: 1MB
        max-swallow-size: 1MB
    ```

- [ ] **JWT Secret 轮换方案** (P2)
  - [ ] 双密钥过渡期（新旧密钥共存 24h）
  - [ ] 密钥版本号写入 JWT Claims
  - [ ] 配置热更新监听
```

**验收标准：**

- CSRF 攻击无法得逞
- 大请求被拒绝（413）
- JWT Secret 可无缝轮换

---

### P2 - 锦上添花（质量提升）

#### 9. 性能优化

```markdown
- [ ] **数据库连接池监控**
  - [ ] HikariCP Metrics 暴露 Prometheus
  - [ ] 告警：活跃连接数 > 80%

- [ ] **线程池监控**
  - [ ] Tomcat 线程池指标
  - [ ] 告警：线程池使用率 > 80%

- [ ] **慢查询日志**
  - [ ] MySQL 慢查询配置（>1s）
  - [ ] jOOQ SQL 日志级别调整
```

---

#### 10. 测试支撑

```markdown
- [ ] **集成测试基类**
  - [ ] `@IntegrationTest` 封装（@SpringBootTest + Testcontainers）
  - [ ] 测试数据库自动清理

- [ ] **Contract Testing**
  - [ ] OpenAPI 契约验证测试
  - [ ] 消费者驱动契约（CDC）

- [ ] **性能测试基准**
  - [ ] JMeter/Gatling 脚本
  - [ ] 单接口 QPS > 1000
  - [ ] P99 延迟 < 200ms
```

---

#### 11. 代码质量

```markdown
- [ ] **静态代码分析**
  - [ ] SpotBugs 配置（零警告）
  - [ ] PMD 配置（零警告）

- [ ] **代码覆盖率**
  - [ ] Jacoco 配置（目标 80%+）
  - [ ] CI 流水线门禁

- [ ] **架构约束测试**
  - [ ] ArchUnit 规则
    - 禁止循环依赖
    - 分层依赖约束
    - 包命名规范
```

---

## 三、实施计划与里程碑

### 第一阶段（2 周）：P0 核心能力

**Week 1:**

- ✅ Gateway 限流增强
- ✅ Outbox 事件表设计 + Publisher
- ✅ 健康检查增强

**Week 2:**

- ✅ Resilience4j 熔断重试
- ✅ 缓存抽象封装
- ✅ 日志结构化改造

**交付物：**

- Gateway 限流配置文档
- Outbox 使用示例代码
- K8s 探针配置模板

---

### 第二阶段（1 周）：P1 重要能力

**Week 3:**

- ✅ Outbox Sender 定时任务
- ✅ 配置刷新机制
- ✅ 敏感数据脱敏
- ✅ CSRF 禁用 + 请求体限制

**交付物：**

- 配置刷新 API 文档
- 脱敏注解使用手册

---

### 第三阶段（可选）：P2 锦上添花

**Week 4+:**

- ⚪ 监控面板（Grafana Dashboard）
- ⚪ 性能测试基准报告
- ⚪ 代码质量工具链集成

**交付物：**

- Grafana 监控大屏
- 性能测试报告
- 代码覆盖率报告

---

## 四、验收标准总览

| 能力域        | 验收指标           | 验证方式           |
|------------|----------------|----------------|
| **限流**     | QPS 可控，429 响应  | JMeter 压测      |
| **熔断**     | 失败率>50% 自动打开   | 模拟下游 500       |
| **Outbox** | 5s 内事件发出       | 集成测试验证         |
| **缓存**     | 故障不中断业务        | Redis 宕机演练     |
| **健康检查**   | K8s 探针就绪       | K8s 环境部署       |
| **重试**     | 网络抖动自动恢复       | 网络模拟延迟         |
| **日志**     | JSON 可解析，无敏感信息 | ELK 验证         |
| **配置刷新**   | 1min 内生效       | ConfigMap 更新测试 |

---

## 五、风险与缓解措施

| 风险                | 影响         | 缓解措施                 |
|-------------------|------------|----------------------|
| Resilience4j 学习曲线 | 开发效率下降     | 提供配置模板 + 示例代码        |
| Outbox 增加 DB 压力   | 性能下降 10%   | 批量处理 + 索引优化          |
| 缓存一致性难题           | 脏数据风险      | 明确 TTL + 延时双删策略      |
| 配置刷新作用域复杂         | Bean 状态不一致 | 限制@RefreshScope 使用范围 |

---

## 六、附录

### A. 依赖版本清单

```xml
<properties>
    <resilience4j.version>2.2.0</resilience4j.version>
    <logstash-logback.version>7.4</logstash-logback.version>
</properties>
```

### B. 参考文档

- [Resilience4j 官方文档](https://resilience4j.readme.io/)
- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [K8s Health Checks](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

---

**文档维护：** 本 TODO List 随项目进展动态更新，完成的任务标记为 ✅ 并移入"已覆盖能力"章节。
