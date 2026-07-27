// 房屋管理 - 门店：列表（区域/名称过滤）/ 创建（选区域）/ 编辑（名称+所属区域）

import { post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, areaId: '', storeName: '' };

async function loadAreas() {
  const r = await post('/propertymgr/areas/query', { pageNo: 1, pageSize: 200 });
  return r.items || [];
}

function areaSelect(areas, current = '', allLabel = null) {
  const opts = allLabel ? [el('option', { value: '' }, allLabel)] : [];
  const sel = el('select', {}, opts.concat(
    areas.map(a => el('option', { value: String(a.areaId) }, `${a.areaId} ${a.areaName}`))));
  sel.value = String(current || '');
  return sel;
}

function editDialog(title, store, onDone) {
  loadAreas().then(areas => {
    if (areas.length === 0) { toast('请先创建区域', 'error'); return; }
    const area = areaSelect(areas, store ? store.areaId : areas[0].areaId);
    const storeName = el('input', { type: 'text', value: store ? store.storeName : '' });
    modal(title, el('div', {}, [
      formRow('所属区域', area),
      formRow('门店名称', storeName),
    ]), {
      onOk: async () => {
        if (!area.value || !storeName.value.trim()) { toast('区域和门店名称必填', 'error'); return false; }
        const body = { areaId: Number(area.value), storeName: storeName.value.trim() };
        if (store) await put(`/propertymgr/stores/${store.storeId}`, body);
        else await post('/propertymgr/stores', body);
        toast('已保存', 'success');
        onDone();
      },
    });
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const nameInput = el('input', { type: 'text', placeholder: '门店名称模糊', value: state.storeName });
  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '门店管理'),
  ]);
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  loadAreas().then(areas => {
    const areaFilter = areaSelect(areas, state.areaId, '（全部区域）');
    toolbar.appendChild(areaFilter);
    toolbar.appendChild(nameInput);
    toolbar.appendChild(el('button', {
      onclick: () => {
        state.areaId = areaFilter.value;
        state.storeName = nameInput.value.trim();
        state.pageNo = 1;
        renderPage(container);
      },
    }, '查询'));
    if (hasPerm('propertymgr:store:write')) {
      toolbar.appendChild(el('button', {
        class: 'primary', onclick: () => editDialog('创建门店', null, () => renderPage(container)),
      }, '创建门店'));
    }

    const areaNameOf = id => {
      const a = areas.find(x => String(x.areaId) === String(id));
      return a ? a.areaName : `#${id}`;
    };

    const body = { pageNo: state.pageNo, pageSize: state.pageSize };
    if (state.areaId) body.areaId = Number(state.areaId);
    if (state.storeName) body.storeName = state.storeName;
    post('/propertymgr/stores/query', body).then(r => {
      state.total = r.total || 0;
      const rows = r.items || [];

      const columns = [
        { title: '门店 ID', render: s => s.storeId },
        { title: '门店名称', render: s => s.storeName },
        { title: '所属区域', render: s => areaNameOf(s.areaId) },
      ];
      if (hasPerm('propertymgr:store:write')) {
        columns.push({ title: '操作', width: '80px', render: s => el('span', { class: 'ops' }, [
          el('a', { onclick: () => editDialog('编辑门店', s, () => renderPage(container)) }, '编辑'),
        ]) });
      }

      clear(tableBox).appendChild(table(columns, rows));
      clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
        state.pageNo = p;
        renderPage(container);
      }));
    });
  });
}

export function renderPropertyStores(container) {
  state.pageNo = 1;
  renderPage(container);
}
