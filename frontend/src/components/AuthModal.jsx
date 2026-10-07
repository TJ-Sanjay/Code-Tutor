import React, { useState } from 'react';
import { X, Lock, Mail, User, Shield } from 'lucide-react';

export default function AuthModal({ isOpen, onClose, onLoginSuccess }) {
  const [tab, setTab] = useState('login');
  const [loginId, setLoginId] = useState('alex@example.com');
  const [loginPwd, setLoginPwd] = useState('Password123!');
  
  const [regName, setRegName] = useState('');
  const [regUsername, setRegUsername] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPwd, setRegPwd] = useState('');
  const [error, setError] = useState('');

  if (!isOpen) return null;

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ identifier: loginId, password: loginPwd })
      });
      const data = await res.json();
      if (data.success) {
        localStorage.setItem('token', data.data.accessToken);
        onLoginSuccess(data.data);
      } else {
        setError(data.message || 'Authentication failed');
      }
    } catch (err) {
      setError('Connection to backend failed');
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fullName: regName,
          username: regUsername,
          email: regEmail,
          password: regPwd
        })
      });
      const data = await res.json();
      if (data.success) {
        localStorage.setItem('token', data.data.accessToken);
        onLoginSuccess(data.data);
      } else {
        setError(data.message || 'Registration failed');
      }
    } catch (err) {
      setError('Registration request error');
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ position: 'relative' }}>
        <button 
          onClick={onClose}
          style={{
            position: 'absolute', top: '1.25rem', right: '1.25rem',
            background: 'transparent', border: 'none', color: 'var(--text-muted)',
            cursor: 'pointer'
          }}
        >
          <X size={20} />
        </button>

        <div style={{ display: 'flex', gap: '0.5rem', background: 'rgba(0,0,0,0.3)', padding: '0.25rem', borderRadius: '8px', marginBottom: '1.5rem' }}>
          <button 
            className={`btn ${tab === 'login' ? 'btn-primary' : ''}`}
            style={{ flex: 1, background: tab === 'login' ? undefined : 'transparent' }}
            onClick={() => { setTab('login'); setError(''); }}
          >
            Sign In
          </button>
          <button 
            className={`btn ${tab === 'register' ? 'btn-primary' : ''}`}
            style={{ flex: 1, background: tab === 'register' ? undefined : 'transparent' }}
            onClick={() => { setTab('register'); setError(''); }}
          >
            Create Account
          </button>
        </div>

        {error && (
          <div style={{
            background: 'rgba(239, 68, 68, 0.15)', border: '1px solid rgba(239, 68, 68, 0.3)',
            color: '#f87171', padding: '0.75rem', borderRadius: '6px', fontSize: '0.85rem', marginBottom: '1rem'
          }}>
            ⚠️ {error}
          </div>
        )}

        {tab === 'login' ? (
          <form onSubmit={handleLogin}>
            <div className="form-group">
              <label className="form-label">EMAIL OR USERNAME</label>
              <input 
                type="text" className="form-input" required 
                value={loginId} onChange={e => setLoginId(e.target.value)} 
              />
            </div>
            <div className="form-group">
              <label className="form-label">PASSWORD</label>
              <input 
                type="password" className="form-input" required 
                value={loginPwd} onChange={e => setLoginPwd(e.target.value)} 
              />
            </div>
            <button type="submit" className="btn btn-primary" style={{ width: '100%', marginTop: '0.5rem' }}>
              Sign In
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister}>
            <div className="form-group">
              <label className="form-label">FULL NAME</label>
              <input 
                type="text" className="form-input" required placeholder="Alex Dev"
                value={regName} onChange={e => setRegName(e.target.value)} 
              />
            </div>
            <div className="form-group">
              <label className="form-label">USERNAME</label>
              <input 
                type="text" className="form-input" required placeholder="alexdev"
                value={regUsername} onChange={e => setRegUsername(e.target.value)} 
              />
            </div>
            <div className="form-group">
              <label className="form-label">EMAIL ADDRESS</label>
              <input 
                type="email" className="form-input" required placeholder="alex@example.com"
                value={regEmail} onChange={e => setRegEmail(e.target.value)} 
              />
            </div>
            <div className="form-group">
              <label className="form-label">PASSWORD</label>
              <input 
                type="password" className="form-input" required minLength={8} placeholder="••••••••"
                value={regPwd} onChange={e => setRegPwd(e.target.value)} 
              />
            </div>
            <button type="submit" className="btn btn-primary" style={{ width: '100%', marginTop: '0.5rem' }}>
              Create Account
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
