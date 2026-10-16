import dagre from '@dagrejs/dagre';

/**
 * Layered ("swim-lane") layout: every node belongs to a lane (layer); lanes stack top → bottom.
 * Node order inside each lane is optimised with barycentre sweeps so connected nodes sit above/below each other and
 * edges cross as little as possible. Lanes with many nodes wrap into several rows.
 */
export function layeredLayout(nodes, edges, { lanes, laneOf, w = 196, h = 60, gx = 28, gy = 70, rowGap = 22, maxRow = 8, labelW = 0 }) {
  const laneIndex = new Map(lanes.map((l, i) => [l, i]));
  const byLane = lanes.map(() => []);
  const nodeLane = new Map();
  nodes.forEach((n) => {
    const li = laneIndex.get(laneOf(n));
    if (li == null) return;
    byLane[li].push(n);
    nodeLane.set(n.id, li);
  });

  const adj = new Map(nodes.map((n) => [n.id, []]));
  edges.forEach((e) => {
    if (adj.has(e.source) && adj.has(e.target)) {
      adj.get(e.source).push(e.target);
      adj.get(e.target).push(e.source);
    }
  });

  const cmp = (a, b) => (a.module || '').localeCompare(b.module || '') || a.label.localeCompare(b.label);
  byLane.forEach((l) => l.sort(cmp));

  const rank = new Map();
  const reindex = () => byLane.forEach((l) => l.forEach((n, i) => rank.set(n.id, l.length > 1 ? i / (l.length - 1) : 0.5)));
  reindex();

  for (let it = 0; it < 8; it++) {
    const order = it % 2 === 0 ? byLane.map((_, i) => i) : byLane.map((_, i) => byLane.length - 1 - i);
    for (const li of order) {
      const lane = byLane[li];
      if (lane.length < 2) continue;
      const bc = new Map();
      lane.forEach((n) => {
        const nb = adj.get(n.id).filter((id) => nodeLane.get(id) !== li);
        bc.set(n.id, nb.length ? nb.reduce((s, id) => s + (rank.get(id) ?? 0.5), 0) / nb.length : rank.get(n.id));
      });
      lane.sort((a, b) => bc.get(a.id) - bc.get(b.id) || cmp(a, b));
      lane.forEach((n, i) => rank.set(n.id, lane.length > 1 ? i / (lane.length - 1) : 0.5));
    }
  }

  const positions = new Map();
  const laneBoxes = [];
  let y = 0;
  let maxWidth = 0;
  const widest = Math.max(1, ...byLane.map((l) => Math.min(l.length, maxRow)));
  const totalW = widest * (w + gx) - gx;
  byLane.forEach((lane, li) => {
    if (!lane.length) return;
    const rows = Math.ceil(lane.length / maxRow);
    const perRow = Math.ceil(lane.length / rows);
    const top = y;
    for (let r = 0; r < rows; r++) {
      const slice = lane.slice(r * perRow, (r + 1) * perRow);
      const rowW = slice.length * (w + gx) - gx;
      const x0 = labelW + (totalW - rowW) / 2;
      slice.forEach((n, i) => positions.set(n.id, { x: x0 + i * (w + gx), y: y + r * (h + rowGap) }));
    }
    const height = rows * h + (rows - 1) * rowGap;
    laneBoxes.push({ lane: lanes[li], x: 0, y: top - 26, width: labelW + totalW, height: height + 52, count: lane.length });
    y += height + gy;
    maxWidth = Math.max(maxWidth, labelW + totalW);
  });
  return { positions, laneBoxes, width: maxWidth, height: y };
}

/** Directed-graph layout through dagre. size(node) → {w, h}; returns Map(id → top-left position). */
export function dagreLayout(nodes, edges, { rankdir = 'TB', nodesep = 36, ranksep = 64, size }) {
  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir, nodesep, ranksep, marginx: 20, marginy: 20, ranker: 'network-simplex' });
  g.setDefaultEdgeLabel(() => ({}));
  nodes.forEach((n) => {
    const s = size(n);
    g.setNode(n.id, { width: s.w, height: s.h });
  });
  edges.forEach((e) => {
    if (g.hasNode(e.source) && g.hasNode(e.target) && e.source !== e.target) g.setEdge(e.source, e.target);
  });
  dagre.layout(g);
  const positions = new Map();
  nodes.forEach((n) => {
    const p = g.node(n.id);
    const s = size(n);
    positions.set(n.id, { x: p.x - s.w / 2, y: p.y - s.h / 2 });
  });
  const gi = g.graph();
  return { positions, width: gi.width, height: gi.height };
}
