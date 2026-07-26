// 合同管理 - 详情：信息 + 房间 + 收费规则 + 按状态操作
// DRAFT → 确认/作废；READY_FOR_CHECK_IN/PARTIALLY_RETURNED → 部分退房/整体退房/续租

import { get, post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, modal, formRow, toast, statusTag, hashQuery, fmtTime } from '../common/ui.js';
import { roomsRulesEditor } from './contract-form.js';

function infoRow(label, value) {
  return el('div', { class: 'form-row' }, [el('label', {}, label), el('span', {}, value ?? '-')]);
}

function partialReturnDialog(detail, onDone) {
  const activeRooms = (detail.rooms || []).filter(r => r.status === 'ACTIVE');
  if (activeRooms.length === 0) { toast('没有可退的在租房间', 'error'); return; }
  const boxes = activeRooms.map(r => {
    const cb = el('input', { type: 'checkbox', value: String(r.id) });
    return el('label', {}, [cb, ` 合同房间#${r.id}（房间 ${r.roomId}，租金 ${r.signedRent}）`]);
  });
  modal('部分退房', el('div', { class: 'check-grid' }, boxes), {
    okText: '退房',
    onOk: async () => {
      const ids = boxes.map(b => b.querySelector('input'))
        .filter(i => i.checked).map(i => Number(i.value));
      if (ids.length === 0) { toast('请勾选要退的房间', 'error'); return false; }
      await post(`/contract/contracts/${detail.id}/partial-return`, { contractRoomIds: ids });
      toast('退房成功', 'success');
      onDone();
    },
  });
}

function renewDialog(detail, onDone) {
  const activeRooms = (detail.rooms || []).filter(r => r.status === 'ACTIVE');
  const startDate = el('input', { type: 'date', value: detail.endDate || '' });
  const endDate = el('input', { type: 'date' });
  const payMode = el('select', {}, ['MONTHLY', 'QUARTERLY', 'YEARLY'].map(m =>
    el('option', { value: m, selected: m === detail.paymentMode ? '' : null }, m)));
  const remark = el('input', { type: 'text', value: `续租自合同 ${detail.contractNo}` });
  // 续租房间手工选择（可沿用源合同在租房间），预填源合同 ACTIVE 房间
  const editor = roomsRulesEditor([], {
    rooms: activeRooms.map(r => ({ roomId: r.roomId, signedRent: r.signedRent })),
  });

  modal(`续租 - ${detail.contractNo}（企业沿用 #${detail.enterpriseId}）`, el('div', {}, [
    formRow('新起租日', startDate, '须 ≥ 原到期日 ' + detail.endDate),
    formRow('新到期日', endDate),
    formRow('付款方式', payMode),
    formRow('备注', remark),
    editor.node,
  ]), {
    width: '720px',
    onOk: async () => {
      if (!startDate.value || !endDate.value) { toast('起止日期必填', 'error'); return false; }
      const rr = editor.collect();
      if (!rr) return false;
      const c = await post(`/contract/contracts/${detail.id}/renew`, {
        startDate: startDate.value,
        endDate: endDate.value,
        paymentMode: payMode.value,
        remark: remark.value.trim() || null,
        rooms: rr.rooms,
        chargeRules: rr.chargeRules.length ? rr.chargeRules : null,
      });
      toast(`续租合同已生成（${c.contractNo}，DRAFT）`, 'success');
      onDone(c.id);
    },
  });
}

