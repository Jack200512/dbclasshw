# M4 交付素材索引

基准：2026-11-20 M3 最终稳定性检查。本文只定位报告、视频与答辩素材；功能边界见 [PROJECT_SPEC](../PROJECT_SPEC.md)，运行和迁移命令见 [README](../README.md)，阶段结果见 [PROGRESS](../PROGRESS.md)。UI 提交：`6f65dc1`。

## 1. 需求分析素材

角色：未登录访客；已登录用户；文档 OWNER / EDITOR / VIEWER（按文档计算，不是全站管理角色）。OWNER 可管理共享与删除；EDITOR 可保存/恢复；VIEWER 可阅读正文与历史。

| 用例模块 | 已实现流程 / API | 代码入口 |
| --- | --- | --- |
| 用户管理 | 注册、用户名/邮箱登录、资料/邮箱修改、退出；`/api/auth/*`、`/api/users/me` | [UserController](../backend/src/main/java/com/dbclasshw/user/UserController.java)、[UserPanel](../frontend/src/UserPanel.vue) |
| 文档管理 | 创建、列表/标题搜索、查看、富文本编辑、手动保存、删除；`/api/documents`、`/{id}` | [DocumentController](../backend/src/main/java/com/dbclasshw/document/DocumentController.java)、[DocumentWorkspace](../frontend/src/DocumentWorkspace.vue) |
| 共享权限 | OWNER 添加、改角色、撤销；成员按权限访问；`/{id}/permissions`、`/{userId}` | [PermissionController](../backend/src/main/java/com/dbclasshw/document/PermissionController.java)、[SharingPanel](../frontend/src/SharingPanel.vue) |
| 基础协作 | 提交后通知、HTTP 补查、无草稿自动同步、有草稿保留、旧版本 409 | [documentChannel](../frontend/src/documentChannel.js)、[DocumentSocketHandler](../backend/src/main/java/com/dbclasshw/sync/DocumentSocketHandler.java) |
| 历史恢复 | 倒序列表、只读预览、恢复产生新版本；`/{id}/versions`、`/{version}`、`/{version}/restore` | [DocumentVersionController](../backend/src/main/java/com/dbclasshw/document/DocumentVersionController.java)、[HistoryPanel](../frontend/src/HistoryPanel.vue) |

可据此绘制用例图，选取“共享编辑并保存”“冲突重载”“恢复历史”编写跨角色用例表。自动保存、逐字符协作和历史差异对比未实现。

## 2. 系统设计素材

单体前后端分离：浏览器 Vue → Vite `/api` 代理 → Spring Security → Controller → Service → MyBatis Mapper → MySQL。原生 WebSocket 复用 HTTP Session，负责通知，不承担持久化写入。

- 页面组装：[App.vue](../frontend/src/App.vue)；富文本：[RichTextEditor.vue](../frontend/src/RichTextEditor.vue)；请求与 CSRF：[api.js](../frontend/src/api.js)；格式兼容：[documentContent.js](../frontend/src/documentContent.js)。
- 后端启动：[DbclasshwApplication](../backend/src/main/java/com/dbclasshw/DbclasshwApplication.java)；配置：[application.properties](../backend/src/main/resources/application.properties)；开发代理：[vite.config.js](../frontend/vite.config.js)。
- 认证链：[SecurityConfig](../backend/src/main/java/com/dbclasshw/security/SecurityConfig.java)、[UserService](../backend/src/main/java/com/dbclasshw/user/UserService.java)。BCrypt、HttpOnly Session、登录轮换 Session ID、CSRF 与退出失效；修改者从认证主体获取。
- 写入流程：[DocumentService](../backend/src/main/java/com/dbclasshw/document/DocumentService.java) 的 `create/update/restore`：授权/版本校验 → 写文档与完整快照 → 原子提交 → 提交后通知。恢复保留来源内容/格式，版本递增。
- 协作流程：[DocumentSocketConfig](../backend/src/main/java/com/dbclasshw/sync/DocumentSocketConfig.java) 认证/Origin/订阅 → `DocumentSocketHandler.committed` 提交后重查权限 → 当前页面 HTTP 拉取；dirty 草稿不覆盖，重连补查。

