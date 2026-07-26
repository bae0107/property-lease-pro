// 工作台：当前用户信息 + 模块入口

import { payload, permissions, hasPerm } from '../common/auth.js';
import { el } from '../common/ui.js';

const ENTRIES = [
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
