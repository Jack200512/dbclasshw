# dbclasshw

数据库课程大作业：基于 Web 的多人在线文档编辑系统。采用前后端分离的单体架构，围绕文档编辑、权限共享和基础多人协作，体现数据库设计、业务功能与系统完整性。

## 技术栈

- 前端：Vue 3、Vite、Element Plus、Tiptap
- 后端：Java 17、Spring Boot、MyBatis、Spring Security、WebSocket
- 数据库：MySQL 8.x
- 版本管理：Git

## 项目目录

```text
dbclasshw/
├── frontend/          # 前端代码预留目录
├── backend/           # 后端代码预留目录
├── docs/              # 后续设计与验收材料
├── README.md          # 项目入口
├── PROJECT_SPEC.md    # 需求与范围依据
├── AGENTS.md          # Agent 开发规范
├── PROGRESS.md        # 阶段进度
└── .gitignore
```

空目录以 `.gitkeep` 跟踪。

## 当前开发状态

M0 工程初始化已完成，仅建立目录、需求文档与开发规范；尚未生成应用、安装依赖、实现业务或创建数据库表。下一阶段为 M1 最小全栈闭环，详见 [阶段进度](PROGRESS.md)。功能边界以 [项目需求](PROJECT_SPEC.md) 为准。

## 后续运行说明

待 M1 建立可运行应用后，补充环境准备、依赖安装、数据库初始化、前后端启动和验证步骤。当前没有可执行的应用启动命令。

本地环境配置与真实凭据不得提交；后续提供必要配置模板时，仅使用占位值。