## 3. 数据库设计素材

完整字段与建表依据：[init.sql](../backend/sql/init.sql)。四表均为 InnoDB / utf8mb4，自增 BIGINT 主键。

| 表 | 关系、约束与索引 |
| --- | --- |
| users | 用户名、邮箱各唯一；密码哈希与时间、enabled。禁用迁移账号不允许登录，无公开默认密码 |
| documents | `owner_id NOT NULL → users.id`，用户 1:N 拥有文档；正文+格式、当前版本、时间；所有者外键索引 |
| document_permissions | 用户与共享文档 M:N；唯一 `(document_id,user_id)`，user_id 索引，CHECK EDITOR/VIEWER；两个外键级联删除；OWNER 来自文档归属 |
| document_versions | 文档 1:N 快照，editor_id → users；唯一 `(document_id,version_number)`、editor_id 索引；版本/格式/变更类型/来源 CHECK；删除文档级联，删除被引用编辑者受限制 |

升级顺序（仅适用于对应旧结构）：[所有权](../backend/sql/migrate-m1-m2.sql) → [富文本](../backend/sql/migrate-m2-richtext.sql) → [共享](../backend/sql/migrate-m2-sharing.sql) → [历史](../backend/sql/migrate-m3-history.sql)。新库只执行最新版 init；先停写、备份，再按 README 选择缺失的迁移。MySQL DDL 隐式提交，不假定完整回滚。

历史 BASELINE 仅保留上线时的当前真实版本；所有者作为基线归属人，不代表实际最后修改者。此前不存在的历史不补造；异常当前快照会使迁移停止。

事务/并发定位：[DocumentService](../backend/src/main/java/com/dbclasshw/document/DocumentService.java) 的 `lockAccessible/update/restore`；[DocumentMapper](../backend/src/main/java/com/dbclasshw/document/DocumentMapper.java) 的 `FOR UPDATE`、版本/授权条件与 `version+1`；[PermissionService](../backend/src/main/java/com/dbclasshw/document/PermissionService.java) 的相同文档锁；[DocumentVersionMapper](../backend/src/main/java/com/dbclasshw/document/DocumentVersionMapper.java) 仅查询/插入快照。

## 4. 实现与测试素材

M3 最终检查：前端 `npm run build`、开启真库开关的 Maven `verify` 通过，**15 项测试，0 失败、0 错误、0 跳过**。复现命令以 README 为准，普通 Maven 测试跳过真库用例时不能作为完整验收。

| 测试素材 | 核心证据 |
| --- | --- |
| [DocumentMySqlIntegrationTest](../backend/src/test/java/com/dbclasshw/document/DocumentMySqlIntegrationTest.java) | BCrypt、唯一性、Session fixation/CSRF、退出、用户隔离、重启持久化、新装结构 |
| [RichDocumentMySqlTest](../backend/src/test/java/com/dbclasshw/document/RichDocumentMySqlTest.java) / [RichTextContentTest](../backend/src/test/java/com/dbclasshw/document/RichTextContentTest.java) | CRUD/搜索特殊字符、旧文本、版本竞争；恶意结构与大小白名单 |
| [SharingMySqlTest](../backend/src/test/java/com/dbclasshw/document/SharingMySqlTest.java) | 三角色矩阵、唯一/CHECK/FK/级联；撤销/降级事务后排队写入拒绝 |
| [DocumentSocketMySqlTest](../backend/src/test/java/com/dbclasshw/document/DocumentSocketMySqlTest.java) | 提交/回滚通知、Origin/订阅、权限、退出、过期 Session |
| [HistoryMySqlTest](../backend/src/test/java/com/dbclasshw/document/HistoryMySqlTest.java) | 隔离库触发器拒绝快照，验证创建/保存/恢复全部回滚且不广播；恢复/保存竞争、旧历史不变、纯文本、唯一/级联 |
| [SchemaMigrationMySqlTest](../backend/src/test/java/com/dbclasshw/document/SchemaMigrationMySqlTest.java) | 独立库从 M1 升级，重复执行与异常停止、保留旧记录，四表完整定义与新装一致 |
| [DocumentUnavailableIntegrationTest](../backend/src/test/java/com/dbclasshw/document/DocumentUnavailableIntegrationTest.java) / [HealthIntegrationTest](../backend/src/test/java/com/dbclasshw/health/HealthIntegrationTest.java) | 401/403、存储失败 503、健康检查；无虚假成功 |

