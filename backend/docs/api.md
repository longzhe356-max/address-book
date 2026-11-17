# 接口文档

与前端 `frontend/js/api.js` 的调用方式一一对应，可直接联调。

- Base URL：`http://localhost:8080`
- 返回 JSON（导出接口返回二进制），字符集 UTF-8
- 字段命名与前端模型一致：`name / group / note / favorite / methods[{type,value}] / updatedAt`

## 通用约定

### 错误响应

```json
{
  "timestamp": "2026-11-16T21:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "name: 姓名不能为空",
  "details": ["name: 姓名不能为空"]
}
```

前端会读取 `message` / `msg` / `error` 任一字段做 toast 提示。

| 状态码 | 含义 |
| --- | --- |
| 200 | 请求成功 |
| 201 | 创建成功 |
| 204 | 删除成功（无响应体） |
| 400 | 参数校验失败 / 文件格式不正确 |
| 404 | 联系人、联系方式不存在 |
| 500 | 服务器内部错误 |

### 联系方式类型

| 传值（大小写均可） | 中文 | 导出列 |
| --- | --- | --- |
| `phone` / `tel` / `mobile` / `电话` | 电话 | 电话 |
| `email` / `mail` / `邮箱` | 邮箱 | 邮箱 |
| `social` / `wechat` / `qq` / `社交账号` | 社交账号 | 社交账号 |
| `address` / `addr` / `地址` | 地址 | 地址 |
| `other` / `其他` | 其他 | 其他 |

响应中的 `type` 统一返回大写枚举名（`PHONE / EMAIL / SOCIAL / ADDRESS / OTHER`），`typeTitle` 返回中文名。

---

## 1. 联系人

### 1.1 列表

`GET /api/contacts?keyword=张`

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `keyword` | 否 | 模糊匹配姓名、分组、备注、任意联系方式内容 |

返回 **JSON 数组**（前端 `pickArray` 也兼容 `{items:[...]}` 形式）：

```json
[
  {
    "id": 1,
    "name": "张三",
    "group": "同事",
    "note": "前端组组长",
    "favorite": true,
    "createdAt": "2026-11-16T20:00:00",
    "updatedAt": "2026-11-16T20:30:00",
    "methods": [
      { "id": 1, "type": "PHONE", "typeTitle": "电话", "label": null, "value": "13800000000" },
      { "id": 2, "type": "EMAIL", "typeTitle": "邮箱", "label": null, "value": "zhangsan@example.com" }
    ]
  }
]
```

排序：收藏优先，其次按 `updatedAt` 倒序。

### 1.2 收藏列表

`GET /api/contacts/favorites`，返回 `favorite = true` 的联系人，按 `updatedAt` 倒序。

### 1.3 详情

`GET /api/contacts/{id}`

### 1.4 新建

`POST /api/contacts`

```json
{
  "name": "张三",
  "group": "同事",
  "note": "前端组组长",
  "favorite": true,
  "methods": [
    { "type": "phone", "value": "13800000000" },
    { "type": "email", "value": "zhangsan@example.com" },
    { "type": "social", "value": "微信 zhangwei_wx" }
  ]
}
```

- `name` 必填（≤100）；`group` ≤100，留空保存为 `未分组`；`note` ≤1000。
- `methods` **至少 1 条**，每条 `value` 不能为空，否则返回 `400`；同类型同内容的重复项会自动去重。
- 成功返回 `201` 与完整联系人对象。

### 1.5 整体更新

`PUT /api/contacts/{id}`

请求体同新建。**`methods` 全量替换**（前端编辑弹窗提交的就是完整表单，语义一致），同样要求至少 1 条。未提交 `favorite` 时保持原值，提交了则覆盖。

### 1.6 删除

`DELETE /api/contacts/{id}` → `204`，级联删除其联系方式。

### 1.7 收藏 / 取消收藏

`PATCH /api/contacts/{id}/favorite`

```json
{ "favorite": true }
```

返回更新后的联系人对象。

---

## 2. 联系方式

### 2.1 追加

`POST /api/contacts/{id}/methods`

```json
{ "type": "social", "value": "QQ 10001" }
```

返回 `201` 与新建的联系方式对象；重复内容返回 `400`。

### 2.2 修改

`PUT /api/contacts/{id}/methods/{methodId}`，请求体同上。

### 2.3 删除

`DELETE /api/contacts/{id}/methods/{methodId}` → `204`。

---

## 3. Excel 导入导出

### 3.1 导出

`GET /api/contacts/export`

- 响应类型：`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- 文件名为 `address-book-YYYY-MM-DD.xlsx`（`Content-Disposition` 同时给出 `filename` 与 `filename*`，前端可解析）
- 列顺序：姓名、分组、电话、邮箱、社交账号、地址、其他、收藏、备注

### 3.2 下载模板

`GET /api/contacts/template` → `address-book-template.xlsx`，含「通讯录」与「填写说明」两个工作表。

### 3.3 导入

`POST /api/contacts/import`（`multipart/form-data`，字段名 `file`）

```bash
curl -F "file=@address-book.xlsx" http://localhost:8080/api/contacts/import
```

响应（字段名与前端 `importContacts()` 读取的一致）：

```json
{
  "totalRows": 10,
  "inserted": 8,
  "updated": 1,
  "failed": 1,
  "errors": ["第 5 行：没有联系方式，已跳过"]
}
```

| 字段 | 说明 |
| --- | --- |
| `totalRows` | 文件中非空数据行数 |
| `inserted` | 新增的联系人数 |
| `updated` | 合并到已有联系人的数量（姓名相同且至少一条联系方式相同） |
| `failed` | 被跳过的行数 |
| `errors` | 跳过原因（最多 100 条） |

约束：

- 仅接受 `.xlsx` / `.xls` 且内容有效的 Excel 文件。
- 表头按别名识别，必须包含「姓名」列；表头前最多允许 5 行标题。
- 单次最多导入 5000 行（`app.excel.max-rows` 可配置）。
- 同一单元格内多个值用 `;` 或 `；` 分隔。

---

## 4. 健康检查

`GET /api/health` —— 前端启动时用它判断是否进入「已连接后端」模式（只判断 2xx，1.5s 超时则回落到演示模式）。

```json
{ "status": "ok", "service": "address-book", "time": "2026-11-16T20:00:00" }
```
