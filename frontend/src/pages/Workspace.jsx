import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { NavLink, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, Boxes, Database, FolderTree, GitBranch, LayoutDashboard, MessageSquareCode, Network, Plug, RefreshCw, Route as RouteIcon, Search, Trash2,
} from 'lucide-react';
import { del, errMsg, get, post } from '../api';
import { Chip, Logo, Modal, Spinner, ThemeToggle, useToast } from '../ui';
import { WorkspaceContext } from './wsContext';
import OverviewTab from '../views/OverviewTab';
import ExplorerTab from '../views/ExplorerTab';
import ArchitectureTab from '../views/ArchitectureTab';
import FlowTab from '../views/FlowTab';
import DatabaseTab from '../views/DatabaseTab';
import ApisTab from '../views/ApisTab';
import DependenciesTab from '../views/DependenciesTab';
import ChatPanel from '../views/ChatPanel';
import { layerColor } from '../lib/colors';

const PROCESSING = new Set(['PENDING', 'QUEUED', 'CLONING', 'EXTRACTING', 'SCANNING', 'PARSING', 'ANALYZING']);

const TABS = [
  { path: 'overview', label: 'Overview', icon: LayoutDashboard },
  { path: 'explorer', label: 'Code', icon: FolderTree },
  { path: 'architecture', label: 'Architecture', icon: Network },
  { path: 'flow', label: 'Data flow', icon: RouteIcon },
  { path: 'database', label: 'Database', icon: Database },
  { path: 'apis', label: 'APIs', icon: Plug },
  { path: 'dependencies', label: 'Dependencies', icon: Boxes },
];

