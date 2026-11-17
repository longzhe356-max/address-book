# 通讯录后端（Spring Boot）

通讯录应用的后端服务，提供 REST + JSON 接口：联系人增删改查、收藏、多联系方式、Excel 导入导出。

前端工程在同仓库的 [`../frontend/`](../frontend/)，两端通过 HTTP 通信，接口契约见 [docs/api.md](docs/api.md)。

## 技术栈

| 层面 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 3.3.5（Web、Data JPA、Validation） |
| 数据库 | H2 文件库（`./data/addressbook.mv.db`） |
| Excel 读写 | Apache POI 5.3.0 |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc + AssertJ |
| 构建 | Maven 3.9 |

## 快速开始

```bash
cd backend
mvn spring-boot:run
```

- 接口：<http://localhost:8080/api/contacts>
- 健康检查：<http://localhost:8080/api/health> → `{"status":"ok"}`
- H2 控制台：<http://localhost:8080/h2-console>，JDBC URL `jdbc:h2:file:./data/addressbook`，用户 `sa`，密码空

构建与测试：

```bash
mvn test              # 28 个单元 / 接口测试
mvn package           # 产出 target/address-book-0.9.0.jar
java -jar target/address-book-0.9.0.jar
```

## 配置

`src/main/resources/application.yml`：

| 配置 | 说明 | 默认 |
| --- | --- | --- |
| `server.port` | 监听端口 | `8080` |
| `spring.datasource.url` | H2 文件路径 | `jdbc:h2:file:./data/addressbook` |
| `app.excel.max-rows` | 单次导入行数上限 | `5000` |
| `app.excel.max-methods-per-type` | 每类联系方式上限 | `20` |

> 注意：H2 2.x 不允许 `AUTO_SERVER=TRUE` 与 `DB_CLOSE_ON_EXIT=FALSE` 同时出现（启动报 `Feature not supported [50100-224]`）。单实例部署不要加 `AUTO_SERVER`。

## 数据模型

```
contact（联系人）
  id, name, contact_group, note, favorite, created_at, updated_at
        │ 1
        │ N
contact_method（联系方式）
  id, contact_id, type(PHONE/EMAIL/SOCIAL/ADDRESS/OTHER), label, value
```

- 删除联系人级联删除其联系方式（`orphanRemoval = true`）。
- 同一联系人下「类型 + 内容」完全相同的联系方式不会被重复写入。
- `group` 是 SQL 保留字，列名用 `contact_group`，JSON 字段仍是 `group`。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| GET | `/api/contacts` | 列表（可选 `?keyword=`） |
| GET | `/api/contacts/favorites` | 收藏列表 |
| GET | `/api/contacts/{id}` | 详情 |
| POST | `/api/contacts` | 新建（至少一条联系方式） |
| PUT | `/api/contacts/{id}` | 整体更新（联系方式全量替换） |
| DELETE | `/api/contacts/{id}` | 删除 |
| PATCH | `/api/contacts/{id}/favorite` | 收藏 / 取消收藏 |
| POST | `/api/contacts/{id}/methods` | 追加联系方式 |
| PUT | `/api/contacts/{id}/methods/{methodId}` | 修改联系方式 |
| DELETE | `/api/contacts/{id}/methods/{methodId}` | 删除联系方式 |
| GET | `/api/contacts/export` | 导出 `.xlsx` |
| GET | `/api/contacts/template` | 下载导入模板 |
| POST | `/api/contacts/import` | 上传 `.xlsx` 导入 |

字段与示例见 [docs/api.md](docs/api.md)。

## Excel 格式

导出与导入同一套表头，一人一行、一类一列：

| 姓名 | 分组 | 电话 | 邮箱 | 社交账号 | 地址 | 其他 | 收藏 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

- 同一列内多条值用 `;` 分隔。
- 导入按表头别名识别，列顺序可换；缺姓名或没有联系方式的行会被跳过并写进 `errors`。
- 同名且至少一条联系方式相同的记录会**合并**，结果区分 `inserted` / `updated` / `failed`。

## 目录结构

```
backend
├── pom.xml
├── README.md
├── docs/
│   ├── api.md                 # 接口文档
│   ├── module-design.md       # 模块划分与设计说明
│   └── coding-standard.md     # 编码规范
├── src/main/java/com/fzu/addressbook
│   ├── AddressBookApplication.java
│   ├── config/WebConfig.java          # 跨域配置
│   ├── controller/                    # HTTP 层
│   ├── domain/                        # 实体与枚举
│   ├── dto/                           # 请求 / 响应对象
│   ├── exception/                     # 统一异常处理
│   ├── repository/                    # JPA 仓储
│   └── service/                       # 业务逻辑（含 Excel 读写）
├── src/main/resources/application.yml
├── src/test/java/com/fzu/addressbook   # 单元测试与接口测试
└── data/                              # 运行后生成的 H2 数据文件（已被 git 忽略）
```

## 模块划分

| 模块 | 职责 | 关键类 |
| --- | --- | --- |
| 联系人管理 | 增删改查、搜索、收藏 | `ContactService`、`ContactController` |
| 联系方式管理 | 多类型联系方式增删改、去重 | `ContactMethod`、`ContactService#addMethod` |
| Excel 导入导出 | 工作簿生成解析、表头别名、逐行校验、同名合并 | `ExcelService`、`ExcelController` |
| 持久化 | 实体关系、查询 | `Contact`、`ContactRepository` |
| 基础设施 | 统一异常、跨域、健康检查 | `GlobalExceptionHandler`、`WebConfig`、`HealthController` |

设计取舍见 [docs/module-design.md](docs/module-design.md)。

## 接口自测

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/contacts
curl -X POST http://localhost:8080/api/contacts -H "Content-Type: application/json" \
  -d '{"name":"测试","group":"同事","favorite":false,"methods":[{"type":"phone","value":"13800000000"}]}'
curl -X PATCH http://localhost:8080/api/contacts/1/favorite -H "Content-Type: application/json" -d '{"favorite":true}'
curl -o 通讯录.xlsx http://localhost:8080/api/contacts/export
curl -F "file=@通讯录.xlsx" http://localhost:8080/api/contacts/import
```
