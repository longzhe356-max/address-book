# 模块划分与设计说明

## 1. 分层结构

```
controller（HTTP 层，参数校验、状态码）
     ↓
service（业务规则、事务边界）
     ↓
repository（Spring Data JPA）
     ↓
domain（实体、枚举）
```

- **controller** 只做参数接收、调用 service、包装响应，不写业务判断。
- **service** 承担业务规则（收藏、去重、导入合并）与事务控制。
- **dto** 与实体解耦：请求体不直接绑定实体，避免外部传入 `id`、`updatedAt` 等不应由客户端控制的字段。
- **exception** 统一异常出口，保证前端拿到的错误结构一致。

## 2. 模块职责

| 模块 | 职责 | 主要类 |
| --- | --- | --- |
| 联系人管理 | 增删改查、搜索、收藏状态维护 | `ContactService`、`ContactController` |
| 联系方式管理 | 多类型联系方式增删改、去重、类型解析 | `ContactMethod`、`MethodType`、`ContactService` |
| Excel 导入导出 | 工作簿生成与解析、表头别名、逐行校验、同名合并 | `ExcelService`、`ExcelController` |
| 持久化 | 实体映射、查询、级联删除 | `Contact`、`ContactRepository` |
| 基础设施 | 异常处理、跨域、健康检查 | `GlobalExceptionHandler`、`WebConfig`、`HealthController` |

## 3. 与前端约定的契约

前后端并行开发，先约定数据模型再各写一半：

| 约定项 | 内容 |
| --- | --- |
| 联系人字段 | `id / name / group / note / favorite / methods / updatedAt` |
| 联系方式字段 | `type / value`（前端只发这两个） |
| 类型取值 | `phone / email / social / address / other` |
| 列表返回 | JSON 数组；收藏优先、`updatedAt` 倒序 |
| 收藏接口 | `PATCH /api/contacts/{id}/favorite`，body `{favorite:boolean}` |
| 导出列 | 姓名、分组、电话、邮箱、社交账号、地址、其他、收藏、备注 |
| 导入返回 | `inserted / updated / failed / errors` |

后端对类型字段做了大小写与中英文的双向兼容（`phone`、`PHONE`、`电话` 都接受），这样前端改字段名或历史数据混用都不会 500。

## 4. 关键设计取舍

### 4.1 联系方式独立成表

作业 1.2 要求「一个联系人关联多种联系方式」。采用 `contact_method` 子表而非在 `contact` 上加 `phone/email` 列，原因：

1. 同一类型可以有多条（工作手机、家庭手机），列模型会退化成 `phone1/phone2/...`。
2. 新增类型只需新增枚举值，无需改表结构。
3. 便于按类型分组导出、按内容搜索。

### 4.2 导出「一人一行、一类一列」

Excel 要求「每个联系人占一行、每类信息占一列」，与前端 `contactsToRows()` 的实现保持一致：

```
姓名   分组   电话                       邮箱                  收藏  备注
张三   同事   13800000000;0591-2286...   zhangsan@example.com  是    前端组组长
```

同一列内多条同类型联系方式用 `;` 分隔，既符合作业要求的扁平结构，又不丢失信息。

### 4.3 导入的容错与合并

- **按行校验、按行跳过**：单行不合格不影响其他行，结果集中返回 `inserted / updated / failed / errors`。
- **同名合并**：姓名相同且至少一条联系方式相同视为同一人，合并联系方式并更新分组/备注，避免重复导入产生重复联系人（与前端演示模式的逻辑一致）。
- **整体事务**：合法行在同一事务内落库。
- **表头定位**：允许用户在第 1 行上方插入标题，向下最多找 5 行；表头按别名识别，列顺序可变。
- **数值单元格处理**：直接输入手机号时 Excel 会存为数值，读取时用 `BigDecimal` 还原为普通数字串，避免科学计数法。

### 4.4 数据库选择

采用 H2 文件数据库而非 MySQL：

- 作业要求「代码应可构建」，H2 让评阅者 `mvn spring-boot:run` 后无需安装数据库；
- JPA 方言与实体映射与 MySQL 基本一致，后续切换只需替换 `application.yml` 的数据源配置；
- `group` 是 SQL 保留字，列名映射为 `contact_group`，JSON 字段不受影响。

### 4.5 事务边界

- 读操作类级 `@Transactional(readOnly = true)`，写操作方法级 `@Transactional`。
- `update` 采用「清空 + 重建」联系方式并依赖 `orphanRemoval` 删除旧记录，保证与提交内容完全一致。
- 导入方法整体事务，解析阶段只做内存校验。

## 5. 扩展点

| 需求 | 扩展方式 |
| --- | --- |
| 支持用户隔离 | 增加 `User` 实体与 `contact.user_id`，service 层按当前登录用户过滤 |
| 支持标签 | 复用 `contact_method.label` 字段，前端补一个可选标签输入框即可 |
| 支持更多导入格式 | 在 `ExcelService` 增加 CSV / vCard 解析器，复用 `parseRow` 的字段映射 |
| 部署到云服务器 | 打包 `jar` 后用 `java -jar` 运行，前端静态文件放 `src/main/resources/static` 实现同源部署 |
