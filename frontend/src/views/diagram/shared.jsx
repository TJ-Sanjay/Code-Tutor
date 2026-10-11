import React, { useCallback, useEffect, useState } from 'react';
import '@xyflow/react/dist/style.css';
import { Handle, Position, getNodesBounds, getViewportForBounds, useReactFlow } from '@xyflow/react';
import { toPng } from 'html-to-image';
import { Download, Maximize2 } from 'lucide-react';
import { layerColor, layerLabel } from '../../lib/colors';

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

/** Fit / export buttons — must be rendered inside <ReactFlow>. */
export function DiagramTools({ containerRef, filename = 'diagram' }) {
  const rf = useReactFlow();
  const exportPng = useCallback(async () => {
    const nodes = rf.getNodes().filter((n) => !n.hidden);
    if (!nodes.length || !containerRef.current) return;
    const bounds = getNodesBounds(nodes);
    const pad = 60;
    const width = Math.ceil(bounds.width + pad * 2);
    const height = Math.ceil(bounds.height + pad * 2);
    const vp = getViewportForBounds(bounds, width, height, 0.1, 2, 0.08);
    const el = containerRef.current.querySelector('.react-flow__viewport');
    const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim() || '#0b0d12';
    try {
      const url = await toPng(el, {
        backgroundColor: bg, width, height, pixelRatio: 2,
        style: { width: `${width}px`, height: `${height}px`, transform: `translate(${vp.x}px, ${vp.y}px) scale(${vp.zoom})` },
        filter: (n) => !(n.classList && (n.classList.contains('react-flow__minimap') || n.classList.contains('react-flow__controls'))),
      });
      const a = document.createElement('a');
      a.download = `${filename}.png`;
      a.href = url;
      a.click();
    } catch (e) {
      console.error('PNG export failed', e);
    }
  }, [rf, containerRef, filename]);

  return (
    <div className="diagram-tools">
      <button className="btn btn-sm" onClick={() => rf.fitView({ duration: 350, padding: 0.12 })}><Maximize2 size={14} /> Fit</button>
      <button className="btn btn-sm" onClick={exportPng}><Download size={14} /> PNG</button>
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
export function StepNode({ data }) {
  const color = layerColor(data.kind);
  return (
    <div className={`snode ${data.dim ? 'dim' : ''} ${data.selected ? 'sel' : ''}`} style={{ '--c': color }}>
      <Handles />
      <div className="snode-top"><span className="snode-num">{data.num}</span><span className="snode-kind">{layerLabel(data.kind)}</span></div>
      <div className="snode-title" title={data.title}>{data.title}</div>
      {data.subtitle && <div className="snode-sub" title={data.subtitle}>{data.subtitle}</div>}
      {data.access && <div className={`snode-access ${data.access === 'PUBLIC' ? 'good' : 'warn'}`}>{data.access === 'PUBLIC' ? 'public' : data.access === 'AUTHENTICATED' ? 'login required' : data.access.toLowerCase()}</div>}
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

export const nodeTypes = { cls: ClassNode, lane: LaneNode, mod: ModuleNode, step: StepNode, table: TableNode };
