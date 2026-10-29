import React, { useEffect, useRef, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { AlertCircle, Eye, EyeOff, GitBranch, Layers, MessageSquareCode, Network, Route, Table2 } from 'lucide-react';
import { useAuth } from '../auth';
import { errMsg, get } from '../api';
import { Logo, Spinner, ThemeToggle } from '../ui';

/** Loads Google's sign-in script once. */
function loadGoogleScript() {
  return new Promise((resolve, reject) => {
    if (window.google?.accounts?.id) return resolve();
    const existing = document.getElementById('google-gsi');
    if (existing) { existing.addEventListener('load', resolve); existing.addEventListener('error', reject); return; }
    const s = document.createElement('script');
    s.id = 'google-gsi';
    s.src = 'https://accounts.google.com/gsi/client';
    s.async = true;
    s.onload = resolve;
    s.onerror = reject;
    document.head.appendChild(s);
  });
}

/** "Continue with Google". New people are signed up automatically the first time. */
function GoogleButton({ onCredential, onError }) {
  const box = useRef(null);
  const [clientId, setClientId] = useState(null); // null = loading, '' = not configured

  useEffect(() => {
    let cancelled = false;
    get('/auth/config').then((c) => !cancelled && setClientId(c.googleClientId || '')).catch(() => !cancelled && setClientId(''));
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!clientId || !box.current) return undefined;
    let cancelled = false;
    loadGoogleScript().then(() => {
      if (cancelled || !box.current) return;
      window.google.accounts.id.initialize({ client_id: clientId, callback: (r) => onCredential(r.credential) });
      box.current.innerHTML = '';
      window.google.accounts.id.renderButton(box.current, {
        type: 'standard', theme: document.documentElement.dataset.theme === 'dark' ? 'filled_black' : 'outline',
        size: 'large', text: 'continue_with', shape: 'rectangular', width: 360, logo_alignment: 'left',
      });
    }).catch(() => onError('Could not load Google sign-in. Check your internet connection.'));
    return () => { cancelled = true; };
  }, [clientId]); // eslint-disable-line react-hooks/exhaustive-deps

  if (clientId === '') {
    return (
      <button type="button" className="btn google-fallback" onClick={() => onError('Google sign-in is not switched on yet. The site owner needs to add a Google client id (see google.cmd.example).')}>
        <GoogleMark /> Continue with Google
      </button>
    );
  }
  return <div className="google-slot" ref={box}>{clientId === null ? <span className="google-loading"><Spinner /></span> : null}</div>;
}

function GoogleMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden>
      <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.2C12.4 13.7 17.7 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4.1 7.1-10.1 7.1-17.5z" />
      <path fill="#FBBC05" d="M10.5 28.6c-.5-1.5-.8-3-.8-4.6s.3-3.1.8-4.6l-7.9-6.2C.9 16.4 0 20.1 0 24s.9 7.6 2.6 10.8l7.9-6.2z" />
      <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.1 1.4-4.9 2.3-8.4 2.3-6.3 0-11.6-4.2-13.5-9.9l-7.9 6.2C6.5 42.6 14.6 48 24 48z" />
    </svg>
  );
}

const FEATURES = [
  { icon: Layers, title: 'Architecture diagrams', text: 'Controllers, services, repositories and entities laid out as a clear layered map.' },
  { icon: Route, title: 'Data-flow tracing', text: 'Follow any request from the browser through security, code and database.' },
  { icon: Table2, title: 'Database diagrams', text: 'Tables, keys and relationships recovered from your entities and SQL.' },
  { icon: MessageSquareCode, title: 'Ask your code', text: 'Questions answered from the real code, with the exact lines highlighted.' },
];

