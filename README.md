# dbclasshw

数据库课程大作业：基于 Web 的多人在线文档编辑系统。采用前后端分离的单体架构，围绕文档编辑、权限共享和基础多人协作，体现数据库设计、业务功能与系统完整性。

## 技术栈

- 当前前端：Vue 3.5.45、Vite 7.3.8、Element Plus 2.14.8，使用 JavaScript；Vue 插件版本为 6.0.11。
- 当前后端：Java 21 编译目标、Spring Boot 3.5.16、Maven、MyBatis Starter 3.0.5、MySQL Connector/J 9.7.0（由 Spring Boot 管理版本）。
- 后续阶段：Spring Security、WebSocket、Tiptap；尚未集成。
- 版本管理：Git

## 项目目录

```text
dbclasshw/
├── frontend/          # Vue 页面、Vite 配置、package.json 与锁文件
├── backend/           # Maven 工程、Java 源码与 HTTP 集成测试
├── docs/              # 后续设计与验收材料
├── README.md          # 项目入口
├── PROJECT_SPEC.md    # 需求与范围依据
├── AGENTS.md          # Agent 开发规范
├── PROGRESS.md        # 阶段进度
└── .gitignore
```

`docs/` 空目录以 `.gitkeep` 跟踪。

## 当前开发状态

M0 和 M1 Task 1、Task 2 已完成。Task 2 已增加 MySQL 配置、过渡文档表初始化脚本及创建/查询 API，真实 MySQL 与后端重启持久化验证通过；详见 [阶段进度](PROGRESS.md)。首页的健康检查行为保持不变，本次未修改前端。功能边界以 [项目需求](PROJECT_SPEC.md) 为准。

## 环境要求

- 已验证环境：Windows 11 x64、Node.js 22.17.1、npm 10.9.2、JDK 21.0.10、Maven 3.9.16。
- 使用已有 JDK 21 和 Maven，无需安装 Java 17 或独立安装 Spring Boot。
- 前端工具要求 Node.js `^20.19.0 || >=22.12.0`，当前环境满足要求。
- 首次安装和构建需要访问 npm registry 与 Maven Central。文档 API 需要运行中的 MySQL 8.x（本机客户端为 8.0.39）；原健康检查不依赖数据库连接。

## 启动与验证

先按下方 MySQL 说明初始化并配置数据库，再从仓库根目录打开两个终端。启动后端：

```powershell
cd backend
mvn "-Dmaven.repo.local=target/maven-repository" spring-boot:run
```

这里显式使用工程内的 Maven 缓存，以适配 Agent 沙箱对用户目录的写入限制，不修改系统环境或全局 Maven 配置。缓存位于被 Git 忽略的 `target/`；清理该目录后需要重新下载依赖。`backend/.mvn/` 仅为本工程将依赖仓库统一到 Maven Central，避免 MyBatis 传递 POM 中的快照仓库访问超时；Maven 命令请在 `backend/` 下执行。

另一个终端安装前端依赖并启动：

```powershell
cd frontend
npm install
npm run dev
```

