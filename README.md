# dbclasshw

数据库课程大作业：基于 Web 的多人在线文档编辑系统。采用前后端分离的单体架构，围绕文档编辑、权限共享和基础多人协作，体现数据库设计、业务功能与系统完整性。

## 技术栈

- 当前前端：Vue 3.5.45、Vite 7.3.8、Element Plus 2.14.8，使用 JavaScript；Vue 插件版本为 6.0.11。
- 当前后端：Java 21 编译目标、Spring Boot 3.5.16、Maven、MyBatis Starter 3.0.5、MySQL Connector/J 9.7.0（由 Spring Boot 管理版本）。
- 认证：Spring Security、BCrypt、服务端 HTTP Session、CSRF 防护。
- 后续阶段：WebSocket、Tiptap；尚未集成。
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

M0、M1 和 M2 Task 1 已完成。当前支持注册、用户名/邮箱登录、Session 登录状态恢复、邮箱资料修改、退出及私人文档创建/列表/详情。文档按登录用户隔离；M1 旧文档保留并归属不可登录的迁移账号。原健康检查仍可用。详见 [阶段进度](PROGRESS.md)，功能边界以 [项目需求](PROJECT_SPEC.md) 为准。

## 环境要求

- 已验证环境：Windows 11 x64、Node.js 22.17.1、npm 10.9.2、JDK 21.0.10、Maven 3.9.16。
- 使用已有 JDK 21 和 Maven，无需安装 Java 17 或独立安装 Spring Boot。
- 前端工具要求 Node.js `^20.19.0 || >=22.12.0`，当前环境满足要求。
- 首次安装和构建需要访问 npm registry 与 Maven Central。文档 API 需要运行中的 MySQL 8.x（本机客户端为 8.0.39）；原健康检查不依赖数据库连接。

## 启动与验证

日常开启顺序：**MySQL → 后端 → 前端 → 浏览器**。已有数据库和本地配置无需每天重新初始化，已有前端依赖也无需每次重新安装。首次从 M1 升级时，必须先按下方数据库升级说明执行迁移。

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

