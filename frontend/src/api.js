import axios from 'axios';

/**
 * Axios instance for the Spring Boot API. In development Vite proxies /api → http://localhost:8080,
 * so the browser only ever talks to one origin.
 */
const api = axios.create({ baseURL: '/api' });

export const TOKEN_KEY = 'ct-token';

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const url = err.config?.url || '';
    const isAuthCall = url.includes('/auth/login') || url.includes('/auth/register') || url.includes('/auth/google');
    if (err.response?.status === 401 && !isAuthCall) {
      // token expired / user no longer exists → the auth context signs the user out
      window.dispatchEvent(new Event('auth:expired'));
    }
    return Promise.reject(err);
  }
);

/** Unwraps the { success, message, data } envelope. */
export const get = async (url, params) => (await api.get(url, { params })).data.data;
export const post = async (url, body, config) => (await api.post(url, body, config)).data.data;
export const del = async (url) => (await api.delete(url)).data.data;

export function errMsg(e) {
  if (e?.response?.data?.message) return e.response.data.message;
  if (e?.code === 'ERR_NETWORK') return 'Cannot reach the server. Is the backend running on port 8080?';
  return e?.message || 'Something went wrong';
}

export default api;
