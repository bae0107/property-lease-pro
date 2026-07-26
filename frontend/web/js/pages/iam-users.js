// 系统管理 - 用户管理：列表 / 创建 STAFF / 分配角色 / 启停 / 重置密码

import { post, put, get } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0 };

async function loadRoles() {
  const r = await post('/iam/roles/query', { pageNo: 1, pageSize: 100 });
  return r.items || [];
}

/** 角色勾选控件，返回 { node, selectedIds() } */
function roleChecklist(roles, checkedIds = []) {
  const boxes = roles.map(r => {
    const cb = el('input', { type: 'checkbox', value: String(r.id) });
    cb.checked = checkedIds.includes(r.id);
    return el('label', {}, [cb, ` ${r.name}（${r.code}）`]);
  });
  return {
    node: el('div', { class: 'check-grid' }, boxes),
    selectedIds() {
      return boxes.map(b => b.querySelector('input'))
        .filter(i => i.checked).map(i => Number(i.value));
    },
  };
}

function createUserDialog(onDone) {
  const username = el('input', { type: 'text' });
  const password = el('input', { type: 'text', placeholder: '至少8位，含小写字母和数字' });
  const mobile = el('input', { type: 'text', placeholder: '11位手机号' });
  const realName = el('input', { type: 'text' });

  loadRoles().then(roles => {
    const roleList = roleChecklist(roles);
    modal('创建 STAFF 用户', el('div', {}, [
      formRow('用户名', username),
      formRow('初始密码', password),
      formRow('手机号', mobile),
      formRow('真实姓名', realName),
      formRow('绑定角色', roleList.node),
    ]), {
      onOk: async () => {
        const roleIds = roleList.selectedIds();
        if (!username.value.trim() || !password.value || !mobile.value.trim()) {
          toast('用户名/密码/手机号必填', 'error'); return false;
        }
        if (roleIds.length === 0) { toast('至少绑定一个角色', 'error'); return false; }
        await post('/iam/users', {
          userType: 'STAFF',
          username: username.value.trim(),
          password: password.value,
          mobile: mobile.value.trim(),
          realName: realName.value.trim() || null,
          roleIds,
        });
        toast('创建成功', 'success');
        onDone();
      },
    });
  });
}

function assignRolesDialog(user, onDone) {
  Promise.all([loadRoles(), get(`/iam/users/${user.id}`)]).then(([roles, detail]) => {
    const checkedIds = (detail.roles || []).map(r => r.id);
    const roleList = roleChecklist(roles, checkedIds);
    modal(`分配角色 - ${user.userName || user.realName || user.id}`, roleList.node, {
      onOk: async () => {
        const roleIds = roleList.selectedIds();
        if (roleIds.length === 0) { toast('至少绑定一个角色', 'error'); return false; }
        await put(`/iam/users/${user.id}/roles`, { roleIds });
        toast('角色已更新', 'success');
        onDone();
      },
    });
  });
}

function resetPasswordDialog(user) {
  const password = el('input', { type: 'text', placeholder: '至少8位，含小写字母和数字' });
  modal(`重置密码 - ${user.userName || user.id}`, formRow('新密码', password), {
    onOk: async () => {
      if (!password.value) { toast('请输入新密码', 'error'); return false; }
      await put(`/iam/users/${user.id}/password`, { password: password.value });
      toast('密码已重置', 'success');
    },
  });
}

function renderPage(container) {
  clear(container);
  const card = el('div', { class: 'card' });
  container.appendChild(card);

  const toolbar = el('div', { class: 'toolbar' }, [el('h2', { style: 'border:none;margin:0' }, '用户管理')]);
  if (hasPerm('iam:user:create')) {
    toolbar.appendChild(el('button', {
      class: 'primary', onclick: () => createUserDialog(() => renderPage(container)),
    }, '创建 STAFF 用户'));
  }
  card.appendChild(toolbar);

  const tableBox = el('div', {});
  const pagerBox = el('div', {});
  card.appendChild(tableBox);
  card.appendChild(pagerBox);

  post('/iam/users/query', { pageNo: state.pageNo, pageSize: state.pageSize }).then(r => {
    state.total = r.total || 0;
    const rows = r.items || [];

    const columns = [
      { title: 'ID', render: u => u.id },
      { title: '用户名', render: u => u.userName || '-' },
      { title: '姓名', render: u => u.realName || '-' },
      { title: '手机号', render: u => u.mobile || '-' },
      { title: '类型', render: u => u.userType },
      { title: '状态', render: u => statusTag(u.status) },
      { title: '角色', render: u => u.roleNames || '-' },
    ];
    const ops = { title: '操作', width: '220px', render: u => {
      const links = [];
      if (hasPerm('iam:user:write')) {
        links.push(el('a', { onclick: () => assignRolesDialog(u, () => renderPage(container)) }, '分配角色'));
        links.push(el('a', {
          onclick: async () => {
            const next = u.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
            await put(`/iam/users/${u.id}/status`, { status: next });
            toast(next === 'ACTIVE' ? '已启用' : '已停用', 'success');
            renderPage(container);
          },
        }, u.status === 'ACTIVE' ? '停用' : '启用'));
      }
      if (hasPerm('iam:user:password:reset')) {
        links.push(el('a', { onclick: () => resetPasswordDialog(u) }, '重置密码'));
      }
      return el('span', { class: 'ops' }, links);
    } };
    if (hasPerm('iam:user:write') || hasPerm('iam:user:password:reset')) columns.push(ops);

    clear(tableBox).appendChild(table(columns, rows));
    clear(pagerBox).appendChild(pager(state.pageNo, state.pageSize, state.total, p => {
      state.pageNo = p;
      renderPage(container);
    }));
  });
}

export function renderIamUsers(container) {
  state.pageNo = 1;
  renderPage(container);
}
