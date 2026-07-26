// 账务 - 押金台账：查询 / 详情

import { get, post } from '../common/api.js';
import { el, clear, table, pager, modal, formRow, statusTag } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, depositType: '', ownerId: '', contractId: '', status: '' };

function detailDialog(id) {
  get(`/accounting/deposit-ledgers/${id}`).then(d => {
    modal(`押金台账 #${d.id}`, el('div', {}, [
      formRow('类型', el('span', {}, d.depositType)),
      formRow('归属', el('span', {}, `${d.ownerType} #${d.ownerId}`)),
      formRow('合同', el('span', {}, String(d.contractId))),
      formRow('当前 Stay', el('span', {}, String(d.currentStayId ?? '-'))),
      formRow('原金额', el('span', {}, String(d.originalAmount))),
      formRow('已占用', el('span', {}, String(d.occupiedAmount))),
      formRow('可退金额', el('span', {}, String(d.refundableAmount ?? '-'))),
      formRow('状态', statusTag(d.status)),
      formRow('关联账单', el('span', {}, String(d.relatedBillId ?? '-'))),
    ]));
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const typeSel = el('select', {}, [
    el('option', { value: '' }, '（全部类型）'),
    el('option', { value: 'ENTERPRISE' }, '企业押金'),
    el('option', { value: 'PERSONAL' }, '个人押金'),
  ]);
  typeSel.value = state.depositType;
  const statusSel = el('select', {}, [el('option', { value: '' }, '（全部状态）')].concat(
    ['PENDING_PAYMENT', 'ACTIVE', 'REFUND_PENDING', 'CLOSED'].map(s => el('option', { value: s }, s))));
  statusSel.value = state.status;
  const ownerInput = el('input', { type: 'number', placeholder: '归属 ID', value: state.ownerId });
  const contractInput = el('input', { type: 'number', placeholder: '合同 ID', value: state.contractId });

  card.appendChild(el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '押金台账'),
    typeSel, statusSel, ownerInput, contractInput,
    el('button', {
      onclick: () => {
        state.depositType = typeSel.value;
        state.status = statusSel.value;
        state.ownerId = ownerInput.value.trim();
        state.contractId = contractInput.value.trim();
        state.pageNo = 1;
        renderPage(container);
      },
    }, '查询'),
  ]));

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  const body = { pageNo: state.pageNo, pageSize: state.pageSize };
  if (state.depositType) body.depositType = state.depositType;
  if (state.status) body.status = state.status;
  if (state.ownerId) body.ownerId = Number(state.ownerId);
  if (state.contractId) body.contractId = Number(state.contractId);
  post('/accounting/deposit-ledgers/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    clear(tableBox).appendChild(table([
      { title: 'ID', render: d => d.id },
      { title: '类型', render: d => d.depositType },
      { title: '归属', render: d => `${d.ownerType}#${d.ownerId}` },
      { title: '合同', render: d => d.contractId },
      { title: '原金额', render: d => d.originalAmount },
      { title: '已占用', render: d => d.occupiedAmount },
      { title: '可退', render: d => d.refundableAmount ?? '-' },
      { title: '状态', render: d => statusTag(d.status) },
      { title: '操作', width: '70px', render: d => el('span', { class: 'ops' }, [
        el('a', { onclick: () => detailDialog(d.id) }, '详情'),
      ]) },
    ], rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderAccountingDeposits(container) {
  state.pageNo = 1;
  renderPage(container);
}
