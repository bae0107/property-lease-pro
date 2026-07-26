// UI 公共层：DOM helper、表格、分页、模态框、toast

/** 创建元素：el('div', {class:'x', onclick:fn}, child1, child2, '文本') */
export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null) continue;
    if (k === 'class') node.className = v;
    else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2), v);
    else if (k === 'value') node.value = v;
    else if (k === 'checked') node.checked = !!v;
    else node.setAttribute(k, v);
  }
  for (const c of children.flat()) {
    if (c == null) continue;
    node.appendChild(typeof c === 'string' || typeof c === 'number'
      ? document.createTextNode(String(c)) : c);
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
  return node;
}

/** toast 提示：type = info / success / error */
export function toast(message, type = 'info', traceId = null) {
  const root = document.getElementById('toast-root');
  const t = el('div', { class: 'toast ' + type },
    el('div', {}, message),
    traceId ? el('div', { class: 'trace' }, 'traceId: ' + traceId) : null);
  root.appendChild(t);
  setTimeout(() => t.remove(), type === 'error' ? 5000 : 2500);
}

/**
 * 表格渲染。
 * columns: [{ title, render(row) → Node|string, width? }]
 */
export function table(columns, rows) {
  const thead = el('tr', {}, columns.map(c =>
    el('th', c.width ? { style: 'width:' + c.width } : {}, c.title)));
  const body = rows.length === 0
    ? el('tr', {}, el('td', { colspan: String(columns.length), style: 'text-align:center;color:#999' }, '暂无数据'))
    : rows.map(r => el('tr', {}, columns.map(c => el('td', {}, c.render(r)))));
  return el('table', { class: 'grid' }, [el('thead', {}, thead), el('tbody', {}, body)]);
}

/** 分页条。onPage(pageNo) 触发重新加载 */
export function pager(pageNo, pageSize, total, onPage) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  return el('div', { class: 'pager' }, [
    el('span', {}, `共 ${total} 条`),
    el('button', { disabled: pageNo <= 1 ? '' : null, onclick: () => onPage(pageNo - 1) }, '上一页'),
    el('span', {}, `${pageNo} / ${pages}`),
    el('button', { disabled: pageNo >= pages ? '' : null, onclick: () => onPage(pageNo + 1) }, '下一页'),
  ]);
}

/**
 * 模态框。content 为 Node；返回 { close }。
 * onOk 返回 false 或抛错则不关闭；返回 Promise 支持异步校验。
 */
export function modal(title, content, { onOk, okText = '确定', width } = {}) {
  const m = el('div', { class: 'modal', style: width ? 'width:' + width : '' });
  const mask = el('div', { class: 'modal-mask' }, m);
  const close = () => mask.remove();
  m.appendChild(el('div', { class: 'modal-title' }, title));
  m.appendChild(el('div', { class: 'modal-body' }, content));
  const foot = el('div', { class: 'modal-foot' }, [
    el('button', { onclick: close }, '取消'),
  ]);
  if (onOk) {
    foot.appendChild(el('button', {
      class: 'primary',
      onclick: async () => {
        try {
          const r = await onOk();
          if (r !== false) close();
        } catch (e) { /* onOk 内部已提示 */ }
      },
    }, okText));
  }
  m.appendChild(foot);
  mask.addEventListener('click', e => { if (e.target === mask) close(); });
  document.body.appendChild(mask);
  return { close };
}

/** 表单行：label + 输入控件 */
export function formRow(label, control, hint = null) {
  return el('div', { class: 'form-row' },
    el('label', {}, label), control,
    hint ? el('span', { class: 'hint' }, hint) : null);
}

/** 解析 hash 中的 query：'#/contract/detail?id=33' → { id: '33' } */
export function hashQuery() {
  const h = location.hash.replace(/^#/, '');
  const i = h.indexOf('?');
  if (i < 0) return {};
  return Object.fromEntries(new URLSearchParams(h.slice(i + 1)));
}

/** date-time 显示：'2026-07-26T10:00:00+08:00' → '2026-07-26 10:00' */
export function fmtTime(s) {
  return s ? String(s).replace('T', ' ').slice(0, 16) : '-';
}

export function statusTag(status) {
  const map = {
    ACTIVE: ['green', '启用/生效'], INACTIVE: ['red', '停用'],
    DRAFT: ['blue', '草稿'], CANCELLED: ['red', '已取消/作废'],
    COMPLETED: ['green', '已完成'],
    PENDING: ['blue', '待处理'], PAID: ['green', '已支付'], REFUNDED: ['green', '已退款'],
    SIGN_BILL_PENDING: ['blue', '待支付签约账单'],
    READY_FOR_CHECK_IN: ['green', '可入住'],
    PARTIALLY_RETURNED: ['blue', '部分退房'], SETTLING: ['blue', '结算中'],
    RETURNED: ['red', '已退房'],
    EMPTY: ['blue', '空房'], WAIT_CHECK_IN: ['blue', '待入住'], OCCUPIED: ['green', '已占用'],
    ASSIGNED: ['blue', '已分配'], CONSUMED: ['green', '已消费'],
    CHECKED_IN: ['green', '在住'], TRANSFERRED: ['blue', '已换宿'], CHECKED_OUT: ['red', '已退宿'],
    SETTLED: ['green', '已结算'], PARTIAL: ['blue', '部分结算'], FAILED: ['red', '失败'],
    RUNNING: ['blue', '运行中'], SUCCESS: ['green', '成功'], PARTIAL_FAILURE: ['red', '部分失败'],
    PENDING_PAYMENT: ['blue', '待支付'], REFUND_PENDING: ['blue', '退款中'], CLOSED: ['red', '已关闭'],
    FROZEN: ['blue', '冻结'],
  };
  const [color, text] = map[status] || ['blue', status];
  return el('span', { class: 'tag ' + color }, text);
}
