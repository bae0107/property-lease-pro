// 入住管理 - 在住：查询 / 办理入住（assignmentId）/ 退宿 / 换宿

import { post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag, fmtTime } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, roomId: '', tenantId: '', stayStatus: '' };

function checkInDialog(onDone) {
  const assignmentId = el('input', { type: 'number', placeholder: '分配 ID（入住分配页 ASSIGNED 行）' });
  modal('办理入住', el('div', {}, [
    formRow('分配 ID', assignmentId, '入住分配页状态为「已分配」的记录 ID'),
  ]), {
    onOk: async () => {
      if (!assignmentId.value) { toast('请输入分配 ID', 'error'); return false; }
      const s = await post('/occupancy/stays/check-in', { assignmentId: Number(assignmentId.value) });
      toast(`入住成功（Stay #${s.id}），个人押金账单已生成`, 'success');
      onDone();
    },
  });
}

function transferDialog(stay, onDone) {
  const toRoom = el('input', { type: 'number', placeholder: '目标合同房间 ID' });
  modal(`换宿 - Stay #${stay.id}（员工 ${stay.tenantId}）`, el('div', {}, [
    formRow('目标合同房间', toRoom, '目标合同须为可入住状态；押金资格与子余额随迁移'),
  ]), {
    onOk: async () => {
      if (!toRoom.value) { toast('请输入目标合同房间 ID', 'error'); return false; }
      const r = await post(`/occupancy/stays/${stay.id}/transfer`, { toContractRoomId: Number(toRoom.value) });
      toast(`换宿成功（新 Stay #${r.toStay.id}）`, 'success');
      onDone();
    },
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const roomInput = el('input', { type: 'number', placeholder: '房间 ID', value: state.roomId });
  const tenantInput = el('input', { type: 'number', placeholder: '员工 ID', value: state.tenantId });
  const statusSel = el('select', {}, [
    el('option', { value: '' }, '（全部状态）'),
    el('option', { value: 'CHECKED_IN' }, '在住'),
    el('option', { value: 'TRANSFERRED' }, '已换宿'),
    el('option', { value: 'CHECKED_OUT' }, '已退宿'),
  ]);
  statusSel.value = state.stayStatus;

  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '在住管理'),
    roomInput, tenantInput, statusSel,
    el('button', {
      onclick: () => {
        state.roomId = roomInput.value.trim();
        state.tenantId = tenantInput.value.trim();
        state.stayStatus = statusSel.value;
        state.pageNo = 1;
        renderPage(container);
      },
    }, '查询'),
  ]);
  if (hasPerm('occupancy:stay:checkin')) {
    toolbar.appendChild(el('button', {
      class: 'primary', onclick: () => checkInDialog(() => renderPage(container)),
    }, '办理入住'));
  }
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  const body = { pageNo: state.pageNo, pageSize: state.pageSize };
  if (state.roomId) body.roomId = Number(state.roomId);
  if (state.tenantId) body.tenantId = Number(state.tenantId);
  if (state.stayStatus) body.stayStatus = state.stayStatus;
  post('/occupancy/stays/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    const columns = [
      { title: 'ID', render: s => s.id },
      { title: '合同', render: s => s.contractId },
      { title: '合同房间', render: s => s.contractRoomId },
      { title: '房间', render: s => s.roomId },
      { title: '员工', render: s => s.tenantId },
      { title: '状态', render: s => statusTag(s.stayStatus) },
      { title: '入住时间', render: s => fmtTime(s.checkInAt) },
      { title: '退宿时间', render: s => fmtTime(s.checkOutAt) },
    ];
    const ops = { title: '操作', width: '130px', render: s => {
      if (s.stayStatus !== 'CHECKED_IN') return '-';
      const links = [];
      if (hasPerm('occupancy:stay:checkout')) {
        links.push(el('a', {
          onclick: () => {
            modal('确认退宿', el('div', {}, `确定办理退宿吗？（Stay #${s.id}，将发起个人押金结算）`), {
              okText: '退宿',
              onOk: async () => {
                await put(`/occupancy/stays/${s.id}/check-out`);
                toast('已退宿，押金结算异步处理中', 'success');
                renderPage(container);
              },
            });
          },
        }, '退宿'));
      }
      if (hasPerm('occupancy:stay:transfer')) {
        links.push(el('a', { onclick: () => transferDialog(s, () => renderPage(container)) }, '换宿'));
      }
      return links.length ? el('span', { class: 'ops' }, links) : '-';
    } };
    if (hasPerm('occupancy:stay:checkout') || hasPerm('occupancy:stay:transfer')) columns.push(ops);

    clear(tableBox).appendChild(table(columns, rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderOccupancyStays(container) {
  state.pageNo = 1;
  renderPage(container);
}
