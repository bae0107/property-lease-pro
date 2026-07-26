// 应用入口：hash 路由 + 布局 + 权限驱动菜单

import { isLoggedIn, currentUsername, hasPerm, hasAnyPerm, getRefreshToken, clearLogin } from './common/auth.js';
import { post } from './common/api.js';
import { el, clear, toast } from './common/ui.js';
import { renderLogin } from './pages/login.js';
import { renderDashboard } from './pages/dashboard.js';
import { renderIamUsers } from './pages/iam-users.js';
import { renderIamRoles } from './pages/iam-roles.js';

// 路由表：path → { title, render, perm（可选，进入该页需要的权限） }
const routes = {
  '/dashboard': { title: '工作台', render: renderDashboard },
  '/iam/users': { title: '用户管理', render: renderIamUsers, perm: 'iam:user:read', group: '系统管理' },
  '/iam/roles': { title: '角色管理', render: renderIamRoles, perm: 'iam:role:read', group: '系统管理' },
};

function currentPath() {
  const h = location.hash.replace(/^#/, '');
  return h || '/dashboard';
}

function buildMenu(container) {
  const visible = Object.entries(routes)
    .filter(([, r]) => !r.perm || hasPerm(r.perm));
  let lastGroup = null;
  for (const [path, r] of visible) {
    if (r.group && r.group !== lastGroup) {
      lastGroup = r.group;
      container.appendChild(el('div', {
        style: 'padding:10px 16px 4px;color:#ffffff66;font-size:12px',
      }, r.group));
    }
    const active = currentPath() === path;
    container.appendChild(el('a', {
      class: 'menu-item' + (active ? ' active' : ''),
      onclick: () => { location.hash = '#' + path; },
    }, r.title));
  }
}

async function logout() {
  try {
    await post('/auth/logout', { refreshToken: getRefreshToken() }, { silent: true });
  } catch (e) { /* 登出失败也强制清本地 */ }
  clearLogin();
  location.hash = '#/login';
  toast('已登出', 'success');
}

function render() {
  const app = document.getElementById('app');
  clear(app);

  if (!isLoggedIn() || currentPath() === '/login') {
    if (isLoggedIn() && currentPath() === '/login') { location.hash = '#/dashboard'; return; }
    renderLogin(app);
    return;
  }

  const route = routes[currentPath()];
  if (!route) { location.hash = '#/dashboard'; return; }
  if (route.perm && !hasPerm(route.perm)) {
    app.appendChild(el('div', { class: 'card', style: 'margin:40px' }, '无权限访问该页面'));
    return;
  }

  const menuBox = el('div', {});
  buildMenu(menuBox);

  const content = el('div', { class: 'content' });
  app.appendChild(el('div', { class: 'layout' }, [
    el('div', { class: 'sidebar' }, [
      el('div', { class: 'logo' }, '物业租赁管理台'),
      menuBox,
    ]),
    el('div', { class: 'main' }, [
      el('div', { class: 'topbar' }, [
        el('span', { class: 'user' }, currentUsername()),
        el('a', { onclick: logout }, '登出'),
      ]),
      content,
    ]),
  ]));

  route.render(content);
}

window.addEventListener('hashchange', render);
render();
