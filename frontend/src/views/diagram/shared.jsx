import React, { useCallback, useEffect, useState } from 'react';
import '@xyflow/react/dist/style.css';
import { Handle, Position, getNodesBounds, useReactFlow } from '@xyflow/react';
import { toPng } from 'html-to-image';
import { Archive, Box, Cog, Download, Globe, KeyRound, Maximize2, Monitor, Puzzle, Reply, ShieldCheck, Table2, Layers } from 'lucide-react';
import { LAYER_HELP, LAYER_PLURAL, layerColor, layerLabel } from '../../lib/colors';

/** Current theme name ('dark' | 'light'), updated live when the user toggles it. */
export function useThemeName() {
  const [t, setT] = useState(() => document.documentElement.getAttribute('data-theme') || 'dark');
  useEffect(() => {
    const on = (e) => setT(e.detail);
    window.addEventListener('ct-theme', on);
    return () => window.removeEventListener('ct-theme', on);
  }, []);
  return t;
}

/**
 * Size of everything that is actually drawn (nodes, edges and edge labels), in diagram coordinates.
 * Measured from the DOM because node data alone misses edge labels and not-yet-measured nodes — that is what cropped exports.
 */
function renderedBounds(root, rf, nodes) {
  const vp = rf.getViewport();
  const host = root.querySelector('.react-flow');
  const hr = host.getBoundingClientRect();
  let x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity;
  root.querySelectorAll('.react-flow__node, .react-flow__edge-path, .react-flow__edge-textbg, .react-flow__edgelabel-renderer > div').forEach((el) => {
    const r = el.getBoundingClientRect();
    if (!r.width && !r.height) return;
    x1 = Math.min(x1, (r.left - hr.left - vp.x) / vp.zoom);
    y1 = Math.min(y1, (r.top - hr.top - vp.y) / vp.zoom);
    x2 = Math.max(x2, (r.right - hr.left - vp.x) / vp.zoom);
    y2 = Math.max(y2, (r.bottom - hr.top - vp.y) / vp.zoom);
  });
  if (!isFinite(x1)) return getNodesBounds(nodes);
  return { x: x1, y: y1, width: x2 - x1, height: y2 - y1 };
}

/** Fit / export buttons — must be rendered inside <ReactFlow>. */
export function DiagramTools({ containerRef, filename = 'diagram' }) {
  const rf = useReactFlow();
  const [busy, setBusy] = useState(false);

  const exportPng = useCallback(async () => {
    const root = containerRef.current;
    const nodes = rf.getNodes().filter((n) => !n.hidden);
    if (!nodes.length || !root) return;
    setBusy(true);
    const viewport = root.querySelector('.react-flow__viewport');
    const markers = root.querySelector('svg.react-flow__marker, .react-flow__marker');
    let markerClone = null;
    try {
      // 1. exact size of the whole diagram (every node, including the lane backgrounds) plus a margin for edges / labels
      const b = renderedBounds(root, rf, nodes);
      const pad = 90;
      const width = Math.ceil(b.width + pad * 2);
      const height = Math.ceil(b.height + pad * 2);
      // keep the bitmap within what browsers can draw, but stay sharp for normal sized diagrams
      const ratio = Math.max(0.5, Math.min(1.5, 5000 / Math.max(width, height)));
      const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim() || '#ffffff';

      // 2. arrow heads are defined outside the exported layer — copy them in for the capture
      if (markers) {
        markerClone = markers.cloneNode(true);
        markerClone.style.cssText = 'position:absolute;width:0;height:0;overflow:visible';
        viewport.appendChild(markerClone);
      }

      // 3. render at scale 1 with the diagram's top-left corner at the margin → nothing can be cut off
      const url = await toPng(viewport, {
        backgroundColor: bg, width, height, pixelRatio: ratio, cacheBust: false, skipFonts: true, fontEmbedCSS: '',
        style: { width: width + 'px', height: height + 'px', transform: 'translate(' + (pad - b.x) + 'px, ' + (pad - b.y) + 'px) scale(1)', transformOrigin: '0 0', fontFamily: "'Segoe UI', system-ui, -apple-system, Arial, sans-serif" },
      });
      const a = document.createElement('a');
      a.download = filename + '.png';
      a.href = url;
      a.click();
    } catch (e) {
      console.error('PNG export failed', e);
      alert('Could not create the PNG: ' + (e?.message || e));
    } finally {
      markerClone?.remove();
      setBusy(false);
    }
  }, [rf, containerRef, filename]);

  return (
    <div className="diagram-tools">
      <button className="btn btn-sm" onClick={() => rf.fitView({ duration: 350, padding: 0.12 })}><Maximize2 size={14} /> Fit</button>
      <button className="btn btn-sm" onClick={exportPng} disabled={busy}><Download size={14} /> {busy ? 'Saving…' : 'PNG'}</button>
    </div>
  );
}

