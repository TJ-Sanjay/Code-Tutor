import React, { useEffect, useMemo, useRef } from 'react';
import { Background, BackgroundVariant, Controls, MarkerType, ReactFlow, ReactFlowProvider, useReactFlow } from '@xyflow/react';
import { dagreLayout } from '../lib/layout';
import { layerColor } from '../lib/colors';
import { DiagramTools, nodeTypes, useThemeName } from './diagram/shared';

const EDGE_COLORS = { FILTER: '#f87171', SECURITY: '#f87171', TABLE: '#fb923c', RESPONSE: '#4ade80', CLIENT: '#38bdf8' };

/** Request flow drawn as a top-to-bottom diagram: browser → security → controller → services → repositories → tables. */
export default function FlowDiagram(props) {
  return <ReactFlowProvider><Inner {...props} /></ReactFlowProvider>;
}

function Inner({ flow, selected, onSelect, compact = false, filename = 'flow', tools = true, className = '' }) {
  const theme = useThemeName();
  const rf = useReactFlow();
  const wrapRef = useRef(null);
  const size = compact ? { w: 196, h: 70 } : { w: 236, h: 88 };

  const { nodes, edges } = useMemo(() => {
    if (!flow) return { nodes: [], edges: [] };
    const base = flow.steps.map((s) => ({ id: String(s.index), label: s.title }));
    const baseEdges = flow.steps.filter((s) => s.parent >= 0).map((s) => ({ source: String(s.parent), target: String(s.index) }));
    const lay = dagreLayout(base, baseEdges, { rankdir: 'TB', nodesep: compact ? 26 : 40, ranksep: compact ? 46 : 62, size: () => size });
    const hasSel = selected != null;
    const nodes = flow.steps.map((s) => ({
      id: String(s.index), type: 'step', position: lay.positions.get(String(s.index)), draggable: !compact,
      data: { num: s.index + 1, kind: s.kind, title: s.title, subtitle: s.subtitle, access: s.kind === 'CONTROLLER' || s.kind === 'SECURITY' ? s.access : null, dim: hasSel && selected !== s.index, selected: selected === s.index },
    }));
    const edges = flow.steps.filter((s) => s.parent >= 0).map((s) => {
      const color = EDGE_COLORS[s.kind] || layerColor(s.kind) || '#7c8cff';
      const active = selected === s.index || selected === s.parent;
      return {
        id: `f${s.index}`, source: String(s.parent), target: String(s.index), sourceHandle: 'b', targetHandle: 't', type: 'smoothstep',
        label: s.edgeLabel || undefined, animated: s.kind === 'TABLE' || s.kind === 'REPOSITORY',
        labelStyle: { fontSize: 10.5, fill: 'var(--text-dim)', fontWeight: 600 }, labelBgStyle: { fill: 'var(--bg)', fillOpacity: 0.92 }, labelBgPadding: [5, 2], labelBgBorderRadius: 5,
        style: { stroke: color, strokeWidth: active ? 2.6 : 1.7, opacity: hasSel && !active ? 0.15 : 0.9, strokeDasharray: s.kind === 'RESPONSE' ? '5 4' : undefined },
        markerEnd: { type: MarkerType.ArrowClosed, color, width: 15, height: 15 },
      };
    });
    return { nodes, edges };
  }, [flow, selected, compact]);

  useEffect(() => {
    const t = setTimeout(() => rf.fitView({ padding: compact ? 0.08 : 0.14, duration: 250, maxZoom: 1.1 }), 80);
    return () => clearTimeout(t);
  }, [flow, rf, compact]);

  return (
    <div className={`flow-canvas ${className}`} ref={wrapRef}>
      <ReactFlow
        nodes={nodes} edges={edges} nodeTypes={nodeTypes} colorMode={theme} fitView minZoom={0.2} maxZoom={1.6}
        nodesConnectable={false} proOptions={{ hideAttribution: true }}
        zoomOnScroll={!compact} panOnScroll={false} preventScrolling={!compact}
        onNodeClick={(_, n) => onSelect?.(Number(n.id) === selected ? null : Number(n.id))}
        onPaneClick={() => onSelect?.(null)}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1.2} color="var(--grid-dot)" />
        {!compact && <Controls showInteractive={false} />}
        {tools && <DiagramTools containerRef={wrapRef} filename={filename} />}
      </ReactFlow>
    </div>
  );
}