function actionBar(detail, reload) {
  if (!hasPerm('contract:contract:write')) return null;
  const canWrite = (fn, text) => el('button', { onclick: fn }, text);
  const btns = [];
  const s = detail.status;

  if (s === 'DRAFT') {
    btns.push(el('button', {
      class: 'primary',
      onclick: async () => {
        await put(`/contract/contracts/${detail.id}/confirm`);
        toast('已确认签约，签约账单已生成', 'success');
        reload();
      },
    }, '确认签约'));
  }
  if (s === 'DRAFT' || s === 'SIGN_BILL_PENDING') {
    btns.push(canWrite(() => {
      modal('确认作废', el('div', {}, `确定作废合同「${detail.contractNo}」吗？`), {
        okText: '作废',
        onOk: async () => {
          await put(`/contract/contracts/${detail.id}/cancel`);
          toast('已作废', 'success');
          reload();
        },
      });
    }, '作废合同'));
  }
  if (s === 'SIGN_BILL_PENDING') {
    btns.push(el('span', { class: 'hint' },
      `签约账单 #${detail.signBillId ?? '?'} 待支付，请到「账单管理」模拟支付`));
  }
  if (s === 'READY_FOR_CHECK_IN' || s === 'PARTIALLY_RETURNED') {
    btns.push(canWrite(() => partialReturnDialog(detail, reload), '部分退房'));
    btns.push(canWrite(() => {
      modal('确认整体退房', el('div', {}, `确定对合同「${detail.contractNo}」整体退房吗？（需所有房间无在住租客）`), {
        okText: '整体退房',
        onOk: async () => {
          await put(`/contract/contracts/${detail.id}/full-return`);
          toast('已发起整体退房', 'success');
          reload();
        },
      });
    }, '整体退房'));
    btns.push(el('button', {
      class: 'primary',
      onclick: () => renewDialog(detail, newId => { location.hash = '#/contract/detail?id=' + newId; }),
    }, '续租'));
  }
  if (btns.length === 0) return null;
  return el('div', { class: 'toolbar' }, btns);
}

function renderPage(container, id) {
  clear(container);
  container.appendChild(el('div', { class: 'card' }, '加载中…'));

  get(`/contract/contracts/${id}`).then(detail => {
    clear(container);

    const head = el('div', { class: 'card' }, [
      el('div', { class: 'toolbar' }, [
        el('h2', { style: 'border:none;margin:0' }, `合同详情 - ${detail.contractNo}`),
        statusTag(detail.status),
        el('a', { onclick: () => { location.hash = '#/contract/list'; } }, '返回列表'),
      ]),
      infoRow('ID', detail.id),
      infoRow('企业 ID', detail.enterpriseId),
      infoRow('租期', `${detail.startDate} ~ ${detail.endDate}`),
      infoRow('付款方式', detail.paymentMode),
      infoRow('签约账单', detail.signBillId ?? '-'),
      infoRow('续租来源', detail.renewedFromContractId ?? '-'),
      infoRow('备注', detail.remark || '-'),
      infoRow('创建时间', fmtTime(detail.createdAt)),
    ]);
    container.appendChild(head);

    const bar = actionBar(detail, () => renderPage(container, id));
    if (bar) head.appendChild(bar);

    container.appendChild(el('div', { class: 'card' }, [
      el('h2', {}, '合同房间'),
      table([
        { title: '合同房间ID', render: r => r.id },
        { title: '房间ID', render: r => r.roomId },
        { title: '签约租金', render: r => r.signedRent },
        { title: '租期', render: r => `${r.leaseStart || '-'} ~ ${r.leaseEnd || '-'}` },
        { title: '状态', render: r => statusTag(r.status) },
        { title: '退房时间', render: r => fmtTime(r.returnedAt) },
      ], detail.rooms || []),
    ]));

    container.appendChild(el('div', { class: 'card' }, [
      el('h2', {}, '收费规则'),
      table([
        { title: 'ID', render: r => r.id },
        { title: '计费类型', render: r => r.chargeType },
        { title: '付款方', render: r => r.payerType },
        { title: '金额', render: r => r.amount },
        { title: '规则快照', render: r => r.ruleSnapshot || '-' },
      ], detail.chargeRules || []),
    ]));
  }).catch(e => {
    clear(container).appendChild(el('div', { class: 'card' }, [
      el('p', {}, `加载失败：${e.message}`),
      el('a', { onclick: () => { location.hash = '#/contract/list'; } }, '返回列表'),
    ]));
  });
}

export function renderContractDetail(container) {
  const id = hashQuery().id;
  if (!id) {
    container.appendChild(el('div', { class: 'card' }, '缺少合同 ID'));
    return;
  }
  renderPage(container, id);
}
