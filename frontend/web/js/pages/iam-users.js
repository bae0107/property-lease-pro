// 系统管理 - 用户管理：列表 / 创建 STAFF / 分配角色 / 启停 / 重置密码

import { post, put, get } from '../common/api.js';
import { hasPerm } from '../common/auth.js';
import { el, clear, table, pager, modal, formRow, toast, statusTag } from '../common/ui.js';

const state = { pageNo: 1, pageSize: 10, total: 0 };

async function loadRoles() {
  const r = await post('/iam/roles/query', { pageNo: 1, pageSize: 100 });
  return r.items || [];
}

/** 角色勾选控件，返回 { node, selectedIds() }；onChange(selectedIds) 可选 */
function roleChecklist(roles, checkedIds = [], onChange = null) {
  const boxes = roles.map(r => {
    const cb = el('input', { type: 'checkbox', value: String(r.id) });
    cb.checked = checkedIds.includes(r.id);
    if (r.requiredDataScopeDimension) {
      cb.setAttribute('title', `要求${r.requiredDataScopeDimension === 'AREA' ? '区域' : '门店'}数据权限`);
    }
    return el('label', {}, [cb, ` ${r.name}（${r.code}）`,
      r.requiredDataScopeDimension
        ? el('span', { class: 'tag blue', style: 'margin-left:4px' },
            r.requiredDataScopeDimension === 'AREA' ? '区域' : '门店')
        : null]);
  });
  const selectedIds = () => boxes.map(b => b.querySelector('input'))
    .filter(i => i.checked).map(i => Number(i.value));
  if (onChange) {
    boxes.forEach(b => b.querySelector('input')
      .addEventListener('change', () => onChange(selectedIds())));
  }
  return {
    node: el('div', { class: 'check-grid' }, boxes),
    selectedIds,
  };
}

