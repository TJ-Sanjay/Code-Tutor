import React, { useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { AlertCircle, Eye, EyeOff, GitBranch, Layers, MessageSquareCode, Network, Route, Table2 } from 'lucide-react';
import { useAuth } from '../auth';
import { errMsg } from '../api';
import { Logo, Spinner, ThemeToggle } from '../ui';

const FEATURES = [
  { icon: Layers, title: 'Architecture diagrams', text: 'Controllers, services, repositories and entities laid out as a clear layered map.' },
  { icon: Route, title: 'Data-flow tracing', text: 'Follow any request from the browser through security, code and database.' },
  { icon: Table2, title: 'Database diagrams', text: 'Tables, keys and relationships recovered from your entities and SQL.' },
  { icon: MessageSquareCode, title: 'Ask your code', text: 'Questions answered from the real code, with the exact lines highlighted.' },
];

export default function AuthPage() {
  const { user, login, register } = useAuth();
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
        <div className="auth-art" aria-hidden>
          <span className="n n1">Controller</span><span className="n n2">Service</span><span className="n n3">Repository</span><span className="n n4">Entity</span>
          <svg viewBox="0 0 300 120" preserveAspectRatio="none"><path d="M60 22 C100 22 90 60 130 60 S170 60 200 98" /><path d="M150 22 C190 22 190 60 230 60" /><path d="M60 98 C110 98 110 60 150 60" /></svg>
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
