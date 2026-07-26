// 合同管理 - 列表：查询（企业/状态）/ 创建（企业 + 房间 + 收费规则）
// 注意：合同查询分页字段是 page/size（不是 pageNo/pageSize）

import { post } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag } from '../common/ui.js';
import { roomsRulesEditor, loadEmptyRooms, loadEnterprises } from './contract-form.js';

const state = { page: 1, size: 10, total: 0, enterpriseId: '', status: '' };

const STATUS_OPTIONS = ['DRAFT', 'SIGN_BILL_PENDING', 'READY_FOR_CHECK_IN', 'PARTIALLY_RETURNED', 'SETTLING', 'COMPLETED', 'CANCELLED'];

function createDialog(onDone) {
  Promise.all([loadEnterprises(), loadEmptyRooms()]).then(([enterprises, emptyRooms]) => {
    const entSel = el('select', {}, [el('option', { value: '' }, '（选企业）')].concat(
      enterprises.map(e => el('option', { value: String(e.id) }, `${e.name}（${e.id}）`))));
    const startDate = el('input', { type: 'date' });
    const endDate = el('input', { type: 'date' });
    const payMode = el('select', {}, ['MONTHLY', 'QUARTERLY', 'YEARLY'].map(m => el('option', { value: m }, m)));
    const remark = el('input', { type: 'text' });
    const editor = roomsRulesEditor(emptyRooms);

    modal('创建合同', el('div', {}, [
      formRow('企业', entSel),
      formRow('起租日', startDate),
      formRow('到期日', endDate),
      formRow('付款方式', payMode),
      formRow('备注', remark),
      editor.node,
    ]), {
      width: '720px',
      onOk: async () => {
        if (!entSel.value || !startDate.value || !endDate.value) {
          toast('企业/起租日/到期日必填', 'error'); return false;
        }
        const rr = editor.collect();
        if (!rr) return false;
        const c = await post('/contract/contracts', {
          enterpriseId: Number(entSel.value),
          startDate: startDate.value,
          endDate: endDate.value,
          paymentMode: payMode.value,
          remark: remark.value.trim() || null,
          rooms: rr.rooms,
          chargeRules: rr.chargeRules.length ? rr.chargeRules : null,
        });
        toast(`合同已创建（${c.contractNo}），请到详情页确认签约`, 'success');
        onDone();
      },
    });
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  loadEnterprises().then(enterprises => {
    const entSel = el('select', {}, [el('option', { value: '' }, '（全部企业）')].concat(
      enterprises.map(e => el('option', { value: String(e.id) }, `${e.name}（${e.id}）`))));
    entSel.value = state.enterpriseId;
    const statusSel = el('select', {}, [el('option', { value: '' }, '（全部状态）')].concat(
      STATUS_OPTIONS.map(s => el('option', { value: s }, s))));
    statusSel.value = state.status;

    const toolbar = el('div', { class: 'toolbar' }, [
      el('h2', { style: 'border:none;margin:0' }, '合同列表'),
      entSel, statusSel,
      el('button', {
        onclick: () => {
          state.enterpriseId = entSel.value;
          state.status = statusSel.value;
          state.page = 1;
          renderPage(container);
        },
      }, '查询'),
    ]);
    if (hasPerm('contract:contract:write')) {
      toolbar.appendChild(el('button', {
        class: 'primary', onclick: () => createDialog(() => renderPage(container)),
      }, '创建合同'));
    }
    card.appendChild(toolbar);

    const tableBox = el('div', {});
    const pagerBox = el('div', {});
    card.appendChild(tableBox);
    card.appendChild(pagerBox);

    const body = { page: state.page, size: state.size };
    if (state.enterpriseId) body.enterpriseId = Number(state.enterpriseId);
    if (state.status) body.status = state.status;
    post('/contract/contracts/query', body).then(r => {
      state.total = r.total || 0;
      const rows = r.items || [];

      clear(tableBox).appendChild(table([
        { title: 'ID', render: c => c.id },
        { title: '合同号', render: c => c.contractNo },
        { title: '企业', render: c => c.enterpriseId },
        { title: '租期', render: c => `${c.startDate} ~ ${c.endDate}` },
        { title: '付款', render: c => c.paymentMode },
        { title: '状态', render: c => statusTag(c.status) },
        { title: '续租来源', render: c => c.renewedFromContractId ?? '-' },
        { title: '操作', width: '70px', render: c => el('span', { class: 'ops' }, [
          el('a', { onclick: () => { location.hash = '#/contract/detail?id=' + c.id; } }, '详情'),
        ]) },
      ], rows));
      clear(pagerBox).appendChild(pager(state.page, state.size, state.total, p => {
        state.page = p;
        renderPage(container);
      }));
    });
  });
}

export function renderContractList(container) {
  state.page = 1;
  renderPage(container);
}
