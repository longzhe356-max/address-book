/**
 * 前端配置：只改这里即可切换后端地址。
 */
window.APP_CONFIG = {
  // 前后端分离：前端跑在 5500，后端跑在 8080
  // 若由后端托管静态文件（同源），把这里改成 ''
  API_BASE: 'http://localhost:8080',

  // 探测 /api/health 的超时时间（毫秒）
  HEALTH_TIMEOUT: 2500,

  // 请求超时（毫秒）
  REQUEST_TIMEOUT: 15000
};
