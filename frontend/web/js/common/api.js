// fetch 封装：自动带 Bearer、统一错误处理

import { API_BASE } from './config.js';
import { getToken, clearLogin } from './auth.js';
import { toast } from './ui.js';

/**
 * 发起 API 请求。
 * @param {string} method HTTP 方法
 * @param {string} path 以 / 开头的业务路径（不含 /api/main-service 前缀）
 * @param {object|null} body JSON 请求体
 * @param {object} opts { silent: true 时不弹错误 toast }
 * @returns {Promise<any>} 解析后的 JSON；出错抛异常（已 toast 提示）
 */
export async function api(method, path, body = null, opts = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers['Authorization'] = 'Bearer ' + token;

  let resp;
  try {
    resp = await fetch(API_BASE + path, {
      method,
      headers,
      body: body != null ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    if (!opts.silent) toast('网络错误：无法连接服务器', 'error');
    throw e;
  }

  if (resp.status === 401) {
    // token 缺失/过期：清登录态并跳登录页
    clearLogin();
    if (location.hash !== '#/login') {
      toast('登录已失效，请重新登录', 'error');
      location.hash = '#/login';
    }
    throw new Error('Unauthorized');
  }

  let data = null;
  const text = await resp.text();
  if (text) {
    try { data = JSON.parse(text); } catch (e) { data = null; }
  }

  if (!resp.ok) {
    const msg = (data && data.message) || ('请求失败 HTTP ' + resp.status);
    const trace = data && data.traceId;
    if (!opts.silent) {
      toast(resp.status === 403 ? '无权限执行该操作' : msg, 'error', trace);
    }
    const err = new Error(msg);
    err.status = resp.status;
    err.body = data;
    throw err;
  }

  return data;
}

export const get = (path, opts) => api('GET', path, null, opts);
export const post = (path, body, opts) => api('POST', path, body, opts);
export const put = (path, body, opts) => api('PUT', path, body, opts);
