// 房屋管理 - 房间：列表（楼栋/状态过滤）/ 创建（选楼栋）

import { post } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, buildingId: '', roomStatus: '' };

async function loadBuildings() {
  const r = await post('/propertymgr/buildings/query', { pageNo: 1, pageSize: 500 });
  return r.items || [];
}

function buildingSelect(buildings, current = '') {
  const sel = el('select', {},
    [el('option', { value: '' }, '（全部楼栋）')].concat(
      buildings.map(b => el('option', { value: b.buildingId },
        `${b.buildingId} ${b.buildingName || ''}`))));
  sel.value = current;
  return sel;
}

function createDialog(onDone) {
  loadBuildings().then(buildings => {
    const building = buildingSelect(buildings);
    const level = el('input', { type: 'text', placeholder: '如 3' });
    const roomNum = el('input', { type: 'text', placeholder: '如 301' });
    const livingNum = el('input', { type: 'number', placeholder: '可住人数，默认 1' });
    modal('创建房间（初始状态：空房）', el('div', {}, [
      formRow('所属楼栋', building),
      formRow('楼层', level),
      formRow('房号', roomNum),
      formRow('可住人数', livingNum),
    ]), {
      onOk: async () => {
        if (!building.value) { toast('请选择楼栋', 'error'); return false; }
        await post('/propertymgr/rooms', {
          buildingId: building.value,
          level: level.value.trim() || null,
          roomNum: roomNum.value.trim() || null,
          livingNum: livingNum.value ? Number(livingNum.value) : null,
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

  const buildingInput = el('input', { type: 'text', placeholder: '楼栋编号，如 B101', value: state.buildingId });
  const statusSel = el('select', {}, [
    el('option', { value: '' }, '（全部状态）'),
    el('option', { value: 'EMPTY' }, '空房'),
    el('option', { value: 'WAIT_CHECK_IN' }, '待入住'),
    el('option', { value: 'OCCUPIED' }, '已占用'),
  ]);
  statusSel.value = state.roomStatus;

  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '房间管理'),
    buildingInput, statusSel,
    el('button', {
      onclick: () => {
        state.buildingId = buildingInput.value.trim();
        state.roomStatus = statusSel.value;
        state.pageNo = 1;
        renderPage(container);
      },
    }, '查询'),
  ]);
  if (hasPerm('propertymgr:room:write')) {
    toolbar.appendChild(el('button', {
      class: 'primary', onclick: () => createDialog(() => renderPage(container)),
    }, '创建房间'));
  }
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  const body = { pageNo: state.pageNo, pageSize: state.pageSize };
  if (state.buildingId) body.buildingId = state.buildingId;
  if (state.roomStatus) body.roomStatus = state.roomStatus;
  post('/propertymgr/rooms/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    clear(tableBox).appendChild(table([
      { title: '房间 ID', render: r2 => r2.roomId },
      { title: '楼栋', render: r2 => r2.buildingId },
      { title: '楼层', render: r2 => r2.level || '-' },
      { title: '房号', render: r2 => r2.roomNum || '-' },
      { title: '可住人数', render: r2 => r2.livingNum ?? '-' },
      { title: '状态', render: r2 => statusTag(r2.roomStatus) },
    ], rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderPropertyRooms(container) {
  state.pageNo = 1;
  renderPage(container);
}
