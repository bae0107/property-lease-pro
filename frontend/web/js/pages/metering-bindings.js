// 抄表 - 绑表与电价：房间设备绑定 / 解绑；电价列表 / 新增版本

import { get, post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, modal, formRow, toast, fmtTime } from '../common/ui.js';

const METER_TYPES = ['WATER', 'ELECTRICITY', 'HOT_WATER'];
const state = { roomId: '' };

function meterTypeSel(withAll = false) {
  const opts = withAll ? [el('option', { value: '' }, '（全部）')] : [];
  METER_TYPES.forEach(t => opts.push(el('option', { value: t }, t)));
  return el('select', {}, opts);
}

function bindDialog(roomId, onDone) {
  const deviceId = el('input', { type: 'number', placeholder: '设备 ID，如 9001' });
  const type = meterTypeSel();
  const initReading = el('input', { type: 'number', placeholder: '起始读数，如 100' });
  modal(`房间 ${roomId} 绑定设备`, el('div', {}, [
    formRow('设备 ID', deviceId),
    formRow('表类型', type),
    formRow('起始读数', initReading),
  ]), {
    onOk: async () => {
      if (!deviceId.value || initReading.value === '') {
        toast('设备 ID 和起始读数必填', 'error'); return false;
      }
      await post(`/metering/rooms/${roomId}/bindings`, {
        deviceId: Number(deviceId.value),
        meterType: type.value,
        initialReading: Number(initReading.value),
      });
      toast('绑定成功', 'success');
      onDone();
    },
  });
}

function priceDialog(onDone) {
  const type = meterTypeSel();
  const unitPrice = el('input', { type: 'number', placeholder: '如 1.8' });
  const effectiveFrom = el('input', { type: 'date' });
  const storeId = el('input', { type: 'number', placeholder: '留空 = 全局默认' });
  modal('新增电价版本', el('div', {}, [
    formRow('表类型', type),
    formRow('单价', unitPrice),
    formRow('生效日期', effectiveFrom, '不能早于今日'),
    formRow('门店 ID', storeId),
  ]), {
    onOk: async () => {
      if (!(Number(unitPrice.value) > 0) || !effectiveFrom.value) {
        toast('单价和生效日期必填', 'error'); return false;
      }
      await post('/metering/prices', {
        meterType: type.value,
        unitPrice: Number(unitPrice.value),
        effectiveFrom: effectiveFrom.value,
        storeId: storeId.value ? Number(storeId.value) : null,
      });
      toast('电价已创建', 'success');
      onDone();
    },
  });
}

function renderBindingsCard(container) {
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const roomInput = el('input', { type: 'number', placeholder: '房间 ID', value: state.roomId });
  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '设备绑定'),
    roomInput,
    el('button', { onclick: () => { state.roomId = roomInput.value.trim(); renderBindingsCard.refresh && renderBindingsCard.refresh(); } }, '查询'),
  ]);
  card.appendChild(toolbar);
  const tableBox = el('div', {});
  card.appendChild(tableBox);

  function refresh() {
    clear(tableBox);
    if (!state.roomId) {
      tableBox.appendChild(el('div', { style: 'color:#999;padding:12px' }, '请输入房间 ID 查询绑定'));
      return;
    }
    get(`/metering/rooms/${state.roomId}/bindings`).then(r => {
      const rows = r.items || [];
      const columns = [
        { title: '绑定 ID', render: b => b.id },
        { title: '设备 ID', render: b => b.deviceId },
        { title: '表类型', render: b => b.meterType },
        { title: '起始读数', render: b => b.initialReading ?? '-' },
        { title: '生效', render: b => b.isActive ? '是' : '否' },
        { title: '绑定时间', render: b => fmtTime(b.bindingStartAt) },
        { title: '解绑时间', render: b => fmtTime(b.bindingEndAt) },
      ];
      if (hasPerm('metering:binding:write')) {
        columns.push({ title: '操作', width: '80px', render: b =>
          !b.isActive ? '-' : el('span', { class: 'ops' }, [
            el('a', {
              onclick: async () => {
                await put(`/metering/rooms/${state.roomId}/bindings/${b.id}/unbind`);
                toast('已解绑', 'success');
                refresh();
              },
            }, '解绑'),
          ]) });
      }
      clear(tableBox).appendChild(table(columns, rows));
    });
  }

  if (hasPerm('metering:binding:write')) {
    toolbar.appendChild(el('button', {
      class: 'primary',
      onclick: () => {
        if (!state.roomId) { toast('请先输入房间 ID 并查询', 'error'); return; }
        bindDialog(state.roomId, refresh);
      },
    }, '绑定设备'));
  }
  renderBindingsCard.refresh = refresh;
  refresh();
}

function renderPricesCard(container) {
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const typeSel = meterTypeSel(true);
  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '电价配置'),
    typeSel,
    el('button', { onclick: refresh }, '查询'),
  ]);
  card.appendChild(toolbar);
  const tableBox = el('div', {});
  card.appendChild(tableBox);

  function refresh() {
    const qs = typeSel.value ? '?meterType=' + typeSel.value : '';
    get('/metering/prices' + qs).then(r => {
      clear(tableBox).appendChild(table([
        { title: 'ID', render: p => p.id },
        { title: '门店', render: p => p.storeId ?? '全局' },
        { title: '表类型', render: p => p.meterType },
        { title: '单价', render: p => p.unitPrice },
        { title: '生效期', render: p => `${p.effectiveFrom} ~ ${p.effectiveTo || '至今'}` },
      ], r.items || []));
    });
  }

  if (hasPerm('metering:price:write')) {
    toolbar.appendChild(el('button', { class: 'primary', onclick: () => priceDialog(refresh) }, '新增电价'));
  }
  refresh();
}

export function renderMeteringBindings(container) {
  clear(container);
  renderBindingsCard(container);
  renderPricesCard(container);
}
