import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Background, BackgroundVariant, BaseEdge, Controls, EdgeLabelRenderer, MarkerType, ReactFlow, ReactFlowProvider, getBezierPath, useReactFlow } from '@xyflow/react';
import { Database, Search } from 'lucide-react';
import { get } from '../api';
import { useWorkspace } from '../pages/wsContext';
import { dagreLayout } from '../lib/layout';
import { Empty, Spinner } from '../ui';
import { DiagramTools, nodeTypes as baseTypes, useThemeName } from './diagram/shared';
import ExplainCard from './ExplainCard';

/** Edge with crow's-foot style cardinality labels: "N" at the FK side, "1" at the referenced side. */
function RelationEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data, style, markerEnd }) {
  const [path] = getBezierPath({ sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition });
  const dx = targetX - sourceX;
  const dy = targetY - sourceY;
  const len = Math.max(1, Math.hypot(dx, dy));
  const off = 26;
  const a = { x: sourceX + (dx / len) * off, y: sourceY + (dy / len) * off };
  const b = { x: targetX - (dx / len) * off, y: targetY - (dy / len) * off };
  return (
    <>
      <BaseEdge id={id} path={path} style={style} markerEnd={markerEnd} />
      <EdgeLabelRenderer>
        <div className="card-badge" style={{ transform: `translate(-50%, -50%) translate(${a.x}px, ${a.y - 12}px)` }}>{data?.from || 'N'}</div>
        <div className="card-badge" style={{ transform: `translate(-50%, -50%) translate(${b.x}px, ${b.y - 12}px)` }}>{data?.to || '1'}</div>
        {data?.label && <div className="edge-col" style={{ transform: `translate(-50%, -50%) translate(${(sourceX + targetX) / 2}px, ${(sourceY + targetY) / 2}px)` }}>{data.label}</div>}
      </EdgeLabelRenderer>
    </>
  );
}

const nodeTypes = baseTypes;
const edgeTypes = { relation: RelationEdge };

export default function DatabaseTab() { return <ReactFlowProvider><Inner /></ReactFlowProvider>; }

function Inner() {
  const { projectId, overview, openCode, follow } = useWorkspace();
  const theme = useThemeName();
  const rf = useReactFlow();
  const wrapRef = useRef(null);
  const [params, setParams] = useSearchParams();
  const [schema, setSchema] = useState(null);
  const [q, setQ] = useState('');
  const [allCols, setAllCols] = useState(false);
  const [detail, setDetail] = useState(null);
  const selected = params.get('id');

  useEffect(() => { get(`/projects/${projectId}/schema`).then(setSchema); }, [projectId]);

  const { nodes, edges } = useMemo(() => {
    if (!schema) return { nodes: [], edges: [] };
    const needle = q.trim().toLowerCase();
    const maxRows = allCols ? 60 : 12;
    const rows = (t) => Math.min(t.columns.length, maxRows) + (t.columns.length > maxRows ? 1 : 0);
    const size = (t) => ({ w: 250, h: 44 + rows(t) * 26 + 8 });
    const tnodes = schema.tables.map((t) => ({ id: t.name, label: t.name, ...t }));
    const lay = dagreLayout(tnodes, schema.relations.map((r) => ({ source: r.fromTable, target: r.toTable })), { rankdir: 'LR', nodesep: 46, ranksep: 130, size });
    const neighbours = new Set();
    if (selected) {
      neighbours.add(selected);
      schema.relations.forEach((r) => { if (r.fromTable === selected) neighbours.add(r.toTable); if (r.toTable === selected) neighbours.add(r.fromTable); });
    }
    const nodes = schema.tables.map((t) => ({
      id: t.name, type: 'table', position: lay.positions.get(t.name),
      data: { label: t.name, columns: t.columns, maxRows, dim: (selected && !neighbours.has(t.name)) || (needle && !t.name.toLowerCase().includes(needle) && !t.columns.some((c) => c.name.toLowerCase().includes(needle))), selected: selected === t.name },
    }));
    const edges = schema.relations.map((r, i) => {
      const active = selected && (r.fromTable === selected || r.toTable === selected);
      const one = r.type === 'ONE_TO_ONE';
      return {
        id: `r${i}`, type: 'relation', source: r.fromTable, target: r.toTable, sourceHandle: 'r', targetHandle: 'l',
        data: { from: one ? '1' : 'N', to: '1', label: active ? r.fromColumn : null },
        style: { stroke: active ? 'var(--accent)' : '#8b93a7', strokeWidth: active ? 2.4 : 1.6, opacity: selected && !active ? 0.12 : 0.9 },
        markerEnd: { type: MarkerType.ArrowClosed, color: active ? 'var(--accent)' : '#8b93a7', width: 14, height: 14 },
      };
    });
    return { nodes, edges };
  }, [schema, selected, q, allCols]);

  useEffect(() => { const t = setTimeout(() => rf.fitView({ padding: 0.12, duration: 300 }), 80); return () => clearTimeout(t); }, [schema, allCols, rf]);

  useEffect(() => {
    setDetail(null);
    if (selected) get(`/projects/${projectId}/explain`, { kind: 'table', id: selected }).then(setDetail).catch(() => setDetail(null));
  }, [selected, projectId]);

  if (!schema) return <div className="ws-center"><Spinner lg /></div>;
  if (schema.tables.length === 0) {
    return <Empty icon={<Database size={26} />} title="No database tables found">The ER diagram is built from JPA entities and SQL <code>CREATE TABLE</code> scripts. None were found in this project.</Empty>;
  }

  return (
    <div className="diagram-wrap" ref={wrapRef}>
      <div className="diagram-toolbar">
        <span className="toolbar-title"><Database size={15} /> {schema.tables.length} tables · {schema.relations.length} relationships</span>
        <div style={{ flex: 1 }} />
        <label className="check"><input type="checkbox" checked={allCols} onChange={(e) => setAllCols(e.target.checked)} /> show all columns</label>
        <div className="toolbar-search"><Search size={14} /><input placeholder="Find table or column…" value={q} onChange={(e) => setQ(e.target.value)} /></div>
      </div>
      <div className="diagram-canvas">
        <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} edgeTypes={edgeTypes} colorMode={theme} fitView minZoom={0.15} maxZoom={1.5}
          nodesConnectable={false} proOptions={{ hideAttribution: true }}
          onNodeClick={(_, n) => setParams(n.id === selected ? {} : { id: n.id })} onPaneClick={() => setParams({})}>
          <Background variant={BackgroundVariant.Dots} gap={22} size={1.2} color="var(--grid-dot)" />
          <Controls showInteractive={false} />
          <DiagramTools containerRef={wrapRef} filename={`${overview.overview.name}-er-diagram`} />
        </ReactFlow>
        <div className="diagram-legend">
          <span><b className="pk-key">PK</b> primary key</span><span><b className="fk-key">FK</b> foreign key</span><span><b>N ─ 1</b> many rows reference one row</span>
        </div>
        {detail ? <div className="diagram-panel"><ExplainCard data={detail} onClose={() => setParams({})} onLink={follow} /></div>
          : <div className="diagram-hint">Click a table to see its columns, constraints and relationships.</div>}
      </div>
    </div>
  );
}