访问 [前端首页](http://127.0.0.1:5173/)，应显示“连接成功”及后端 JSON。后端默认使用端口 8080；Vite 固定使用 5173，端口占用时会报错。开发代理在 `frontend/vite.config.js` 中将 `/api` 转发至 `http://127.0.0.1:8080`，组件只使用相对路径。

停止后端（对应终端按 `Ctrl+C`），在首页点击“重新检查”应显示连接失败；重新启动后端后再次点击可恢复。页面每次打开自动检查一次，之后通过按钮检查，不持续轮询。

可直接检查后端或代理接口：

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:5173/api/health
```

成功响应均为 HTTP 200：

```json
{"status":"UP","message":"Backend is running"}
```

在各自目录执行构建或测试：

```powershell
# frontend/
npm run build

# backend/
mvn "-Dmaven.repo.local=target/maven-repository" verify
java -jar target/backend-0.1.0-SNAPSHOT.jar
```

JAR 启动是 Maven 启动的替代方式，不要同时占用 8080。普通后端测试使用随机端口和真实 HTTP 请求，无需数据库；真实 MySQL 测试需显式启用，见下文。前端构建产物在 `frontend/dist/`；当前 `/api` 代理仅配置于 Vite 开发服务器，生产环境部署转发留待后续任务。

本次已验证：依赖安装、前端构建、Maven 测试与打包、Maven/JAR 启动、直连接口、Vite 代理，以及真实 Edge 浏览器的加载、成功、重试和后端停止后的失败提示。浏览器检查临时工具与截图放在 `backend/target/browser-check/`，不属于项目依赖、不提交。

本地环境配置与真实凭据不得提交；后续提供必要配置模板时，仅使用占位值。

## MySQL 初始化与本地配置

在 `backend/` 目录复制配置模板，使用编辑器在本机填入有权访问项目数据库的账号和密码，不要把密码写在命令参数或聊天中：

```powershell
Copy-Item application-local.example.properties application-local.properties
```

`application-local.properties` 已被 Git 忽略，并从后端当前工作目录自动加载。也可使用进程级环境变量 `DBCLASSHW_DB_URL`、`DBCLASSHW_DB_USERNAME`、`DBCLASSHW_DB_PASSWORD`；不需要修改系统级环境变量。URL 必须指向 `dbclasshw`，默认地址为 `127.0.0.1:3306`，连接字符集为 utf8mb4。本地文件中的数据源属性优先于这些自定义环境变量；选择一种方式配置即可。Java properties 中的反斜杠等特殊字符需要按 properties 语法转义。

使用已有 MySQL 客户端交互式登录（替换账号占位值，`-p` 在终端安全提示输入密码）：

```powershell
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u YOUR_LOCAL_USERNAME -p
```

在 MySQL 提示符执行（客户端从 `backend/` 启动）：

```sql
SOURCE sql/init.sql;
SHOW CREATE TABLE dbclasshw.documents;
```

脚本只创建 `dbclasshw` 和 `documents`（均使用 `IF NOT EXISTS`），不删除、不重建或清空数据；已有表不会被自动迁移，如结构不匹配应先检查差异。数据库和表明确使用 utf8mb4，存储引擎为 InnoDB。应用不自动执行初始化 SQL。

过渡表字段：`id` 自增 BIGINT 主键、`title` 必填 VARCHAR(255)、`content` 可空 TEXT、`version` 默认 1、`created_at` / `updated_at` 为数据库维护的 DATETIME。用户和 `owner_id` 关系留待 M2，不创建其他业务表。

## 文档 API

以下 PowerShell 示例在后端启动后执行，会实际新增一条文档：

```powershell
$body = @{ title = 'My First Document'; content = 'Hello Database!' } | ConvertTo-Json
$response = Invoke-WebRequest -Method Post -Uri http://127.0.0.1:8080/api/documents -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$response.StatusCode  # 201
$document = $response.Content | ConvertFrom-Json
$document
Invoke-RestMethod http://127.0.0.1:8080/api/documents
Invoke-RestMethod "http://127.0.0.1:8080/api/documents/$($document.id)"
```

POST 返回新文档，并提供 `Location: /api/documents/{id}`。两个 GET 成功时为 200；列表为 JSON 数组，单篇为 JSON 对象，字段统一为 `id`、`title`、`content`、`version`、`createdAt`、`updatedAt`，时间使用 ISO 本地日期时间字符串。ID 和时间由数据库生成。

空、空白、缺失或超过 255 字符的标题返回 400；传入 `id`、`version`、时间或其他未知字段也返回 400。不存在的 ID 返回 404 与 `{"code":"DOCUMENT_NOT_FOUND","message":"Document not found"}`。数据库访问失败返回 503 与通用 `DATABASE_UNAVAILABLE` 信息，不返回 SQL、连接信息或堆栈。

手工验证持久化：记录返回的 `id`，停止并重新启动后端，再执行单篇 GET；可在 MySQL 中用 `SELECT * FROM dbclasshw.documents WHERE id = 实际ID;` 直接核对。

## Task 2 自动化验证

在 `backend/` 执行普通检查：

```powershell
mvn "-Dmaven.repo.local=target/maven-repository" verify
```

普通测试覆盖健康检查、无效输入和连接到不可用端口时返回 503；不会用内存数据库模拟持久化。真实 MySQL 用例默认跳过；本地配置完成后显式启用：

```powershell
$env:DBCLASSHW_MYSQL_TEST = 'true'
try {
    mvn "-Dmaven.repo.local=target/maven-repository" verify
} finally {
    Remove-Item Env:DBCLASSHW_MYSQL_TEST
}
```

真实用例要求账号具备项目库的创建/建表、读写权限；执行同一初始化脚本，仅操作 `dbclasshw`。用例通过真实 HTTP 创建带 UUID 标记的文档、直接 SQL 核对记录、检查列表/单篇与异常响应，并关闭后端应用上下文和连接池，再启动新的应用上下文验证数据仍然存在。测试不清空数据，保留 `M1-Task2-test-` 前缀的测试记录；报告位于 `target/surefire-reports/`。重启持久化断言仅在真实测试启用且通过时才视为验收通过。

本次真实验证结果：5 项测试全部通过，无失败、错误或跳过；自动化测试记录 ID 为 1。另外通过 JAR 进程创建 ID 2 文档，停止并重新启动 JAR 后仍返回同一文档。示例调用实际得到 HTTP 201，响应如下；单篇 GET 返回同一对象，列表 GET 返回包含该对象的数组：

```json
{"id":2,"title":"My First Document","content":"Hello Database!","version":1,"createdAt":"2026-11-12T21:25:39","updatedAt":"2026-11-12T21:25:39"}
```
