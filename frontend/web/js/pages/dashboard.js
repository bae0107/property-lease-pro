// 工作台：当前用户信息 + 模块入口

import { payload, permissions, hasPerm } from '../common/auth.js';
import { el } from '../common/ui.js';

const ENTRIES = [
  { path: '/property/areas', title: '区域管理', desc: '区域列表 / 创建 / 改名', perm: 'propertymgr:area:read' },
  { path: '/property/stores', title: '门店管理', desc: '门店列表 / 创建 / 编辑', perm: 'propertymgr:store:read' },
  { path: '/property/buildings', title: '楼栋管理', desc: '楼栋列表 / 创建', perm: 'propertymgr:building:read' },
  { path: '/property/rooms', title: '房间管理', desc: '房间列表 / 创建', perm: 'propertymgr:room:read' },
  { path: '/customer/enterprises', title: '企业管理', desc: '企业列表 / 创建 / 编辑', perm: 'customer:enterprise:read' },
  { path: '/customer/employees', title: '员工管理', desc: '企业员工录入', perm: 'customer:employee:read' },
  { path: '/contract/list', title: '合同列表', desc: '合同创建 / 确认 / 续租 / 退房', perm: 'contract:contract:read' },
  { path: '/occupancy/assignments', title: '入住分配', desc: '分配员工到合同房间', perm: 'occupancy:assignment:read' },
  { path: '/occupancy/stays', title: '在住管理', desc: '入住 / 退宿 / 换宿', perm: 'occupancy:stay:read' },
  { path: '/metering/bindings', title: '绑表与电价', desc: '房间绑表 / 电价维护', perm: 'metering:binding:read' },
  { path: '/metering/readings', title: '抄表与日结', desc: '读数录入 / 日结费用', perm: 'metering:reading:read' },
  { path: '/accounting/bills', title: '账单管理', desc: '账单查询 / 模拟支付 / 作废', perm: 'accounting:bill:read' },
  { path: '/accounting/accounts', title: '房间账户', desc: '子余额 / 流水 / 充值', perm: 'accounting:room-account:read' },
  { path: '/accounting/deposits', title: '押金台账', desc: '企业/个人押金查询', perm: 'accounting:deposit:read' },
  { path: '/schedule/tasks', title: '定时任务', desc: '手动触发 / 日志查询', perm: 'schedule:task:read' },
  { path: '/iam/users', title: '用户管理', desc: '创建 STAFF / 分配角色 / 启停', perm: 'iam:user:read' },
  { path: '/iam/roles', title: '角色管理', desc: '角色与权限分配', perm: 'iam:role:read' },
];

export function renderDashboard(container) {
  const p = payload() || {};
  const perms = permissions();

  container.appendChild(el('div', { class: 'card' }, [
    el('h2', {}, '当前用户'),
    el('div', { class: 'form-row' }, [el('label', {}, '用户名'), el('span', {}, p.sub || '-')]),
    el('div', { class: 'form-row' }, [el('label', {}, '用户ID'), el('span', {}, String(p.userId ?? '-'))]),
    el('div', { class: 'form-row' }, [el('label', {}, '权限数量'), el('span', {}, String(perms.length))]),
    el('div', { class: 'form-row' }, [
      el('label', {}, '权限列表'),
      el('span', { style: 'color:#999;font-size:12px;word-break:break-all' }, perms.join('  ') || '（无）'),
    ]),
  ]));

  const cards = ENTRIES.filter(e => hasPerm(e.perm)).map(e =>
    el('div', { class: 'entry-card', onclick: () => { location.hash = '#' + e.path; } }, [
      el('div', { class: 't' }, e.title),
      el('div', { class: 'd' }, e.desc),
    ]));

  container.appendChild(el('div', { class: 'card' }, [
    el('h2', {}, '功能入口'),
    cards.length ? el('div', { class: 'entry-grid' }, cards)
                 : el('div', { style: 'color:#999' }, '当前账号暂无可用的功能模块'),
  ]));
}