export default function AuthPage() {
  const { user, login, register, loginWithGoogle } = useAuth();
  const location = useLocation();
  const [mode, setMode] = useState('login');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [form, setForm] = useState({ identifier: '', password: '', fullName: '', username: '', email: '' });

  if (user) return <Navigate to={location.state?.from || '/'} replace />;

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      if (mode === 'login') await login(form.identifier.trim(), form.password);
      else await register({ fullName: form.fullName.trim(), username: form.username.trim(), email: form.email.trim(), password: form.password });
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setBusy(false);
    }
  };

  const googleSignIn = async (credential) => {
    setError('');
    setBusy(true);
    try {
      await loginWithGoogle(credential);
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-shell">
      <aside className="auth-brand">
        <Logo size={34} />
        <div className="auth-hero">
          <h1>Understand any codebase in minutes, not days.</h1>
          <p>Paste a GitHub link or upload a ZIP. CodeTutor reads the project, draws how it works and answers your questions about it.</p>
          <ul className="auth-features">
            {FEATURES.map(({ icon: Icon, title, text }) => (
              <li key={title}>
                <span className="auth-feature-ico"><Icon size={18} /></span>
                <div><strong>{title}</strong><span>{text}</span></div>
              </li>
            ))}
          </ul>
        </div>
        <div className="auth-path" aria-hidden>
          <span className="auth-path-label">A request, traced</span>
          <div className="auth-path-row">
            {[['Browser', '#0891b2'], ['Controller', '#2563eb'], ['Service', '#0d9488'], ['Repository', '#d97706'], ['Table', '#ea580c']].map(([n, c], i) => (
              <React.Fragment key={n}>
                {i > 0 && <i className="auth-arrow" style={{ animationDelay: i * 0.25 + 's' }} />}
                <span className="auth-pill" style={{ '--c': c, animationDelay: i * 0.25 + 's' }}>{n}</span>
              </React.Fragment>
            ))}
          </div>
        </div>
      </aside>

      <main className="auth-main">
        <div className="auth-top"><ThemeToggle /></div>
        <form className="auth-card" onSubmit={submit} noValidate>
          <div className="auth-tabs">
            <button type="button" className={mode === 'login' ? 'on' : ''} onClick={() => { setMode('login'); setError(''); }}>Sign in</button>
            <button type="button" className={mode === 'register' ? 'on' : ''} onClick={() => { setMode('register'); setError(''); }}>Create account</button>
          </div>
          <h2>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2>
          <p className="auth-sub">{mode === 'login' ? 'Sign in to see your own projects.' : 'Every account gets its own private workspace — nobody else can see your projects.'}</p>

          {error && <div className="error-box"><AlertCircle size={16} style={{ flexShrink: 0, marginTop: 2 }} /><span>{error}</span></div>}

          <GoogleButton onCredential={googleSignIn} onError={setError} />
          <div className="auth-or"><span>or use your email</span></div>

          {mode === 'register' && (
            <>
              <div className="field"><label>Full name</label><input className="input" value={form.fullName} onChange={set('fullName')} placeholder="Ada Lovelace" autoComplete="name" /></div>
              <div className="field-row">
                <div className="field"><label>Username</label><input className="input" value={form.username} onChange={set('username')} placeholder="ada" autoComplete="username" /></div>
                <div className="field"><label>Email</label><input className="input" type="email" value={form.email} onChange={set('email')} placeholder="ada@example.com" autoComplete="email" /></div>
              </div>
            </>
          )}
          {mode === 'login' && (
            <div className="field"><label>Email or username</label><input className="input" value={form.identifier} onChange={set('identifier')} placeholder="you@example.com" autoComplete="username" autoFocus /></div>
          )}
          <div className="field">
            <label>Password</label>
            <div className="pw-wrap">
              <input className="input" type={showPw ? 'text' : 'password'} value={form.password} onChange={set('password')}
                placeholder={mode === 'register' ? 'At least 8 characters' : 'Your password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
              <button type="button" className="icon-btn pw-eye" onClick={() => setShowPw((s) => !s)} tabIndex={-1}>{showPw ? <EyeOff size={16} /> : <Eye size={16} />}</button>
            </div>
          </div>

          <button className="btn btn-primary btn-lg" disabled={busy} type="submit">
            {busy ? <Spinner /> : null}{mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
          <p className="auth-foot"><GitBranch size={13} /> Your code and chats are private to your account.</p>
        </form>
      </main>
    </div>
  );
}
