import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Background, BackgroundVariant, Controls, MarkerType, ReactFlow, ReactFlowProvider, useReactFlow } from '@xyflow/react';
import { Boxes, Layers, Network, Search } from 'lucide-react';
import { get } from '../api';
import { useWorkspace } from '../pages/wsContext';
import { dagreLayout, layeredLayout } from '../lib/layout';
import { LAYERS, layerColor, layerLabel } from '../lib/colors';
import { Empty, Spinner } from '../ui';
import { DiagramTools, nodeTypes, useThemeName } from './diagram/shared';
import ExplainCard from './ExplainCard';

const LANES = ['FRONTEND', 'CONTROLLER', 'ADVICE', 'SERVICE', 'COMPONENT', 'SECURITY', 'REPOSITORY', 'ENTITY', 'MODEL', 'DTO', 'CONFIG', 'APPLICATION', 'EXCEPTION', 'UTIL', 'OTHER', 'TABLE'];
const DEFAULT_HIDDEN = new Set(['DTO', 'MODEL', 'CONFIG', 'APPLICATION', 'EXCEPTION', 'UTIL', 'OTHER', 'ADVICE']);

const EDGE_STYLE = {
  DEPENDS_ON: { color: '#7c8cff', dash: undefined, width: 1.6 },
  USES: { color: '#8b93a7', dash: '4 4', width: 1.2 },
  EXTENDS: { color: '#fbbf24', dash: '2 4', width: 1.6 },
  IMPLEMENTS: { color: '#2dd4bf', dash: '2 4', width: 1.6 },
  MAPS_TO: { color: '#fb923c', dash: undefined, width: 1.6 },
  CALLS_API: { color: '#38bdf8', dash: undefined, width: 1.6 },
};

export default function ArchitectureTab() {
  return <ReactFlowProvider><Inner /></ReactFlowProvider>;
}

