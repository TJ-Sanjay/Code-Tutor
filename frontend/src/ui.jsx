import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { AlertCircle, CheckCircle2, Moon, Sun, X } from 'lucide-react';

/* ───────── theme ───────── */
export function useTheme() {
  const [theme, setTheme] = useState(() => document.documentElement.getAttribute('data-theme') || 'dark');
  const toggle = useCallback(() => {
    const next = theme === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    try { localStorage.setItem('ct-theme', next); } catch { /* private mode */ }
    setTheme(next);
    window.dispatchEvent(new CustomEvent('ct-theme', { detail: next }));
  }, [theme]);
  return [theme, toggle];
}

export function ThemeToggle() {
  const [theme, toggle] = useTheme();
  return (
    <button className="icon-btn" onClick={toggle} title={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}>
      {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
    </button>
  );
}

/* ───────── toast ───────── */
const ToastContext = createContext(() => {});
export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const push = useCallback((message, tone = 'good') => {
    const id = Math.random().toString(36).slice(2);
    setToasts((t) => [...t, { id, message, tone }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4200);
  }, []);
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toast-stack">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.tone}`}>
            {t.tone === 'bad' ? <AlertCircle size={16} color="var(--bad)" /> : <CheckCircle2 size={16} color="var(--good)" />}
            <span>{t.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/* ───────── modal ───────── */
export function Modal({ title, children, onClose, actions, width }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <div className="modal" style={width ? { maxWidth: width } : undefined}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <h3>{title}</h3>
          {onClose && <button className="icon-btn" onClick={onClose} style={{ marginTop: -6, marginRight: -8 }}><X size={16} /></button>}
        </div>
        {children}
        {actions && <div className="modal-actions">{actions}</div>}
      </div>
    </div>
  );
}

/* ───────── small pieces ───────── */
/** Number that counts up from 0 when it appears (skipped for reduced-motion users). */
export function CountUp({ value, duration = 800 }) {
  const [n, setN] = useState(0);
  useEffect(() => {
    if (value == null) return undefined;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches || value < 2) { setN(value); return undefined; }
    let raf;
    const t0 = performance.now();
    const tick = (t) => {
      const p = Math.min(1, (t - t0) / duration);
      setN(Math.round(value * (1 - Math.pow(1 - p, 3))));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value, duration]);
  return <>{new Intl.NumberFormat('en').format(n)}</>;
}

export const Spinner = ({ lg }) => <span className={`spinner ${lg ? 'lg' : ''}`} />;

export function Chip({ color, children, onClick, title, style }) {
  return (
    <span className={`chip ${onClick ? 'chip-btn' : ''}`} style={{ '--c': color, ...style }} onClick={onClick} title={title}>
      {children}
    </span>
  );
}

export function LayerChip({ layer, label, onClick }) {
  return <Chip color={layerColorOf(layer)} onClick={onClick}><span className="dot" />{label}</Chip>;
}

import { layerColor } from './lib/colors';
const layerColorOf = layerColor;

export function Empty({ icon, title, children, action }) {
  return (
    <div className="empty">
      {icon && <div className="ico">{icon}</div>}
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function Markdown({ children }) {
  return (
    <div className="md">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children || ''}</ReactMarkdown>
    </div>
  );
}

export function Logo({ size = 30, text = true }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10, fontWeight: 700, fontSize: size * 0.58, letterSpacing: '-0.01em' }}>
      <svg width={size} height={size} viewBox="0 0 32 32">
        <rect width="32" height="32" rx="9" fill="var(--accent)" />
        <path d="M12 11l-5 5 5 5M20 11l5 5-5 5M17.5 9l-3 14" stroke="#fff" strokeWidth="2.2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {text && <span>Code<span style={{ color: 'var(--accent)' }}>Tutor</span></span>}
    </span>
  );
}
