import React from 'react';
import { FolderGit2, Trash2, Code2, Network, ExternalLink } from 'lucide-react';

export default function ProjectList({ projects, token, onDeleteProject, onViewCode, onViewDiagram }) {
  if (!projects || projects.length === 0) {
    return (
      <div className="glass-card" style={{ textAlign: 'center', padding: '3.5rem 2rem', color: 'var(--text-muted)' }}>
        <FolderGit2 size={48} style={{ opacity: 0.4, marginBottom: '0.8rem' }} />
        <div style={{ fontSize: '1.1rem', fontWeight: 600, color: 'white' }}>No Projects Imported Yet</div>
        <div style={{ fontSize: '0.85rem', marginTop: '0.3rem' }}>
          Enter a GitHub URL or upload a ZIP file above to create your first code understanding workspace.
        </div>
      </div>
    );
  }

  return (
    <div className="glass-card" style={{ padding: '1rem', overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.875rem' }}>
        <thead>
          <tr style={{ borderBottom: '1px solid var(--border-color)', color: 'var(--text-muted)' }}>
            <th style={{ padding: '0.75rem 1rem' }}>ID</th>
            <th style={{ padding: '0.75rem 1rem' }}>PROJECT NAME</th>
            <th style={{ padding: '0.75rem 1rem' }}>SOURCE TYPE</th>
            <th style={{ padding: '0.75rem 1rem' }}>REPOSITORY / TARGET</th>
            <th style={{ padding: '0.75rem 1rem' }}>STATUS</th>
            <th style={{ padding: '0.75rem 1rem' }}>PROGRESS</th>
            <th style={{ padding: '0.75rem 1rem' }}>CREATED AT</th>
            <th style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>ACTIONS</th>
          </tr>
        </thead>
        <tbody>
          {projects.map((p) => (
            <tr key={p.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
              <td style={{ padding: '1rem', fontWeight: 700, color: 'var(--text-muted)' }}>#{p.id}</td>
              <td style={{ padding: '1rem', fontWeight: 600, color: 'white' }}>{p.name}</td>
              <td style={{ padding: '1rem' }}>
                <span style={{
                  padding: '0.2rem 0.5rem', borderRadius: '4px', fontSize: '0.7rem', fontWeight: 700,
                  background: p.sourceType === 'GITHUB' ? 'rgba(99, 102, 241, 0.2)' : 'rgba(236, 72, 153, 0.2)',
                  color: p.sourceType === 'GITHUB' ? '#818cf8' : '#f472b6',
                  border: `1px solid ${p.sourceType === 'GITHUB' ? 'rgba(99, 102, 241, 0.3)' : 'rgba(236, 72, 153, 0.3)'}`
                }}>
                  {p.sourceType}
                </span>
              </td>
              <td style={{ padding: '1rem', fontFamily: "'Fira Code', monospace", fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                {p.githubUrl ? (
                  <a href={p.githubUrl} target="_blank" rel="noreferrer" style={{ color: '#818cf8', textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: '0.25rem' }}>
                    {p.repositoryName || p.githubUrl} <ExternalLink size={12} />
                  </a>
                ) : (
                  p.zipFileName || 'Uploaded ZIP archive'
                )}
              </td>
              <td style={{ padding: '1rem' }}>
                <span style={{
                  padding: '0.25rem 0.6rem', borderRadius: '12px', fontSize: '0.75rem', fontWeight: 600,
                  background: p.status === 'COMPLETED' ? 'rgba(16, 185, 129, 0.15)' :
                              p.status === 'FAILED' ? 'rgba(239, 68, 68, 0.15)' :
                              p.status === 'CLONING' || p.status === 'EXTRACTING' ? 'rgba(59, 130, 246, 0.15)' :
                              'rgba(245, 158, 11, 0.15)',
                  color: p.status === 'COMPLETED' ? '#34d399' :
                         p.status === 'FAILED' ? '#f87171' :
                         p.status === 'CLONING' || p.status === 'EXTRACTING' ? '#60a5fa' :
                         '#fbbf24',
                  border: `1px solid ${
                    p.status === 'COMPLETED' ? 'rgba(16, 185, 129, 0.3)' :
                    p.status === 'FAILED' ? 'rgba(239, 68, 68, 0.3)' :
                    p.status === 'CLONING' || p.status === 'EXTRACTING' ? 'rgba(59, 130, 246, 0.3)' :
                    'rgba(245, 158, 11, 0.3)'
                  }`
                }} title={p.statusMessage}>
                  {p.status}
                </span>
              </td>
              <td style={{ padding: '1rem', fontWeight: 600 }}>{p.progressPercent}%</td>
              <td style={{ padding: '1rem', color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                {new Date(p.createdAt).toLocaleString()}
              </td>
              <td style={{ padding: '1rem', textAlign: 'right' }}>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                  <button className="btn btn-outline" style={{ padding: '0.4rem 0.75rem', fontSize: '0.75rem' }} onClick={() => onViewCode(p)}>
                    <Code2 size={14} /> Code Viewer
                  </button>
                  <button className="btn btn-outline" style={{ padding: '0.4rem 0.75rem', fontSize: '0.75rem' }} onClick={() => onViewDiagram(p)}>
                    <Network size={14} /> Architecture Graph
                  </button>
                  <button className="btn btn-danger" style={{ padding: '0.4rem 0.6rem' }} onClick={() => onDeleteProject(p.id)}>
                    <Trash2 size={14} />
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
