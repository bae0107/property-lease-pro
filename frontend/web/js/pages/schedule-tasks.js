// 运维 - 定时任务：手动触发 / 日志查询

import { post } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, toast, statusTag, fmtTime } from '../common/ui.js';

const TASK_TYPES = [
  { value: 'DAILY_METER_SETTLEMENT', label: '日结（水电费结算）' },
  { value: 'CONTRACT_EXPIRY_CHECK', label: '合同到期检查' },
];
const state = { pageNo: 1, pageSize: 10, total: 0, taskType: '', status: '' };

function renderTriggerCard(container, refreshLogs) {
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const typeSel = el('select', {}, TASK_TYPES.map(t => el('option', { value: t.value }, t.label)));
  const dateInput = el('input', { type: 'date' });
  const forceCb = el('input', { type: 'checkbox' });

  card.appendChild(el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '手动触发'),
    typeSel, dateInput,
    el('label', {}, [forceCb, ' 强制重跑']),
    el('button', {
      class: 'primary',
      onclick: async () => {
        const body = {};
        if (dateInput.value) body.targetDate = dateInput.value;
        if (forceCb.checked) body.force = true;
        const r = await post(`/schedule/tasks/${typeSel.value}/trigger`, body);
        toast(`已触发：${r.message || ('taskLog #' + r.taskLogId)}`, 'success');
        refreshLogs();
      },
    }, '触发'),
    el('span', { class: 'hint' }, '业务日期留空默认昨日'),
  ]));
}

function renderLogsCard(container) {
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const typeSel = el('select', {}, [el('option', { value: '' }, '（全部任务）')].concat(
    TASK_TYPES.map(t => el('option', { value: t.value }, t.label))));
  typeSel.value = state.taskType;
  const statusSel = el('select', {}, [el('option', { value: '' }, '（全部状态）')].concat(
    ['RUNNING', 'SUCCESS', 'PARTIAL_FAILURE', 'FAILED'].map(s => el('option', { value: s }, s))));
  statusSel.value = state.status;

  card.appendChild(el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '任务日志'),
    typeSel, statusSel,
    el('button', {
      onclick: () => {
        state.taskType = typeSel.value;
        state.status = statusSel.value;
        state.pageNo = 1;
        refresh();
      },
    }, '查询'),
  ]));
  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  function refresh() {
    const body = { pageNo: state.pageNo, pageSize: state.pageSize };
    if (state.taskType) body.taskType = state.taskType;
    if (state.status) body.status = state.status;
    post('/schedule/tasks/query', body).then(r => {
      state.total = r.total || 0;
      clear(tableBox).appendChild(table([
        { title: 'ID', render: t => t.id },
        { title: '任务', render: t => t.taskType },
        { title: '业务日期', render: t => t.scheduledDate },
        { title: '状态', render: t => statusTag(t.status) },
        { title: '触发方式', render: t => t.triggeredBy || '-' },
        { title: '总数/成功/失败', render: t =>
            `${t.totalCount ?? '-'} / ${t.successCount ?? '-'} / ${t.failureCount ?? '-'}` },
        { title: '错误摘要', render: t => t.errorSummary || '-' },
        { title: '开始', render: t => fmtTime(t.startedAt) },
        { title: '结束', render: t => fmtTime(t.finishedAt) },
      ], r.items || []));
      clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
        state.pageNo = p;
        refresh();
      }));
    });
  }
  refresh();
  return refresh;
}

export function renderScheduleTasks(container) {
  clear(container);
  state.pageNo = 1;
  if (hasPerm('schedule:task:trigger')) {
    renderTriggerCard(container, () => renderLogsCard.refresh && renderLogsCard.refresh());
  }
  renderLogsCard.refresh = renderLogsCard(container);
}
