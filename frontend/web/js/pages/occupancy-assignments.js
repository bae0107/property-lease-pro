// 入住管理 - 分配：查询 / 创建（合同 → 合同房间 + 企业员工 两级联动）/ 取消

import { get, post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag, fmtTime } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, contractId: '', status: '' };

function assignDialog(onDone) {
  const contractInput = el('input', { type: 'number', placeholder: '合同 ID' });
  const roomSel = el('select', { disabled: '' }, el('option', { value: '' }, '（先加载合同）'));
  const tenantSel = el('select', { disabled: '' }, el('option', { value: '' }, '（先加载合同）'));
  const hint = el('span', { class: 'hint' }, '');

  function loadContract() {
    const cid = contractInput.value;
    if (!cid) { toast('请输入合同 ID', 'error'); return; }
    get(`/contract/contracts/${cid}`).then(detail => {
      const rooms = (detail.rooms || []).filter(r => r.status === 'ACTIVE');
      clear(roomSel).appendChild(el('option', { value: '' }, `（选合同房间，共 ${rooms.length} 间在租）`));
      rooms.forEach(r => roomSel.appendChild(
        el('option', { value: String(r.id) }, `#${r.id}（房间 ${r.roomId}，租金 ${r.signedRent}）`)));
      roomSel.removeAttribute('disabled');
      hint.textContent = `合同 ${detail.contractNo}，企业 #${detail.enterpriseId}，状态 ${detail.status}`;
      return post('/customer/employees/query', { pageNo: 1, pageSize: 500, enterpriseId: detail.enterpriseId });
    }).then(r => {
      if (!r) return;
      const employees = r.items || [];
      clear(tenantSel).appendChild(el('option', { value: '' }, `（选员工，共 ${employees.length} 人）`));
      employees.forEach(e => tenantSel.appendChild(
        el('option', { value: String(e.id) }, `${e.name}（#${e.id}）`)));
      tenantSel.removeAttribute('disabled');
    });
  }

  modal('创建入住分配', el('div', {}, [
    formRow('合同 ID', el('span', {}, [contractInput, ' ',
      el('button', { onclick: loadContract }, '加载合同')])),
    formRow('', hint),
    formRow('合同房间', roomSel),
    formRow('员工', tenantSel),
  ]), {
    onOk: async () => {
      if (!roomSel.value || !tenantSel.value) { toast('请选择合同房间和员工', 'error'); return false; }
      await post('/occupancy/assignments', {
        contractRoomId: Number(roomSel.value),
        tenantId: Number(tenantSel.value),
      });
      toast('分配成功', 'success');
      onDone();
    },
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const contractInput = el('input', { type: 'number', placeholder: '合同 ID', value: state.contractId });
  const statusSel = el('select', {}, [
    el('option', { value: '' }, '（全部状态）'),
    el('option', { value: 'ASSIGNED' }, '已分配'),
    el('option', { value: 'CONSUMED' }, '已消费'),
    el('option', { value: 'CANCELLED' }, '已取消'),
  ]);
  statusSel.value = state.status;

  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '入住分配'),
    contractInput, statusSel,
    el('button', {
      onclick: () => {
        state.contractId = contractInput.value.trim();
        state.status = statusSel.value;
        state.pageNo = 1;
        renderPage(container);
      },
    }, '查询'),
  ]);
  if (hasPerm('occupancy:assignment:write')) {
    toolbar.appendChild(el('button', {
      class: 'primary', onclick: () => assignDialog(() => renderPage(container)),
    }, '创建分配'));
  }
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  const body = { pageNo: state.pageNo, pageSize: state.pageSize };
  if (state.contractId) body.contractId = Number(state.contractId);
  if (state.status) body.status = state.status;
  post('/occupancy/assignments/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    const columns = [
      { title: 'ID', render: a => a.id },
      { title: '合同', render: a => a.contractId },
      { title: '合同房间', render: a => a.contractRoomId },
      { title: '房间', render: a => a.roomId },
      { title: '员工', render: a => a.tenantId },
      { title: '状态', render: a => statusTag(a.status) },
      { title: '分配时间', render: a => fmtTime(a.assignedAt) },
    ];
    if (hasPerm('occupancy:assignment:write')) {
      columns.push({ title: '操作', width: '80px', render: a =>
        a.status !== 'ASSIGNED' ? '-' : el('span', { class: 'ops' }, [
          el('a', {
            onclick: () => {
              modal('确认取消', el('div', {}, `确定取消分配 #${a.id} 吗？`), {
                okText: '取消分配',
                onOk: async () => {
                  await put(`/occupancy/assignments/${a.id}/cancel`);
                  toast('已取消', 'success');
                  renderPage(container);
                },
              });
            },
          }, '取消'),
        ]) });
    }

    clear(tableBox).appendChild(table(columns, rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderOccupancyAssignments(container) {
  state.pageNo = 1;
  renderPage(container);
}
