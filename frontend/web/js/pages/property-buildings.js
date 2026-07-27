// 房屋管理 - 楼栋：列表（名称模糊）/ 创建

import { post } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, buildingName: '' };

async function loadStores() {
  const r = await post('/propertymgr/stores/query', { pageNo: 1, pageSize: 200 });
  return r.items || [];
}

function createDialog(onDone) {
  loadStores().then(stores => {
    if (stores.length === 0) { toast('请先创建门店', 'error'); return; }
    const buildingId = el('input', { type: 'text', placeholder: '业务编号，如 B101' });
    const store = el('select', {},
      stores.map(s => el('option', { value: String(s.storeId) }, `${s.storeId} ${s.storeName}`)));
    const buildingName = el('input', { type: 'text' });
    modal('创建楼栋', el('div', {}, [
      formRow('楼栋编号', buildingId),
      formRow('所属门店', store),
      formRow('楼栋名称', buildingName),
    ]), {
      onOk: async () => {
        if (!buildingId.value.trim() || !store.value) {
          toast('楼栋编号和门店必填', 'error'); return false;
        }
        await post('/propertymgr/buildings', {
          buildingId: buildingId.value.trim(),
          storeId: Number(store.value),
          buildingName: buildingName.value.trim() || null,
        });
        toast('创建成功', 'success');
        onDone();
      },
    });
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
  Promise.all([post('/propertymgr/buildings/query', body), loadStores()]).then(([r, stores]) => {
    state.total = r.total || 0;
    const rows = r.items || [];
    const storeNameOf = id => {
      const s = stores.find(x => String(x.storeId) === String(id));
      return s ? `${s.storeName}（#${id}）` : `#${id}`;
    };

    clear(tableBox).appendChild(table([
      { title: '楼栋编号', render: b => b.buildingId },
      { title: '所属门店', render: b => storeNameOf(b.storeId) },
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
