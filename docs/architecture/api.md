---

# API 分层与 URL 设计规范

> 适用于基于 Gateway + 微服务架构的系统
> 目标：**API 稳定、服务可演进、安全边界清晰、便于长期维护**

---

## 1. 设计目标

本规范用于统一以下内容：

* API URL 设计规范
* Gateway 路由原则
* 外部 / 内部 API 分层
* Domain 与 Service 的关系约束
* 认证与鉴权边界
* 多服务拆分与演进策略

核心目标：

> **API 面向业务，服务面向实现，Gateway 负责隔离变化**

---

## 2. API 分层总览

系统中的 API 分为三类：

| 层级     | 前缀                    | 是否对外 | 说明        |
|--------|-----------------------|------|-----------|
| 对外 API | `/api/**`             | ✅    | 客户端访问     |
| 内部 API | `/internal/user/**`   | ❌    | 用户触发服务间调用 |
| 内部 API | `/internal/system/**` | ❌    | 系统触发服务间调用 |

---

## 3. 对外 API 规范（External API）

### 3.1 URL 结构

```
/api/{domain}/{resource}/...
```

| 部分       | 含义                    |
|----------|-----------------------|
| api      | 固定前缀                  |
| domain   | 业务领域（Bounded Context） |
| resource | 资源（可选）                |

---

### 3.2 Domain 规范（强制）

Domain 表示**业务领域，不是服务名**。

✅ 合法示例：

```
iam
order
room
billing
asset
```

❌ 禁止：

```
user-service
order-backend
auth-center
```

---

### 3.3 Resource 规范

#### 情况一：Domain 本身是聚合根（推荐）

```http
GET /api/room/{id}
```

适用场景：

* Room
* Account
* Profile

此时 **domain = resource**

---

#### 情况二：Domain 下有多个资源

```http
GET /api/iam/users/{id}
GET /api/iam/roles/{id}
```

适用场景：

* IAM
* Order
* Asset

---

### 3.4 对外 API 示例

```http
POST   /api/iam/login
GET    /api/iam/users/{id}
GET    /api/order/orders/{id}
POST   /api/room
```

---

## 4. 内部 API 规范（Service-to-Service）

### 4.1 设计原则

* ❌ 不对外暴露
* ❌ 不稳定（允许变更）
* ✅ 服务间协作使用
* ✅ 支持 service token （user/system）

---

### 4.2 URL 规范

```
/internal/user/{resource}/...
/internal/system/{domain}/{action}
```

### 示例

```http
POST /internal/system/iam/verify-token
GET  /internal/user/load
POST /internal/user/orders/{1}
```

---

### 4.3 内部 API 特点

| 特性         | 说明            |
|------------|---------------|
| 不走 Gateway | 直接调用          |
| 可使用 RPC 风格 | 不强制 REST      |
| 强依赖认证      | service-token |
| 不对前端暴露     | 严格隔离          |

## 5. Domain 与 Service 的关系规范（核心）

### 5.1 推荐关系（✔）

```
Domain → Service
```

```text
/api/order/** → order-service
```

✔ 清晰
✔ 易维护
✔ 推荐模式

---

### 5.2 允许关系（✔ 但需谨慎）

```
多个 Domain → 一个 Service
```

例如：

```text
iam-service:
  - iam
  - role
  - permission
```

✔ 常见于早期
✔ 合理

---

### 5.3 谨慎关系（⚠）

```
一个 Domain → 多个 Service
```

例如：

```text
/api/order/**
    ├── order-command-service
    └── order-query-service
```

允许前提：

* 对外 API 不变
* Gateway 负责聚合
* 不暴露内部结构

---

### 5.4 禁止关系（❌）

```
/api/order-command/**
/api/order-query/**
```

原因：

* 暴露实现细节
* API 与架构耦合
* 无法平滑演进

---

## 6. Gateway 路由规范

### 6.1 路由原则

1. 只路由 `/api/**`
2. 不路由 `/internal/**`
3. domain → service 映射
4. 鉴权在 gateway 完成

---

### 6.2 示例配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: iam
          uri: ${services.iam}
          predicates:
            - Path=/api/iam/**
          filters:
            - JwtAuth
            - StripPrefix=2

        - id: order
          uri: ${services.order}
          predicates:
            - Path=/api/order/**
          filters:
            - JwtAuth
            - StripPrefix=2
```

---

## 7. 多环境配置规范

### application.yml

```yaml
services:
  iam: http://iam-service:8080
  order: http://order-service:8080
```

### application-local.yml

```yaml
services:
  iam: http://localhost:8081
  order: http://localhost:8082
```

---

## 8. 关于 Public API 的规范

### ❌ 不推荐

```
/public/login
```

### ✅ 正确方式

```http
/api/iam/login
```

由 Gateway 控制是否鉴权：

```yaml
security:
  permit:
    - /api/iam/login
```

👉 **是否需要认证是“策略”，不是 URL 语义**

---

## 9. 推荐目录结构（示例）

```
gateway
 ├── routes.yml
 ├── security.yml
 └── application.yml

iam-service
 ├── controller
 │   ├── ApiController      (/api/**)
 │   ├── InternalController (/internal/user/**)
 │   └── SystemController   (/internal/system/**)
```

---

## 10. 最终设计原则（可作为架构原则）

> 1. API 面向业务，不面向服务
> 2. Domain 是对外契约，Service 是内部实现
> 3. Gateway 是边界，不是转发器
> 4. 对外稳定，对内可变
> 5. 鉴权是策略，不是路径
> 6. 所有服务拆分不影响 API

---

