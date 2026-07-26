// 登录页

import { post } from '../common/api.js';
import { saveLogin } from '../common/auth.js';
import { el } from '../common/ui.js';

export function renderLogin(container) {
  const userInput = el('input', { type: 'text', placeholder: '用户名', style: 'width:100%' });
  const passInput = el('input', { type: 'password', placeholder: '密码', style: 'width:100%' });
  const errBox = el('div', { class: 'error' });

  async function doLogin() {
    errBox.textContent = '';
    const username = userInput.value.trim();
    const password = passInput.value;
    if (!username || !password) { errBox.textContent = '请输入用户名和密码'; return; }
    try {
      const result = await post('/auth/login/password', { username, password }, { silent: true });
      saveLogin(result);
      location.hash = '#/dashboard';
    } catch (e) {
      errBox.textContent = (e.body && e.body.message) || '登录失败，请检查用户名密码';
    }
  }

  passInput.addEventListener('keydown', e => { if (e.key === 'Enter') doLogin(); });

  container.appendChild(el('div', { class: 'login-wrap' },
    el('div', { class: 'login-box' }, [
      el('h1', {}, '物业租赁管理台'),
      el('div', { class: 'form-row' }, userInput),
      el('div', { class: 'form-row' }, passInput),
      errBox,
      el('button', { class: 'primary', onclick: doLogin }, '登 录'),
    ])));
  userInput.focus();
}
