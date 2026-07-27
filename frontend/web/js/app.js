// 应用入口：hash 路由 + 布局 + 权限驱动菜单

import { isLoggedIn, currentUsername, hasPerm, hasAnyPerm, getRefreshToken, clearLogin } from './common/auth.js';
import { post } from './common/api.js';
import { el, clear, toast } from './common/ui.js';
import { renderLogin } from './pages/login.js';
import { renderDashboard } from './pages/dashboard.js';
import { renderIamUsers } from './pages/iam-users.js';
import { renderIamRoles } from './pages/iam-roles.js';
import { renderPropertyAreas } from './pages/property-areas.js';
import { renderPropertyStores } from './pages/property-stores.js';
import { renderPropertyBuildings } from './pages/property-buildings.js';
import { renderPropertyRooms } from './pages/property-rooms.js';
import { renderCustomerEnterprises } from './pages/customer-enterprises.js';
import { renderCustomerEmployees } from './pages/customer-employees.js';
import { renderContractList } from './pages/contract-list.js';
import { renderContractDetail } from './pages/contract-detail.js';
import { renderOccupancyAssignments } from './pages/occupancy-assignments.js';
import { renderOccupancyStays } from './pages/occupancy-stays.js';
import { renderMeteringBindings } from './pages/metering-bindings.js';
import { renderMeteringReadings } from './pages/metering-readings.js';
import { renderAccountingBills } from './pages/accounting-bills.js';
import { renderAccountingAccounts } from './pages/accounting-accounts.js';
import { renderAccountingDeposits } from './pages/accounting-deposits.js';
import { renderScheduleTasks } from './pages/schedule-tasks.js';

// 路由表：path → { title, render, perm（可选，进入该页需要的权限） }
// hideInMenu: 详情页等不出现在菜单，但仍受 perm 约束
const routes = {
  '/dashboard': { title: '工作台', render: renderDashboard },
  '/property/areas': { title: '区域管理', render: renderPropertyAreas, perm: 'propertymgr:area:read', group: '房屋管理' },
  '/property/stores': { title: '门店管理', render: renderPropertyStores, perm: 'propertymgr:store:read', group: '房屋管理' },
  '/property/buildings': { title: '楼栋管理', render: renderPropertyBuildings, perm: 'propertymgr:building:read', group: '房屋管理' },
  '/property/rooms': { title: '房间管理', render: renderPropertyRooms, perm: 'propertymgr:room:read', group: '房屋管理' },
  '/customer/enterprises': { title: '企业管理', render: renderCustomerEnterprises, perm: 'customer:enterprise:read', group: '客户管理' },
  '/customer/employees': { title: '员工管理', render: renderCustomerEmployees, perm: 'customer:employee:read', group: '客户管理' },
  '/contract/list': { title: '合同列表', render: renderContractList, perm: 'contract:contract:read', group: '合同管理' },
  '/contract/detail': { title: '合同详情', render: renderContractDetail, perm: 'contract:contract:read', group: '合同管理', hideInMenu: true },
  '/occupancy/assignments': { title: '入住分配', render: renderOccupancyAssignments, perm: 'occupancy:assignment:read', group: '入住管理' },
  '/occupancy/stays': { title: '在住管理', render: renderOccupancyStays, perm: 'occupancy:stay:read', group: '入住管理' },
  '/metering/bindings': { title: '绑表与电价', render: renderMeteringBindings, perm: 'metering:binding:read', group: '抄表' },
  '/metering/readings': { title: '抄表与日结', render: renderMeteringReadings, perm: 'metering:reading:read', group: '抄表' },
  '/accounting/bills': { title: '账单管理', render: renderAccountingBills, perm: 'accounting:bill:read', group: '账务' },
  '/accounting/accounts': { title: '房间账户', render: renderAccountingAccounts, perm: 'accounting:room-account:read', group: '账务' },
  '/accounting/deposits': { title: '押金台账', render: renderAccountingDeposits, perm: 'accounting:deposit:read', group: '账务' },
  '/schedule/tasks': { title: '定时任务', render: renderScheduleTasks, perm: 'schedule:task:read', group: '运维' },
  '/iam/users': { title: '用户管理', render: renderIamUsers, perm: 'iam:user:read', group: '系统管理' },
  '/iam/roles': { title: '角色管理', render: renderIamRoles, perm: 'iam:role:read', group: '系统管理' },
};

function currentPath() {
  const h = location.hash.replace(/^#/, '').split('?')[0];
  return h || '/dashboard';
}

function buildMenu(container) {
  const visible = Object.entries(routes)
    .filter(([, r]) => !r.hideInMenu && (!r.perm || hasPerm(r.perm)));
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