访问 [前端首页](http://127.0.0.1:5173/)，顶部应显示“连接成功”和 `UP · Backend is running`；未登录时显示登录/注册表单，登录后加载当前用户的私人文档列表。后端默认使用端口 8080；Vite 固定使用 5173，端口占用时会报错。开发代理在 `frontend/vite.config.js` 中将 `/api` 转发至 `http://127.0.0.1:8080`，组件只使用相对路径。

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

JAR 启动是 Maven 启动的替代方式，不要同时占用 8080。普通后端测试使用随机端口和真实 HTTP 请求，无需数据库；认证与隔离的真实 MySQL 测试需显式启用，见下文。前端构建产物在 `frontend/dist/`；当前 `/api` 代理仅配置于 Vite 开发服务器，生产环境部署转发留待后续任务。

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

## 数据库初始化与 M1 升级

MySQL 8.x 必须已运行。`backend/application-local.properties` 使用真实本地配置且已被 Git 忽略；只在文件不存在时，从 `application-local.example.properties` 复制并在编辑器中填写账号、密码，不覆盖已有配置。也可使用进程环境变量 `DBCLASSHW_DB_URL`、`DBCLASSHW_DB_USERNAME`、`DBCLASSHW_DB_PASSWORD`。URL 指向 `dbclasshw`，默认 `127.0.0.1:3306`。本地文件属性优先于这些自定义环境变量。Java properties 中反斜杠等特殊字符须按语法转义，不提交真实凭据。

SQL 均从 `backend/` 下启动已有 MySQL 客户端后执行，密码在终端交互输入：

```powershell
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u YOUR_LOCAL_USERNAME -p
```

**全新数据库**执行：

```sql
SOURCE sql/init.sql;
```

**已有 M1 数据库**先停止后端写入、检查表结构与文档数量并备份，再执行独立增量脚本：

```sql
SHOW CREATE TABLE dbclasshw.documents;
SELECT COUNT(*) FROM dbclasshw.documents;
SOURCE sql/migrate-m1-m2.sql;
```

备份可在 `backend/` 的另一个终端执行（先确保 `target/` 目录存在；该目录被 Git 忽略）：

```powershell
New-Item -ItemType Directory -Force target
mysqldump -h 127.0.0.1 -P 3306 -u YOUR_LOCAL_USERNAME -p --single-transaction --result-file=target/before-m2.sql dbclasshw
```

应用不会自动运行 SQL。初始化和迁移均使用 InnoDB、utf8mb4；迁移脚本需要项目库建表、改表、创建/执行/删除存储过程权限。MySQL DDL 隐式提交，不能依赖完整事务回滚；出现脚本校验错误时先检查实际结构，不要删除表或强行继续。正常完成后可重复执行迁移；其只填补空的所有者，不重分配已有所有者。脚本只临时创建/删除本项目的 `migrate_m1_m2` 过程，不删除业务表或文档。

当前结构：

- `users`：自增 `id`、唯一用户名和邮箱、BCrypt `password_hash`、`enabled`、创建/更新时间。
- `documents`：保留原 `id`、标题、正文、版本、时间；新增 `owner_id BIGINT NOT NULL` 和指向 `users.id` 的外键。
- 专用账号 `__m1_migration__`：`enabled=FALSE`，密码字段为不可用于认证的禁用标记，不能登录，无公开默认密码。旧文档只归属此账号，不会自动赠予新注册用户；本阶段不提供认领或管理页面。

升级后核对：

```sql
SHOW CREATE TABLE dbclasshw.users;
SHOW CREATE TABLE dbclasshw.documents;
SELECT COUNT(*) FROM dbclasshw.documents WHERE owner_id IS NULL;
SELECT d.id, d.owner_id, u.username FROM dbclasshw.documents d
JOIN dbclasshw.users u ON d.owner_id=u.id ORDER BY d.id;
```

本次已在真实数据库执行迁移及重复执行验证，5 篇旧文档的 ID、标题、正文、版本、创建/更新时间与迁移前快照完全相同；快照保存在本地忽略的 `backend/target/` 下。新注册用户初始列表为空，这是私人文档隔离的预期行为。

## 认证 API 与 CSRF

| 方法与路径 | 请求 / 功能 | 成功状态 |
| --- | --- | --- |
| `GET /api/health` | 公共健康检查 | 200 |
| `GET /api/auth/csrf` | 公共，返回 `headerName`、`parameterName`、`token` | 200 |
| `POST /api/auth/register` | `{username,email,password}`，注册但不自动登录 | 201 |
| `POST /api/auth/login` | `{login,password}`，login 可为用户名或邮箱 | 200，用户资料 |
| `POST /api/auth/logout` | 无正文，失效 Session 并清理 Cookie | 200 |
| `GET /api/users/me` | 当前用户 ID、用户名、邮箱、注册时间 | 200 |
| `PUT /api/users/me` | `{email}`，修改邮箱；用户名本阶段保持不变 | 200 |
| `POST /api/documents` | `{title,content}`，所有者从当前 Session 获取 | 201 |
| `GET /api/documents` | 当前用户拥有的文档数组 | 200 |
| `GET /api/documents/{id}` | 当前用户的文档详情 | 200 |

用户名为 3–32 位英文字母、数字或下划线，必须以字母开头；邮箱格式有效且不超过 254 字符；密码 8–64 字符且 UTF-8 不超过 72 字节。用户名/邮箱由数据库唯一约束保证唯一，邮箱保存为小写。文档标题非空且不超过 255 字符，正文可空。未知字段（包括客户端指定的 ID、角色、所有者、版本或时间）会被拒绝。响应不包含密码哈希。

未认证的受保护读取返回 401；输入错误 400；用户名/邮箱冲突 409；不存在或不属于当前用户的文档统一 404。缺失/无效 CSRF 的状态变更请求在安全过滤器中返回 403（包括尚未登录的请求），不会执行写入；携带有效 CSRF 但未认证的文档创建请求返回 401。数据库访问失败返回通用 503，不返回 SQL 或内部堆栈。

认证使用 Spring Security 6.5.x（由 Boot 管理）和服务端内存 Session，不使用 JWT。JSESSIONID 为 HttpOnly、SameSite=Lax、仅 Cookie 跟踪；登录时更换 Session ID，闲置 30 分钟过期。刷新页面保持登录；后端进程重启后需要重新登录，MySQL 文档数据不受影响。当前是本机 HTTP 开发环境，生产 HTTPS 的安全 Cookie 与部署配置留待对应阶段。

CSRF 使用默认 Session 仓库与 XOR 请求处理器：`GET /api/auth/csrf` 显式解析延迟 Token，前端在内存中保存返回的掩码 Token，写请求带相应请求头；不需要读取 Session Cookie，也不把凭据存入 localStorage。页面加载、登录成功、退出成功后重新获取 Token。403 后不自动重放写请求，避免重复创建。

PowerShell 调用示例（将注册字段替换为自己的信息，密码用安全输入，不写入文档）：

```powershell
$base = 'http://127.0.0.1:8080'
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -SessionVariable webSession
$headers = @{ $csrf.headerName = $csrf.token }
$password = Read-Host '输入密码' -AsSecureString
$credential = [System.Net.NetworkCredential]::new('', $password)
$register = @{ username = 'your_username'; email = 'you@example.com'; password = $credential.Password } | ConvertTo-Json
Invoke-RestMethod "$base/api/auth/register" -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json' -Body $register
$login = @{ login = 'your_username'; password = $credential.Password } | ConvertTo-Json
Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json' -Body $login
# Login changes both session ID and CSRF token; keep the same WebSession cookie jar.
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $webSession
$headers = @{ $csrf.headerName = $csrf.token }
$body = @{ title = 'My Private Document'; content = 'Hello Database!' } | ConvertTo-Json
Invoke-RestMethod "$base/api/documents" -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
Invoke-RestMethod "$base/api/documents" -WebSession $webSession
Invoke-RestMethod "$base/api/auth/logout" -Method Post -WebSession $webSession -Headers $headers
# Obtain a new CSRF token before another login after logout.
```

## 浏览器操作与测试

打开 `http://127.0.0.1:5173/`，注册后登录；在个人资料中可修改邮箱，在工作台新建并查看私人文档。刷新后应恢复登录并能重新选择文档。退出后工作台消失，重新用用户名或更新后的邮箱登录可读取同一文档。用另一个浏览器用户环境注册 B，其列表不包含 A 的文档，直接请求 A 的文档也返回 404。会话过期时前端提示并返回登录界面。

普通检查无需真实 MySQL，真实测试需先完成初始化/升级并配置本地连接：

```powershell
# frontend/
npm run build

# backend/ -- health, unauthenticated access, CSRF and unavailable-database tests
mvn "-Dmaven.repo.local=target/maven-repository" verify

# backend/ -- enable real MySQL authentication, isolation and persistence tests
$env:DBCLASSHW_MYSQL_TEST = 'true'
try {
    mvn "-Dmaven.repo.local=target/maven-repository" verify
} finally {
    Remove-Item Env:DBCLASSHW_MYSQL_TEST
}
```

真实用例不自动迁移现有库，也不清空数据；创建唯一标记的测试用户和私人文档。新安装一致性用例另建 `dbclasshw_m2_verify_<随机后缀>` 测试库，逐项比较列与约束，保留该测试库而不自动删除。报告在 `backend/target/surefire-reports/`。

本次前端构建及 Maven 测试/打包通过，启用真实 MySQL 后 6 项测试无失败或跳过；覆盖重复用户名/邮箱、BCrypt、错误密码、Session fixation、登录/退出 Token 刷新、旧会话失效、无效输入、A/B 隔离、数据库约束和重新启动应用后登录读取持久化文档。真实 Edge 完成注册 → 登录 → 创建 → 刷新 → 查看 → 修改邮箱 → 退出 → 邮箱重新登录，并验证 HttpOnly、旧会话失效、会话过期回登录及双用户隔离。临时浏览器脚本和截图在忽略的 `backend/target/browser-check/`，未新增项目级浏览器框架依赖。
