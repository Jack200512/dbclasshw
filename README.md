# dbclasshw

数据库课程大作业：基于 Web 的多人在线文档编辑系统。采用前后端分离的单体架构，围绕文档编辑、权限共享和基础多人协作，体现数据库设计、业务功能与系统完整性。

## 技术栈

- 当前前端：Vue 3.5.45、Vite 7.3.8、Element Plus 2.14.8，使用 JavaScript；Vue 插件版本为 6.0.11。
- 当前后端：Java 21 编译目标、Spring Boot 3.5.16、Maven、MyBatis Starter 3.0.5、MySQL Connector/J 9.7.0（由 Spring Boot 管理版本）。
- 认证：Spring Security、BCrypt、服务端 HTTP Session、CSRF 防护。
- 富文本：Tiptap 3.33.1（Vue 3、StarterKit、ProseMirror）。
- 权限共享：OWNER / EDITOR / VIEWER，按注册用户授权。
- 文档同步：Spring 原生 WebSocket 提交后通知 + HTTP 拉取最新内容。
- 历史版本：完整快照、只读预览及产生新版本的安全恢复。自动保存尚未实现。
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

`docs/M2_ACCEPTANCE.md` 记录 P0 MVP 集成验收与已知边界。

## 当前开发状态

M0、M1、M2 和 M3 Task 1 已完成，P0 MVP 保持通过，新增文档历史版本与恢复。当前支持注册、用户名/邮箱登录、Session 登录状态恢复、邮箱资料修改、退出及私人富文本文档的创建、列表、详情、编辑、手动保存、删除和标题搜索。保存使用版本号检测冲突；在线用户可收到提交后更新通知，无草稿时自动同步，有草稿时保留并提示。文档默认私有，仅所有者和有效共享用户可访问；M1 旧文档保留并归属不可登录的迁移账号，未自动共享。原健康检查仍可用。详见 [阶段进度](PROGRESS.md)，功能边界以 [项目需求](PROJECT_SPEC.md) 为准。

## 环境要求

- 已验证环境：Windows 11 x64、Node.js 22.17.1、npm 10.9.2、JDK 21.0.10、Maven 3.9.16。
- 使用已有 JDK 21 和 Maven，无需安装 Java 17 或独立安装 Spring Boot。
- 前端工具要求 Node.js `^20.19.0 || >=22.12.0`，当前环境满足要求。
- 首次安装和构建需要访问 npm registry 与 Maven Central。文档 API 需要运行中的 MySQL 8.x（本机客户端为 8.0.39）；原健康检查不依赖数据库连接。

## 启动与验证

日常开启顺序：**MySQL → 后端 → 前端 → 浏览器**。已有数据库和本地配置无需每天重新初始化，已有前端依赖也无需每次重新安装。首次从旧版本升级时，必须先按下方数据库升级说明执行对应迁移，包括 M3 历史版本迁移。

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