const hidden = { opacity: 0, width: 1, height: 1, minWidth: 1, minHeight: 1, border: 'none', background: 'transparent' };

/**
 * Invisible connection points on all four sides. Downward edges leave from the bottom ("b") and enter at the top ("t");
 * upward edges leave from the top ("st") and enter at the bottom ("tb"); same-row edges use the sides.
 */
export function Handles() {
  return (
    <>
      <Handle id="t" type="target" position={Position.Top} style={hidden} />
      <Handle id="st" type="source" position={Position.Top} style={hidden} />
      <Handle id="b" type="source" position={Position.Bottom} style={hidden} />
      <Handle id="tb" type="target" position={Position.Bottom} style={hidden} />
      <Handle id="l" type="target" position={Position.Left} style={hidden} />
      <Handle id="sl" type="source" position={Position.Left} style={hidden} />
      <Handle id="r" type="source" position={Position.Right} style={hidden} />
      <Handle id="tr" type="target" position={Position.Right} style={hidden} />
    </>
  );
}

/* ───────── class box (architecture) ───────── */
export function ClassNode({ data }) {
  const color = layerColor(data.layer);
  return (
    <div className={`dnode ${data.dim ? 'dim' : ''} ${data.selected ? 'sel' : ''} ${data.match ? 'match' : ''}`} style={{ '--c': color }}>
      <Handles />
      <div className="dnode-bar" />
      <div className="dnode-body">
        <div className="dnode-title" title={data.label}>{data.label}</div>
        <div className="dnode-sub">
          <span>{data.badge || layerLabel(data.layer)}</span>
          {data.module && <span className="dnode-mod">{data.module}</span>}
        </div>
      </div>
    </div>
  );
}

export function LaneNode({ data }) {
  return (
    <div className="lane" style={{ '--c': layerColor(data.layer), width: data.width, height: data.height }}>
      <div className="lane-label"><span className="dot" />{layerLabel(data.layer)} <em>{data.count}</em></div>
    </div>
  );
}

/* ───────── module card ───────── */
export function ModuleNode({ data }) {
  return (
    <div className={`mnode ${data.dim ? 'dim' : ''} ${data.selected ? 'sel' : ''}`}>
      <Handles />
      <div className="mnode-title">{data.label}</div>
      <div className="mnode-meta">{data.classes} classes{data.endpoints ? ` · ${data.endpoints} APIs` : ''}</div>
      <div className="mnode-bars">
        {data.layers.map(([l, n]) => <span key={l} style={{ background: layerColor(l), flexGrow: n }} title={`${n} ${layerLabel(l)}`} />)}
      </div>
    </div>
  );
}

/* ───────── flow step ───────── */
const KIND_ICON = {
  CLIENT: Monitor, FILTER: ShieldCheck, SECURITY: KeyRound, CONTROLLER: Globe, SERVICE: Cog, REPOSITORY: Archive,
  TABLE: Table2, RESPONSE: Reply, COMPONENT: Puzzle,
};
export const kindIcon = (k) => KIND_ICON[k] || Box;