function createUserDialog(onDone) {
  const username = el('input', { type: 'text' });
  const password = el('input', { type: 'text', placeholder: '至少8位，含小写字母和数字' });
  const mobile = el('input', { type: 'text', placeholder: '11位手机号' });
  const realName = el('input', { type: 'text' });

  Promise.all([
    loadRoles(),
    loadScopeResources('AREA'),
    loadScopeResources('STORE'),
  ]).then(([roles, areas, stores]) => {
    // 选中带维度要求的角色时，动态渲染对应的数据权限行
    const scopeBox = el('div', {});
    let scopeRows = [];
    const rebuildScopes = checkedIds => {
      const dims = [...new Set(roles
        .filter(r => checkedIds.includes(r.id) && r.requiredDataScopeDimension)
        .map(r => r.requiredDataScopeDimension))];
      clear(scopeBox);
      scopeRows = dims.map(d => {
        const row = scopeRow(d, d === 'AREA' ? '区域' : '门店',
          d === 'AREA' ? areas : stores, null);
        scopeBox.appendChild(row.node);
        return row;
      });
    };
    const roleList = roleChecklist(roles, [], rebuildScopes);
    modal('创建 STAFF 用户', el('div', {}, [
      formRow('用户名', username),
      formRow('初始密码', password),
      formRow('手机号', mobile),
      formRow('真实姓名', realName),
      formRow('绑定角色', roleList.node),
      scopeBox,
    ]), {
      onOk: async () => {
        const roleIds = roleList.selectedIds();
        if (!username.value.trim() || !password.value || !mobile.value.trim()) {
          toast('用户名/密码/手机号必填', 'error'); return false;
        }
        if (roleIds.length === 0) { toast('至少绑定一个角色', 'error'); return false; }
        let scopes = null;
        if (scopeRows.length > 0) {
          try {
            scopes = scopeRows.map(r => r.collect());
          } catch (e) { toast(e.message, 'error'); return false; }
          if (scopes.some(s => !s)) {
            toast('所选角色要求配置数据权限，请选择范围', 'error'); return false;
          }
        }
        const body = {
          userType: 'STAFF',
          username: username.value.trim(),
          password: password.value,
          mobile: mobile.value.trim(),
          realName: realName.value.trim() || null,
          roleIds,
        };
        if (scopes) body.scopes = scopes;
        await post('/iam/users', body);
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

async function loadScopeResources(dimension) {
  const path = dimension === 'AREA' ? '/propertymgr/areas/query' : '/propertymgr/stores/query';
  const r = await post(path, { pageNo: 1, pageSize: 200 });
  return (r.items || []).map(x => dimension === 'AREA'
    ? { id: x.areaId, name: x.areaName }
    : { id: x.storeId, name: x.storeName });
}

/** 单维度数据权限编辑行，返回 { node, collect() → DataScopeItem|null } */
function scopeRow(dimension, label, resources, current) {
  const mode = el('select', {}, [
    el('option', { value: '' }, '不配置'),
    el('option', { value: 'ALL' }, '全部' + label),
    el('option', { value: 'SPECIFIC' }, '指定' + label),
  ]);
  mode.value = current ? current.scopeType : '';

  const boxes = resources.map(r => {
    const cb = el('input', { type: 'checkbox', value: String(r.id) });
    cb.checked = !!(current && current.resourceIds && current.resourceIds.includes(r.id));
    return el('label', {}, [cb, ` ${r.name}（#${r.id}）`]);
  });
  const checklist = el('div', {
    class: 'check-grid',
    style: mode.value === 'SPECIFIC' ? '' : 'display:none',
  }, boxes.length ? boxes : [el('span', { style: 'color:#999' }, `暂无${label}，请先到房屋管理创建`)]);

  mode.addEventListener('change', () => {
    checklist.style.display = mode.value === 'SPECIFIC' ? '' : 'none';
  });

  return {
    node: el('div', {}, [
      formRow(label + '范围', mode),
      el('div', { class: 'form-row' }, [el('label', {}, ''), checklist]),
    ]),
    collect() {
      if (!mode.value) return null;
      if (mode.value === 'ALL') return { dimension, scopeType: 'ALL' };
      const ids = boxes.map(b => b.querySelector('input'))
        .filter(i => i.checked).map(i => Number(i.value));
      if (ids.length === 0) throw new Error(`请选择至少一个${label}，或改为「全部/不配置」`);
      return { dimension, scopeType: 'SPECIFIC', resourceIds: ids };
    },
  };
}

function dataScopeDialog(user, onDone) {
  Promise.all([
    get(`/iam/users/${user.id}/data-scope`),
    loadScopeResources('AREA'),
    loadScopeResources('STORE'),
  ]).then(([dataScope, areas, stores]) => {
    const current = {};
    for (const s of (dataScope && dataScope.scopes) || []) current[s.dimension] = s;

    const areaRow = scopeRow('AREA', '区域', areas, current.AREA);
    const storeRow = scopeRow('STORE', '门店', stores, current.STORE);

    modal(`数据权限 - ${user.userName || user.realName || user.id}`, el('div', {}, [
      el('div', { style: 'color:#999;font-size:12px;margin-bottom:8px' },
        '维度须与用户角色的数据权限维度要求一致；保存为全量替换'),
      areaRow.node,
      storeRow.node,
    ]), {
      width: '640px',
      onOk: async () => {
        let scopes;
        try {
          scopes = [areaRow.collect(), storeRow.collect()].filter(Boolean);
        } catch (e) { toast(e.message, 'error'); return false; }
        await put(`/iam/users/${user.id}/data-scope`, { scopes });
        toast('数据权限已更新', 'success');
        onDone();
      },
    });
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
    const ops = { title: '操作', width: '280px', render: u => {
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
      if (hasPerm('iam:user:scope:write')) {
        links.push(el('a', { onclick: () => dataScopeDialog(u, () => renderPage(container)) }, '数据权限'));
      }
      return el('span', { class: 'ops' }, links);
    } };
    if (hasPerm('iam:user:write') || hasPerm('iam:user:password:reset') || hasPerm('iam:user:scope:write')) columns.push(ops);

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
