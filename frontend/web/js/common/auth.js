// 认证：token 存储 + JWT payload 解析 + 权限判断

const KEY_TOKEN = 'plp_access_token';
const KEY_REFRESH = 'plp_refresh_token';
const KEY_USER = 'plp_user';

export function saveLogin(loginResult) {
  localStorage.setItem(KEY_TOKEN, loginResult.accessToken);
  localStorage.setItem(KEY_REFRESH, loginResult.refreshToken || '');
  localStorage.setItem(KEY_USER, JSON.stringify({
    userId: loginResult.userId,
    userType: loginResult.userType,
  }));
}

export function clearLogin() {
  localStorage.removeItem(KEY_TOKEN);
  localStorage.removeItem(KEY_REFRESH);
  localStorage.removeItem(KEY_USER);
}

export function getToken() {
  return localStorage.getItem(KEY_TOKEN);
}

export function getRefreshToken() {
  return localStorage.getItem(KEY_REFRESH);
}

export function isLoggedIn() {
  return !!getToken();
}

/** 解析 User JWT payload：{ sub, userId, permissions[], authVersion, exp } */
export function payload() {
  const token = getToken();
  if (!token) return null;
  try {
    const part = token.split('.')[1];
    const json = decodeURIComponent(atob(part.replace(/-/g, '+').replace(/_/g, '/'))
      .split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''));
    return JSON.parse(json);
  } catch (e) {
    return null;
  }
}

export function currentUsername() {
  const p = payload();
  return p ? (p.sub || '') : '';
}

export function permissions() {
  const p = payload();
  return (p && p.permissions) || [];
}

export function hasPerm(code) {
  return permissions().includes(code);
}

/** 拥有任一权限即视为可见（用于菜单分组） */
export function hasAnyPerm(...codes) {
  const perms = permissions();
  return codes.some(c => perms.includes(c));
}
