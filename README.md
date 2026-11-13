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

M0、M1 Task 1–3 已完成。前端文档工作台已接入真实文档 API，支持创建、列表与只读详情，数据保存到 MySQL；刷新页面及重启后端后的持久化验证通过。原健康检查仍可用。详见 [阶段进度](PROGRESS.md)，功能边界以 [项目需求](PROJECT_SPEC.md) 为准。

## 环境要求

- 已验证环境：Windows 11 x64、Node.js 22.17.1、npm 10.9.2、JDK 21.0.10、Maven 3.9.16。
- 使用已有 JDK 21 和 Maven，无需安装 Java 17 或独立安装 Spring Boot。
- 前端工具要求 Node.js `^20.19.0 || >=22.12.0`，当前环境满足要求。
- 首次安装和构建需要访问 npm registry 与 Maven Central。文档 API 需要运行中的 MySQL 8.x（本机客户端为 8.0.39）；原健康检查不依赖数据库连接。

## 启动与验证

日常开启顺序：**MySQL → 后端 → 前端 → 浏览器**。已有数据库和本地配置无需每天重新初始化，已有前端依赖也无需每次重新安装。

先确认 MySQL 服务运行。在本机 PowerShell 中查询实际服务名（不要直接假定为 `MySQL80`）：

```powershell
Get-Service -Name '*mysql*'
```

若状态为 `Running`，直接启动后端即可；若为 `Stopped`，在管理员 PowerShell 中执行：

```powershell
$mysqlServiceName = Read-Host '输入上一步查到的 MySQL 服务 Name'
Start-Service -Name $mysqlServiceName
Get-Service -Name $mysqlServiceName
```

也可在 Windows“服务”应用中查找并启动实际 MySQL 服务。如果查询不到服务，应检查本机已有 MySQL 的启动方式，不要重复安装服务。Agent 沙箱可能无法访问 Windows 服务管理器，此时在本机终端操作即可。

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

`npm install` 仅首次使用或依赖变更后需要执行；日常启动运行 `npm run dev` 即可。两个服务终端应保持打开，看到启动成功后再访问页面。

访问 [前端首页](http://127.0.0.1:5173/)，顶部应显示“连接成功”和 `UP · Backend is running`，左侧自动加载文档列表。后端默认使用端口 8080；Vite 固定使用 5173，端口占用时会报错。开发代理在 `frontend/vite.config.js` 中将 `/api` 转发至 `http://127.0.0.1:8080`，组件只使用相对路径。

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

## 关闭与重新开启

日常关闭顺序：**前端 → 后端 → MySQL（按需）**。

1. 在运行 `npm run dev` 的终端按 `Ctrl+C`，停止 Vite；若终端询问是否终止批处理，确认终止。
2. 在运行 `mvn ... spring-boot:run` 或 `java -jar ...` 的终端按 `Ctrl+C`，等待后端退出。关闭浏览器标签页不会停止这些服务。
3. 如果希望把 MySQL 也关闭，且确认没有其他项目或程序正在使用它，在管理员 PowerShell 中按实际服务名停止：

```powershell
Get-Service -Name '*mysql*'
$mysqlServiceName = Read-Host '输入要停止的 MySQL 服务 Name'
Stop-Service -Name $mysqlServiceName
Get-Service -Name $mysqlServiceName
```

MySQL 是本机独立服务，前后端退出后仍可保持运行；正常停止服务不会删除数据库。数据库中已经创建成功的文档会保留，尚未提交成功的表单输入不会自动保存。

如果原服务终端已找不到，先查监听端口和对应进程：

```powershell
Get-NetTCPConnection -State Listen -LocalPort 5173,8080 -ErrorAction SilentlyContinue |
    Select-Object LocalAddress, LocalPort, OwningProcess
$serviceProcessId = [int](Read-Host '输入需要核对的 OwningProcess')
Get-CimInstance Win32_Process -Filter "ProcessId = $serviceProcessId" |
    Select-Object ProcessId, Name, CommandLine
```

确认命令行确实属于本仓库的 Vite 或 Spring Boot 后，才可用 `Stop-Process -Id $serviceProcessId` 停止该进程。优先回到原终端使用 `Ctrl+C`；不要批量结束所有 `java`、`node` 或 `mysqld` 进程，不要使用结束进程的方式关闭 MySQL。

关闭后再次查询 5173、8080：没有监听记录表示前后端监听已停止（并不代表 MySQL 已停止）。MySQL 是否停止以 `Get-Service` 的状态为准。

重新开启时重复“启动与验证”的步骤：启动 MySQL，在仓库根目录分别打开两个终端启动后端和前端，再访问 `http://127.0.0.1:5173/`。仅重启后端时，前端和 MySQL 可以保持运行；后端启动成功后在页面刷新列表或重试即可，无需重新执行建表 SQL。

## M1 浏览器操作闭环

1. 按下方说明初始化 MySQL、填写本地配置，并分别启动后端和前端。
2. 打开 `http://127.0.0.1:5173/`，确认连接成功、文档列表加载完成；无文档时显示空状态。
3. 点击“新建文档”，填写标题（必填、不可全为空格、最多 255 个字符）与选填正文，再点击“创建文档”。提交期间按钮和表单禁用，防止重复创建。
4. 创建成功后列表更新，主区域通过单篇 API 加载新文档。也可点击左侧其他文档查看标题、正文、版本及时间；正文安全按普通文本显示并保留换行。
5. 刷新浏览器，在列表中重新选择该文档，确认数据仍在。停止并重新启动后端后，再刷新列表并查看同一文档，确认数据仍在 MySQL 中。
6. 后端停止时，列表、详情和创建操作显示错误；重启后可使用“重试列表”“重试详情”或重新提交恢复。创建失败保留输入；请求超时后先刷新列表确认是否已经创建，避免把重试当成新的创建。

Task 3 验证：`npm run build` 通过；启用真实 MySQL 的现有 Maven 测试共 5 项全部通过。真实 Edge 浏览器验证了创建、列表、详情、刷新、后端进程重启持久化、重复点击只发出一次 POST、失败后输入保留与重试，以及延迟旧响应不会覆盖当前详情。空列表与详情 404 使用隔离响应测试界面，核心闭环始终访问真实 API 和 MySQL，未清空已有数据。临时浏览器脚本和截图位于被忽略的 `backend/target/browser-check/`，未增加前端测试框架或项目依赖。

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