export function StepNode({ data }) {
  const color = layerColor(data.kind);
  const Icon = kindIcon(data.kind);
  return (
    <div className={`snode ${data.dim ? 'dim' : ''} ${data.selected ? 'sel' : ''} ${data.compact ? 'compact' : ''}`} style={{ '--c': color, animationDelay: (data.delay || 0) + 'ms' }}>
      <Handles />
      <div className="snode-band">
        <Icon size={13} /><span>{data.kindLabel || layerLabel(data.kind)}</span><i>{data.num}</i>
      </div>
      <div className="snode-body">
        <div className="snode-title" title={data.title}>{data.title}</div>
        {data.subtitle && <div className="snode-sub" title={data.subtitle}>{data.subtitle}</div>}
        {data.access && <span className={`snode-access ${data.access === 'PUBLIC' ? 'good' : 'warn'}`}>{data.access === 'PUBLIC' ? 'public' : data.access === 'AUTHENTICATED' ? 'login required' : data.access.replace('ROLE:', 'role ').toLowerCase()}</span>}
      </div>
    </div>
  );
}

/* ───────── high-level architecture band: coloured header + name pills ───────── */
export function BandNode({ data }) {
  const Icon = kindIcon(data.layer);
  const card = data.variant === 'card';
  return (
    <div className={`band ${card ? 'band-card' : ''}`} style={{ '--c': layerColor(data.layer), width: data.width, minHeight: data.height, animationDelay: (data.delay || 0) + 'ms' }}>
      <Handles />
      <div className="band-head">
        <span className="band-ico"><Icon size={card ? 16 : 20} /></span>
        <div className="band-title"><b>{LAYER_PLURAL[data.layer] || layerLabel(data.layer)}</b><span>{LAYER_HELP[data.layer] || ''}</span></div>
        <em>{data.count}</em>
      </div>
      <div className="band-body">
        {data.items.map((it) => (
          <button key={it.id} className={`bchip ${data.selected === it.id ? 'on' : ''}`} onClick={(e) => { e.stopPropagation(); data.onPick?.(it.id); }} title={it.name}>{it.name}</button>
        ))}
        {data.more > 0 && <span className="bmore">+ {data.more} more</span>}
      </div>
    </div>
  );
}

/* ───────── ER table ───────── */
export function TableNode({ data }) {
  const rows = data.columns;
  return (
    <div className={`tnode ${data.dim ? 'dim' : ''} ${data.selected ? 'sel' : ''}`}>
      <Handles />
      <div className="tnode-head"><span>{data.label}</span><em>{data.columns.length}</em></div>
      <div className="tnode-rows">
        {rows.slice(0, data.maxRows).map((c) => (
          <div key={c.name} className="tnode-row">
            <span className="tnode-key">{c.primaryKey ? <i className="pk">PK</i> : c.foreignKey ? <i className="fk">FK</i> : null}</span>
            <span className="tnode-name">{c.name}</span>
            <span className="tnode-type">{shortType(c.type)}</span>
          </div>
        ))}
        {rows.length > data.maxRows && <div className="tnode-more">+ {rows.length - data.maxRows} more columns</div>}
      </div>
    </div>
  );
}

function shortType(t) { return (t || '').replace('VARCHAR', 'varchar').replace('INTEGER', 'int').replace('BIGINT', 'bigint').replace('TIMESTAMP', 'timestamp').replace('BOOLEAN', 'bool').toLowerCase(); }

export function CaptionNode({ data }) { return <div className="support-title">{data.text}</div>; }

export const nodeTypes = { caption: CaptionNode, cls: ClassNode, lane: LaneNode, mod: ModuleNode, step: StepNode, table: TableNode, band: BandNode };
