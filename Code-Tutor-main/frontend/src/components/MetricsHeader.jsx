import React from 'react';
import { FolderGit2, HardDrive, Cpu, ShieldCheck } from 'lucide-react';

export default function MetricsHeader({ projectsCount, storageUsed, storageLimit }) {
  const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
      gap: '1.25rem',
      marginBottom: '2rem'
    }}>
      <div className="glass-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>
            ACTIVE PROJECTS
          </span>
          <FolderGit2 size={18} color="var(--primary)" />
        </div>
        <div style={{ fontFamily: "'Outfit', sans-serif", fontSize: '1.8rem', fontWeight: 700 }}>
          {projectsCount} / 10
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
          User repositories imported
        </div>
      </div>

      <div className="glass-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>
            STORAGE USED
          </span>
          <HardDrive size={18} color="var(--accent)" />
        </div>
        <div style={{ fontFamily: "'Outfit', sans-serif", fontSize: '1.8rem', fontWeight: 700 }}>
          {formatBytes(storageUsed)}
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
          Workspace disk usage
        </div>
      </div>

      <div className="glass-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>
            STORAGE LIMIT
          </span>
          <HardDrive size={18} color="var(--pink)" />
        </div>
        <div style={{ fontFamily: "'Outfit', sans-serif", fontSize: '1.8rem', fontWeight: 700 }}>
          {formatBytes(storageLimit || 5368709120)}
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
          Maximum quota allocated
        </div>
      </div>

      <div className="glass-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase' }}>
            PIPELINE STATUS
          </span>
          <ShieldCheck size={18} color="var(--green)" />
        </div>
        <div style={{ fontFamily: "'Outfit', sans-serif", fontSize: '1.8rem', fontWeight: 700, color: 'var(--green)' }}>
          Active
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
          40 Modules Configured
        </div>
      </div>
    </div>
  );
}
