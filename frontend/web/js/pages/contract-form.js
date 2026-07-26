// 合同表单共享件：房间行 + 收费规则行的动态编辑器
// 用于 contract-list（创建）与 contract-detail（续租）

import { post } from '../common/api.js';
import { el, toast } from '../common/ui.js';

/** 加载空房（创建合同可选房间） */
export async function loadEmptyRooms() {
  const r = await post('/propertymgr/rooms/query', { pageNo: 1, pageSize: 500, roomStatus: 'EMPTY' });
  return r.items || [];
}

/** 加载企业列表 */
export async function loadEnterprises() {
  const r = await post('/customer/enterprises/query', { pageNo: 1, pageSize: 500 });
  return r.items || [];
}

/**
 * 房间 + 收费规则动态编辑器。
 * @param {Array} roomOptions 可选房间（propertymgr Room 列表）；为空数组时房间行用手输 roomId
 * @param {object} opts { rooms: 预填房间行, chargeRules: 预填规则行, fixedRoomIds: 预填且不可改（续租） }
 * @returns {{ node, collect() }} collect() 校验失败返回 null（已 toast）
 */
export function roomsRulesEditor(roomOptions, opts = {}) {
  const roomsBox = el('div', {});
  const rulesBox = el('div', {});

  function roomRow(init = {}) {
    const roomSel = roomOptions.length
      ? el('select', {}, [el('option', { value: '' }, '（选房间）')].concat(
          roomOptions.map(r => el('option', { value: String(r.roomId) },
            `#${r.roomId} ${r.buildingId}-${r.roomNum || ''}`))))
      : el('input', { type: 'number', placeholder: '房间 ID', style: 'width:90px' });
    if (init.roomId != null) roomSel.value = String(init.roomId);
    const rent = el('input', { type: 'number', placeholder: '签约租金', style: 'width:100px', value: init.signedRent ?? '' });
    const ls = el('input', { type: 'date', value: init.leaseStart || '' });
    const le = el('input', { type: 'date', value: init.leaseEnd || '' });
    const del = el('a', { onclick: () => row.remove() }, '删除');
    const row = el('div', { class: 'toolbar', style: 'margin:4px 0' }, [roomSel, rent, ls, le, del]);
    row._collect = () => ({
      roomId: Number(roomSel.value),
      signedRent: Number(rent.value),
      leaseStart: ls.value,
      leaseEnd: le.value,
    });
    return row;
  }

  function ruleRow(init = {}) {
    const type = el('input', { type: 'text', placeholder: '如 ENTERPRISE_DEPOSIT', style: 'width:190px', value: init.chargeType || '' });
    const payer = el('select', {}, [
      el('option', { value: 'ENTERPRISE' }, 'ENTERPRISE'),
      el('option', { value: 'TENANT' }, 'TENANT'),
    ]);
    if (init.payerType) payer.value = init.payerType;
    const amount = el('input', { type: 'number', placeholder: '金额', style: 'width:110px', value: init.amount ?? '' });
    const del = el('a', { onclick: () => row.remove() }, '删除');
    const row = el('div', { class: 'toolbar', style: 'margin:4px 0' }, [type, payer, amount, del]);
    row._collect = () => ({
      chargeType: type.value.trim(),
      payerType: payer.value,
      amount: Number(amount.value),
    });
    return row;
  }

  (opts.rooms && opts.rooms.length ? opts.rooms : [{}]).forEach(r => roomsBox.appendChild(roomRow(r)));
  (opts.chargeRules || []).forEach(r => rulesBox.appendChild(ruleRow(r)));

  const node = el('div', {}, [
    el('div', { class: 'form-row' }, [
      el('label', {}, '房间'),
      el('button', { onclick: () => roomsBox.appendChild(roomRow()) }, '+ 添加房间'),
    ]),
    roomsBox,
    el('div', { class: 'form-row' }, [
      el('label', {}, '收费规则'),
      el('button', { onclick: () => rulesBox.appendChild(ruleRow()) }, '+ 添加规则'),
      el('span', { class: 'hint' }, '企业押金用 ENTERPRISE_DEPOSIT；可为空'),
    ]),
    rulesBox,
  ]);

  function collect() {
    const roomRows = [...roomsBox.children];
    if (roomRows.length === 0) { toast('至少添加一个房间', 'error'); return null; }
    const rooms = [];
    for (const row of roomRows) {
      const r = row._collect();
      if (!r.roomId || !(r.signedRent > 0) || !r.leaseStart || !r.leaseEnd) {
        toast('房间行需完整：房间/租金/起止日期', 'error'); return null;
      }
      rooms.push(r);
    }
    const chargeRules = [];
    for (const row of [...rulesBox.children]) {
      const c = row._collect();
      if (!c.chargeType || !(c.amount > 0)) {
        toast('收费规则行需完整：类型/金额', 'error'); return null;
      }
      chargeRules.push(c);
    }
    return { rooms, chargeRules };
  }

  return { node, collect };
}
