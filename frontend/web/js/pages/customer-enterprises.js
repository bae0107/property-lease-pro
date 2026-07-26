// 客户管理 - 企业：列表 / 创建 / 编辑

import { post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, status: '' };

function enterpriseDialog(title, init, onSubmit) {
  const name = el('input', { type: 'text', value: init.name || '' });
  const contactName = el('input', { type: 'text', value: init.contactName || '' });
  const contactMobile = el('input', { type: 'text', value: init.contactMobile || '' });
  const remark = el('input', { type: 'text', value: init.remark || '' });
  modal(title, el('div', {}, [
    formRow('企业名称', name),
    formRow('联系人', contactName),
    formRow('联系电话', contactMobile),
    formRow('备注', remark),
  ]), {
    onOk: async () => {
      if (!name.value.trim()) { toast('企业名称必填', 'error'); return false; }
      await onSubmit({
        name: name.value.trim(),
        contactName: contactName.value.trim() || null,
        contactMobile: contactMobile.value.trim() || null,
        remark: remark.value.trim() || null,
      });
    },
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const statusSel = el('select', {}, [
    el('option', { value: '' }, '（全部状态）'),
    el('option', { value: 'ACTIVE' }, '启用'),
    el('option', { value: 'INACTIVE' }, '停用'),
  ]);
  statusSel.value = state.status;

  const toolbar = el('div', { class: 'toolbar' }, [
    el('h2', { style: 'border:none;margin:0' }, '企业管理'),
    statusSel,
    el('button', { onclick: () => { state.status = statusSel.value; state.pageNo = 1; renderPage(container); } }, '查询'),
  ]);
  if (hasPerm('customer:enterprise:write')) {
    toolbar.appendChild(el('button', {
      class: 'primary',
      onclick: () => enterpriseDialog('创建企业', {}, async body => {
        await post('/customer/enterprises', body);
        toast('创建成功', 'success');
        renderPage(container);
      }),
    }, '创建企业'));
  }
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  const body = { pageNo: state.pageNo, pageSize: state.pageSize };
  if (state.status) body.status = state.status;
  post('/customer/enterprises/query', body).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    const columns = [
      { title: 'ID', render: e => e.id },
      { title: '企业名称', render: e => e.name },
      { title: '联系人', render: e => e.contactName || '-' },
      { title: '联系电话', render: e => e.contactMobile || '-' },
      { title: '状态', render: e => statusTag(e.status) },
      { title: '备注', render: e => e.remark || '-' },
    ];
    if (hasPerm('customer:enterprise:write')) {
      columns.push({ title: '操作', width: '80px', render: e => el('span', { class: 'ops' }, [
        el('a', {
          onclick: () => enterpriseDialog('编辑企业 - ' + e.name, e, async body => {
            await put(`/customer/enterprises/${e.id}`, body);
            toast('已保存', 'success');
            renderPage(container);
          }),
        }, '编辑'),
      ]) });
    }

    clear(tableBox).appendChild(table(columns, rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderCustomerEnterprises(container) {
  state.pageNo = 1;
  renderPage(container);
}
