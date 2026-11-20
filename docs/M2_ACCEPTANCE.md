# M2 / P0 MVP 验收

验收日期：2026-11-20。结论：**通过，M2 完成，具备进入 M3 的条件**。仅面向 README 描述的本地单机环境，不代表生产部署或负载测试认证。

## P0 覆盖与系统场景

| 范围 / 场景 | 本次证据 | 结果 |
| --- | --- | --- |
| 用户管理 / A | 真库 HTTP 验证重复用户名/邮箱、BCrypt、错误密码、Session fixation、CSRF；Edge 注册、修改邮箱、刷新、退出、旧 Session 拒绝、邮箱重新登录 | 已实现且验证通过 |
| 文档管理 / B | 真库 CRUD、输入、搜索特殊字符、旧文本；Edge 创建、格式工具栏、手动保存、搜索、重新打开、刷新、实际后端进程重启、删除后 404 | 已实现且验证通过 |
| 权限共享 / C | 真库 OWNER/EDITOR/VIEWER 全操作矩阵、私有文档隔离、IDOR、伪造所有者；Edge 三个独立 Session 添加/修改/撤销共享、编辑者保存、查看者只读 | 已实现且验证通过 |
| 协作冲突 / D | 真库提交前无通知、提交后通知、409/回滚不广播；Edge 双向同步、查看者接收、草稿不变、旧版本 409、安全重载、服务器版本递增至 8 | 已实现且验证通过 |
| 撤销与并发 / E | 真库权限事务持锁，排队保存重新校验后返回 403/404；同版本并发仅一成功；Edge 降级禁写、撤销断开、后续更新不泄露且保留草稿 | 已实现且验证通过 |
| 异常恢复 / F | Edge 实际停机和恢复、HTTP 失败输入保留、WebSocket 自动/手动重连及补查、外部注销、删除、空搜索、重复点击仅一次 POST、延迟真实 GET 快速切换；真库 Session 过期关闭连接且 HTTP 401 | 已实现且验证通过 |

对 PROJECT_SPEC 的 P0 无未实现项、无验证不足的已知阻塞项。P1 自动保存/历史版本以及 P2 功能不纳入本次交付。

## 数据库与安全

- 三张业务表：users、documents、document_permissions，InnoDB / utf8mb4。验证主键、用户名/邮箱及共享唯一约束、owner_id 非空和外键、角色 CHECK、级联清理。
- 新增 `SchemaMigrationMySqlTest`：在独立随机测试库建立 M1 文档，运行真实所有权/富文本/共享迁移及各自支持阶段的重复执行。ID 42、中文/emoji/HTML 字面文本、版本 7 和原始时间不变；旧文档归属禁用迁移账号。通过 `SHOW CREATE TABLE` 对比全新初始化和升级后的完整定义，包含索引列、外键目标/动作和 CHECK。
- 真库回归同时对比当前项目库与新安装的列/约束；本次不在已有业务库重新执行迁移，不修改已有账号密码或清空数据。测试库及随机标记用户保留，仅删除本测试创建的目标文档；中断用例可能留下带测试前缀的记录。
- Session Cookie 为 HttpOnly；登录轮换 Session ID，退出旧 Session 失效；缺失/无效/登录前旧 CSRF Token 被拒绝。HTTP 服务端独立校验所有权和角色。
- WebSocket 校验 Session、Origin、订阅权限和发送时权限；匿名/非法 Origin/无权限订阅/伪造成功事件被拒绝。事务回滚不发送成功通知。
- 恶意富文本节点/属性/链接和超限 JSON 被拒绝，旧 HTML 字符串安全显示；参数化 SQL 与 LIKE 特殊字符转义通过。错误响应采用固定业务 JSON，不返回哈希、SQL 或堆栈。

## 执行结果与复现

| 检查 | 结果 / 证据位置（本机忽略目录） |
| --- | --- |
| `frontend/`：`npm run build` | 通过，Vite 7.3.8 |
| `backend/`：设置 `DBCLASSHW_MYSQL_TEST=true` 后 `mvn "-Dmaven.repo.local=target/maven-repository" -B -ntp verify` | 14 项测试，失败 0、错误 0、跳过 0，打包通过；`target/m2-acceptance-final.log`、`target/surefire-reports/` |
| Edge：`node src/test/browser/lifecycle.cjs` | 通过 A/B/F；真实重启前后 API 文档对象逐字段一致；`target/browser-check/lifecycle-backend.log` |
| Edge：`node src/test/browser/sharing.cjs` | 通过三用户共享 UI；截图 `target/browser-check/sharing-revoked-draft.png` |
| Edge：`node src/test/browser/ws.cjs` | 通过 D/E/F；截图 `target/browser-check/ws-draft-protected.png` |
| 已有额外浏览器回归 `target/browser-check/rich.cjs` | 通过全部工具栏格式、旧文本安全转换、双标签 409、保存失败/离开提醒及 CRUD；截图 `rich-success.png` |

浏览器使用本机 Edge + 现有临时 Playwright Core 1.65.0，不增加应用依赖，不用 Mock 冒充成功结果。前三个脚本已纳入版本管理；完整前置条件和命令见 README“重复执行 M2 浏览器验收”。额外 rich 脚本和截图为本机证据，旧文本/富文本核心断言也保存在 Maven 测试中。

## 问题与边界

- 未发现需要修复的 P0/P1 业务缺陷，无遗留阻塞；本次不修改业务代码或表结构。
- 初次检查时 MySQL 停机，真库测试中止，用户启动后完整重跑通过。
- 验收期间曾并行运行 JAR 和重新打包，Windows 文件占用导致 repackage 失败并影响该轮浏览器访问。停止自身后端、重新构建后，相关浏览器用例和完整 `verify` 均通过；README 增加先停止 JAR 再打包的说明。
- 新浏览器用例的断言修正了 contenteditable 的尾部显示换行；共享旧用例改为验证 WebSocket 推送降级后的禁用按钮，真实服务端 403/404 由 MySQL 测试独立覆盖。
- 非阻塞限制：单机内存 Session/连接；重启需重新登录；未保存草稿仅在页面内存；通知尽力投递，以 HTTP 补查和版本号为准；不能擦除此前已加载的内容。不提供自动合并、历史版本、自动保存或 OT/CRDT。
- 未进行生产部署、压力/长时间运行测试；不将本次功能验收解释为相关保证。M3 范围待下一任务确定。