本次真实 Edge 抽样：[ui.cjs](../backend/src/test/browser/ui.cjs)、[sharing.cjs](../backend/src/test/browser/sharing.cjs)、[history.cjs](../backend/src/test/browser/history.cjs)、[ws.cjs](../backend/src/test/browser/ws.cjs) 全部通过；覆盖注册/退出重登、CRUD/搜索、三角色、历史恢复、双向同步、草稿、409、撤销和重连。UI 在 1366×768 / 1920×1080 检查通过，无页面脚本错误。

本机证据位于忽略目录：`backend/target/m3-final-stability.log`、`surefire-reports/`、`m3-final-database.log`、`browser-check/ui-layout-*.png`、`ui-history-*.png`、`history-success.png`、`ws-draft-protected.png`。这些不会随 Git 交付；M4 可重跑对应脚本再择要导出截图。历史 P0 验收见 [M2_ACCEPTANCE](M2_ACCEPTANCE.md)。

最终只读数据库核对：现有四表定义与新装完整一致；无孤立所有者/权限/历史、无缺失或不匹配的当前快照，启用账号均为 BCrypt。失败注入与完整迁移仅在独立测试库执行，原业务库不清空。

## 5. 五分钟内演示路线

预先启动 MySQL/后端/前端，准备三个本地注册账号 A/B/C 和两个独立浏览器环境；密码仅在本机输入，不写入素材。准备普通富文本文档，包含标题、加粗/列表和至少两次保存形成的历史；旧纯文本可作为可选兼容素材。

| 时间预算 | 操作 / 画面 |
| --- | --- |
| 0:00–0:30 | A 登录，说明私人文档、角色与手动保存 |
| 0:30–1:20 | 新建文档，设置富文本并保存，再修改保存产生历史 |
| 1:20–2:10 | A 给 B EDITOR、C VIEWER，展示成员管理和权限差别 |
| 2:10–3:10 | B 保存，A 无草稿自动同步；说明有草稿只提示、旧版本 409 |
| 3:10–4:20 | 预览 v1、确认恢复；显示新版本递增，另一用户同步恢复结果 |
| 4:20–4:50 | 刷新核对持久化，简述事务、权限及范围边界 |

仅用真实 API/数据库，不使用硬编码演示结果；避免录到本地配置、数据库终端凭据或 Cookie。此表为路线索引，不替代正式视频脚本。

## 6. 答辩代码入口与边界

按“页面按钮 → api.js → Controller → Service → Mapper → SQL”解释普通请求；按“事务提交 → DocumentEvent → SocketHandler → documentChannel → 工作台核对”解释同步。上文链接已覆盖认证、分层、SQL、权限、乐观锁、WebSocket 与历史事务，优先理解 Service 的锁定/回滚边界，而非背诵组件样式。

单机内存 Session/连接，重启需重新登录；草稿仅在页面内存。通知尽力投递，HTTP 补查与版本号保证当前状态；不能远程擦除已加载内容。不支持自动保存、自动合并、OT/CRDT、多节点或生产部署；生产压力/长时间稳定性未验证。M4 重点为报告、视频和代码讲解，不扩大业务范围。
