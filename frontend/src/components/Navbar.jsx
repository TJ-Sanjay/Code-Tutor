import React from 'react';
import { Cpu, LogOut, User as UserIcon, ShieldCheck } from 'lucide-react';

export default function Navbar({ user, onOpenAuth, onLogout }) {
  return (
    <header style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      padding: '1rem 2rem',
      borderBottom: '1px solid var(--border-color)',
      backdropFilter: 'blur(20px)',
      background: 'rgba(9, 13, 22, 0.85)',
      position: 'sticky',
      top: 0,
      zIndex: 100
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
        <div style={{
          width: '42px',
          height: '42px',
          borderRadius: '12px',
          background: 'linear-gradient(135deg, var(--primary), var(--accent))',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'white',
          boxShadow: '0 0 20px rgba(99, 102, 241, 0.4)'
        }}>
          <Cpu size={24} />
        </div>
        <div>
          <div style={{
            fontFamily: "'Outfit', sans-serif",
            fontSize: '1.45rem',
            fontWeight: 800,
            background: 'linear-gradient(to right, #fff, #a5b4fc)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent'
          }}>
            Code Tutor AI
          </div>
        </div>
      </div>

      <div style={{
        fontSize: '0.75rem',
        padding: '0.3rem 0.75rem',
        borderRadius: '20px',
        background: 'rgba(16, 185, 129, 0.15)',
        border: '1px solid rgba(16, 185, 129, 0.3)',
        color: '#34d399',
        fontWeight: 600,
        display: 'flex',
        alignItems: 'center',
        gap: '0.4rem'
      }}>
        <ShieldCheck size={14} />
        Backend API Active (Port 8080)
      </div>

      <div>
        {user ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>{user.fullName || user.username}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{user.email}</div>
            </div>
            <button className="btn btn-danger" onClick={onLogout}>
              <LogOut size={16} /> Sign Out
            </button>
          </div>
        ) : (
          <button className="btn btn-primary" onClick={onOpenAuth}>
            <UserIcon size={16} /> Sign In / Register
          </button>
        )}
      </div>
    </header>
  );
}
