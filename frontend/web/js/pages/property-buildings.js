// 房屋管理 - 楼栋：列表（名称模糊）/ 创建

import { post } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, buildingName: '' };

function createDialog(onDone) {
  const buildingId = el('input', { type: 'text', placeholder: '业务编号，如 B101' });
  const storeId = el('input', { type: 'number', placeholder: '门店 ID，如 1' });
  const buildingName = el('input', { type: 'text' });
  modal('创建楼栋', el('div', {}, [
    formRow('楼栋编号', buildingId),
    formRow('门店 ID', storeId),
    formRow('楼栋名称', buildingName),
  ]), {
    onOk: async () => {
      if (!buildingId.value.trim() || !storeId.value) {
        toast('楼栋编号和门店 ID 必填', 'error'); return false;
      }
      await post('/propertymgr/buildings', {
        buildingId: buildingId.value.trim(),
        storeId: Number(storeId.value),
        buildingName: buildingName.value.trim() || null,
      });
      toast('创建成功', 'success');
      onDone();
    },
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const nameInput = el('input', { type: 'text', placeholder: '楼栋名称模糊', value: state.buildingName });
  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '楼栋管理'),
    nameInput,
    el('button', { onclick: () => { state.buildingName = nameInput.value.trim(); state.pageNo = 1; renderPage(container); } }, '查询'),
  ]);
  if (hasPerm('propertymgr:building:write')) {
    toolbar.appendChild(el('button', {
      class: 'primary', onclick: () => createDialog(() => renderPage(container)),
    }, '创建楼栋'));
  }
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  const body = { pageNo: state.pageNo, pageSize: state.pageSize };
  if (state.buildingName) body.buildingName = state.buildingName;
  post('/propertymgr/buildings/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    clear(tableBox).appendChild(table([
      { title: '楼栋编号', render: b => b.buildingId },
      { title: '门店 ID', render: b => b.storeId },
      { title: '楼栋名称', render: b => b.buildingName || '-' },
    ], rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderPropertyBuildings(container) {
  state.pageNo = 1;
  renderPage(container);
}