export default function Workspace() {
  const { id } = useParams();
  const nav = useNavigate();
  const toast = useToast();
  const [project, setProject] = useState(null);
  const [missing, setMissing] = useState(false);
  const [overview, setOverview] = useState(null);
  const [chatOpen, setChatOpen] = useState(() => localStorage.getItem('ct-chat-open') !== '0');
  const [palette, setPalette] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  /* ───── load + poll the project until analysis is finished ───── */
  const loadProject = useCallback(async () => {
    try {
      const p = await get(`/projects/${id}/status`);
      setProject(p);
      return p;
    } catch (e) {
      if (e.response?.status === 404) setMissing(true);
      return null;
    }
  }, [id]);

  useEffect(() => { setProject(null); setOverview(null); setMissing(false); loadProject(); }, [id, loadProject]);

  useEffect(() => {
    if (!project || !PROCESSING.has(project.status)) return undefined;
    const t = setInterval(loadProject, 1300);
    return () => clearInterval(t);
  }, [project, loadProject]);

  useEffect(() => {
    if (project?.ready && !overview) get(`/projects/${id}/overview`).then(setOverview).catch((e) => toast(errMsg(e), 'bad'));
  }, [project, overview, id, toast]);

  /* ───── helpers shared with all tabs ───── */
  const go = useCallback((tab, query) => {
    const qs = query ? `?${new URLSearchParams(Object.entries(query).filter(([, v]) => v != null && v !== '')).toString()}` : '';
    nav(`/project/${id}/${tab}${qs}`);
  }, [id, nav]);

  const openCode = useCallback((file, start, end) => go('explorer', { file, start, end }), [go]);

  const toggleChat = useCallback(() => setChatOpen((o) => { localStorage.setItem('ct-chat-open', o ? '0' : '1'); return !o; }), []);
  const openChat = useCallback(() => setChatOpen((o) => { if (!o) localStorage.setItem('ct-chat-open', '1'); return true; }), []);

  const ask = useCallback((query, selection) => {
    openChat();
    setTimeout(() => window.dispatchEvent(new CustomEvent('ct-ask', { detail: { query, selection } })), 60);
  }, [openChat]);

  /** Follow a link returned by the explain API / chat actions. */
  const follow = useCallback((link) => {
    if (!link) return;
    if (link.kind === 'flow') go('flow', { endpoint: link.id });
    else if (link.tab) go(link.tab, link.id ? { id: link.id } : undefined);
    else if (link.file) openCode(link.file, link.line, link.endLine || link.line);
  }, [go, openCode]);

  useEffect(() => {
    const onKey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setPalette(true); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const reanalyze = async () => {
    try { setProject(await post(`/projects/${id}/reanalyze`)); setOverview(null); } catch (e) { toast(errMsg(e), 'bad'); }
  };
  const remove = async () => {
    try { await del(`/projects/${id}`); nav('/'); } catch (e) { toast(errMsg(e), 'bad'); }
  };

  const ctx = useMemo(() => ({ projectId: id, project, overview, go, openCode, ask, follow, reload: loadProject }), [id, project, overview, go, openCode, ask, follow, loadProject]);

  if (missing) {
    return (
      <div style={{ height: '100vh', display: 'grid', placeItems: 'center' }}>
        <div className="empty"><h3>Project not found</h3><p>It may have been deleted, or it belongs to another account.</p>
          <button className="btn btn-primary" onClick={() => nav('/')}>Back to my projects</button></div>
      </div>
    );
  }

  const processing = project && PROCESSING.has(project.status);
  const failed = project?.status === 'FAILED';
  const ready = project?.ready && overview;

  return (
    <WorkspaceContext.Provider value={ctx}>
      <div className="ws">
        <header className="ws-head">
          <button className="icon-btn" onClick={() => nav('/')} title="All projects"><ArrowLeft size={18} /></button>
          <Logo size={24} text={false} />
          <div className="ws-title">
            <b>{project?.name || 'Loading…'}</b>
            {project && <span><GitBranch size={12} /> {project.repositoryName || project.zipFileName}</span>}
          </div>
          {project && (processing ? <Chip color="var(--warn)"><Spinner />Analysing {project.progressPercent}%</Chip>
            : failed ? <Chip color="var(--bad)">Analysis failed</Chip>
              : <Chip color="var(--good)"><span className="dot" />Ready</Chip>)}
          <div style={{ flex: 1 }} />
          {ready && (
            <button className="search-btn" onClick={() => setPalette(true)}>
              <Search size={15} /> <span>Find class, method, endpoint, file…</span> <kbd>Ctrl K</kbd>
            </button>
          )}
          <ThemeToggle />
          {ready && (
            <button className={`btn btn-sm ${chatOpen ? 'btn-primary' : ''}`} onClick={toggleChat}><MessageSquareCode size={15} /> Ask AI</button>
          )}
        </header>

        {ready && (
          <nav className="ws-tabs">
            {TABS.map(({ path, label, icon: Icon }) => (
              <NavLink key={path} to={`/project/${id}/${path}`} className={({ isActive }) => `ws-tab ${isActive ? 'on' : ''}`}>
                <Icon size={16} />{label}
                {path === 'apis' && overview.overview.endpointCount > 0 && <em>{overview.overview.endpointCount}</em>}
                {path === 'database' && overview.overview.tableCount > 0 && <em>{overview.overview.tableCount}</em>}
              </NavLink>
            ))}
            <div style={{ flex: 1 }} />
            <button className="btn btn-ghost btn-sm" onClick={reanalyze} title="Run the analysis again"><RefreshCw size={14} /> Re-analyse</button>
            <button className="btn btn-ghost btn-sm" style={{ color: 'var(--bad)' }} onClick={() => setConfirmDelete(true)}><Trash2 size={14} /></button>
          </nav>
        )}

        <div className="ws-body">
          <main className="ws-main">
            {!project && <div className="ws-center"><Spinner lg /></div>}
            {processing && <Processing project={project} />}
            {failed && (
              <div className="ws-center">
                <div className="card card-pad" style={{ maxWidth: 480, textAlign: 'center' }}>
                  <h3 style={{ marginBottom: 8 }}>The analysis could not finish</h3>
                  <p className="error-box" style={{ textAlign: 'left' }}>{project.statusMessage}</p>
                  <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 16 }}>
                    <button className="btn btn-primary" onClick={reanalyze}><RefreshCw size={15} /> Try again</button>
                    <button className="btn btn-danger" onClick={() => setConfirmDelete(true)}><Trash2 size={15} /> Delete</button>
                  </div>
                </div>
              </div>
            )}
            {project?.ready && !overview && <div className="ws-center"><Spinner lg /></div>}
            {ready && (
              <Routes>
                <Route index element={<Navigate to="overview" replace />} />
                <Route path="overview" element={<OverviewTab />} />
                <Route path="explorer" element={<ExplorerTab />} />
                <Route path="architecture" element={<ArchitectureTab />} />
                <Route path="flow" element={<FlowTab />} />
                <Route path="database" element={<DatabaseTab />} />
                <Route path="apis" element={<ApisTab />} />
                <Route path="dependencies" element={<DependenciesTab />} />
                <Route path="*" element={<Navigate to="overview" replace />} />
              </Routes>
            )}
          </main>
          {ready && chatOpen && <aside className="ws-chat"><ChatPanel onClose={toggleChat} /></aside>}
        </div>

        {palette && ready && <Palette onClose={() => setPalette(false)} />}
        {confirmDelete && (
          <Modal title="Delete this project?" onClose={() => setConfirmDelete(false)} actions={<>
            <button className="btn" onClick={() => setConfirmDelete(false)}>Cancel</button>
            <button className="btn btn-danger" onClick={remove}><Trash2 size={15} /> Delete project</button>
          </>}><p>The project, its analysis and your chat history will be permanently removed.</p></Modal>
        )}
      </div>
    </WorkspaceContext.Provider>
  );
}

