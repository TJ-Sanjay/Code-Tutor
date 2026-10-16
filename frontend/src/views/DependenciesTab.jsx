import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ArrowDownToLine, ArrowUpFromLine, Boxes, ChevronRight, Package, Search } from 'lucide-react';
import { get } from '../api';
import { useWorkspace } from '../pages/wsContext';
import { layerColor, layerLabel } from '../lib/colors';
import { Empty, Spinner } from '../ui';
import ExplainCard from './ExplainCard';

const REL = { INJECTS: 'injects', CALLS: 'calls', EXTENDS: 'extends', IMPLEMENTS: 'implements', USES: 'uses' };

/** Interactive code-dependency tree (lazy loaded) + the third-party libraries of the build file. */
export default function DependenciesTab() {
  const { projectId, overview, follow } = useWorkspace();
  const [params, setParams] = useSearchParams();
  const [view, setView] = useState('tree');
  const [dir, setDir] = useState('out');
  const [roots, setRoots] = useState(null);
  const [children, setChildren] = useState({});   // path-key → nodes
  const [openKeys, setOpenKeys] = useState(new Set());
  const [selected, setSelected] = useState(null);
  const [detail, setDetail] = useState(null);
  const [classes, setClasses] = useState([]);
  const [q, setQ] = useState('');
  const [rootId, setRootId] = useState(params.get('id'));

  const loadRoots = useCallback(async () => {
    setChildren({}); setOpenKeys(new Set());
    if (rootId) {
      const cls = await get(`/projects/${projectId}/classes`);
      const c = cls.find((x) => x.id === rootId);
      setRoots(c ? [{ id: c.id, name: c.name, layer: c.layer, module: c.module, hasChildren: true, file: c.file, line: c.line, summary: c.summary }] : []);
    } else {
      setRoots(await get(`/projects/${projectId}/dependencies`));
    }
  }, [projectId, rootId]);

  useEffect(() => { loadRoots(); }, [loadRoots]);
  useEffect(() => { get(`/projects/${projectId}/classes`).then(setClasses); }, [projectId]);

  // changing direction restarts the tree from the same root(s)
  useEffect(() => { setChildren({}); setOpenKeys(new Set()); }, [dir]);

  const toggle = async (key, node) => {
    const next = new Set(openKeys);
    if (next.has(key)) { next.delete(key); setOpenKeys(next); return; }
    next.add(key);
    setOpenKeys(next);
    if (!children[key]) {
      const list = await get(`/projects/${projectId}/dependencies`, { class: node.id, dir });
      setChildren((c) => ({ ...c, [key]: list }));
    }
  };

  const pick = (node) => {
    setSelected(node.id);
    get(`/projects/${projectId}/explain`, { kind: 'class', id: node.id }).then(setDetail).catch(() => setDetail(null));
  };

  const matches = useMemo(() => {
    const n = q.trim().toLowerCase();
    return n.length < 2 ? [] : classes.filter((c) => c.name.toLowerCase().includes(n)).slice(0, 8);
  }, [q, classes]);

  const libs = overview.libraries || [];
  const maven = libs.filter((l) => l.source === 'maven' || l.source === 'gradle');
  const npm = libs.filter((l) => l.source === 'npm');
  const pip = libs.filter((l) => l.source === 'pip');

  return (
    <div className="page">
      <div className="page-head">
        <div><h2>Dependencies</h2><p>How the code depends on itself, and which libraries it is built on.</p></div>
        <div className="seg">
          <button className={view === 'tree' ? 'on' : ''} onClick={() => setView('tree')}><Boxes size={14} /> Code tree</button>
          <button className={view === 'libs' ? 'on' : ''} onClick={() => setView('libs')}><Package size={14} /> Libraries ({libs.length})</button>
        </div>
      </div>

      {view === 'tree' ? (
        <div className="dep-layout">
          <div className="card dep-card">
            <div className="dep-tools">
              <div className="toolbar-search wide" style={{ position: 'relative' }}>
                <Search size={14} /><input placeholder="Start from a class…" value={q} onChange={(e) => setQ(e.target.value)} />
                {matches.length > 0 && (
                  <div className="dep-suggest">
                    {matches.map((c) => (
                      <button key={c.id} onClick={() => { setRootId(c.id); setParams({ id: c.id }); setQ(''); }}>
                        <span className="dot" style={{ '--c': layerColor(c.layer) }} />{c.name}<em>{c.module}</em>
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <div className="seg">
                <button className={dir === 'out' ? 'on' : ''} onClick={() => setDir('out')} title="What this class depends on"><ArrowDownToLine size={14} /> Depends on</button>
                <button className={dir === 'in' ? 'on' : ''} onClick={() => setDir('in')} title="Who depends on this class"><ArrowUpFromLine size={14} /> Used by</button>
              </div>
              {rootId && <button className="btn btn-sm" onClick={() => { setRootId(null); setParams({}); }}>Show all entry points</button>}
            </div>
            <div className="dep-tree">
              {roots === null && <div style={{ padding: 20 }}><Spinner /></div>}
              {roots?.length === 0 && <div className="empty" style={{ padding: 28 }}>Nothing to show.</div>}
              {roots?.map((n) => <TreeRow key={n.id} node={n} keyPath={n.id} depth={0} path={[n.id]} dir={dir} children_={children} openKeys={openKeys} toggle={toggle} selected={selected} pick={pick} />)}
            </div>
          </div>
          <div className="dep-detail">
            {detail ? <ExplainCard data={detail} onClose={() => { setDetail(null); setSelected(null); }} onLink={follow} />
              : <div className="card card-pad" style={{ color: 'var(--text-dim)' }}>
                <b style={{ color: 'var(--text)' }}>Explore how classes depend on each other</b>
                <p style={{ marginTop: 6 }}>Expand a class with the arrow to see what it uses. Click a name to read what it does. Switch to “Used by” to see who relies on a class — handy before you change it.</p>
              </div>}
          </div>
        </div>
      ) : (
        <div className="libs">
          {[['Maven / Gradle', maven], ['npm packages', npm], ['Python packages', pip]].filter(([, l]) => l.length).map(([title, list]) => (
            <div key={title} className="card card-pad">
              <div className="card-title">{title} · {list.length}</div>
              <div className="lib-grid">
                {list.map((l, i) => (
                  <div key={l.name + i} className="lib">
                    <b title={l.name}>{l.name.includes(':') ? l.name.split(':')[1] : l.name}</b>
                    <span>{l.name.includes(':') ? l.name.split(':')[0] : ''}</span>
                    <em>{l.version}{l.scope && l.scope !== 'compile' && l.scope !== 'runtime' ? ` · ${l.scope}` : ''}</em>
                  </div>
                ))}
              </div>
            </div>
          ))}
          {libs.length === 0 && <Empty icon={<Package size={26} />} title="No build file found">No <code>pom.xml</code>, <code>build.gradle</code> or <code>package.json</code> was detected.</Empty>}
        </div>
      )}
    </div>
  );
}

function TreeRow({ node, keyPath, depth, path, dir, children_, openKeys, toggle, selected, pick }) {
  const isOpen = openKeys.has(keyPath);
  const kids = children_[keyPath];
  const cyclic = depth > 0 && path.slice(0, -1).includes(node.id);
  return (
    <>
      <div className={`dep-row ${selected === node.id ? 'on' : ''}`} style={{ paddingLeft: 10 + depth * 22 }}>
        {node.hasChildren && !cyclic ? (
          <button className={`dep-chev ${isOpen ? 'open' : ''}`} onClick={() => toggle(keyPath, node)}><ChevronRight size={15} /></button>
        ) : <span className="dep-chev" />}
        <span className="dot" style={{ '--c': layerColor(node.layer) }} />
        <button className="dep-name" onClick={() => pick(node)}>{node.name}</button>
        {node.relation && <span className="dep-rel">{dir === 'in' ? 'uses it via' : ''} {REL[node.relation] || node.relation}</span>}
        <span className="dep-layer">{layerLabel(node.layer)}</span>
        {node.module && <span className="dep-module">{node.module}</span>}
        {cyclic && <span className="dep-cycle" title="Already shown above — circular reference">↺ circular</span>}
      </div>
      {isOpen && kids === undefined && <div style={{ paddingLeft: 40 + depth * 22, padding: 6 }}><Spinner /></div>}
      {isOpen && kids?.map((c) => <TreeRow key={keyPath + '>' + c.id} node={c} keyPath={keyPath + '>' + c.id} depth={depth + 1} path={[...path, c.id]} dir={dir}
        children_={children_} openKeys={openKeys} toggle={toggle} selected={selected} pick={pick} />)}
    </>
  );
}
