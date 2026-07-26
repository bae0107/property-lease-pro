// 抄表 - 读数与日结：抄表录入 / 读数查询 / 日结费用 + 分摊明细

import { get, post } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag, fmtTime } from '../common/ui.js';

const METER_TYPES = ['WATER', 'ELECTRICITY', 'HOT_WATER'];
const readingsState = { pageNo: 1, pageSize: 10, total: 0, roomId: '', meterType: '' };
const chargesState = { pageNo: 1, pageSize: 10, total: 0, roomId: '', startDate: '', endDate: '' };

function readingDialog(onDone) {
  const roomId = el('input', { type: 'number', placeholder: '房间 ID' });
  const type = el('select', {}, METER_TYPES.map(t => el('option', { value: t }, t)));
  const value = el('input', { type: 'number', placeholder: '表盘累计读数（非增量）' });
  const time = el('input', { type: 'datetime-local' });
  modal('抄表录入', el('div', {}, [
    formRow('房间 ID', roomId),
    formRow('表类型', type),
    formRow('读数', value),
    formRow('抄表时间', time, '留空取当前时间'),
  ]), {
    onOk: async () => {
      if (!roomId.value || value.value === '') { toast('房间 ID 和读数必填', 'error'); return false; }
      await post('/metering/readings', {
        roomId: Number(roomId.value),
        meterType: type.value,
        readingValue: Number(value.value),
        readingTime: time.value ? new Date(time.value).toISOString() : new Date().toISOString(),
      });
      toast('读数已提交', 'success');
      onDone();
    },
  });
}

function apportionmentsDialog(chargeId) {
  get(`/metering/daily-charges/${chargeId}/apportionments`).then(r => {
    modal(`日结 #${chargeId} 分摊明细`, table([
      { title: 'ID', render: a => a.id },
      { title: '员工', render: a => a.tenantId },
      { title: 'Stay', render: a => a.stayId },
      { title: '结算日', render: a => a.settlementDate || '-' },
      { title: '分摊金额', render: a => a.apportionedAmount },
    ], r.items || []));
  });
}

function renderReadingsCard(container) {
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const roomInput = el('input', { type: 'number', placeholder: '房间 ID', value: readingsState.roomId });
  const typeSel = el('select', {}, [el('option', { value: '' }, '（全部表）')].concat(
    METER_TYPES.map(t => el('option', { value: t }, t))));
  typeSel.value = readingsState.meterType;

  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '读数查询'),
    roomInput, typeSel,
    el('button', {
      onclick: () => {
        readingsState.roomId = roomInput.value.trim();
        readingsState.meterType = typeSel.value;
        readingsState.pageNo = 1;
        refresh();
      },
    }, '查询'),
  ]);
  if (hasPerm('metering:reading:write')) {
    toolbar.appendChild(el('button', { class: 'primary', onclick: () => readingDialog(refresh) }, '抄表录入'));
  }
  card.appendChild(toolbar);
  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  function refresh() {
    const body = { pageNo: readingsState.pageNo, pageSize: readingsState.pageSize };
    if (readingsState.roomId) body.roomId = Number(readingsState.roomId);
    if (readingsState.meterType) body.meterType = readingsState.meterType;
    post('/metering/readings/query', body).then(r => {
      readingsState.total = r.total || 0;
      clear(tableBox).appendChild(table([
        { title: 'ID', render: m => m.id },
        { title: '房间', render: m => m.roomId },
        { title: '表类型', render: m => m.meterType },
        { title: '读数', render: m => m.readingValue },
        { title: '抄表时间', render: m => fmtTime(m.readingTime) },
        { title: '来源', render: m => m.source },
        { title: '锚点', render: m => m.anchorType || '-' },
        { title: 'Stay', render: m => m.stayId ?? '-' },
      ], r.items || []));
      clear(pagerBox).appendChild(pager(readingsState.pageNo, readingsState.pageSize, readingsState.total, p => {
        readingsState.pageNo = p;
        refresh();
      }));
    });
  }
  refresh();
}

function renderChargesCard(container) {
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const roomInput = el('input', { type: 'number', placeholder: '房间 ID', value: chargesState.roomId });
  const startInput = el('input', { type: 'date', value: chargesState.startDate });
  const endInput = el('input', { type: 'date', value: chargesState.endDate });

  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '日结费用'),
    roomInput, startInput, endInput,
    el('button', {
      onclick: () => {
        chargesState.roomId = roomInput.value.trim();
        chargesState.startDate = startInput.value;
        chargesState.endDate = endInput.value;
        chargesState.pageNo = 1;
        refresh();
      },
    }, '查询'),
    el('span', { class: 'hint' }, '无数据可先到「定时任务」手动触发日结'),
  ]);
  card.appendChild(toolbar);
  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  function refresh() {
    const body = { pageNo: chargesState.pageNo, pageSize: chargesState.pageSize };
    if (chargesState.roomId) body.roomId = Number(chargesState.roomId);
    if (chargesState.startDate) body.startDate = chargesState.startDate;
    if (chargesState.endDate) body.endDate = chargesState.endDate;
    post('/metering/daily-charges/query', body).then(r => {
      chargesState.total = r.total || 0;
      clear(tableBox).appendChild(table([
        { title: 'ID', render: c => c.id },
        { title: '房间', render: c => c.roomId },
        { title: '结算日', render: c => c.settlementDate },
        { title: '水费', render: c => c.waterAmount ?? '-' },
        { title: '电费', render: c => c.electricAmount ?? '-' },
        { title: '热水', render: c => c.hotWaterAmount ?? '-' },
        { title: '合计', render: c => c.totalAmount },
        { title: '状态', render: c => statusTag(c.status) },
        { title: '操作', width: '90px', render: c => el('span', { class: 'ops' }, [
          el('a', { onclick: () => apportionmentsDialog(c.id) }, '分摊明细'),
        ]) },
      ], r.items || []));
      clear(pagerBox).appendChild(pager(chargesState.pageNo, chargesState.pageSize, chargesState.total, p => {
        chargesState.pageNo = p;
        refresh();
      }));
    });
  }
  refresh();
}

export function renderMeteringReadings(container) {
  clear(container);
  readingsState.pageNo = 1;
  chargesState.pageNo = 1;
  renderReadingsCard(container);
  renderChargesCard(container);
}
