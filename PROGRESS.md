# 阶段进度

当前阶段：**M1 最小全栈闭环（Task 1、Task 2 已完成）**。需求详情统一维护在 [PROJECT_SPEC.md](PROJECT_SPEC.md)。

- [x] 确定项目目标及 P0 / P1 / P2 功能边界。
- [x] 确定 Vue 3 前端、Spring Boot 后端及 MySQL 的技术栈与前后端分离单体架构。
- [x] 记录用户、文档、共享权限及历史版本的概念模型。
- [x] 建立 `frontend/`、`backend/`、`docs/` 及空目录占位文件。
- [x] 完成 README、需求文档、Agent 规范与 Git 忽略规则。
- [x] 检查目录、文档一致性及 Git 跟踪范围。
- [x] M1 Task 1：建立 JavaScript Vue 3 + Vite + Element Plus 前端与 Java 21 + Spring Boot Maven 后端。
- [x] 实现 `/api/health`，通过 Vite 代理发起真实 HTTP 请求，展示加载、成功、失败及重试状态。
- [x] 完成依赖安装、前端构建、后端测试（1 项通过、无失败或跳过）和打包；验证 Maven 与 JAR 启动、直连接口及代理响应。
- [x] 使用真实 Edge 浏览器验证加载、成功、重试及后端停止后的失败提示；同步启动说明。
- [x] Task 2：增加 MyBatis/MySQL 依赖、环境变量与本地配置模板、utf8mb4 非破坏性初始化 SQL。
- [x] Task 2：实现 Controller → Service → Mapper 分层，支持文档创建、列表和单篇查询；增加输入校验、404 与数据库失败响应。
- [x] Task 2：增加无数据库异常测试及显式启用的真实 MySQL 测试（含重启应用后的持久化断言），补充运行说明。
- [x] Task 2：Maven 测试与打包通过；启用真实 MySQL 用例后共 5 项测试通过，无失败、错误或跳过。
- [x] Task 2：验证真实建表、HTTP 创建/查询、直接 SQL 核对及重启应用上下文后的持久化；另以 JAR 独立进程停止并重启，确认 ID 2 文档仍可查询，健康检查与 Vite 代理正常。
- [ ] Task 3：在 Task 2 真实数据库验收完成后，按下一任务要求开发；本次不提前实现。

遗留问题：无阻塞 Task 3 的问题。本地凭据已放入 Git 忽略的 `backend/application-local.properties`，示例恢复为占位值。真实验证保留两条记录：ID 1 为带 UUID 的自动化测试文档，ID 2 为手工调用示例，未删除已有数据。未创建用户、权限或历史版本表，未修改前端。

工程约束：沙箱无法写入默认 Maven 缓存，验证使用 `target/maven-repository`；项目级 Maven 配置只访问 Central，避免传递 POM 的快照仓库超时；未修改系统环境或全局 Maven 配置。HTTP 集成测试不使用 Mock，已排除不需要且在沙箱中触发 JVM 附加限制的 Mockito 依赖。
