import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle, ArrowRight, Boxes, FileArchive, FolderGit2, GitBranch, Github, LogOut, MoreVertical, RefreshCw, ShieldCheck, Trash2, UploadCloud, Zap,
} from 'lucide-react';
import { useAuth } from '../auth';
import api, { del, errMsg, get, post } from '../api';
import { Chip, Empty, Logo, Modal, Spinner, ThemeToggle, useToast } from '../ui';
import { formatBytes, formatNumber, initials, repoNameFromUrl, timeAgo } from '../lib/format';
import { langColor } from '../lib/colors';

const PROCESSING = new Set(['PENDING', 'QUEUED', 'CLONING', 'EXTRACTING', 'SCANNING', 'PARSING', 'ANALYZING']);

export default function Dashboard() {
  const { user, logout, refresh } = useAuth();
  const toast = useToast();
  const [projects, setProjects] = useState(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirm, setConfirm] = useState(null);

  const load = useCallback(async () => {
    try {
      setProjects(await get('/projects'));
    } catch (e) {
      toast(errMsg(e), 'bad');
      setProjects((p) => p || []);
    }
  }, [toast]);

  useEffect(() => { load(); }, [load]);

  // keep polling while anything is being analysed
  const busy = projects?.some((p) => PROCESSING.has(p.status));
  useEffect(() => {
    if (!busy) { refresh(); return undefined; }
    const t = setInterval(load, 1800);
    return () => clearInterval(t);
  }, [busy, load, refresh]);

  const remove = async () => {
    const p = confirm;
    setConfirm(null);
    try {
      await del(`/projects/${p.id}`);
      toast(`“${p.name}” deleted`);
      load();
      refresh();
    } catch (e) {
      toast(errMsg(e), 'bad');
    }
  };

  const reanalyze = async (p) => {
    try {
      await post(`/projects/${p.id}/reanalyze`);
      load();
    } catch (e) {
      toast(errMsg(e), 'bad');
    }
  };

  const used = user?.storageUsedBytes || 0;
  const limit = user?.storageLimitBytes || 1;

  return (
    <>
      <header className="topbar">
        <Logo />
        <div className="topbar-right">
          <ThemeToggle />
          <button className="user-btn" onClick={() => setMenuOpen((o) => !o)}>
            <span className="avatar">{initials(user?.fullName)}</span>
            <span className="user-name">{user?.fullName}</span>
          </button>
        </div>
        {menuOpen && (
          <>
            <div style={{ position: 'fixed', inset: 0, zIndex: 35 }} onClick={() => setMenuOpen(false)} />
            <div className="user-menu">
              <div className="who">
                <span className="avatar" style={{ width: 38, height: 38, fontSize: 14 }}>{initials(user?.fullName)}</span>
                <div><b>{user?.fullName}</b><span>{user?.email}</span></div>
              </div>
              <div className="storage-row"><span>Storage</span><span>{formatBytes(used)} of {formatBytes(limit)}</span></div>
              <div className="progress"><span style={{ width: `${Math.min(100, (used / limit) * 100)}%` }} /></div>
              <div className="divider" />
              <button className="btn btn-ghost" style={{ width: '100%', justifyContent: 'flex-start' }} onClick={logout}><LogOut size={16} /> Sign out</button>
            </div>
          </>
        )}
      </header>

      <main className="dash">
        <div className="dash-hero">
          <h1>Hi {user?.fullName?.split(' ')[0]}, what do you want to understand today?</h1>
          <p>Import a Spring Boot, Django or React project and get diagrams, data flows and answers — only you can see your projects.</p>
        </div>

        <ImportCard onCreated={() => { load(); refresh(); }} />

        <div className="section-head">
          <h2>Your projects {projects ? <span style={{ color: 'var(--text-faint)', fontWeight: 500 }}>({projects.length})</span> : null}</h2>
        </div>

        {projects === null ? (
          <div className="proj-grid">{[1, 2, 3].map((i) => <div key={i} className="skeleton" style={{ height: 200 }} />)}</div>
        ) : projects.length === 0 ? (
          <div className="card"><Empty icon={<FolderGit2 size={26} />} title="No projects yet">Paste a GitHub link above (try <code>https://github.com/spring-projects/spring-petclinic</code>) or upload a ZIP of your own project.</Empty></div>
        ) : (
          <div className="proj-grid stagger">
            {projects.map((p) => <ProjectCard key={p.id} p={p} onDelete={() => setConfirm(p)} onReanalyze={() => reanalyze(p)} />)}
          </div>
        )}
      </main>

      {confirm && (
        <Modal title="Delete this project?" onClose={() => setConfirm(null)} actions={<>
          <button className="btn" onClick={() => setConfirm(null)}>Cancel</button>
          <button className="btn btn-danger" onClick={remove}><Trash2 size={15} /> Delete project</button>
        </>}>
          <p>“{confirm.name}”, its analysis and chat history will be permanently removed.</p>
        </Modal>
      )}
    </>
  );
}

/* ───────────────────────── import ───────────────────────── */
function uploadError(err) {
  const s = err.response?.status;
  if (s === 413) return 'The file is larger than the 100 MB limit. Tip: leave out node_modules, target and build folders before zipping.';
  if (!err.response) return err.code === 'ERR_NETWORK' ? 'The upload could not reach the server. Make sure the backend is running on port 8080, and that the file is under 100 MB.' : err.message;
  return errMsg(err);
}

function ImportCard({ onCreated }) {
  const toast = useToast();
  const [tab, setTab] = useState('github');
  const [url, setUrl] = useState('');
  const [name, setName] = useState('');
  const [file, setFile] = useState(null);
  const [over, setOver] = useState(false);
  const [busy, setBusy] = useState(false);
  const [pct, setPct] = useState(0);
  const [error, setError] = useState('');
  const fileRef = useRef(null);

  const pickFile = (f) => {
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.zip')) { setError('Please choose a .zip file.'); return; }
    setError('');
    setFile(f);
    if (!name) setName(f.name.replace(/\.zip$/i, ''));
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      if (tab === 'github') {
        const repo = repoNameFromUrl(url);
        if (!repo) throw new Error('Enter a GitHub URL like https://github.com/owner/repo');
        await post('/projects/github', { name: (name || repo).trim(), description: '', githubUrl: url.trim() });
      } else {
        if (!file) throw new Error('Choose a ZIP file first.');
        const fd = new FormData();
        fd.append('name', (name || file.name.replace(/\.zip$/i, '')).trim());
        fd.append('file', file);
        setPct(0);
        await api.post('/projects/upload', fd, { timeout: 0, onUploadProgress: (e) => setPct(Math.round((e.loaded * 100) / (e.total || file.size || 1))) });
      }
      toast('Import started — analysis runs in the background');
      setUrl(''); setName(''); setFile(null);
      onCreated();
    } catch (err) {
      setError(uploadError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="import-card" onSubmit={submit}>
      <div className="import-tabs">
        <button type="button" className={tab === 'github' ? 'on' : ''} onClick={() => { setTab('github'); setError(''); }}><Github size={16} /> GitHub repository</button>
        <button type="button" className={tab === 'zip' ? 'on' : ''} onClick={() => { setTab('zip'); setError(''); }}><FileArchive size={16} /> Upload ZIP</button>
      </div>
      <div className="import-body">
        {error && <div className="error-box"><AlertCircle size={16} style={{ flexShrink: 0, marginTop: 2 }} /><span>{error}</span></div>}
        {tab === 'github' ? (
          <div className="import-row">
            <div className="field"><label>Repository URL</label>
              <input className="input" value={url} onChange={(e) => { setUrl(e.target.value); if (!name) { /* auto name on submit */ } }} placeholder="https://github.com/owner/repository" /></div>
            <div className="field"><label>Project name <span style={{ color: 'var(--text-faint)', fontWeight: 400 }}>(optional)</span></label>
              <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder={repoNameFromUrl(url) || 'Auto from URL'} /></div>
            <button className="btn btn-primary" style={{ height: 40 }} disabled={busy || !url.trim()}>{busy ? <Spinner /> : <Zap size={16} />} Analyse</button>
          </div>
        ) : (
          <div className="import-row">
            <div
              className={`dropzone ${over ? 'over' : ''}`} style={{ gridColumn: 'span 1' }}
              onClick={() => fileRef.current?.click()}
              onDragOver={(e) => { e.preventDefault(); setOver(true); }} onDragLeave={() => setOver(false)}
              onDrop={(e) => { e.preventDefault(); setOver(false); pickFile(e.dataTransfer.files?.[0]); }}
            >
              <span className="ico"><UploadCloud size={22} /></span>
              <div>{file ? <><b>{file.name}</b><span>{formatBytes(file.size)} · click to change</span></> : <><b>Drop a .zip here or click to browse</b><span>Up to 100 MB · no need to include node_modules or target</span></>}</div>
              <input ref={fileRef} type="file" accept=".zip" hidden onChange={(e) => pickFile(e.target.files?.[0])} />
            </div>
            <div className="field"><label>Project name <span style={{ color: 'var(--text-faint)', fontWeight: 400 }}>(optional)</span></label>
              <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="From file name" /></div>
            <button className="btn btn-primary" style={{ height: 40 }} disabled={busy || !file}>{busy ? <Spinner /> : <Zap size={16} />} {busy && pct > 0 && pct < 100 ? `Uploading ${pct}%` : busy ? 'Starting…' : 'Analyse'}</button>
          </div>
        )}
      </div>
      <div className="import-note">
        <span><ShieldCheck size={13} /> node_modules, target, build, .git and secret files (.env, keys) are skipped automatically</span>
        <span><Boxes size={13} /> Size and file-count limits protect the server</span>
      </div>
    </form>
  );
}

/* ───────────────────────── card ───────────────────────── */
function ProjectCard({ p, onDelete, onReanalyze }) {
  const nav = useNavigate();
  const [menu, setMenu] = useState(false);
  const processing = PROCESSING.has(p.status);
  const failed = p.status === 'FAILED';
  let langs = {};
  try { langs = p.techStackJson ? JSON.parse(p.techStackJson) : {}; } catch { langs = {}; }
  const top = Object.entries(langs).slice(0, 5);

  const open = () => { if (!processing) nav(`/project/${p.id}/overview`); };

  return (
    <div className={`card proj-card ${processing ? 'busy' : ''}`} onClick={open}>
      <div className="proj-head">
        <div style={{ minWidth: 0 }}>
          <div className="proj-name">{p.name}</div>
          <div className="proj-sub">
            {p.sourceType === 'GITHUB' ? <><GitBranch size={13} />{p.repositoryName}</> : <><FileArchive size={13} />{p.zipFileName}</>}
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }} onClick={(e) => e.stopPropagation()}>
          {processing ? <Chip color="var(--warn)"><Spinner />Analysing</Chip>
            : failed ? <Chip color="var(--bad)">Failed</Chip>
              : <Chip color="var(--good)"><span className="dot" />Ready</Chip>}
          <div style={{ position: 'relative' }}>
            <button className="icon-btn" onClick={() => setMenu((m) => !m)}><MoreVertical size={16} /></button>
            {menu && (
              <>
                <div style={{ position: 'fixed', inset: 0, zIndex: 20 }} onClick={() => setMenu(false)} />
                <div className="card" style={{ position: 'absolute', right: 0, top: 34, zIndex: 21, width: 170, padding: 6, boxShadow: 'var(--shadow)' }}>
                  <button className="btn btn-ghost btn-sm" style={{ width: '100%', justifyContent: 'flex-start' }} disabled={processing} onClick={() => { setMenu(false); onReanalyze(); }}><RefreshCw size={14} /> Re-analyse</button>
                  <button className="btn btn-ghost btn-sm" style={{ width: '100%', justifyContent: 'flex-start', color: 'var(--bad)' }} onClick={() => { setMenu(false); onDelete(); }}><Trash2 size={14} /> Delete</button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {processing && (
        <div style={{ display: 'grid', gap: 8 }}>
          <div className="proj-msg"><Spinner />{p.statusMessage || 'Working…'}</div>
          <div className="progress"><span style={{ width: `${Math.max(6, p.progressPercent || 0)}%` }} /></div>
        </div>
      )}
      {failed && (
        <div style={{ display: 'grid', gap: 10 }}>
          <div className="proj-fail">{p.statusMessage}</div>
          <div className="proj-actions" onClick={(e) => e.stopPropagation()}>
            <button className="btn btn-sm" onClick={onReanalyze}><RefreshCw size={14} /> Try again</button>
            <button className="btn btn-sm btn-danger" onClick={onDelete}><Trash2 size={14} /> Delete</button>
          </div>
        </div>
      )}
      {p.ready && (
        <>
          <div className="proj-stats">
            <div className="proj-stat"><b>{formatNumber(p.classCount)}</b><span>Classes</span></div>
            <div className="proj-stat"><b>{formatNumber(p.endpointCount)}</b><span>APIs</span></div>
            <div className="proj-stat"><b>{formatNumber(p.tableCount)}</b><span>Tables</span></div>
            <div className="proj-stat"><b>{formatNumber(p.moduleCount)}</b><span>Modules</span></div>
          </div>
          {top.length > 0 && (
            <div style={{ display: 'grid', gap: 6 }}>
              <div className="langbar">{top.map(([l, v]) => <span key={l} style={{ width: `${v}%`, background: langColor(l) }} title={`${l} ${v}%`} />)}</div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', fontSize: 11.5, color: 'var(--text-faint)' }}>
                {top.slice(0, 4).map(([l, v]) => <span key={l} style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}><span className="dot" style={{ '--c': langColor(l) }} />{l} {v}%</span>)}
              </div>
            </div>
          )}
        </>
      )}

      <div className="proj-foot">
        <span>{timeAgo(p.createdAt)} · {p.fileCount ? `${formatNumber(p.fileCount)} files · ` : ''}{formatBytes(p.sizeBytes)}</span>
        {p.ready && <span style={{ color: 'var(--accent)', display: 'inline-flex', alignItems: 'center', gap: 4, fontWeight: 600 }}>Open <ArrowRight size={14} /></span>}
      </div>
    </div>
  );
}
