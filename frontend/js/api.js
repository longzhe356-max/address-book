/**
 * 接口层：所有 fetch 都收敛在这里，页面只调用 AddressBookAPI.xxx()。
 *
 * 与后端的约定见 docs/api.md：
 *   GET    /api/health
 *   GET    /api/contacts                -> [ {id,name,group,note,favorite,methods,updatedAt} ]
 *   POST   /api/contacts                -> 201 新建后的完整对象
 *   PUT    /api/contacts/{id}           -> 200 更新后的完整对象
 *   DELETE /api/contacts/{id}           -> 204
 *   PATCH  /api/contacts/{id}/favorite  -> 200 更新后的完整对象
 *   GET    /api/contacts/export         -> xlsx 二进制
 *   GET    /api/contacts/template       -> xlsx 二进制
 *   POST   /api/contacts/import         -> { inserted, updated, failed, errors }
 */
(function (global) {
  'use strict';

  const cfg = global.APP_CONFIG || {};
  const base = String(cfg.API_BASE || '').replace(/\/+$/, '');
  const DEFAULT_TIMEOUT = Number(cfg.REQUEST_TIMEOUT) || 15000;

  class ApiError extends Error {
    constructor(message, status) {
      super(message);
      this.name = 'ApiError';
      this.status = status || 0;
    }
  }

  function endpoint(path) {
    return base + path;
  }

  /** 从 Content-Disposition 里取文件名，优先 RFC 5987 的 filename* */
  function filenameFrom(disposition, fallback) {
    if (!disposition) return fallback;
    const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    if (star) {
      try {
        return decodeURIComponent(star[1].trim());
      } catch (ignored) {
        /* 继续尝试 filename= */
      }
    }
    const plain = /filename="?([^";]+)"?/i.exec(disposition);
    return plain ? plain[1].trim() : fallback;
  }

  async function failMessage(res) {
    let message = '请求失败：HTTP ' + res.status;
    try {
      const data = await res.json();
      message = data.message || data.msg || data.error || message;
    } catch (ignored) {
      /* 响应不是 JSON，保留默认文案 */
    }
    return new ApiError(message, res.status);
  }

  async function request(path, options) {
    const opts = options || {};
    const method = opts.method || 'GET';
    const timeout = opts.timeout || DEFAULT_TIMEOUT;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);

    const headers = { Accept: 'application/json' };
    let body = opts.body;
    if (body !== undefined && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json;charset=UTF-8';
      body = JSON.stringify(body);
    }

    let res;
    try {
      res = await fetch(endpoint(path), { method, headers, body, signal: controller.signal });
    } catch (err) {
      const text = err && err.name === 'AbortError'
        ? '请求超时（' + Math.round(timeout / 1000) + ' 秒）'
        : '无法连接后端：请确认 ' + (base || '同源') + ' 已启动，并允许跨域';
      throw new ApiError(text, 0);
    } finally {
      clearTimeout(timer);
    }

    if (!res.ok) throw await failMessage(res);
    if (res.status === 204) return null;

    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  /** 下载二进制（导出 Excel / 导入模板） */
  async function download(path, fallbackName) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 60000);
    let res;
    try {
      res = await fetch(endpoint(path), { headers: { Accept: '*/*' }, signal: controller.signal });
    } catch (err) {
      throw new ApiError('下载失败：无法连接后端', 0);
    } finally {
      clearTimeout(timer);
    }
    if (!res.ok) throw await failMessage(res);
    if ((res.headers.get('Content-Type') || '').includes('json')) {
      throw await failMessage(res);
    }
    const blob = await res.blob();
    return { blob, filename: filenameFrom(res.headers.get('Content-Disposition'), fallbackName) };
  }

  function saveBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename || 'download';
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  global.AddressBookAPI = {
    ApiError,
    baseUrl: base,
    health: () => request('/api/health', { timeout: Number(cfg.HEALTH_TIMEOUT) || 2500 }),
    list: () => request('/api/contacts'),
    get: (id) => request('/api/contacts/' + encodeURIComponent(id)),
    create: (contact) => request('/api/contacts', { method: 'POST', body: contact }),
    update: (id, contact) => request('/api/contacts/' + encodeURIComponent(id), { method: 'PUT', body: contact }),
    remove: (id) => request('/api/contacts/' + encodeURIComponent(id), { method: 'DELETE' }),
    setFavorite: (id, favorite) => request('/api/contacts/' + encodeURIComponent(id) + '/favorite', {
      method: 'PATCH',
      body: { favorite: !!favorite }
    }),
    exportAll: () => download('/api/contacts/export', '通讯录.xlsx'),
    template: () => download('/api/contacts/template', '通讯录导入模板.xlsx'),
    importFile: (file) => {
      const form = new FormData();
      form.append('file', file, file.name);
      return request('/api/contacts/import', { method: 'POST', body: form, timeout: 60000 });
    },
    saveBlob
  };
})(window);
