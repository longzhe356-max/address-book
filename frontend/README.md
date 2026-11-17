# 通讯录 · 前端（前后端分离）

纯 HTML + CSS + 原生 JavaScript 实现，**没有构建步骤、没有第三方依赖**，任何静态服务器都能跑。通过 REST 调用同组后端（Spring Boot），前后端分别部署。

## 1. 运行

前后端分离：前端跑在 5500，后端跑在 8080，各开一个终端。

后端先起来：

```bash
cd ../backend      # 后端工程
mvn spring-boot:run
```

前端用任意静态服务器（默认 5500）：

```bash
cd frontend
python -m http.server 5500
# 或 npx serve -l 5500 .
```

浏览器打开 <http://localhost:5500>，右上角出现绿色「已连接后端」即联调成功。

> 直接用 `file://` 打开 `index.html` 会因为跨域被浏览器拦住，请务必用静态服务器。

## 2. 配置

只改 `js/config.js`：

```js
window.APP_CONFIG = {
  API_BASE: 'http://localhost:8080', // 后端地址；同源部署时留空 ''
  HEALTH_TIMEOUT: 2500,
  REQUEST_TIMEOUT: 15000
};
```

后端已在 `WebConfig` 中对 `/api/**` 开放 CORS 并暴露 `Content-Disposition`，所以跨端口可以正常读取导出文件名。

## 3. 目录结构

```
frontend
├── index.html          页面结构（顶栏 / 三栏 / 弹窗 / toast）
├── css/style.css       全部样式（CSS 变量、深色模式、响应式、骨架屏）
└── js
    ├── config.js       后端地址与超时
    ├── api.js          接口层：所有 fetch 收敛在这里
    └── app.js          状态、渲染与交互
```

## 4. 功能与评分点对照

| 作业要求 | 分值 | 前端实现 |
| --- | --- | --- |
| 页面样式 | 10 | 三栏布局、卡片、深色模式、骨架屏、响应式（≥1180 三栏 / ≤960 单栏 / ≤700 手机） |
| 1.1 收藏联系人 | 25 | 列表星标 + 详情页收藏按钮、乐观更新、左侧「收藏」筛选、收藏数统计 |
| 1.2 多个联系方式 | 10 | 编辑弹窗动态增删行，类型可选 电话 / 邮箱 / 社交账号 / 地址 / 其他 |
| 1.3 导入导出 | 25 | 「导出」直连后端下载 `.xlsx`；「导入」上传文件，弹窗展示 新增/合并/跳过 与错误明细 |
| 交互细节 | — | 关键字搜索、分组筛选、`Ctrl+K` 聚焦搜索、`Esc` 关弹窗、空状态、toast 提示、`DELETE` 二次确认 |

## 5. 与后端的接口对应

| 前端调用 | 后端接口 |
| --- | --- |
| `API.health()` | `GET /api/health` |
| `API.list()` | `GET /api/contacts` |
| `API.create(payload)` | `POST /api/contacts` |
| `API.update(id, payload)` | `PUT /api/contacts/{id}` |
| `API.remove(id)` | `DELETE /api/contacts/{id}` |
| `API.setFavorite(id, bool)` | `PATCH /api/contacts/{id}/favorite` |
| `API.exportAll()` | `GET /api/contacts/export` |
| `API.template()` | `GET /api/contacts/template` |
| `API.importFile(file)` | `POST /api/contacts/import`（`multipart/form-data`，字段 `file`） |

数据模型：`{ id, name, group, note, favorite, methods: [{ type, value }], updatedAt }`，
`type ∈ { PHONE, EMAIL, SOCIAL, ADDRESS, OTHER }`。详见 [../backend/docs/api.md](../backend/docs/api.md)。

## 6. 已做的浏览器验证

用 Edge DevTools 协议跑了真实浏览器端到端（`frontend` + 后端同时启动）：

- 列表渲染 5 条、连接徽标「已连接后端」、有数据时空状态隐藏、默认选中第一位
- 新建联系人（两条联系方式）→ 列表 6 条、详情正确
- 收藏 → 后端 `favorite` 变 true
- 搜索过滤 → 1 条，清空恢复
- 导出 → 真实下载 `通讯录_20261116.xlsx`（4.4 KB）
- 导入同一文件 → 结果弹窗 `新增 0 / 合并 6 / 跳过 0`
- 移动端 414px 布局截图
- 全程无控制台报错
