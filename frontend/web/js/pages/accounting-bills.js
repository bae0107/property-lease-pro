// 账务 - 账单：查询 / 详情 / 模拟支付（reale2e 限定 dev 端点）/ 作废

import { get, post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag, fmtTime } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, billType: '', billStatus: '', contractId: '', roomId: '' };

const BILL_TYPES = ['ENTERPRISE_SIGN_BILL', 'PERSONAL_DEPOSIT_BILL', 'RECHARGE_BILL', 'RENT_BILL', 'SETTLEMENT_BILL', 'REFUND_BILL'];
const BILL_STATUSES = ['PENDING', 'PAID', 'CANCELLED', 'REFUNDED'];

function detailDialog(id) {
  get(`/accounting/bills/${id}`).then(b => {
    modal(`账单详情 - ${b.billNo}`, el('div', {}, [
      formRow('ID', el('span', {}, String(b.id))),
      formRow('类型', el('span', {}, b.billType)),
      formRow('归属', el('span', {}, `${b.billOwnerType} #${b.billOwnerId}`)),
      formRow('合同/房间/员工', el('span', {},
        `${b.contractId ?? '-'} / ${b.roomId ?? '-'} / ${b.tenantId ?? '-'}`)),
      formRow('金额', el('span', {}, String(b.totalAmount))),
      formRow('状态', statusTag(b.billStatus)),
      formRow('billing侧ID', el('span', {}, String(b.billingServiceBillId ?? '-'))),
      formRow('支付时间', el('span', {}, fmtTime(b.paidAt))),
      formRow('创建时间', el('span', {}, fmtTime(b.createdAt))),
    ]));
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const typeSel = el('select', {}, [el('option', { value: '' }, '（全部类型）')].concat(
    BILL_TYPES.map(t => el('option', { value: t }, t))));
  typeSel.value = state.billType;
  const statusSel = el('select', {}, [el('option', { value: '' }, '（全部状态）')].concat(
    BILL_STATUSES.map(s => el('option', { value: s }, s))));
  statusSel.value = state.billStatus;
  const contractInput = el('input', { type: 'number', placeholder: '合同 ID', value: state.contractId });
  const roomInput = el('input', { type: 'number', placeholder: '房间 ID', value: state.roomId });

  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '账单管理'),
    typeSel, statusSel, contractInput, roomInput,
    el('button', {
      onclick: () => {
        state.billType = typeSel.value;
        state.billStatus = statusSel.value;
        state.contractId = contractInput.value.trim();
        state.roomId = roomInput.value.trim();
        state.pageNo = 1;
        renderPage(container);
      },
    }, '查询'),
  ]);
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  const body = { pageNo: state.pageNo, pageSize: state.pageSize };
  if (state.billType) body.billType = state.billType;
  if (state.billStatus) body.billStatus = state.billStatus;
  if (state.contractId) body.contractId = Number(state.contractId);
  if (state.roomId) body.roomId = Number(state.roomId);
  post('/accounting/bills/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    const columns = [
      { title: 'ID', render: b => b.id },
      { title: '账单号', render: b => b.billNo },
      { title: '类型', render: b => b.billType },
      { title: '归属', render: b => `${b.billOwnerType}#${b.billOwnerId}` },
      { title: '合同', render: b => b.contractId ?? '-' },
      { title: '房间', render: b => b.roomId ?? '-' },
      { title: '金额', render: b => b.totalAmount },
      { title: '状态', render: b => statusTag(b.billStatus) },
      { title: '创建时间', render: b => fmtTime(b.createdAt) },
      { title: '操作', width: '190px', render: b => {
        const links = [el('a', { onclick: () => detailDialog(b.id) }, '详情')];
        if (b.billStatus === 'PENDING') {
          links.push(el('a', {
            onclick: async () => {
              await post(`/dev/bills/${b.id}/mock-pay`);
              toast(`账单 #${b.id} 已模拟支付`, 'success');
              renderPage(container);
            },
          }, '模拟支付'));
          if (hasPerm('accounting:bill:write')) {
            links.push(el('a', {
              onclick: () => {
                modal('确认作废', el('div', {}, `确定作废账单「${b.billNo}」吗？`), {
                  okText: '作废',
                  onOk: async () => {
                    await put(`/accounting/bills/${b.id}/cancel`);
                    toast('已作废', 'success');
                    renderPage(container);
                  },
                });
              },
            }, '作废'));
          }
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

export function renderAccountingBills(container) {
  state.pageNo = 1;
  renderPage(container);
}
