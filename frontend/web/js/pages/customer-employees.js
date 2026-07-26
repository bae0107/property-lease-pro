// 客户管理 - 员工：先选企业 → 列表 / 创建 / 编辑
// 注意：后端 EmployeeQueryRequest 的 enterpriseId 必填

import { post, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0, enterpriseId: '', status: '' };

async function loadEnterprises() {
  const r = await post('/customer/enterprises/query', { pageNo: 1, pageSize: 500 });
  return r.items || [];
}

function employeeDialog(title, init, onSubmit) {
  const name = el('input', { type: 'text', value: init.name || '' });
  const mobile = el('input', { type: 'text', value: init.mobile || '' });
  modal(title, el('div', {}, [
    formRow('姓名', name),
    formRow('手机号', mobile),
  ]), {
    onOk: async () => {
      if (!name.value.trim()) { toast('姓名必填', 'error'); return false; }
      await onSubmit({ name: name.value.trim(), mobile: mobile.value.trim() || null });
    },
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  loadEnterprises().then(enterprises => {
    const entSel = el('select', {},
      [el('option', { value: '' }, '（请选择企业）')].concat(
        enterprises.map(e => el('option', { value: String(e.id) }, `${e.name}（${e.id}）`))));
    entSel.value = state.enterpriseId;
    const statusSel = el('select', {}, [
      el('option', { value: '' }, '（全部状态）'),
      el('option', { value: 'ACTIVE' }, '启用'),
      el('option', { value: 'INACTIVE' }, '停用'),
    ]);
    statusSel.value = state.status;

    const toolbar = el('div', { class: 'toolbar' }, [
      el('h2', { style: 'border:none;margin:0' }, '员工管理'),
      entSel, statusSel,
      el('button', {
        onclick: () => {
          state.enterpriseId = entSel.value;
          state.status = statusSel.value;
          state.pageNo = 1;
          renderPage(container);
        },
      }, '查询'),
    ]);
    if (hasPerm('customer:employee:write')) {
      toolbar.appendChild(el('button', {
        class: 'primary',
        onclick: () => {
          if (!state.enterpriseId) { toast('请先选择企业', 'error'); return; }
          employeeDialog('创建员工', {}, async body => {
            await post('/customer/employees', { ...body, enterpriseId: Number(state.enterpriseId) });
            toast('创建成功', 'success');
            renderPage(container);
          });
        },
      }, '创建员工'));
    }
    card.appendChild(toolbar);

    const tableBox = el('div', {});
    const pagerBox = el('div', {});
    card.appendChild(tableBox);
    card.appendChild(pagerBox);

    if (!state.enterpriseId) {
      tableBox.appendChild(el('div', { style: 'color:#999;padding:20px' }, '请先选择企业再查询员工'));
      return;
    }

    const body = { pageNo: state.pageNo, pageSize: state.pageSize, enterpriseId: Number(state.enterpriseId) };
    if (state.status) body.status = state.status;
    post('/customer/employees/query', body).then(r => {
      state.total = r.total || 0;
      const rows = r.items || [];

      const columns = [
        { title: 'ID', render: e => e.id },
        { title: '姓名', render: e => e.name },
        { title: '手机号', render: e => e.mobile || '-' },
        { title: '状态', render: e => statusTag(e.status) },
      ];
      if (hasPerm('customer:employee:write')) {
        columns.push({ title: '操作', width: '80px', render: e => el('span', { class: 'ops' }, [
          el('a', {
            onclick: () => employeeDialog('编辑员工 - ' + e.name, e, async body => {
              await put(`/customer/employees/${e.id}`, body);
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
  });
}

export function renderCustomerEmployees(container) {
  state.pageNo = 1;
  renderPage(container);
}
