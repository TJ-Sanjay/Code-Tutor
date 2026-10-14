import React, { useEffect, useMemo, useRef } from 'react';
import { Background, BackgroundVariant, Controls, MarkerType, ReactFlow, ReactFlowProvider, useReactFlow } from '@xyflow/react';
import { dagreLayout } from '../lib/layout';
import { layerColor } from '../lib/colors';
import { DiagramTools, nodeTypes, useThemeName } from './diagram/shared';

/**
 * Turns the raw step list into what is drawn. With `collapseFilters`, the run of security filters
 * (Cors → Jwt → Exception translation …) becomes ONE node, which keeps the picture short and easy to read.
 */
export function buildView(flow, collapseFilters) {
  const steps = flow.steps;
  if (!collapseFilters) {
    return { nodes: steps.map((s) => ({ ...s, id: s.index, num: String(s.index + 1), first: s.index })), toNew: new Map(steps.map((s) => [s.index, s.index])) };
  }
  const nodes = [];
  const toNew = new Map();
  let i = 0;
  while (i < steps.length) {
    const s = steps[i];
    if (s.kind === 'FILTER') {
      let j = i;
      while (j + 1 < steps.length && steps[j + 1].kind === 'FILTER') j++;
      const group = steps.slice(i, j + 1);
      const id = nodes.length;
      group.forEach((g) => toNew.set(g.index, id));
      nodes.push({
        id, index: s.index, first: s.index, parent: s.parent, kind: 'FILTER', num: group.length > 1 ? `${s.index + 1}–${steps[j].index + 1}` : String(s.index + 1),
        title: group.length > 1 ? 'Security filters' : s.title,
        subtitle: group.length > 1 ? group.map((g) => g.title.replace(/Filter$/, '')).join(' → ') : s.subtitle,
        edgeLabel: s.edgeLabel, group,
      });
      i = j + 1;
    } else {
      const id = nodes.length;
      toNew.set(s.index, id);
      nodes.push({ ...s, id, num: String(s.index + 1), first: s.index });
      i++;
    }
  }
  nodes.forEach((n) => { n.parentNew = n.parent >= 0 ? toNew.get(n.parent) : -1; });
  return { nodes, toNew };
}

/** Request flow drawn top to bottom: browser → security → controller → services → repositories → tables. */
export default function FlowDiagram(props) {
  return <ReactFlowProvider><Inner {...props} /></ReactFlowProvider>;
}

function Inner({ flow, selected, onSelect, compact = false, collapseFilters = true, filename = 'flow', tools = true, className = '' }) {
  const theme = useThemeName();
  const rf = useReactFlow();
  const wrapRef = useRef(null);
  const size = compact ? { w: 200, h: 74 } : { w: 244, h: 96 };

  const { nodes, edges } = useMemo(() => {
    if (!flow) return { nodes: [], edges: [] };
    const view = buildView(flow, collapseFilters);
    const vn = view.nodes.map((n) => ({ ...n, parentNew: n.parentNew ?? n.parent }));
    const base = vn.map((n) => ({ id: String(n.id), label: n.title }));
    const baseEdges = vn.filter((n) => n.parentNew >= 0).map((n) => ({ source: String(n.parentNew), target: String(n.id) }));
    const lay = dagreLayout(base, baseEdges, { rankdir: 'TB', nodesep: compact ? 28 : 44, ranksep: compact ? 52 : 74, size: () => size });
    const selNew = selected == null ? null : view.toNew.get(selected);
    const hasSel = selNew != null;
    const nodes = vn.map((n) => ({
      id: String(n.id), type: 'step', position: lay.positions.get(String(n.id)), draggable: !compact,
      data: {
        num: n.num, kind: n.kind, title: n.title, subtitle: n.subtitle, compact, delay: n.id * 55,
        access: n.kind === 'CONTROLLER' || n.kind === 'SECURITY' ? n.access : null,
        dim: hasSel && selNew !== n.id, selected: selNew === n.id,
      },
    }));
    const edges = vn.filter((n) => n.parentNew >= 0).map((n) => {
      const color = layerColor(n.kind);
      const active = hasSel && (selNew === n.id || selNew === n.parentNew);
      return {
        id: `f${n.id}`, source: String(n.parentNew), target: String(n.id), sourceHandle: 'b', targetHandle: 't', type: 'smoothstep',
        label: n.edgeLabel || undefined, animated: true,
        labelStyle: { fontSize: 11, fill: 'var(--text-dim)', fontWeight: 600 }, labelBgStyle: { fill: 'var(--bg)', fillOpacity: 1 }, labelBgPadding: [7, 3], labelBgBorderRadius: 7,
        style: { stroke: color, strokeWidth: active ? 3 : 2, opacity: hasSel && !active ? 0.15 : 0.95 },
        markerEnd: { type: MarkerType.ArrowClosed, color, width: 16, height: 16 },
      };
    });
    return { nodes, edges, view };
  }, [flow, selected, compact, collapseFilters]);

  useEffect(() => {
    const t = setTimeout(() => rf.fitView({ padding: compact ? 0.08 : 0.14, duration: 250, maxZoom: 1.1 }), 90);
    return () => clearTimeout(t);
  }, [flow, rf, compact, collapseFilters]);

  const pick = (_, n) => {
    const view = buildView(flow, collapseFilters);
    const node = view.nodes[Number(n.id)];
    const old = node ? node.first : Number(n.id);
    onSelect?.(old === selected ? null : old);
  };

  return (
    <div className={`flow-canvas ${className}`} ref={wrapRef}>
      <ReactFlow
        nodes={nodes} edges={edges} nodeTypes={nodeTypes} colorMode={theme} fitView minZoom={0.2} maxZoom={1.6}
        nodesConnectable={false} proOptions={{ hideAttribution: true }}
        zoomOnScroll={!compact} panOnScroll={false} preventScrolling={!compact}
        onNodeClick={pick} onPaneClick={() => onSelect?.(null)}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1.2} color="var(--grid-dot)" />
        {!compact && <Controls showInteractive={false} />}
        {tools && <DiagramTools containerRef={wrapRef} filename={filename} />}
      </ReactFlow>
    </div>
  );
}