function Inner() {
  const { projectId, overview, openCode, follow } = useWorkspace();
  const theme = useThemeName();
  const rf = useReactFlow();
  const wrapRef = useRef(null);
  const [graph, setGraph] = useState(null);
  const [mode, setMode] = useState('overview');
  const [hidden, setHidden] = useState(DEFAULT_HIDDEN);
  const [moduleFilter, setModuleFilter] = useState('all');
  const [query, setQuery] = useState('');
  const [sameLane, setSameLane] = useState(false);
  const [showUses, setShowUses] = useState(false);
  const [selected, setSelected] = useState(null);
  const [detail, setDetail] = useState(null);
  const [width, setWidth] = useState(1200);

  useEffect(() => { get(`/projects/${projectId}/graph`).then(setGraph); }, [projectId]);
  useEffect(() => {
    if (!wrapRef.current) return undefined;
    const ro = new ResizeObserver(([e]) => setWidth(e.contentRect.width));
    ro.observe(wrapRef.current);
    return () => ro.disconnect();
  }, [graph]);

  const presentLayers = useMemo(() => {
    if (!graph) return [];
    const s = new Set();
    graph.nodes.forEach((n) => {
      if (n.type === 'CLASS') s.add(n.layer);
      if (n.type === 'TABLE') s.add('TABLE');
      if (n.type === 'JSFILE') s.add('FRONTEND');
    });
    return LANES.filter((l) => s.has(l));
  }, [graph]);

  /* ───── derive the visible nodes / edges ───── */
  const model = useMemo(() => {
    if (!graph) return null;
    const q = query.trim().toLowerCase();
    const byId = new Map(graph.nodes.map((n) => [n.id, n]));
    const vis = new Map();
    const hide = (layer) => hidden.has(layer);

    graph.nodes.forEach((n) => {
      if (n.type === 'CLASS') {
        if (hide(n.layer) || n.layer === 'TEST') return;
        if (moduleFilter !== 'all' && n.module !== moduleFilter) return;
        vis.set(n.id, { id: n.id, label: n.label, layer: n.layer, module: n.module, file: n.file, line: n.line, kind: 'class' });
      } else if (n.type === 'TABLE' && !hide('TABLE')) {
        vis.set(n.id, { id: n.id, label: n.label, layer: 'TABLE', module: null, file: n.file, line: n.line, kind: 'table', badge: n.subtitle });
      }
    });
    // front-end files are folded into one box per folder to keep the diagram readable
    const jsGroup = new Map();
    if (!hide('FRONTEND')) {
      graph.nodes.filter((n) => n.type === 'JSFILE').forEach((n) => {
        const dir = n.file.includes('/') ? n.file.substring(0, n.file.lastIndexOf('/')) : '.';
        const parts = dir.split('/');
        const label = parts.slice(-1)[0] === 'src' ? 'src' : parts.slice(-1)[0];
        const gid = `js:${dir}`;
        jsGroup.set(n.id, gid);
        if (!vis.has(gid)) vis.set(gid, { id: gid, label: `${label}/`, layer: 'FRONTEND', module: 'react', file: null, line: 0, kind: 'frontend', badge: 'React · 0 files' });
        const v = vis.get(gid);
        v.count = (v.count || 0) + 1;
        v.badge = `React · ${v.count} files`;
      });
    }

    const edgeMap = new Map();
    const addEdge = (s, t, type, label) => {
      if (!vis.has(s) || !vis.has(t) || s === t) return;
      const key = `${s}|${t}`;
      const prev = edgeMap.get(key);
      const rank = { EXTENDS: 4, IMPLEMENTS: 4, DEPENDS_ON: 3, MAPS_TO: 3, CALLS_API: 3, USES: 1 };
      if (!prev || rank[type] > rank[prev.type]) edgeMap.set(key, { source: s, target: t, type, label });
    };
    graph.edges.forEach((e) => {
      if (e.type === 'CONTAINS' || e.type === 'REFERENCES' || e.type === 'HANDLED_BY' || e.type === 'IMPORTS') return;
      if (e.type === 'CALLS_API') {
        const ep = byId.get(e.target);
        const handler = graph.edges.find((x) => x.type === 'HANDLED_BY' && x.source === e.target);
        const js = jsGroup.get(e.source);
        if (ep && handler && js) addEdge(js, handler.target, 'CALLS_API', e.label);
        return;
      }
      addEdge(e.source, e.target, e.type, e.label);
    });
    let edges = [...edgeMap.values()];

    if (q) {
      const match = new Set([...vis.values()].filter((n) => n.label.toLowerCase().includes(q) || (n.module || '').toLowerCase().includes(q)).map((n) => n.id));
      vis.forEach((n) => { n.match = match.has(n.id); });
    }
    return { nodes: [...vis.values()], edges, byId };
  }, [graph, hidden, moduleFilter, query]);

  /* ───── lay out + build React Flow elements ───── */
  const { rfNodes, rfEdges } = useMemo(() => {
    if (!model) return { rfNodes: [], rfEdges: [] };
    const sel = selected;
    const neighbours = new Set();
    if (sel) {
      neighbours.add(sel);
      model.edges.forEach((e) => { if (e.source === sel) neighbours.add(e.target); if (e.target === sel) neighbours.add(e.source); });
    }
    const q = query.trim();
    const dimmed = (id) => (sel ? !neighbours.has(id) : false);

    if (mode === 'overview') return buildOverview(graph, sel, (id) => setSelected((cur) => (cur === id ? null : id)));
    if (mode === 'modules') return buildModules(graph, model, sel, neighbours);

    const lanes = LANES.filter((l) => model.nodes.some((n) => n.layer === l));
    const maxRow = Math.max(3, Math.min(9, Math.floor((width - 220) / 226)));
    const lay = layeredLayout(model.nodes, model.edges, { lanes, laneOf: (n) => n.layer, maxRow, labelW: 0 });
    const nodes = [];
    lay.laneBoxes.forEach((b) => {
      nodes.push({ id: `lane:${b.lane}`, type: 'lane', position: { x: -30, y: b.y }, data: { layer: b.lane, count: b.count, width: b.width + 60, height: b.height }, draggable: false, selectable: false, zIndex: -1, style: { pointerEvents: 'none' } });
    });
    const laneOf = new Map(model.nodes.map((n) => [n.id, lanes.indexOf(n.layer)]));
    model.nodes.forEach((n) => {
      const p = lay.positions.get(n.id);
      if (!p) return;
      nodes.push({ id: n.id, type: 'cls', position: p, data: { label: n.label, layer: n.layer, module: n.module, badge: n.badge, dim: dimmed(n.id) || (q && !n.match), selected: sel === n.id, match: q && n.match }, draggable: true });
    });
    const edges = [];
    model.edges.forEach((e, i) => {
      const sl = laneOf.get(e.source);
      const tl = laneOf.get(e.target);
      if (sl === tl && !sameLane && e.type !== 'EXTENDS' && e.type !== 'IMPLEMENTS') return;
      // plain "uses" references are noisy; keep them only for repository→entity, when asked, or around the selected box
      const touchesSel = sel && (e.source === sel || e.target === sel);
      if (e.type === 'USES' && e.label !== 'manages' && !showUses && !touchesSel) return;
      const st = EDGE_STYLE[e.type] || EDGE_STYLE.USES;
      const active = sel && (e.source === sel || e.target === sel);
      const faded = sel && !active;
      const sp = lay.positions.get(e.source);
      const tp = lay.positions.get(e.target);
      let sh = 'b';
      let th = 't';
      if (sl === tl) {
        const rowSame = sp && tp && Math.abs(sp.y - tp.y) < 4;
        if (rowSame) { const right = sp.x < tp.x; sh = right ? 'r' : 'sl'; th = right ? 'l' : 'tr'; }
        else if (sp && tp && sp.y > tp.y) { sh = 'st'; th = 'tb'; }
      } else if (sl > tl) { sh = 'st'; th = 'tb'; }
      edges.push({
        id: `e${i}`, source: e.source, target: e.target, sourceHandle: sh, targetHandle: th,
        type: 'default', animated: false, label: active ? e.label : undefined,
        labelStyle: { fontSize: 11, fill: 'var(--text)' }, labelBgStyle: { fill: 'var(--bg-card)', fillOpacity: 0.95 }, labelBgPadding: [6, 3], labelBgBorderRadius: 6,
        style: { stroke: active ? 'var(--accent)' : st.color, strokeWidth: active ? 2.4 : st.width, strokeDasharray: st.dash, opacity: faded ? 0.08 : active ? 1 : 0.75 },
        markerEnd: { type: e.type === 'EXTENDS' || e.type === 'IMPLEMENTS' ? MarkerType.Arrow : MarkerType.ArrowClosed, color: active ? 'var(--accent)' : st.color, width: 16, height: 16 },
        zIndex: active ? 10 : 0,
      });
    });
    return { rfNodes: nodes, rfEdges: edges };
  }, [model, mode, selected, query, width, sameLane, showUses, graph]);

  useEffect(() => {
    const t = setTimeout(() => {
      if (mode === 'overview' && wrapRef.current) {
        // open the high-level view at a readable size (fit to width) — scroll down to move through the layers
        const w = wrapRef.current.clientWidth;
        const total = 1370;
        const zoom = Math.min(1, (w - 60) / total);
        rf.setViewport({ x: Math.max(20, (w - total * zoom) / 2), y: 92, zoom }, { duration: 300 });
      } else {
        rf.fitView({ padding: 0.12, duration: 300 });
      }
    }, 80);
    return () => clearTimeout(t);
  }, [mode, hidden, moduleFilter, rfNodes.length, rf, width]);

  /* ───── selection → explain panel ───── */
  useEffect(() => {
    setDetail(null);
    if (!selected) return;
    const n = model?.byId.get(selected);
    let url;
    if (mode === 'modules' || selected.startsWith('module:') || selected.startsWith('layer:')) url = null;
    else if (n?.type === 'TABLE') url = { kind: 'table', id: n.label };
    else if (n?.type === 'CLASS') url = { kind: 'class', id: n.id };
    else if (selected.startsWith('js:')) {
      const g = model?.nodes.find((x) => x.id === selected);
      if (g) {
        setDetail({ kind: 'file', title: g.label, subtitle: 'React front-end folder', summary: g.count + ' source files of the React app. They call the backend endpoints shown as links to controllers.', facts: [], links: [], layer: 'FRONTEND' });
        return;
      }
      url = { kind: 'file', id: selected.slice(3) };
    }
    if (!url) return;
    get(`/projects/${projectId}/explain`, url).then(setDetail).catch(() => setDetail(null));
  }, [selected, projectId, model, mode]);

  const toggleLayer = (l) => setHidden((h) => { const n = new Set(h); n.has(l) ? n.delete(l) : n.add(l); return n; });
  const modules = overview.modules;

  if (!graph) return <div className="ws-center"><Spinner lg /></div>;
  if (!graph.nodes.some((n) => n.type === 'CLASS')) {
    return <Empty icon={<Network size={26} />} title="No Java classes found">The architecture diagram is built from Java classes. This project has none that could be parsed.</Empty>;
  }

  return (
    <div className="diagram-wrap" ref={wrapRef}>
      <div className="diagram-toolbar">
        <div className="seg">
          <button className={mode === 'overview' ? 'on' : ''} onClick={() => { setMode('overview'); setSelected(null); }}><Layers size={14} /> Overview</button>
          <button className={mode === 'layers' ? 'on' : ''} onClick={() => { setMode('layers'); setSelected(null); }}><Boxes size={14} /> Detailed</button>
          <button className={mode === 'modules' ? 'on' : ''} onClick={() => { setMode('modules'); setSelected(null); }}><Network size={14} /> Modules</button>
        </div>
        {mode === 'layers' && (
          <>
            <select className="select toolbar-select" value={moduleFilter} onChange={(e) => { setModuleFilter(e.target.value); setSelected(null); }}>
              <option value="all">All modules</option>
              {modules.map((m) => <option key={m.name} value={m.name}>{m.name}</option>)}
            </select>
            <div className="layer-toggles">
              {presentLayers.map((l) => (
                <button key={l} className={`chip chip-btn ${hidden.has(l) ? 'off' : ''}`} style={{ '--c': layerColor(l) }} onClick={() => toggleLayer(l)} title={hidden.has(l) ? 'Show' : 'Hide'}>
                  <span className="dot" />{layerLabel(l)}
                </button>
              ))}
            </div>
            <label className="check"><input type="checkbox" checked={showUses} onChange={(e) => setShowUses(e.target.checked)} /> “uses” links</label>
            <label className="check"><input type="checkbox" checked={sameLane} onChange={(e) => setSameLane(e.target.checked)} /> same-layer links</label>
          </>
        )}
        <div style={{ flex: 1 }} />
        {mode === 'overview' && <span className="hint">Requests flow top → bottom · scroll to move · Ctrl + scroll to zoom · click a name for details</span>}
        <div className="toolbar-search"><Search size={14} /><input placeholder="Highlight…" value={query} onChange={(e) => setQuery(e.target.value)} /></div>
      </div>

      <div className="diagram-canvas">
        <ReactFlow
          nodes={rfNodes} edges={rfEdges} nodeTypes={nodeTypes} colorMode={theme}
          nodesConnectable={false} minZoom={0.15} maxZoom={1.6} fitView panOnScroll={mode === 'overview'} zoomOnScroll={mode !== 'overview'} proOptions={{ hideAttribution: true }}
          onNodeClick={(_, n) => { if (n.type !== 'lane' && n.type !== 'lcard') setSelected(n.id === selected ? null : n.id); }}
          onPaneClick={() => setSelected(null)}
        >
          <Background variant={BackgroundVariant.Dots} gap={22} size={1.2} color="var(--grid-dot)" />
          <Controls showInteractive={false} />
          <DiagramTools containerRef={wrapRef} filename={`${overview.overview.name}-architecture`} />
        </ReactFlow>

        {mode === 'layers' && (<div className="diagram-legend">
          <span><i style={{ background: EDGE_STYLE.DEPENDS_ON.color }} />injects / calls</span>
          <span><i className="dashed" style={{ borderColor: EDGE_STYLE.USES.color }} />uses</span>
          <span><i className="dashed" style={{ borderColor: EDGE_STYLE.EXTENDS.color }} />extends</span>
          <span><i className="dashed" style={{ borderColor: EDGE_STYLE.IMPLEMENTS.color }} />implements</span>
          <span><i style={{ background: EDGE_STYLE.MAPS_TO.color }} />maps to table</span>
        </div>)}

        {detail ? (
          <div className="diagram-panel">
            <ExplainCard data={detail} onClose={() => setSelected(null)} onLink={follow} />
          </div>
        ) : !selected && mode !== 'overview' && (
          <div className="diagram-hint">Click any box to see what it does and highlight everything connected to it.</div>
        )}
      </div>
    </div>
  );
}