/* ───────────────────────── processing screen ───────────────────────── */
function Processing({ project }) {
  return (
    <div className="ws-center">
      <div className="card card-pad" style={{ width: 'min(480px, 92%)', textAlign: 'center' }}>
        <Spinner lg />
        <h3 style={{ margin: '16px 0 4px' }}>Reading your project</h3>
        <p style={{ color: 'var(--text-dim)', marginBottom: 16 }}>{project.statusMessage || 'Working…'}</p>
        <div className="progress"><span style={{ width: `${Math.max(6, project.progressPercent || 0)}%` }} /></div>
        <p className="hint" style={{ marginTop: 12 }}>You can leave this page — the analysis keeps running in the background.</p>
      </div>
    </div>
  );
}

/* ───────────────────────── command palette ───────────────────────── */
function Palette({ onClose }) {
  const { projectId, go, openCode } = useWorkspace();
  const [q, setQ] = useState('');
  const [hits, setHits] = useState([]);
  const [sel, setSel] = useState(0);
  const inputRef = useRef(null);

  useEffect(() => { inputRef.current?.focus(); }, []);
  useEffect(() => {
    if (q.trim().length < 2) { setHits([]); return undefined; }
    const t = setTimeout(() => get(`/projects/${projectId}/symbols`, { q }).then((h) => { setHits(h); setSel(0); }).catch(() => setHits([])), 140);
    return () => clearTimeout(t);
  }, [q, projectId]);

  const choose = (h) => {
    onClose();
    if (h.kind === 'endpoint') go('flow', { endpoint: h.id });
    else openCode(h.file, h.line, h.line);
  };

  const onKey = (e) => {
    if (e.key === 'Escape') onClose();
    else if (e.key === 'ArrowDown') { e.preventDefault(); setSel((s) => Math.min(hits.length - 1, s + 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setSel((s) => Math.max(0, s - 1)); }
    else if (e.key === 'Enter' && hits[sel]) choose(hits[sel]);
  };

  const color = { class: layerColor('CONTROLLER'), method: 'var(--tok-method)', endpoint: '#a78bfa', file: 'var(--text-faint)' };

  return (
    <div className="modal-overlay" style={{ alignItems: 'flex-start', paddingTop: '12vh' }} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="palette">
        <div className="palette-input"><Search size={17} /><input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={onKey} placeholder="Search classes, methods, endpoints, files…" /></div>
        <div className="palette-list">
          {q.trim().length < 2 && <div className="palette-empty">Type at least 2 characters</div>}
          {q.trim().length >= 2 && hits.length === 0 && <div className="palette-empty">No matches</div>}
          {hits.map((h, i) => (
            <button key={h.kind + h.id + i} className={`palette-item ${i === sel ? 'on' : ''}`} onMouseEnter={() => setSel(i)} onClick={() => choose(h)}>
              <span className="chip" style={{ '--c': color[h.kind] }}>{h.kind}</span>
              <b>{h.name}</b><span>{h.detail}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
