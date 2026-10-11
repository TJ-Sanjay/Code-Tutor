import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ChevronRight, FileText, Folder, FolderOpen, Info, Search, X } from 'lucide-react';
import { get } from '../api';
import { useWorkspace } from '../pages/wsContext';
import { langColor, layerColor, layerLabel } from '../lib/colors';
import { fileName, formatBytes } from '../lib/format';
import { Chip, Spinner } from '../ui';
import CodeViewer from './CodeViewer';
import ExplainCard from './ExplainCard';

function flatten(node, out = []) {
  if (node.type === 'FILE') out.push(node);
  node.children?.forEach((c) => flatten(c, out));
  return out;
}

export default function ExplorerTab() {
  const { projectId, follow } = useWorkspace();
  const [params, setParams] = useSearchParams();
  const [root, setRoot] = useState(null);
  const [open, setOpen] = useState(() => new Set(['']));
  const [tabs, setTabs] = useState([]);
  const [q, setQ] = useState('');
  const [info, setInfo] = useState(null);
  const [showInfo, setShowInfo] = useState(true);
  const file = params.get('file');
  const start = params.get('start');
  const end = params.get('end');

  useEffect(() => { get(`/projects/${projectId}/files`).then(setRoot); }, [projectId]);
  const files = useMemo(() => (root ? flatten(root) : []), [root]);

  // first visit: open something useful
  useEffect(() => {
    if (!root || file) return;
    const pick = files.find((f) => /^readme\.md$/i.test(f.name)) || files.find((f) => f.layer === 'APPLICATION')
      || files.find((f) => f.layer === 'CONTROLLER') || files[0];
    if (pick) setParams({ file: pick.path }, { replace: true });
  }, [root, file, files, setParams]);

  // keep ancestors of the active file expanded and remember opened tabs
  useEffect(() => {
    if (!file) return;
    setOpen((o) => {
      const n = new Set(o);
      const parts = file.split('/');
      for (let i = 1; i < parts.length; i++) n.add(parts.slice(0, i).join('/'));
      return n;
    });
    setTabs((t) => (t.includes(file) ? t : [...t, file].slice(-8)));
  }, [file]);

  useEffect(() => {
    if (!file) return;
    setInfo(null);
    get(`/projects/${projectId}/explain`, { kind: 'file', id: file }).then(setInfo).catch(() => setInfo(null));
  }, [file, projectId]);

  const openFile = useCallback((path) => setParams({ file: path }), [setParams]);
  const closeTab = (p, e) => {
    e.stopPropagation();
    const next = tabs.filter((t) => t !== p);
    setTabs(next);
    if (p === file) next.length ? openFile(next[next.length - 1]) : setParams({});
  };

  const toggle = (path) => setOpen((o) => { const n = new Set(o); n.has(path) ? n.delete(path) : n.add(path); return n; });

  const results = useMemo(() => {
    const n = q.trim().toLowerCase();
    if (!n) return null;
    return files.filter((f) => f.path.toLowerCase().includes(n)).slice(0, 80);
  }, [q, files]);

  if (!root) return <div className="ws-center"><Spinner lg /></div>;
  const active = files.find((f) => f.path === file);

  return (
    <div className="explorer">
      <aside className="tree-pane">
        <div className="side-search"><Search size={14} /><input placeholder="Search files…" value={q} onChange={(e) => setQ(e.target.value)} />{q && <button className="icon-btn" style={{ width: 22, height: 22 }} onClick={() => setQ('')}><X size={13} /></button>}</div>
        <div className="tree-scroll">
          {results ? (
            results.length === 0 ? <div className="hint" style={{ padding: 14 }}>No file matches “{q}”.</div> :
              results.map((f) => <FileRow key={f.path} node={f} depth={0} active={f.path === file} onOpen={openFile} showPath />)
          ) : (
            root.children.map((c) => <TreeNode key={c.path} node={c} depth={0} open={open} toggle={toggle} active={file} onOpen={openFile} />)
          )}
        </div>
        <div className="tree-foot">{files.length} files · read-only view of the project</div>
      </aside>

      <section className="editor-pane">
        {tabs.length > 0 && (
          <div className="file-tabs">
            {tabs.map((t) => (
              <button key={t} className={`file-tab ${t === file ? 'on' : ''}`} onClick={() => openFile(t)} title={t}>
                <span className="dot" style={{ '--c': langColor(files.find((f) => f.path === t)?.language) }} />{fileName(t)}
                <span className="x" onClick={(e) => closeTab(t, e)}><X size={12} /></span>
              </button>
            ))}
          </div>
        )}
        {file && (
          <div className="crumbs">
            <span className="crumb-path">{file.split('/').join('  ›  ')}</span>
            {active && <Chip color={langColor(active.language)}>{active.language}</Chip>}
            {active?.layer && <Chip color={layerColor(active.layer)}><span className="dot" />{layerLabel(active.layer)}</Chip>}
            {active && <span className="hint">{active.lines} lines · {formatBytes(active.size)}</span>}
            <div style={{ flex: 1 }} />
            <button className={`btn btn-sm ${showInfo ? '' : 'btn-ghost'}`} onClick={() => setShowInfo((s) => !s)}><Info size={14} /> About this file</button>
          </div>
        )}
        <div className="editor-area">
          {file ? <CodeViewer key={file} path={file} start={start} end={end} /> : <div className="ws-center"><span className="hint">Choose a file on the left</span></div>}
          {file && info && showInfo && !start && (
            <ExplainCard className="file-info" data={info} onClose={() => setShowInfo(false)} onLink={follow} compact />
          )}
        </div>
      </section>
    </div>
  );
}

function TreeNode({ node, depth, open, toggle, active, onOpen }) {
  if (node.type === 'FILE') return <FileRow node={node} depth={depth} active={active === node.path} onOpen={onOpen} />;
  const isOpen = open.has(node.path);
  return (
    <>
      <button className="tree-row dir" style={{ paddingLeft: 8 + depth * 14 }} onClick={() => toggle(node.path)}>
        <ChevronRight size={14} className={`chev ${isOpen ? 'open' : ''}`} />
        {isOpen ? <FolderOpen size={15} color="var(--accent)" /> : <Folder size={15} color="var(--accent)" />}
        <span>{node.name}</span>
      </button>
      {isOpen && node.children.map((c) => <TreeNode key={c.path} node={c} depth={depth + 1} open={open} toggle={toggle} active={active} onOpen={onOpen} />)}
    </>
  );
}

function FileRow({ node, depth, active, onOpen, showPath }) {
  return (
    <button className={`tree-row file ${active ? 'on' : ''}`} style={{ paddingLeft: 8 + depth * 14 + 18 }} onClick={() => onOpen(node.path)} title={node.path}>
      <FileText size={14} color={langColor(node.language)} />
      <span>{showPath ? node.path : node.name}</span>
      {node.layer && <i className="dot" style={{ '--c': layerColor(node.layer) }} title={layerLabel(node.layer)} />}
    </button>
  );
}