访问 [前端首页](http://127.0.0.1:5173/)，顶部应显示“连接成功”和 `UP · Backend is running`；未登录时显示登录/注册表单，登录后加载当前用户拥有和被共享的文档列表。后端默认使用端口 8080；Vite 固定使用 5173，端口占用时会报错。开发代理在 `frontend/vite.config.js` 中将 `/api` 转发至 `http://127.0.0.1:8080`，组件只使用相对路径。

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

JAR 启动是 Maven 启动的替代方式，不要同时占用 8080。在 Windows 下重新打包前先停止正在使用该 JAR 的后端，否则文件占用会导致 repackage 失败；打包完成后再启动。普通后端测试使用随机端口和真实 HTTP 请求，无需数据库；认证与隔离的真实 MySQL 测试需显式启用，见下文。前端构建产物在 `frontend/dist/`；当前 `/api` 代理仅配置于 Vite 开发服务器，生产环境部署转发留待后续任务。

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

**已有 M1 数据库**先停止后端写入、检查表结构与文档数量并备份，再按顺序执行四个独立增量脚本：

```sql
SHOW CREATE TABLE dbclasshw.documents;
SELECT COUNT(*) FROM dbclasshw.documents;
SOURCE sql/migrate-m1-m2.sql;
SOURCE sql/migrate-m2-richtext.sql;
SOURCE sql/migrate-m2-sharing.sql;
SOURCE sql/migrate-m3-history.sql;
```

**已完成 M2 Task 1** 依次执行 richtext、sharing、history 三个增量脚本；**已完成 M2 Task 2** 执行 sharing、history；**已完成 M2 Task 3–5** 只执行 history。全新安装只执行最新版 `init.sql`。所有权迁移仅适用于旧 M1 或 M2 Task 1 结构；升级富文本后不要重新运行旧所有权迁移。

备份可在 `backend/` 的另一个终端执行（先确保 `target/` 目录存在；该目录被 Git 忽略）：

```powershell
New-Item -ItemType Directory -Force target
mysqldump -h 127.0.0.1 -P 3306 -u YOUR_LOCAL_USERNAME -p --single-transaction --result-file=target/before-m2.sql dbclasshw
```

应用不会自动运行 SQL。初始化和迁移均使用 InnoDB、utf8mb4；迁移脚本需要项目库建表、改表、创建/执行/删除存储过程权限。MySQL DDL 隐式提交，不能依赖完整事务回滚；出现脚本校验错误时先检查实际结构，不要删除表或强行继续。在各脚本对应的目标结构中可重复执行迁移；所有权脚本只填补空的所有者，不重分配已有所有者，富文本脚本仅在格式列缺失时新增该列。脚本只临时创建/删除本项目的 `migrate_m1_m2` 过程，不删除业务表或文档。

当前结构：

- `users`：自增 `id`、唯一用户名和邮箱、BCrypt `password_hash`、`enabled`、创建/更新时间。
- `documents`：保留原 `id`、标题、正文、版本、时间和所有者外键；富文本升级新增 `content_format VARCHAR(16) NOT NULL DEFAULT 'PLAIN_TEXT'`。旧内容不批量转换；`content` 保持 TEXT。
- `document_permissions`：文档、用户外键，唯一 `(document_id,user_id)`，角色限制 EDITOR/VIEWER；删除文档时级联清理授权。
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
| `POST /api/documents` | `{title,content}`，content 为 Tiptap JSON 对象，所有者从当前 Session 获取 | 201 |
| `GET /api/documents?keyword=...` | 自有和有效共享文档的标题搜索；空关键词返回全部可见文档，按 ID 排序 | 200 |
| `GET /api/documents/{id}` | 当前用户可见的文档详情，包含 currentRole | 200 |
| `PUT /api/documents/{id}` | `{title,content,version}`，原子乐观锁更新 | 200，返回新版本 |
| `DELETE /api/documents/{id}` | 仅所有者，物理删除单篇 | 204，无正文 |

用户名为 3–32 位英文字母、数字或下划线，必须以字母开头；邮箱格式有效且不超过 254 字符；密码 8–64 字符且 UTF-8 不超过 72 字节。用户名/邮箱由数据库唯一约束保证唯一，邮箱保存为小写。文档标题非空且不超过 255 字符，新建/保存正文必须是有效 Tiptap JSON（空正文用空段落文档）。未知字段（包括客户端指定的 ID、角色、所有者、版本或时间）会被拒绝。响应不包含密码哈希。

未认证的受保护读取返回 401；输入错误 400；用户名/邮箱冲突 409；不存在或未授权的文档统一 404；可见文档上的越权操作返回 403。缺失/无效 CSRF 的状态变更请求在安全过滤器中返回 403（包括尚未登录的请求），不会执行写入；携带有效 CSRF 但未认证的文档创建请求返回 401。数据库访问失败返回通用 503，不返回 SQL 或内部堆栈。

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
$content = @{ type = 'doc'; content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'Hello Database!' }) }) }
$body = @{ title = 'My Private Document'; content = $content } | ConvertTo-Json -Depth 20
Invoke-RestMethod "$base/api/documents" -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
Invoke-RestMethod "$base/api/documents" -WebSession $webSession
Invoke-RestMethod "$base/api/auth/logout" -Method Post -WebSession $webSession -Headers $headers
# Obtain a new CSRF token before another login after logout.
```

## 浏览器操作与测试

M3 Task 2 已优化页面布局：顶部集中显示后端状态，资料区可直接修改邮箱；文档的编辑/保存、共享、历史、重新加载和删除入口位于正文上方。“共享文档”打开独立弹窗，完成后关闭弹窗继续操作；历史预览的恢复/关闭按钮位于底部固定操作区。长正文和历史内容在各自区域内滚动，原有手动保存和草稿确认规则不变。

现有浏览器工具环境中，可从 `backend/` 执行 `node src/test/browser/ui.cjs`，验证注册登录、常见错误、长内容、1366×768 / 1920×1080 布局及弹窗/草稿保护；截图位于忽略的 `target/browser-check/ui-*.png`。后端与前端需已按下方说明启动。

M2 Task 5 已于 2026-11-20 完成系统级验收。P0 覆盖、真实 MySQL/浏览器证据和限制见 [M2 验收记录](docs/M2_ACCEPTANCE.md)。后端真库测试现共 14 项；不设置真库开关时会跳过相关用例，不能将普通构建通过视为真库验收通过。

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

M2 Task 1 验收时，前端构建及 Maven 测试/打包通过，启用真实 MySQL 后 6 项测试无失败或跳过；覆盖重复用户名/邮箱、BCrypt、错误密码、Session fixation、登录/退出 Token 刷新、旧会话失效、无效输入、A/B 隔离、数据库约束和重新启动应用后登录读取持久化文档。真实 Edge 完成注册 → 登录 → 创建 → 刷新 → 查看 → 修改邮箱 → 退出 → 邮箱重新登录，并验证 HttpOnly、旧会话失效、会话过期回登录及双用户隔离。临时浏览器脚本和截图在忽略的 `backend/target/browser-check/`，未新增项目级浏览器框架依赖。


## 富文本编辑、版本冲突与兼容

登录后点击“新建文档”，输入标题并用工具栏设置段落、加粗、斜体、标题、无序/有序列表，点击“创建文档”。打开已有文档时默认只读，点击“编辑文档”后修改，再点击“保存文档”；保存期间禁止重复提交。保存成功返回只读状态并展示最新版本，保存失败保留本地标题和正文。切换、新建、取消编辑、重新加载或退出登录遇到未保存修改时需要确认；关闭/刷新页面也有浏览器离开提醒。

左侧按标题搜索仅搜索自己拥有或当前被共享的文档，空关键词返回全部，`%`、`_`、`!` 按字面量处理而不是用户可控制的 LIKE 通配符。删除需要确认，只删除当前用户的目标文档；成功后清理详情并更新列表。共享权限和历史快照均通过外键级联清理。

新建和更新请求示例：

```json
{
  "title": "My rich document",
  "content": {
    "type": "doc",
    "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "Hello", "marks": [{ "type": "bold" }] }] }]
  },
  "version": 1
}
```

`version` 仅 PUT 必填，POST 不接受客户端指定版本。响应沿用 `content` 字符串字段，并增加 `contentFormat`：`TIPTAP_JSON` 时 content 是规范 JSON 的序列化字符串；`PLAIN_TEXT` 时是历史原文。前端只按明确的格式标记解析，不根据开头是否为 `{` 猜测；历史文本按换行构建安全的段落/文本节点，绝不当作 HTML。旧文档编辑后保存才转为 JSON，其他旧数据保持不变。

后端白名单只接受本阶段启用的 doc、paragraph、text、heading（1–3）、bulletList、orderedList、listItem、hardBreak 和 bold/italic 标记，拒绝未知节点、任意 HTML 属性和链接。规范 JSON 的 UTF-8 序列化大小最多 60,000 字节、嵌套深度最多 24、节点数最多 4,000；超限或结构错误返回 400。未启用图片、链接、代码等复杂扩展。前端使用 Tiptap 渲染，不通过 `v-html` 注入原始内容；编辑器按需加载，并在组件销毁时释放。

更新先在事务内锁定文档行并读取当前角色，只有 OWNER/EDITOR 可以继续；SQL 保留 `id`、`version` 与 OWNER/EDITOR 授权条件。权限无效返回 404/403，授权有效但原子 UPDATE 影响 0 行返回 409：

```json
{"code":"DOCUMENT_VERSION_CONFLICT","message":"Document changed; reload before saving"}
```

冲突不覆盖数据库，也不自动提升本地版本强制重试；页面保留当前草稿，提供“重新加载最新版本”，放弃本地修改前需要确认。本阶段不自动合并、不自动保存。

M2 Task 2 实测：新增格式列的迁移及重复执行成功，迁移前 9 篇文档的 ID、所有者、标题、正文、版本与时间不变；新安装与升级的列/约束一致。前端构建通过，启用真实 MySQL 的 Maven 测试及打包通过，共 9 项测试无失败或跳过。真实 MySQL 验证 CRUD、富文本回读、搜索通配符转义、旧文本转换、CSRF 和所有者隔离；顺序旧版本提交及同时提交均验证一成功、一 409，版本只递增一次。真实 Edge 验证格式工具栏、创建/编辑/保存/刷新/搜索/删除、网络失败草稿保留、未保存提醒、双标签页冲突和安全重载、旧文本 HTML 字符串安全显示与转换、退出后 401。测试只删除本用例创建的文档，不清空已有业务数据。


## 文档共享与权限（M2 Task 3）

共享表由 `sql/migrate-m2-sharing.sql` 增量建立，升级到当前版本还需运行后文的历史迁移；前端启动命令不变。迁移只创建 `document_permissions`，不会改写用户或文档，也不会给旧文档自动授权。新安装的 `init.sql` 已包含同一张权限表。重复执行不会清空既有授权；已有同名表结构不一致时先检查，不要删除重建。

权限表包含自增 ID、`document_id`、`user_id`、`role`、创建/更新时间，均为 NOT NULL。`(document_id,user_id)` 唯一索引支持按文档查成员，另有 user_id 索引；CHECK 约束只允许 EDITOR/VIEWER，两个外键均有效，文档删除时授权通过 `ON DELETE CASCADE` 清理。OWNER 仅由 documents.owner_id 表示，共享 API 不生成 OWNER 记录，也不支持转移所有权。

| 操作 | OWNER | EDITOR | VIEWER |
| --- | --- | --- | --- |
| 查看、列表、搜索 | 允许 | 允许 | 允许 |
| 编辑与保存 | 允许 | 允许 | 禁止 |
| 删除文档 | 允许 | 禁止 | 禁止 |
| 查看成员、添加/修改/撤销共享 | 允许 | 禁止 | 禁止 |

所有请求仍须登录；状态变更仍须携带 CSRF。未授权文档返回 404，已授权读者越权写入/管理返回 403。响应中 `currentRole` 为当前请求用户的有效角色，不接受客户端伪造该字段。

| 方法与路径 | 请求 | 成功响应 |
| --- | --- | --- |
| `POST /api/documents/{id}/permissions` | `{ "login": "用户名或邮箱", "role": "EDITOR" }` | 201，安全的成员列表 |
| `GET /api/documents/{id}/permissions` | 无 | 200，成员列表 |
| `PUT /api/documents/{id}/permissions/{userId}` | `{ "role": "VIEWER" }` | 200，更新后的成员列表 |
| `DELETE /api/documents/{id}/permissions/{userId}` | 无 | 204 |

成员列表只含授权 ID、用户 ID、用户名、邮箱、角色和时间，不含密码或哈希。目标用户须存在且启用；自己、OWNER 角色或非法字段返回 400；重复共享返回 409 / `SHARING_CONFLICT`；授权记录不存在返回 404。角色更新与撤销立即影响后续 API 检查，不需要重新登录。

浏览器操作：A 登录并创建文档，点击“共享文档”，输入 B 的用户名或邮箱并选择 EDITOR，添加共享；也可给 C 添加 VIEWER。B/C 刷新列表，在“共享给我的”中打开文档；列表显示所属类型与角色。B 可编辑保存，A 无本地草稿时自动看到更新；C 只读且无编辑、删除、共享管理按钮。A 在成员列表选择角色即可修改，撤销需要确认。B 编辑期间若被降级或撤销，下一次保存会被后端拒绝并保留本地草稿，不自动丢弃。已加载的内容不会被远程擦除，当前已增加提交后的 WebSocket 通知与安全重载，连接失败时仍可手动刷新/重新加载。

并发策略：文档更新、删除以及共享管理都在事务内先对相同文档行 `SELECT ... FOR UPDATE`，再按固定顺序读取/锁定权限；保存 SQL 同时检查版本与有效编辑授权。撤销/降级持有文档锁到提交，排队的保存得到锁后重新校验最新权限，因此不会在权限变更已提交后继续使用旧权限写入。若保存先获得锁并提交，它按合法顺序发生在撤销之前；不能以 HTTP 响应到达顺序判断事务先后。不使用分布式锁或进程级权限缓存。

本次真实迁移和重复执行后，原有 21 个用户、13 篇文档所有字段不变。启用真实 MySQL 的 Maven 验证共 11 项测试通过，无失败或跳过，覆盖角色矩阵、重复授权、CHECK/唯一/外键约束、级联清理、OWNER/EDITOR 同版本冲突，以及权限变更事务持锁时排队的 HTTP 保存请求在提交后被拒绝。新安装与升级的列和约束仍比对一致。真实 Edge 三个独立 Session 验证共享界面、编辑者保存、所有者读取更新、查看者只读、共享搜索、降级/撤销后的失败与草稿保留；既有认证、CSRF、资料、富文本 CRUD、旧文本兼容及搜索回归通过。


## WebSocket 文档同步（M2 Task 4）

采用 Spring 原生 WebSocket，不使用 STOMP 或消息代理。**HTTP PUT 唯一负责持久化，WebSocket 只通知，HTTP GET 获取权威内容**。本次没有业务表迁移，也不需要额外数据库、Redis 或消息队列；运行和本地配置步骤保持不变。更新后端依赖后重新构建/启动，并重启 Vite 使代理配置生效；`/api` 开发代理已启用 `ws: true`。

登录并打开文档时，前端按当前页面的 host/protocol 连接 `/api/ws/documents`（HTTP 对应 ws，HTTPS 对应 wss），浏览器自动携带 HttpOnly Session Cookie，URL 不携带密码、Session ID 或认证 Token。每个工作台只订阅当前文档，切换、退出和卸载会清理旧连接。

握手复用 Spring Security 认证，同时检查 Origin；默认只接受 `http://127.0.0.1:5173`、`http://localhost:5173` 及同主机名的 8080 地址。其他本地地址需要通过 `app.websocket.allowed-origins` 显式配置，不使用通配符，也不关闭 HTTP CSRF。原生 WebSocket 不承接任何内容写入，Origin 白名单负责防止跨站建立业务连接。

客户端可发送的消息仅有：

```json
{"type":"SUBSCRIBE","documentId":123}
```

```json
{"type":"UNSUBSCRIBE"}
```

```json
{"type":"PING"}
```

服务端验证文档的当前 OWNER/EDITOR/VIEWER 权限后返回 `SUBSCRIBED`，包含 documentId、version 和 currentRole。无权限、过期会话、非法消息或伪造 `DOCUMENT_UPDATED` 会关闭连接，不发送正文或未授权文档元数据。PING 只用于检测通知通道，不能延长 HTTP Session 的有效期。

保存成功提交后的通知示例：

```json
{
  "type":"DOCUMENT_UPDATED",
  "documentId":123,
  "version":8,
  "updatedAt":"2026-11-13T18:00:00",
  "currentRole":"EDITOR",
  "actorId":42
}
```

actorId 为可选字段；并发通知可能合并为数据库中已提交的最新版本，不把旧操作错误标为最新修改者。通知不包含富文本正文。重复、乱序或已处理版本由客户端忽略。

服务层在事务内发布内部事件，`@TransactionalEventListener` 仅在提交成功后处理；回滚、输入错误、403/404 或 409 不广播成功更新。发送在独立事务中重新锁定文档并校验实时权限，与共享撤销/降级使用同一文档行锁，避免“先查权限再无约束广播”的竞态。消息发送失败不会把已提交的 HTTP 保存改报失败；HTTP 查询与版本号始终是正确性依据。

角色变更后，仍有读取权限的订阅者收到 `ACCESS_CHANGED` 并核对 HTTP 最新状态；被撤销用户或文档删除时只关闭相关连接，不发送删除后不可见的文档数据。退出通过关闭关联连接与 Session 销毁监听立即清理；闲置过期还会在每次发送/消息处理以及每 5 秒巡检时检查。已经加载到浏览器的内容不会被远程擦除，但后续请求必须重新授权。

前端同步规则：

- 无未保存修改：收到新版本后 GET，完成时再次确认当前文档及 dirty 状态，再更新标题、正文、版本和基线，不制造假 dirty。
- 有草稿：保留标题、正文和读取时的版本号，只提示远端更新；重新加载必须确认放弃修改。保存旧版本仍返回 409，不自动合并或强制覆盖。
- 权限降级或失效：保留草稿，更新权限状态并禁用不允许的保存；HTTP 后端继续独立校验。
- 通道异常：HTTP 仍可读取和保存；自动重试最多连续 5 次，退避为 0.5、1、2、4、8 秒。成功订阅后重置计数，也可点击“重连实时通知”。
- 初次订阅、重连成功和页面重新获得焦点时重新 GET 核对，补偿断线期间遗漏；有草稿时仍不替换内容。切换文档及过期 HTTP 响应有序号保护。

手动验收：

1. 启动 MySQL、后端和前端，用两个独立浏览器环境登录 A、B；A 创建文档并共享 B 为 EDITOR。
2. 两端打开同一文档，确认“实时连接正常”。A 编辑保存，B 无草稿时应自动显示新内容；B 保存后 A 同样更新。
3. B 修改但不保存，A 再保存；B 应提示远端更新且本地内容不变，B 保存得到 409；只有确认重新加载才丢弃草稿。
4. 中断 B 的业务 WebSocket，验证 HTTP 仍可用；A 保存后恢复通道，B 无草稿时补查更新，有草稿时保留。不要将 Vite 热更新通道误当业务连接。
5. A 降级/撤销 B，检查 B 后续保存被拒绝或禁用、撤销后订阅关闭；退出或删除文档后连接也失效。VIEWER 可接收更新，但不能保存。

使用原 README 中的 Maven 真库开关执行测试。当前前端构建和 Maven 测试/打包通过；13 项测试无失败或跳过。真实 MySQL + 原生 WebSocket 验证提交前无消息、提交后版本通知、回滚/409 无通知、非法 Origin、未登录/未授权订阅、伪造消息、撤销/降级、退出、Session 过期、删除及订阅切换。真实 Edge 三个独立会话验证双向自动同步、VIEWER 接收、在线/断线草稿保护及 409、自动重连、重连补查、切换隔离和权限失效；实际版本递增至 8，草稿未覆盖服务器内容。

边界：单机、内存连接关系、尽力通知，不持久化事件；重连通过 HTTP 补查，不保证逐事件回放。不支持逐字符协作、OT/CRDT、自动保存、自动合并、离线队列或多节点部署。服务重启后连接丢失且需重新登录；本地未保存草稿只在当前页面内存中，不作为持久化备份。

## 重复执行 M2 浏览器验收

当前 M3 后端需先完成历史表迁移。既有 M2 验收记录保留原阶段测试数量；当前真库回归为 15 项，包含历史功能专项。

已有 MySQL 和本地连接配置必须有效；先运行上面的前端构建及启用真库的 Maven `verify`。测试会创建随机标记的用户和文档，只删除自身成功流程创建的文档，不清空已有数据；失败时可能留下验收记录。迁移测试只在新建的 `dbclasshw_m2_verify_*` 库执行完整升级并保留证据。

浏览器脚本使用本机 Edge 和可选的 Playwright Core，不加入应用依赖。若已有可用的 Playwright Core，通过 `PLAYWRIGHT_MODULE_PATH` 指向其模块目录即可；本次复用了 `backend/target/browser-check/node_modules/playwright-core`。全新环境需要时，可在 `backend/` 下单独安装到忽略目录：

```powershell
npm install --prefix target/browser-check --no-save --package-lock=false playwright-core@1.65.0
```

保持前端 `npm run dev` 运行。在 `backend/` 下执行：

```powershell
# 8080 必须空闲：脚本自己启动 JAR，测试真实停机/重启，结束时关闭自己的后端。
node src/test/browser/lifecycle.cjs

# 然后在另一个终端按正常步骤启动后端，再执行共享与同步测试。
node src/test/browser/sharing.cjs
node src/test/browser/ws.cjs
```

`lifecycle.cjs` 验证注册、资料、富文本、重复提交、失败草稿、刷新、后端重启、持久化、快速切换、删除及 Session 失效；`sharing.cjs` 验证三用户共享界面与角色控制；`ws.cjs` 验证双向同步、查看者通知、在线/断线草稿与 409、自动重连及撤销。全部使用真实 API 和 MySQL；故障验证仅中断请求/业务 WebSocket，不伪造成功响应。日志和截图在 `backend/target/browser-check/`。Edge 在 Agent 沙箱内可能需要运行授权，不能以浏览器启动受限报告验收通过。

## 文档历史版本与恢复（M3 Task 1）

每次成功创建/保存，在同一事务中插入完整状态快照。创建为 v1，保存递增版本；例如 v1 → v2 → v3 后恢复 v1，会产生内容来自 v1 的 v4，原记录保持不变。历史记录无单独修改/删除接口；删除文档时通过外键级联清理。

`document_versions` 包含主键、文档 ID、版本号、标题、正文、格式、修改者 ID、生成时间、change_type、source_version。`(document_id,version_number)` 唯一索引支持倒序查询；editor_id 外键和索引关联用户，删除用户受引用限制；document_id 外键级联删除。使用 InnoDB / utf8mb4，并校验正版本、格式、变更类型和恢复来源。类型为 CREATED、UPDATED、RESTORED、BASELINE。

**已有 M2 数据库升级**：停止所有后端写入，按前面的备份说明保存数据库，使用已有 MySQL 客户端从 `backend/` 执行：

```sql
SOURCE sql/migrate-m3-history.sql;
SELECT COUNT(*) FROM dbclasshw.documents;
SELECT change_type,COUNT(*) FROM dbclasshw.document_versions GROUP BY change_type;
SELECT COUNT(*) FROM dbclasshw.documents d
LEFT JOIN dbclasshw.document_versions v ON v.document_id=d.id AND v.version_number=d.version
WHERE v.id IS NULL;
```

最后一个查询应为 0。全新安装执行最新 `init.sql` 即包含四张业务表，不再运行升级脚本。旧 M1/M2 结构按前面的顺序先完成所有权、富文本和共享升级，再执行历史迁移。

迁移只为尚无历史的文档补一条当前真实版本的 BASELINE，保留全部文档原字段。基线时间沿用 updated_at；editor_id 使用所有者作为基线归属人，**不表示其为实际最后修改者**，不会伪造更早记录。重复执行保留已有合法快照；发现历史超前、当前快照缺失或内容不一致时抛错停止，不能删除记录后强行继续。DDL 隐式提交，出现异常需检查已执行步骤，不依赖完整回滚。

本机已停止后端并执行两次迁移：原 183 个用户、29 篇文档字段不变，29 篇旧文档均有唯一正确基线，未生成更早历史；快照比对材料位于忽略的 `backend/target/sharing-m3-*`。独立测试库验证迁移重跑、异常状态停止及新安装/升级的四表完整定义一致。

| API | 请求 / 响应 |
| --- | --- |
| `GET /api/documents/{id}/versions` | 倒序元数据数组：versionNumber、title、editorId/editorName、createdAt、changeType、sourceVersion 等，不返回正文 |
| `GET /api/documents/{id}/versions/{version}` | 完整快照，content 为字符串，contentFormat 明确标记 PLAIN_TEXT/TIPTAP_JSON |
| `POST /api/documents/{id}/versions/{version}/restore` | JSON `{ "version": 3 }` 表示客户端读取的**当前版本号**；路径中的 version 为历史来源；成功 200 返回新当前文档 |

OWNER/EDITOR/VIEWER 均可读取历史，OWNER/EDITOR 可恢复，VIEWER 恢复返回 403；无权文档及不存在版本返回 404，过期当前版本返回 409。恢复使用 Session + CSRF，不接受客户端指定修改者、所有者或新版本编号。复用文档锁、当前权限和原子乐观锁；文档更新与 RESTORED 插入原子提交，插入失败整笔回滚，成功提交后才发送 DOCUMENT_UPDATED。恢复保留来源格式，旧纯文本不会被猜测为 JSON 或 HTML。

浏览器打开文档 →“历史版本”→选择版本只读预览；当前编辑器与草稿保持独立。恢复前确认生成新版本和保留历史；存在草稿会额外警告。取消或失败/409 保留草稿，成功后加载新当前版本；有权限的其他用户无草稿自动同步，有草稿只提示。预览失败支持重试，过期响应有序号保护。

验证方式：运行前述 `npm run build` 和开启 `DBCLASSHW_MYSQL_TEST=true` 的 Maven `verify`；本次 15 项全部通过（原 14 项回归 + 历史专项），无失败或跳过。历史专项在新建的 `dbclasshw_m3_verify_*` 库用临时触发器拒绝快照，验证创建/保存/恢复回滚、失败不通知、唯一约束、并发、权限撤销和级联；不在原业务库注入失败，测试库保留。

已有 Edge/Playwright Core 环境中，保持前后端运行，在 `backend/` 执行：

```powershell
node src/test/browser/history.cjs
```

本次真实 Edge 已通过 v1–v6 历史、只读预览、OWNER/EDITOR 恢复、VIEWER 只读、取消/409 草稿保护、双向恢复通知、刷新、失败预览重试及纯文本基线恢复；共享与 WebSocket 旧浏览器用例也通过。脚本使用随机测试用户，纯文本夹具由同目录 `HistoryPlainFixture.java` 在事务中创建，只写本轮用户数据，不改已有文档；成功后删除自身文档。截图在 `target/browser-check/history-success.png`。未实现自动保存、差异对比或单独删除历史记录。
