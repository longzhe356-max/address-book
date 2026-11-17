# 编码规范

本工程遵循以下约定，新增代码请保持一致。作业博客中的「代码规范链接」指向本文件。

## 1. 命名

| 类型 | 规则 | 示例 |
| --- | --- | --- |
| 包名 | 全小写，按层划分 | `com.fzu.addressbook.service` |
| 类名 | 大驼峰 | `ContactService`、`ExcelController` |
| 方法 / 变量 | 小驼峰 | `listFavorites`、`updatedAt` |
| 常量 | 全大写下划线 | `MAX_ERRORS`、`SHEET_NAME` |
| 数据库表 / 列 | 小写下划线 | `contact_method`、`created_at` |

## 2. 分层职责

- `controller` 不写业务判断，只做参数接收与响应包装。
- `service` 承担业务规则与事务边界；对外暴露 DTO，不把实体直接返回给前端。
- `repository` 只声明查询，不写业务逻辑。
- `domain` 中的实体不依赖 DTO 与 Spring MVC。

## 3. 接口与异常

- 统一返回 JSON；创建返回 `201`，删除返回 `204`。
- 业务异常使用 `BadRequestException`（400）与 `NotFoundException`（404），由 `GlobalExceptionHandler` 统一转换。
- 请求 DTO 使用 `jakarta.validation` 注解做基础校验，业务校验放在 service。

## 4. 代码风格

- 缩进 4 个空格，UTF-8 编码，文件末尾留一个空行。
- 单个方法尽量不超过 60 行；超过时抽取私有方法。
- 公开类、公开方法必须写 Javadoc，说明**做什么**而不是**怎么做**。
- 避免魔法值：重复出现的字符串 / 数字提取为常量。
- 集合为空时返回空集合而非 `null`。

## 5. 日志

- 使用 SLF4J，禁止 `System.out.println`。
- 只记录异常与关键状态变化，不打印用户隐私字段（完整手机号、邮箱）到日志。

## 6. 测试

- 新增业务方法需配套单元测试，命名 `方法名_场景_期望`。
- 测试使用 H2 内存库，避免依赖本地数据文件。
- 接口变更需同步更新 `docs/api.md` 与 README 的接口表。

## 7. 提交规范

提交信息采用 `<类型>: <说明>` 前缀：

| 类型 | 用途 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | 修复缺陷 |
| `refactor` | 重构（不改变行为） |
| `test` | 测试相关 |
| `docs` | 文档 |
| `chore` | 构建、依赖、配置 |

一次提交只做一件事，说明使用中文或英文均可，但需能说明「做了什么」。
