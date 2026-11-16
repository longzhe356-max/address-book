# 通讯录 · Address Book

一个前后端分离的通讯录应用：联系人增删改查、收藏、多联系方式、Excel 导入导出。后端 Spring Boot + H2，前端纯原生 HTML/CSS/JavaScript（无构建步骤、无第三方依赖）。

## 技术栈

| 部分 | 选型 |
| --- | --- |
| 后端 | Java 21 · Spring Boot 3.3.5（Web / Data JPA / Validation）· Apache POI 5.3.0 |
| 数据库 | H2 文件库（`backend/data/addressbook.mv.db`，运行后自动生成） |
| 前端 | 原生 HTML + CSS + JavaScript，无框架、无打包 |
| 测试 | JUnit 5 · MockMvc · AssertJ（28 个单元 / 接口测试） |
| 构建 | Maven 3.9 |

## 功能

- **联系人管理**：新增、编辑、删除（二次确认）、关键字搜索、分组筛选
- **收藏**：星标切换、只看收藏、收藏数统计
- **多联系方式**：一个联系人可挂多条电话 / 邮箱 / 社交账号 / 地址 / 其他，支持编辑时动态增删
- **Excel 导入导出**：导出 `.xlsx`、下载导入模板、上传导入并按「新增 / 合并 / 跳过」给出结果与错误明细
- **界面**：三栏布局、深色模式、骨架屏、响应式（≥1180 三栏 / ≤960 单栏 / ≤700 手机）、`Ctrl+K` 聚焦搜索

## 目录结构

```
.
├── frontend/                 # 静态页面，任意静态服务器可跑
│   ├── index.html
│   ├── css/style.css
│   └── js/{config,api,app}.js
└── backend/                  # Spring Boot 服务
    ├── pom.xml
    ├── docs/                 # 接口文档、模块设计、编码规范
    └── src/{main,test}/...
```

## 快速开始

需要 JDK 21 和 Maven 3.9。开两个终端：

```bash
# 终端 1：后端（默认 8080）
cd backend
mvn spring-boot:run

# 终端 2：前端（默认 5500）
cd frontend
python -m http.server 5500     # 或 npx serve -l 5500 .
```

浏览器打开 <http://localhost:5500>，右上角显示「已连接后端」即联调成功。

> 直接双击 `index.html` 用 `file://` 打开会被浏览器跨域策略拦截，请用静态服务器。

前端后端地址在 `frontend/js/config.js` 的 `API_BASE` 中配置，同源部署时改成空字符串 `''`。

运行测试与打包：

```bash
cd backend
mvn test                # 28 个测试
mvn package             # 产出 target/address-book-0.9.0.jar
java -jar target/address-book-0.9.0.jar
```

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| GET | `/api/contacts` | 列表（可选 `?keyword=`） |
| GET | `/api/contacts/favorites` | 收藏列表 |
| POST | `/api/contacts` | 新建 |
| PUT | `/api/contacts/{id}` | 整体更新 |
| DELETE | `/api/contacts/{id}` | 删除 |
| PATCH | `/api/contacts/{id}/favorite` | 收藏 / 取消收藏 |
| POST / PUT / DELETE | `/api/contacts/{id}/methods[/{methodId}]` | 联系方式增改删 |
| GET | `/api/contacts/export` | 导出 `.xlsx` |
| GET | `/api/contacts/template` | 下载导入模板 |
| POST | `/api/contacts/import` | 上传 `.xlsx` 导入 |

完整字段与示例见 [`backend/docs/api.md`](backend/docs/api.md)，模块划分见 [`backend/docs/module-design.md`](backend/docs/module-design.md)。

## 界面预览

截图放在博客里，这里用外链引用（把地址换成你自己的图片即可）：

<!-- ![通讯录界面](https://example.com/screenshot.png) -->

> 图文说明：<!-- 博客链接 -->

## 已知边界

- H2 控制台默认开启（`/h2-console`，用户 `sa`、空密码），仅适合本地开发；正式部署请关闭并换用持久化数据库。
- 跨域目前对 `/api/**` 放开所有来源（见 `WebConfig`），生产环境应收紧到实际域名。

## 许可证

[MIT](LICENSE)
