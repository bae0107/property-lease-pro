# IAM 分页资源示例模块

这个示例模块展示了以下内容：

- 统一 `PageRequest / PageResponse / ListViewMeta`
- `PageResourceDefinition` 声明式资源定义
- `PageQueryService` 通用过滤与分页执行
- `iam-users` / `iam-roles` / `iam-permissions` 三个端到端分页接口
- 无第三方依赖，可直接用 `javac` 编译并运行测试

## 编译与运行测试

```bash
cd java-module
./run-tests.sh
```
