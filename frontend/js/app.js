/**
 * 页面逻辑：状态 + 渲染 + 交互。
 * 只依赖 AddressBookAPI（js/api.js）与原生 DOM，无任何框架/构建步骤。
 */
(function () {
  'use strict';

  const API = window.AddressBookAPI;

  const TYPE_ORDER = ['PHONE', 'EMAIL', 'SOCIAL', 'ADDRESS', 'OTHER'];
  const TYPE_LABEL = { PHONE: '电话', EMAIL: '邮箱', SOCIAL: '社交账号', ADDRESS: '地址', OTHER: '其他' };

  /** 统一用线性 SVG 图标，避免彩色 emoji 与单色界面混搭 */
  const ICON = {
    PHONE: '<svg viewBox="0 0 24 24"><path d="M6.4 3h3.1l1.5 4-2 1.5a12.4 12.4 0 0 0 6.5 6.5l1.5-2 4 1.5v3.1A2.4 2.4 0 0 1 18.6 20C10.6 19.2 4.8 13.4 4 5.4A2.4 2.4 0 0 1 6.4 3Z"/></svg>',
    EMAIL: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="m3.5 7.5 8.5 5.6 8.5-5.6"/></svg>',
    SOCIAL: '<svg viewBox="0 0 24 24"><path d="M21 11.6a8.4 8.4 0 0 1-9 8.4H7.2L3 22l1.1-4.6A8.4 8.4 0 1 1 21 11.6Z"/></svg>',
    ADDRESS: '<svg viewBox="0 0 24 24"><path d="M12 21s7-6.3 7-11a7 7 0 1 0-14 0c0 4.7 7 11 7 11Z"/><circle cx="12" cy="10" r="2.4"/></svg>',
    OTHER: '<svg viewBox="0 0 24 24"><path d="M4 8.5A2.5 2.5 0 0 1 6.5 6H11l2 2h4.5A2.5 2.5 0 0 1 20 10.5v6A2.5 2.5 0 0 1 17.5 19h-11A2.5 2.5 0 0 1 4 16.5Z"/></svg>',
    EDIT: '<svg viewBox="0 0 24 24"><path d="M4 20h4l10.5-10.5a2.1 2.1 0 0 0-3-3L5 17Z"/><path d="m14.5 6.5 3 3"/></svg>',
    TRASH: '<svg viewBox="0 0 24 24"><path d="M4 7h16"/><path d="M9 7V5h6v2"/><path d="M6 7l1 13h10l1-13"/></svg>'
  };

  const state = {
    contacts: [],
    filter: { type: 'all', group: null },
    keyword: '',
    selectedId: null,
    loading: true,
    error: null,
    backendOnline: false,
    editingId: null
  };

  const el = {};

  function $(id) {
    return document.getElementById(id);
  }

  function cacheElements() {
    el.search = $('search-input');
    el.connBadge = $('conn-badge');
    el.btnNew = $('btn-new');
    el.btnEmptyNew = $('btn-empty-new');
    el.btnImport = $('btn-import');
    el.btnExport = $('btn-export');
    el.btnTemplate = $('btn-template');
    el.fileInput = $('file-input');
    el.filterNav = $('filter-nav');
    el.groupNav = $('group-nav');
    el.groupTitle = $('group-title');
    el.listTitle = $('list-title');
    el.listCount = $('list-count');
    el.list = $('contact-list');
    el.listEmpty = $('list-empty');
    el.emptyText = $('empty-text');
    el.detail = $('detail');
    el.detailEmpty = $('detail-empty');
    el.statTotal = $('stat-total');
    el.statFav = $('stat-fav');
    el.editorModal = $('editor-modal');
    el.editorTitle = $('editor-title');
    el.editorForm = $('editor-form');
    el.formError = $('form-error');
    el.methodRows = $('method-rows');
    el.groupOptions = $('group-options');
    el.confirmModal = $('confirm-modal');
    el.confirmText = $('confirm-text');
    el.confirmOk = $('confirm-ok');
    el.resultModal = $('result-modal');
    el.resInserted = $('res-inserted');
    el.resUpdated = $('res-updated');
    el.resFailed = $('res-failed');
    el.resErrors = $('res-errors');
    el.toasts = $('toast-stack');
  }

  /* ---------------- 小工具 ---------------- */

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function initials(name) {
    const text = String(name || '').trim();
    if (!text) return '?';
    return /[a-zA-Z]/.test(text[0]) ? text[0].toUpperCase() : text.slice(0, 1);
  }

  /** 用姓名生成稳定的头像色相，避免每次刷新换色 */
  function hueOf(name) {
    let hash = 0;
    for (let i = 0; i < String(name || '').length; i++) {
      hash = (hash * 31 + String(name).charCodeAt(i)) % 360;
    }
    return hash;
  }

  function formatTime(iso) {
    if (!iso) return '—';
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return '—';
    const pad = (n) => (n < 10 ? '0' + n : String(n));
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
      + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
  }

  function debounce(fn, wait) {
    let timer = null;
    return function () {
      const args = arguments;
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(null, args), wait);
    };
  }

  /* ---------------- toast ---------------- */

  function toast(message, type) {
    const node = document.createElement('div');
    node.className = 'toast' + (type ? ' toast-' + type : '');
    node.innerHTML = '<span class="toast-dot"></span><span>' + escapeHtml(message) + '</span>';
    el.toasts.appendChild(node);
    requestAnimationFrame(() => node.classList.add('is-in'));
    setTimeout(() => {
      node.classList.remove('is-in');
      setTimeout(() => node.remove(), 220);
    }, type === 'error' ? 5200 : 3200);
  }

  /* ---------------- 数据加载 ---------------- */

  async function checkBackend() {
    try {
      await API.health();
      state.backendOnline = true;
    } catch (err) {
      state.backendOnline = false;
    }
    renderConn();
  }

  async function loadContacts() {
    state.loading = true;
    state.error = null;
    renderList();
    try {
      const data = await API.list();
      state.contacts = Array.isArray(data) ? data : [];
      state.backendOnline = true;
      if (!state.contacts.some((c) => String(c.id) === String(state.selectedId))) {
        state.selectedId = state.contacts.length ? state.contacts[0].id : null;
      }
    } catch (err) {
      state.error = err.message;
      state.contacts = [];
      state.backendOnline = false;
    } finally {
      state.loading = false;
      renderAll();
    }
  }

  /* ---------------- 派生数据 ---------------- */

  function groupOf(contact) {
    return String((contact && contact.group) || '').trim() || '未分组';
  }

  function groupStats() {
    const map = new Map();
    state.contacts.forEach((contact) => {
      const group = groupOf(contact);
      map.set(group, (map.get(group) || 0) + 1);
    });
    return Array.from(map.entries()).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'zh-Hans-CN'));
  }

  function matchesKeyword(contact, keyword) {
    if (!keyword) return true;
    const haystack = [contact.name, contact.group, contact.note]
      .concat((contact.methods || []).map((m) => m.value))
      .join(' ')
      .toLowerCase();
    return haystack.includes(keyword);
  }

  function visibleContacts() {
    const keyword = state.keyword.trim().toLowerCase();
    let list = state.contacts;

    if (state.filter.type === 'fav') {
      list = list.filter((c) => c.favorite);
    } else if (state.filter.type === 'group') {
      list = list.filter((c) => groupOf(c) === state.filter.group);
    }

    list = list.filter((c) => matchesKeyword(c, keyword));

    return list.slice().sort((a, b) => {
      if (!!a.favorite !== !!b.favorite) return a.favorite ? -1 : 1;
      return String(a.name || '').localeCompare(String(b.name || ''), 'zh-Hans-CN');
    });
  }

  function selectedContact() {
    return state.contacts.find((c) => String(c.id) === String(state.selectedId)) || null;
  }

  /* ---------------- 渲染 ---------------- */

  function renderConn() {
    el.connBadge.dataset.state = state.backendOnline ? 'online' : 'offline';
    el.connBadge.textContent = state.backendOnline ? '已连接后端' : '后端未连接';
    el.connBadge.title = state.backendOnline
      ? '数据来自 ' + (API.baseUrl || '同源后端')
      : '请先启动后端服务，然后点击这里重试';
  }

  function renderNav() {
    const favCount = state.contacts.filter((c) => c.favorite).length;
    const items = [
      { key: 'all', label: '全部联系人', count: state.contacts.length, active: state.filter.type === 'all' },
      { key: 'fav', label: '收藏', count: favCount, active: state.filter.type === 'fav' }
    ];

    el.filterNav.innerHTML = items.map((item) =>
      '<button type="button" class="nav-item' + (item.active ? ' is-active' : '') + '" data-nav="' + item.key + '">'
      + '<span class="nav-label">' + escapeHtml(item.label) + '</span>'
      + '<span class="nav-count">' + item.count + '</span>'
      + '</button>'
    ).join('');

    const groups = groupStats();
    el.groupTitle.hidden = groups.length === 0;
    el.groupNav.innerHTML = groups.map(([name, count]) => {
      const active = state.filter.type === 'group' && state.filter.group === name;
      return '<button type="button" class="nav-item' + (active ? ' is-active' : '') + '" data-nav="group" data-group="'
        + escapeHtml(name) + '">'
        + '<span class="nav-label">' + escapeHtml(name) + '</span>'
        + '<span class="nav-count">' + count + '</span>'
        + '</button>';
    }).join('');
  }

  function renderStats() {
    el.statTotal.textContent = state.contacts.length;
    el.statFav.textContent = state.contacts.filter((c) => c.favorite).length;
  }

  function renderListHeading(list) {
    let title = '全部联系人';
    if (state.filter.type === 'fav') title = '收藏的联系人';
    if (state.filter.type === 'group') title = state.filter.group;
    if (state.keyword.trim()) title = '搜索：' + state.keyword.trim();
    el.listTitle.textContent = title;
    el.listCount.textContent = list.length;
  }

  function skeletonRow() {
    return '<li class="contact-row skeleton">'
      + '<span class="avatar skeleton-block"></span>'
      + '<span class="row-main"><span class="skeleton-line w60"></span><span class="skeleton-line w40"></span></span>'
      + '</li>';
  }

  function methodPreview(contact) {
    const methods = contact.methods || [];
    if (!methods.length) return '暂无联系方式';
    return methods.slice(0, 2).map((m) => m.value).join(' · ') + (methods.length > 2 ? ' …' : '');
  }

  function renderList() {
    const list = visibleContacts();
    renderListHeading(list);

    if (state.loading) {
      el.list.innerHTML = Array.from({ length: 4 }, skeletonRow).join('');
      el.listEmpty.hidden = true;
      return;
    }

    if (state.error) {
      el.list.innerHTML = '';
      el.listEmpty.hidden = false;
      el.emptyText.textContent = state.error;
      el.btnEmptyNew.hidden = true;
      return;
    }

    el.listEmpty.hidden = list.length !== 0;
    el.btnEmptyNew.hidden = false;
    el.btnEmptyNew.textContent = state.contacts.length === 0 ? '新建第一位联系人' : '新建联系人';
    el.emptyText.textContent = state.contacts.length === 0
      ? '还没有联系人，先新建一位吧'
      : '没有符合条件的联系人，换个关键字试试';

    el.list.innerHTML = list.map((contact) => {
      const active = String(contact.id) === String(state.selectedId);
      const group = groupOf(contact);
      return '<li class="contact-row' + (active ? ' is-selected' : '') + '" data-id="' + escapeHtml(contact.id) + '" tabindex="0">'
        + '<span class="avatar" style="--hue:' + hueOf(contact.name) + '">' + escapeHtml(initials(contact.name)) + '</span>'
        + '<span class="row-main">'
        + '<span class="row-name">' + escapeHtml(contact.name || '未命名')
        + (contact.favorite ? '<span class="chip chip-gold">收藏</span>' : '')
        + '</span>'
        + '<span class="row-sub">' + escapeHtml(group) + ' · ' + escapeHtml(methodPreview(contact)) + '</span>'
        + '</span>'
        + '<button type="button" class="star' + (contact.favorite ? ' is-on' : '') + '" data-action="fav" data-id="'
        + escapeHtml(contact.id) + '" title="' + (contact.favorite ? '取消收藏' : '收藏') + '" aria-label="'
        + (contact.favorite ? '取消收藏' : '收藏') + '">' + (contact.favorite ? '★' : '☆') + '</button>'
        + '</li>';
    }).join('');
  }

  function renderDetail() {
    const contact = selectedContact();
    if (!contact) {
      el.detail.hidden = true;
      el.detailEmpty.hidden = false;
      return;
    }

    el.detailEmpty.hidden = true;
    el.detail.hidden = false;

    const methods = contact.methods || [];
    const methodItems = methods.length
      ? methods.map((method) => {
        const type = String(method.type || 'OTHER').toUpperCase();
        return '<li class="method-item">'
          + '<span class="method-icon">' + (ICON[type] || ICON.OTHER) + '</span>'
          + '<span class="method-type">' + escapeHtml(TYPE_LABEL[type] || TYPE_LABEL.OTHER) + '</span>'
          + '<span class="method-value">' + escapeHtml(method.value) + '</span>'
          + '</li>';
      }).join('')
      : '<li class="method-empty">还没有联系方式</li>';

    el.detail.innerHTML =
      '<header class="detail-head">'
      + '<span class="avatar avatar-lg" style="--hue:' + hueOf(contact.name) + '">' + escapeHtml(initials(contact.name)) + '</span>'
      + '<div class="detail-title">'
      + '<h2>' + escapeHtml(contact.name || '未命名') + '</h2>'
      + '<div class="chips">'
      + '<span class="chip">' + escapeHtml(groupOf(contact)) + '</span>'
      + (contact.favorite ? '<span class="chip chip-gold">已收藏</span>' : '')
      + '</div>'
      + '<p class="muted">更新于 ' + escapeHtml(formatTime(contact.updatedAt)) + '</p>'
      + '</div>'
      + '</header>'

      + '<div class="detail-actions">'
      + '<button type="button" class="btn" data-act="fav">' + (contact.favorite ? '★ 取消收藏' : '☆ 收藏') + '</button>'
      + '<button type="button" class="btn" data-act="edit">' + ICON.EDIT + '编辑</button>'
      + '<button type="button" class="btn danger" data-act="delete">' + ICON.TRASH + '删除</button>'
      + '</div>'

      + '<section class="detail-section">'
      + '<h3>联系方式 <span class="pill">' + methods.length + '</span></h3>'
      + '<ul class="method-list">' + methodItems + '</ul>'
      + '</section>'

      + (contact.note
        ? '<section class="detail-section"><h3>备注</h3><p class="note-box">' + escapeHtml(contact.note) + '</p></section>'
        : '');

    el.detail.dataset.id = contact.id;
  }

  function renderAll() {
    renderConn();
    renderNav();
    renderStats();
    renderList();
    renderDetail();
    renderGroupOptions();
  }

  function renderGroupOptions() {
    el.groupOptions.innerHTML = groupStats()
      .map(([name]) => '<option value="' + escapeHtml(name) + '"></option>')
      .join('');
  }

  /* ---------------- 编辑器 ---------------- */

  function openModal(node) {
    node.hidden = false;
    document.body.classList.add('is-modal-open');
    const focusable = node.querySelector('input, select, textarea, button');
    if (focusable) setTimeout(() => focusable.focus(), 30);
  }

  function closeModal(node) {
    node.hidden = true;
    if (!document.querySelector('.modal-backdrop:not([hidden])')) {
      document.body.classList.remove('is-modal-open');
    }
  }

  function methodRow(type, value) {
    const options = TYPE_ORDER.map((key) =>
      '<option value="' + key + '"' + (key === type ? ' selected' : '') + '>' + TYPE_LABEL[key] + '</option>'
    ).join('');
    const row = document.createElement('div');
    row.className = 'method-row';
    row.innerHTML = '<select class="method-type" aria-label="联系方式类型">' + options + '</select>'
      + '<input class="method-value" maxlength="500" placeholder="电话号码 / 邮箱 / 微信号…" aria-label="联系方式内容" autocomplete="off">'
      + '<button type="button" class="icon-btn remove" aria-label="删除这一行">✕</button>';
    row.querySelector('.method-value').value = value || '';
    return row;
  }

  function addMethodRow(type, value) {
    const row = methodRow(type || 'PHONE', value);
    el.methodRows.appendChild(row);
    return row;
  }

  function openEditor(contact) {
    state.editingId = contact ? contact.id : null;
    el.editorTitle.textContent = contact ? '编辑联系人' : '新建联系人';
    el.formError.hidden = true;
    el.methodRows.innerHTML = '';

    const form = el.editorForm;
    form.elements.name.value = contact ? contact.name || '' : '';
    form.elements.group.value = contact ? contact.group || '' : '';
    form.elements.note.value = contact ? contact.note || '' : '';

    const methods = (contact && contact.methods && contact.methods.length)
      ? contact.methods
      : [{ type: 'PHONE', value: '' }];
    methods.forEach((method) => addMethodRow(String(method.type || 'PHONE').toUpperCase(), method.value));

    openModal(el.editorModal);
  }

  function readForm() {
    const form = el.editorForm;
    const name = form.elements.name.value.trim();
    const group = form.elements.group.value.trim();
    const note = form.elements.note.value.trim();

    const methods = Array.from(el.methodRows.querySelectorAll('.method-row'))
      .map((row) => ({
        type: row.querySelector('.method-type').value,
        value: row.querySelector('.method-value').value.trim()
      }))
      .filter((method) => method.value);

    return { name, group, note, methods };
  }

  async function saveContact(event) {
    event.preventDefault();
    const data = readForm();

    if (!data.name) {
      showFormError('请填写姓名');
      el.editorForm.elements.name.focus();
      return;
    }
    if (!data.methods.length) {
      showFormError('请至少填写一条联系方式');
      return;
    }

    const current = state.editingId ? selectedContact() : null;
    const payload = {
      name: data.name,
      group: data.group || '未分组',
      note: data.note,
      favorite: current ? !!current.favorite : false,
      methods: data.methods
    };

    const button = $('btn-save');
    button.disabled = true;
    button.classList.add('is-busy');
    try {
      const saved = state.editingId
        ? await API.update(state.editingId, payload)
        : await API.create(payload);
      closeModal(el.editorModal);
      state.selectedId = saved && saved.id != null ? saved.id : state.editingId;
      await loadContacts();
      toast(state.editingId ? '已保存修改' : '已添加「' + data.name + '」');
    } catch (err) {
      showFormError(err.message);
    } finally {
      button.disabled = false;
      button.classList.remove('is-busy');
    }
  }

  function showFormError(message) {
    el.formError.textContent = message;
    el.formError.hidden = false;
  }

  /* ---------------- 操作 ---------------- */

  async function toggleFavorite(contact) {
    if (!contact) return;
    const next = !contact.favorite;
    contact.favorite = next;
    renderList();
    renderDetail();
    try {
      const saved = await API.setFavorite(contact.id, next);
      Object.assign(contact, saved || {});
      renderAll();
      toast(next ? '已收藏「' + contact.name + '」' : '已取消收藏「' + contact.name + '」');
    } catch (err) {
      contact.favorite = !next;
      renderList();
      renderDetail();
      toast(err.message, 'error');
    }
  }

  function confirmDialog(message) {
    el.confirmText.textContent = message;
    openModal(el.confirmModal);
    return new Promise((resolve) => {
      function done(result) {
        el.confirmOk.removeEventListener('click', onOk);
        el.confirmModal.querySelectorAll('[data-close]').forEach((btn) => btn.removeEventListener('click', onCancel));
        closeModal(el.confirmModal);
        resolve(result);
      }
      function onOk() { done(true); }
      function onCancel() { done(false); }
      el.confirmOk.addEventListener('click', onOk);
      el.confirmModal.querySelectorAll('[data-close]').forEach((btn) => btn.addEventListener('click', onCancel));
    });
  }

  async function deleteContact(contact) {
    if (!contact) return;
    const ok = await confirmDialog('确定删除「' + (contact.name || '未命名') + '」吗？删除后无法恢复。');
    if (!ok) return;
    try {
      await API.remove(contact.id);
      if (String(state.selectedId) === String(contact.id)) state.selectedId = null;
      await loadContacts();
      toast('已删除「' + contact.name + '」', 'info');
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  async function exportExcel() {
    const button = el.btnExport;
    button.disabled = true;
    button.classList.add('is-busy');
    try {
      const file = await API.exportAll();
      API.saveBlob(file.blob, file.filename);
      toast('已导出 ' + file.filename);
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      button.disabled = false;
      button.classList.remove('is-busy');
    }
  }

  async function downloadTemplate() {
    try {
      const file = await API.template();
      API.saveBlob(file.blob, file.filename);
      toast('模板已下载');
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  async function importFile(file) {
    if (!file) return;
    if (!/\.(xlsx|xls)$/i.test(file.name)) {
      toast('请选择 .xlsx 或 .xls 文件', 'error');
      return;
    }
    el.btnImport.disabled = true;
    el.btnImport.classList.add('is-busy');
    try {
      const result = await API.importFile(file);
      showImportResult(result);
      await loadContacts();
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      el.btnImport.disabled = false;
      el.btnImport.classList.remove('is-busy');
      el.fileInput.value = '';
    }
  }

  function showImportResult(result) {
    const data = result || {};
    el.resInserted.textContent = Number(data.inserted) || 0;
    el.resUpdated.textContent = Number(data.updated) || 0;
    el.resFailed.textContent = Number(data.failed) || 0;
    const errors = Array.isArray(data.errors) ? data.errors : [];
    el.resErrors.innerHTML = errors.slice(0, 20)
      .map((item) => '<li>' + escapeHtml(item) + '</li>')
      .join('');
    el.resErrors.hidden = errors.length === 0;
    openModal(el.resultModal);
  }

  /* ---------------- 事件绑定 ---------------- */

  function selectContact(id) {
    state.selectedId = id;
    renderList();
    renderDetail();
  }

  function handleNavClick(event) {
    const button = event.target.closest('[data-nav]');
    if (!button) return;
    const nav = button.dataset.nav;
    if (nav === 'all') state.filter = { type: 'all', group: null };
    else if (nav === 'fav') state.filter = { type: 'fav', group: null };
    else state.filter = { type: 'group', group: button.dataset.group };
    renderNav();
    renderList();
  }

  function handleListClick(event) {
    const star = event.target.closest('[data-action="fav"]');
    if (star) {
      event.stopPropagation();
      const contact = state.contacts.find((c) => String(c.id) === String(star.dataset.id));
      toggleFavorite(contact);
      return;
    }
    const row = event.target.closest('.contact-row');
    if (row && row.dataset.id) selectContact(row.dataset.id);
  }

  function handleDetailClick(event) {
    const action = event.target.closest('[data-act]');
    if (!action) return;
    const contact = selectedContact();
    if (!contact) return;
    switch (action.dataset.act) {
      case 'fav':
        toggleFavorite(contact);
        break;
      case 'edit':
        openEditor(contact);
        break;
      case 'delete':
        deleteContact(contact);
        break;
      default:
        break;
    }
  }

  function bindEvents() {
    el.search.addEventListener('input', debounce(() => {
      state.keyword = el.search.value;
      renderList();
    }, 120));

    el.filterNav.addEventListener('click', handleNavClick);
    el.groupNav.addEventListener('click', handleNavClick);
    el.list.addEventListener('click', handleListClick);
    el.list.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      const row = event.target.closest('.contact-row');
      if (row && row.dataset.id) {
        event.preventDefault();
        selectContact(row.dataset.id);
      }
    });

    el.detail.addEventListener('click', handleDetailClick);

    el.btnNew.addEventListener('click', () => openEditor(null));
    el.btnEmptyNew.addEventListener('click', () => openEditor(null));
    el.btnExport.addEventListener('click', exportExcel);
    el.btnTemplate.addEventListener('click', downloadTemplate);
    el.btnImport.addEventListener('click', () => el.fileInput.click());
    el.fileInput.addEventListener('change', () => importFile(el.fileInput.files[0]));

    el.connBadge.addEventListener('click', async () => {
      await checkBackend();
      if (state.backendOnline) {
        toast('后端连接正常');
        loadContacts();
      } else {
        toast('仍然连不上后端，请确认服务已启动', 'error');
      }
    });

    el.editorForm.addEventListener('submit', saveContact);
    $('btn-add-method').addEventListener('click', () => {
      const row = addMethodRow('PHONE', '');
      row.querySelector('.method-value').focus();
    });
    el.methodRows.addEventListener('click', (event) => {
      const remove = event.target.closest('.remove');
      if (!remove) return;
      const rows = el.methodRows.querySelectorAll('.method-row');
      if (rows.length <= 1) {
        rows[0].querySelector('.method-value').value = '';
        return;
      }
      remove.closest('.method-row').remove();
    });

    document.addEventListener('click', (event) => {
      const closer = event.target.closest('[data-close]');
      if (!closer) return;
      const modal = closer.closest('.modal-backdrop');
      if (modal) closeModal(modal);
    });

    document.querySelectorAll('.modal-backdrop').forEach((backdrop) => {
      backdrop.addEventListener('mousedown', (event) => {
        if (event.target === backdrop) closeModal(backdrop);
      });
    });

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        const open = Array.from(document.querySelectorAll('.modal-backdrop:not([hidden])')).pop();
        if (open) closeModal(open);
        return;
      }
      if ((event.ctrlKey || event.metaKey) && String(event.key).toLowerCase() === 'k') {
        event.preventDefault();
        el.search.focus();
        el.search.select();
      }
    });
  }

  /* ---------------- 启动 ---------------- */

  async function boot() {
    cacheElements();
    bindEvents();
    renderAll();
    await checkBackend();
    if (!state.backendOnline) {
      toast('连不上后端：先运行 mvn spring-boot:run，再刷新页面', 'error');
    }
    await loadContacts();
  }

  document.addEventListener('DOMContentLoaded', boot);
})();
