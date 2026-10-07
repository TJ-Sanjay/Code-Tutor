import React, { useState } from 'react';
import { GitBranch, FileArchive, CheckCircle2, AlertCircle } from 'lucide-react';

export default function ProjectImport({ token, onProjectAdded }) {
  const [ghName, setGhName] = useState('Spring PetClinic');
  const [ghUrl, setGhUrl] = useState('https://github.com/spring-projects/spring-petclinic');
  const [ghDesc, setGhDesc] = useState('Sample Spring PetClinic application for analysis');
  const [ghLoading, setGhLoading] = useState(false);
  const [ghMsg, setGhMsg] = useState(null);

  const [zipName, setZipName] = useState('');
  const [zipDesc, setZipDesc] = useState('');
  const [zipFile, setZipFile] = useState(null);
  const [zipLoading, setZipLoading] = useState(false);
  const [zipMsg, setZipMsg] = useState(null);

  const handleGithubSubmit = async (e) => {
    e.preventDefault();
    setGhLoading(true);
    setGhMsg(null);
    try {
      const res = await fetch('/api/projects/github', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ name: ghName, description: ghDesc, githubUrl: ghUrl })
      });
      const data = await res.json();
      if (data.success) {
        setGhMsg({ type: 'success', text: 'GitHub repository validated and project created!' });
        onProjectAdded();
      } else {
        setGhMsg({ type: 'error', text: data.message || 'Import failed' });
      }
    } catch (err) {
      setGhMsg({ type: 'error', text: 'Connection error' });
    } finally {
      setGhLoading(false);
    }
  };

  const handleZipSubmit = async (e) => {
    e.preventDefault();
    if (!zipFile) {
      setZipMsg({ type: 'error', text: 'Please select a .zip file' });
      return;
    }
    setZipLoading(true);
    setZipMsg(null);

    const formData = new FormData();
    formData.append('name', zipName);
    formData.append('description', zipDesc);
    formData.append('file', zipFile);

    try {
      const res = await fetch('/api/projects/upload', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: formData
      });
      const data = await res.json();
      if (data.success) {
        setZipMsg({ type: 'success', text: 'ZIP archive validated and uploaded successfully!' });
        onProjectAdded();
        setZipName('');
        setZipFile(null);
      } else {
        setZipMsg({ type: 'error', text: data.message || 'Upload failed' });
      }
    } catch (err) {
      setZipMsg({ type: 'error', text: 'Upload failed' });
    } finally {
      setZipLoading(false);
    }
  };

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
      gap: '1.5rem',
      marginBottom: '2.5rem'
    }}>
      {/* GitHub Importer */}
      <div className="glass-card">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.25rem' }}>
          <div style={{
            width: '40px', height: '40px', borderRadius: '10px',
            background: 'rgba(99, 102, 241, 0.15)', color: 'var(--primary)',
            display: 'flex', alignItems: 'center', justifyContent: 'center'
          }}>
            <GitBranch size={22} />
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '1.1rem' }}>Import GitHub Repository</div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Enter a public or validated GitHub URL to parse and analyze structure.</div>
          </div>
        </div>

        {ghMsg && (
          <div style={{
            background: ghMsg.type === 'success' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
            border: `1px solid ${ghMsg.type === 'success' ? 'rgba(16, 185, 129, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`,
            color: ghMsg.type === 'success' ? '#34d399' : '#f87171',
            padding: '0.65rem 0.85rem', borderRadius: '6px', fontSize: '0.8rem', marginBottom: '1rem',
            display: 'flex', alignItems: 'center', gap: '0.4rem'
          }}>
            {ghMsg.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
            {ghMsg.text}
          </div>
        )}

        <form onSubmit={handleGithubSubmit}>
          <div className="form-group">
            <label className="form-label">Repository Name</label>
            <input type="text" className="form-input" required value={ghName} onChange={e => setGhName(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">GitHub Repository URL</label>
            <input type="url" className="form-input" required value={ghUrl} onChange={e => setGhUrl(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Description</label>
            <input type="text" className="form-input" value={ghDesc} onChange={e => setGhDesc(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={ghLoading}>
            {ghLoading ? 'Validating Repository...' : 'Import Repository'}
          </button>
        </form>
      </div>

      {/* ZIP Upload Card */}
      <div className="glass-card">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.25rem' }}>
          <div style={{
            width: '40px', height: '40px', borderRadius: '10px',
            background: 'rgba(236, 72, 153, 0.15)', color: 'var(--pink)',
            display: 'flex', alignItems: 'center', justifyContent: 'center'
          }}>
            <FileArchive size={22} />
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '1.1rem' }}>Upload Project ZIP Archive</div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Upload a standalone .zip file containing full codebase source code.</div>
          </div>
        </div>

        {zipMsg && (
          <div style={{
            background: zipMsg.type === 'success' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
            border: `1px solid ${zipMsg.type === 'success' ? 'rgba(16, 185, 129, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`,
            color: zipMsg.type === 'success' ? '#34d399' : '#f87171',
            padding: '0.65rem 0.85rem', borderRadius: '6px', fontSize: '0.8rem', marginBottom: '1rem',
            display: 'flex', alignItems: 'center', gap: '0.4rem'
          }}>
            {zipMsg.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
            {zipMsg.text}
          </div>
        )}

        <form onSubmit={handleZipSubmit}>
          <div className="form-group">
            <label className="form-label">Project Name</label>
            <input type="text" className="form-input" required placeholder="My Local Project" value={zipName} onChange={e => setZipName(e.target.value)} />
          </div>
          <div className="form-group">
            <label className="form-label">Select ZIP File</label>
            <input type="file" accept=".zip" className="form-input" onChange={e => setZipFile(e.target.files[0])} />
          </div>
          <button type="submit" className="btn btn-outline" style={{ width: '100%', marginTop: '1.65rem' }} disabled={zipLoading}>
            {zipLoading ? 'Uploading Archive...' : 'Upload & Extract ZIP'}
          </button>
        </form>
      </div>
    </div>
  );
}
