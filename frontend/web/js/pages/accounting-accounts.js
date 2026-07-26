// 账务 - 房间账户：查询 / 子余额详情 / 流水 / 充值

import { get, post } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag, fmtTime } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, roomId: '', contractId: '', status: '' };

function detailDialog(id) {
  get(`/accounting/room-accounts/${id}`).then(a => {
    modal(`房间账户 #${a.id}（房间 ${a.roomId}）`, el('div', {}, [
      formRow('当前合同', el('span', {}, String(a.currentContractId ?? '-'))),
      formRow('状态', statusTag(a.status)),
      el('h2', { style: 'margin-top:8px' }, '子余额'),
      table([
        { title: 'ID', render: s => s.id },
        { title: '归属', render: s => `${s.ownerType}#${s.ownerId}` },
        { title: '可用余额', render: s => s.availableBalance },
        { title: '冻结余额', render: s => s.frozenBalance },
      ], a.subBalances || []),
    ]));
  });
}

function entriesDialog(account) {
  const ownerType = el('select', {}, [
    el('option', { value: '' }, '（全部）'),
    el('option', { value: 'ENTERPRISE' }, 'ENTERPRISE'),
    el('option', { value: 'TENANT' }, 'TENANT'),
  ]);
  const entryType = el('select', {}, [el('option', { value: '' }, '（全部）')].concat(
    ['RECHARGE', 'DAILY_DEDUCT', 'TRANSFER_OUT', 'TRANSFER_IN', 'REFUND', 'FREEZE', 'UNFREEZE', 'ADJUST', 'INSUFFICIENT']
      .map(t => el('option', { value: t }, t))));
  const tableBox = el('div', {});
  const m = modal(`账户 #${account.id} 流水`, el('div', {}, [
    el('div', { class: 'toolbar' }, [ownerType, entryType,
      el('button', { onclick: refresh }, '查询')]),
    tableBox,
  ]), { width: '720px' });

  function refresh() {
    const body = { pageNo: 1, pageSize: 50 };
    if (ownerType.value) body.ownerType = ownerType.value;
    if (entryType.value) body.entryType = entryType.value;
    post(`/accounting/room-accounts/${account.id}/entries/query`, body).then(r => {
      clear(tableBox).appendChild(table([
        { title: 'ID', render: e => e.id },
        { title: '归属', render: e => `${e.ownerType}#${e.ownerId}` },
        { title: '类型', render: e => e.entryType },
        { title: '金额', render: e => e.amount },
        { title: '关联账单', render: e => e.relatedBillId ?? '-' },
        { title: '时间', render: e => fmtTime(e.occurredAt) },
        { title: '备注', render: e => e.note || '-' },
      ], r.items || []));
    });
  }
  refresh();
}

function rechargeDialog(account, onDone) {
  const amount = el('input', { type: 'number', placeholder: '金额，如 100' });
  const payerType = el('select', {}, [
    el('option', { value: 'ENTERPRISE' }, 'ENTERPRISE（企业子余额）'),
    el('option', { value: 'TENANT' }, 'TENANT（员工子余额）'),
  ]);
  const payerId = el('input', { type: 'number', placeholder: 'TENANT 时填员工 ID' });
  const method = el('select', {}, [
    el('option', { value: 'WECHAT_PAY' }, 'WECHAT_PAY'),
    el('option', { value: 'ALIPAY' }, 'ALIPAY'),
  ]);
  modal(`账户 #${account.id} 充值`, el('div', {}, [
    formRow('金额', amount),
    formRow('充值到', payerType),
    formRow('归属 ID', payerId),
    formRow('支付方式', method),
  ]), {
    onOk: async () => {
      if (!(Number(amount.value) > 0)) { toast('金额必填', 'error'); return false; }
      if (payerType.value === 'TENANT' && !payerId.value) { toast('TENANT 充值需填员工 ID', 'error'); return false; }
      const r = await post(`/accounting/room-accounts/${account.id}/recharge`, {
        amount: Number(amount.value),
        payerType: payerType.value,
        payerId: payerId.value ? Number(payerId.value) : null,
        paymentMethod: method.value,
      });
      toast(`充值账单 #${r.billId} 已生成，请到「账单管理」模拟支付`, 'success');
      onDone();
    },
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const roomInput = el('input', { type: 'number', placeholder: '房间 ID', value: state.roomId });
  const contractInput = el('input', { type: 'number', placeholder: '合同 ID', value: state.contractId });
  const statusSel = el('select', {}, [
    el('option', { value: '' }, '（全部状态）'),
    el('option', { value: 'ACTIVE' }, '生效'),
    el('option', { value: 'FROZEN' }, '冻结'),
    el('option', { value: 'CLOSED' }, '关闭'),
  ]);
  statusSel.value = state.status;

  card.appendChild(el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '房间账户'),
    roomInput, contractInput, statusSel,
    el('button', {
      onclick: () => {
        state.roomId = roomInput.value.trim();
        state.contractId = contractInput.value.trim();
        state.status = statusSel.value;
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
  if (state.roomId) body.roomId = Number(state.roomId);
  if (state.contractId) body.contractId = Number(state.contractId);
  if (state.status) body.status = state.status;
  post('/accounting/room-accounts/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    const columns = [
      { title: 'ID', render: a => a.id },
      { title: '房间', render: a => a.roomId },
      { title: '当前合同', render: a => a.currentContractId ?? '-' },
      { title: '状态', render: a => statusTag(a.status) },
      { title: '操作', width: '190px', render: a => {
        const links = [
          el('a', { onclick: () => detailDialog(a.id) }, '子余额'),
        ];
        if (hasPerm('accounting:entry:read')) {
          links.push(el('a', { onclick: () => entriesDialog(a) }, '流水'));
        }
        if (hasPerm('accounting:recharge:write') && a.status === 'ACTIVE') {
          links.push(el('a', { onclick: () => rechargeDialog(a, () => renderPage(container)) }, '充值'));
        }
        return el('span', { class: 'ops' }, links);
      } },
    ];

    clear(tableBox).appendChild(table(columns, rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderAccountingAccounts(container) {
  state.pageNo = 1;
  renderPage(container);
}
