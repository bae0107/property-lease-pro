// 房屋管理 - 区域：列表（名称模糊）/ 创建 / 改名

import { post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, areaName: '' };

function createDialog(onDone) {
  const areaName = el('input', { type: 'text', placeholder: '如：华东区' });
  modal('创建区域', el('div', {}, [
    formRow('区域名称', areaName),
  ]), {
    onOk: async () => {
      if (!areaName.value.trim()) { toast('区域名称必填', 'error'); return false; }
      await post('/propertymgr/areas', { areaName: areaName.value.trim() });
      toast('创建成功', 'success');
      onDone();
    },
  });
}

function renameDialog(area, onDone) {
  const areaName = el('input', { type: 'text', value: area.areaName });
  modal(`区域改名 - #${area.areaId}`, el('div', {}, [
    formRow('区域名称', areaName),
  ]), {
    onOk: async () => {
      if (!areaName.value.trim()) { toast('区域名称必填', 'error'); return false; }
      await put(`/propertymgr/areas/${area.areaId}`, { areaName: areaName.value.trim() });
      toast('已保存', 'success');
      onDone();
    },
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const nameInput = el('input', { type: 'text', placeholder: '区域名称模糊', value: state.areaName });
  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '区域管理'),
    nameInput,
    el('button', { onclick: () => { state.areaName = nameInput.value.trim(); state.pageNo = 1; renderPage(container); } }, '查询'),
  ]);
  if (hasPerm('propertymgr:area:write')) {
    toolbar.appendChild(el('button', {
      class: 'primary', onclick: () => createDialog(() => renderPage(container)),
    }, '创建区域'));
  }
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  const body = { pageNo: state.pageNo, pageSize: state.pageSize };
  if (state.areaName) body.areaName = state.areaName;
  post('/propertymgr/areas/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    const columns = [
      { title: '区域 ID', render: a => a.areaId },
      { title: '区域名称', render: a => a.areaName },
    ];
    if (hasPerm('propertymgr:area:write')) {
      columns.push({ title: '操作', width: '80px', render: a => el('span', { class: 'ops' }, [
        el('a', { onclick: () => renameDialog(a, () => renderPage(container)) }, '改名'),
      ]) });
    }

    clear(tableBox).appendChild(table(columns, rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderPropertyAreas(container) {
  state.pageNo = 1;
  renderPage(container);
}