/* ───────── module-level diagram ───────── */
function buildModules(graph, model, sel, neighbours) {
  const classes = graph.nodes.filter((n) => n.type === 'CLASS' && n.layer !== 'TEST');
  const mod = new Map();
  classes.forEach((c) => {
    if (!mod.has(c.module)) mod.set(c.module, { name: c.module, layers: new Map(), classes: 0 });
    const m = mod.get(c.module);
    m.classes++;
    m.layers.set(c.layer, (m.layers.get(c.layer) || 0) + 1);
  });
  const moduleOf = new Map(classes.map((c) => [c.id, c.module]));
  const epCount = new Map();
  graph.edges.filter((e) => e.type === 'HANDLED_BY').forEach((e) => { const m = moduleOf.get(e.target); if (m) epCount.set(m, (epCount.get(m) || 0) + 1); });
  const agg = new Map();
  graph.edges.forEach((e) => {
    if (!['DEPENDS_ON', 'EXTENDS', 'IMPLEMENTS', 'USES'].includes(e.type)) return;
    const a = moduleOf.get(e.source);
    const b = moduleOf.get(e.target);
    if (!a || !b || a === b) return;
    const k = `${a}|${b}`;
    agg.set(k, (agg.get(k) || 0) + (e.type === 'USES' ? 1 : 2));
  });
  const nodes0 = [...mod.values()].map((m) => ({ id: `module:${m.name}`, label: m.name }));
  const edges0 = [...agg.keys()].map((k) => { const [a, b] = k.split('|'); return { source: `module:${a}`, target: `module:${b}` }; });
  const lay = dagreLayout(nodes0, edges0, { rankdir: 'TB', nodesep: 50, ranksep: 90, size: () => ({ w: 210, h: 92 }) });
  const nodes = [...mod.values()].map((m) => ({
    id: `module:${m.name}`, type: 'mod', position: lay.positions.get(`module:${m.name}`),
    data: { label: m.name, classes: m.classes, endpoints: epCount.get(m.name) || 0, layers: [...m.layers.entries()], dim: sel ? !neighbours.has(`module:${m.name}`) : false, selected: sel === `module:${m.name}` },
  }));
  const edges = [...agg.entries()].map(([k, w], i) => {
    const [a, b] = k.split('|');
    const active = sel && (sel === `module:${a}` || sel === `module:${b}`);
    return {
      id: `m${i}`, source: `module:${a}`, target: `module:${b}`, sourceHandle: 'b', targetHandle: 't', label: active ? `${w} links` : undefined,
      style: { stroke: active ? 'var(--accent)' : '#7c8cff', strokeWidth: Math.min(5, 1 + w / 4), opacity: sel && !active ? 0.1 : 0.7 },
      markerEnd: { type: MarkerType.ArrowClosed, color: active ? 'var(--accent)' : '#7c8cff' },
      labelStyle: { fontSize: 11, fill: 'var(--text)' }, labelBgStyle: { fill: 'var(--bg-card)' },
    };
  });
  return { rfNodes: nodes, rfEdges: edges };
}

