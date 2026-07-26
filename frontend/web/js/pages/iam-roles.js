// 系统管理 - 角色管理：列表 / 创建 / 分配权限 / 删除

import { post, get, put } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0 };

async function loadPermissions() {
  const r = await post('/iam/permissions/query', { pageNo: 1, pageSize: 500 });
  return r.items || [];
}

/** 权限勾选控件（按 resource 前缀分组），返回 { node, selectedIds() } */
function permChecklist(perms, checkedIds = []) {
  const groups = {};
  for (const p of perms) {
    const g = p.code.split(':')[0];
    (groups[g] = groups[g] || []).push(p);
  }
  const boxes = [];
  const children = [];
  for (const [g, list] of Object.entries(groups)) {
    children.push(el('div', { class: 'check-group-title' }, g));
    for (const p of list) {
      const cb = el('input', { type: 'checkbox', value: String(p.id) });
      cb.checked = checkedIds.includes(p.id);
      boxes.push(cb);
      children.push(el('label', { title: p.description || '' }, [cb, ` ${p.code}`]));
    }
  }
  return {
    node: el('div', { class: 'check-grid' }, children),
    selectedIds() {
      return boxes.filter(i => i.checked).map(i => Number(i.value));
    },
  };
}

function createRoleDialog(onDone) {
  const name = el('input', { type: 'text', placeholder: '如：运营STAFF' });
  const code = el('input', { type: 'text', placeholder: '大写+下划线，如 OPS_STAFF' });
  const description = el('input', { type: 'text' });
  modal('创建角色', el('div', {}, [
    formRow('角色名称', name),
    formRow('角色编码', code),
    formRow('描述', description),
  ]), {
    onOk: async () => {
      if (!name.value.trim() || !code.value.trim()) {
        toast('名称和编码必填', 'error'); return false;
      }
      await post('/iam/roles', {
        name: name.value.trim(),
        code: code.value.trim(),
        description: description.value.trim() || null,
      });
      toast('创建成功', 'success');
      onDone();
    },
  });
}

function assignPermsDialog(role, onDone) {
  Promise.all([loadPermissions(), get(`/iam/roles/${role.id}`)]).then(([perms, detail]) => {
    const checkedIds = (detail.permissions || []).map(p => p.id);
    const list = permChecklist(perms, checkedIds);
    modal(`分配权限 - ${role.name}（${role.code}）`, list.node, {
      width: '720px',
      onOk: async () => {
        await put(`/iam/roles/${role.id}/permissions`, { permissionIds: list.selectedIds() });
        toast('权限已更新', 'success');
        onDone();
      },
    });
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const toolbar = el('div', { class: 'toolbar' }, [el('h2', { style: 'border:none;margin:0' }, '角色管理')]);
  if (hasPerm('iam:role:write')) {
    toolbar.appendChild(el('button', {
      class: 'primary', onclick: () => createRoleDialog(() => renderPage(container)),
    }, '创建角色'));
  }
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  post('/iam/roles/query', { pageNo: state.pageNo, pageSize: state.pageSize }).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    const columns = [
      { title: 'ID', render: r2 => r2.id },
      { title: '名称', render: r2 => r2.name },
      { title: '编码', render: r2 => r2.code },
      { title: '类型', render: r2 => r2.roleType },
      { title: '来源', render: r2 => r2.sourceType === 'BUILTIN'
          ? el('span', { class: 'tag blue' }, '内置') : el('span', { class: 'tag' }, '自定义') },
      { title: '描述', render: r2 => r2.description || '-' },
    ];
    if (hasPerm('iam:role:write')) {
      columns.push({ title: '操作', width: '170px', render: r2 => el('span', { class: 'ops' }, [
        el('a', { onclick: () => assignPermsDialog(r2, () => renderPage(container)) }, '分配权限'),
        r2.sourceType === 'BUILTIN' ? null : el('a', {
          onclick: () => {
            modal('确认删除', el('div', {}, `确定删除角色「${r2.name}」吗？`), {
              okText: '删除',
              onOk: async () => {
                await post('/iam/roles/batch-delete', { ids: [r2.id] });
                toast('已删除', 'success');
                renderPage(container);
              },
            });
          },
        }, '删除'),
      ]) });
    }

    clear(tableBox).appendChild(table(columns, rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderIamRoles(container) {
  state.pageNo = 1;
  renderPage(container);
}