/* ───────── high-level view: coloured bands stacked in request order ───────── */
const MAIN_FLOW = ['FRONTEND', 'CONTROLLER', 'SERVICE', 'REPOSITORY', 'ENTITY', 'TABLE'];
const SUPPORT = ['SECURITY', 'COMPONENT', 'CONFIG', 'APPLICATION', 'DTO', 'MODEL', 'ADVICE', 'EXCEPTION', 'UTIL', 'OTHER'];
const STEP_VERB = {
  'FRONTEND|CONTROLLER': 'HTTP requests', 'CONTROLLER|SERVICE': 'calls', 'SERVICE|REPOSITORY': 'queries',
  'REPOSITORY|ENTITY': 'maps', 'ENTITY|TABLE': 'stored in', 'CONTROLLER|REPOSITORY': 'queries', 'SERVICE|ENTITY': 'uses',
};

function buildOverview(graph, selected, onPick) {
  const items = {};
  const layerOf = new Map();
  const push = (layer, id, name) => { (items[layer] ||= []).push({ id, name }); layerOf.set(id, layer); };
  graph.nodes.forEach((n) => {
    if (n.type === 'CLASS' && n.layer !== 'TEST') push(n.layer, n.id, n.label);
    else if (n.type === 'TABLE') push('TABLE', n.id, n.label);
    else if (n.type === 'JSFILE') push('FRONTEND', n.id, n.label);
  });
  Object.values(items).forEach((l) => l.sort((a, b) => a.name.localeCompare(b.name)));
  const main = MAIN_FLOW.filter((l) => items[l]?.length);
  const support = SUPPORT.filter((l) => items[l]?.length);

  // how many connections link two layers (class → class, class → table, react file → controller)
  const handler = new Map();
  graph.edges.filter((e) => e.type === 'HANDLED_BY').forEach((e) => handler.set(e.source, e.target));
  const counts = new Map();
  const bump = (a, b) => { if (a && b && a !== b) counts.set(`${a}|${b}`, (counts.get(`${a}|${b}`) || 0) + 1); };
  graph.edges.forEach((e) => {
    if (['DEPENDS_ON', 'USES', 'MAPS_TO'].includes(e.type)) bump(layerOf.get(e.source), layerOf.get(e.target));
    else if (e.type === 'CALLS_API') bump('FRONTEND', layerOf.get(handler.get(e.target)));
  });

  // estimate how many rows the wrapped name pills need so bands never overlap
  const rowsFor = (names, area) => {
    let rows = 1; let used = 0;
    names.forEach((n) => { const w = n.length * 7.4 + 28; if (used + w > area) { rows++; used = 0; } used += w + 8; });
    return rows;
  };

  const BW = 1010; const HEAD = 230; const GAP_Y = 76; const MAX = 24;
  const nodes = [];
  let y = 0;
  main.forEach((l, i) => {
    const list = items[l];
    const shown = list.slice(0, MAX);
    const more = list.length - shown.length;
    const rows = rowsFor(shown.map((s) => s.name).concat(more > 0 ? ['+ 00 more'] : []), BW - HEAD - 34);
    const h = Math.max(100, rows * 38 + 30);
    nodes.push({ id: `layer:${l}`, type: 'band', position: { x: 0, y }, draggable: false,
      data: { layer: l, count: list.length, items: shown, more, width: BW, height: h, selected, onPick, delay: i * 90 } });
    y += h + GAP_Y;
  });

  const SX = BW + 90; const SW = 270;
  let sy = 0;
  if (support.length) nodes.push({ id: 'caption:support', type: 'caption', position: { x: SX, y: -34 }, draggable: false, selectable: false, data: { text: 'Supporting parts · used across all layers' } });
  support.forEach((l, i) => {
    const list = items[l];
    const shown = list.slice(0, 7);
    const more = list.length - shown.length;
    const rows = rowsFor(shown.map((s) => s.name).concat(more > 0 ? ['+ 00 more'] : []), SW - 32);
    const h = 78 + rows * 38 + 12;
    nodes.push({ id: `layer:${l}`, type: 'band', position: { x: SX, y: sy }, draggable: false,
      data: { layer: l, count: list.length, items: shown, more, width: SW, variant: 'card', selected, onPick, delay: 400 + i * 70 } });
    sy += h + 22;
  });

  const edges = [];
  for (let i = 0; i + 1 < main.length; i++) {
    const a = main[i]; const b = main[i + 1];
    const n = counts.get(`${a}|${b}`) || 0;
    const color = layerColor(b);
    const verb = STEP_VERB[`${a}|${b}`] || 'uses';
    edges.push({
      id: `o${i}`, source: `layer:${a}`, target: `layer:${b}`, sourceHandle: 'b', targetHandle: 't', type: 'default', animated: true,
      label: n ? `${verb} · ${n}` : verb, labelStyle: { fontSize: 12.5, fill: 'var(--text)', fontWeight: 700 },
      labelBgStyle: { fill: 'var(--bg-card)', fillOpacity: 1, stroke: color, strokeWidth: 1.5 }, labelBgPadding: [10, 5], labelBgBorderRadius: 12,
      style: { stroke: color, strokeWidth: 3 }, markerEnd: { type: MarkerType.ArrowClosed, color, width: 12, height: 12 },
    });
  }
  return { rfNodes: nodes, rfEdges: edges };
}
