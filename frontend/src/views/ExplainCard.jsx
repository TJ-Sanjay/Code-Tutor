import React from 'react';
import { Braces, Code2, Database, FileText, Globe, Lock, Route, Variable, X, Box } from 'lucide-react';
import { layerColor, layerLabel } from '../lib/colors';

const ICONS = { class: Box, interface: Box, enum: Box, record: Box, method: Braces, constructor: Braces, field: Variable, parameter: Variable, file: FileText, endpoint: Globe, table: Database };

/**
 * The small "what is this?" popup: a plain-language summary plus facts and clickable links.
 * Used when clicking a file, class, method or field in the code viewer and in the diagrams.
 */
export default function ExplainCard({ data, onClose, onLink, style, className = '', compact }) {
  if (!data) return null;
  const Icon = ICONS[data.kind] || Code2;
  const color = data.layer ? layerColor(data.layer) : 'var(--accent)';
  const groups = [];
  (data.links || []).forEach((l) => {
    let g = groups.find((x) => x.name === l.group);
    if (!g) { g = { name: l.group, items: [] }; groups.push(g); }
    g.items.push(l);
  });
  const primary = (data.links || []).find((l) => l.kind === 'flow');

  return (
    <div className={`explain ${className}`} style={{ '--c': color, ...style }} onMouseDown={(e) => e.stopPropagation()}>
      <div className="explain-head">
        <span className="explain-ico"><Icon size={16} /></span>
        <div className="explain-titles">
          <div className="explain-title" title={data.title}>{data.title}</div>
          {data.subtitle && <div className="explain-sub" title={data.subtitle}>{data.subtitle}</div>}
        </div>
        {onClose && <button className="icon-btn" onClick={onClose} style={{ width: 26, height: 26 }}><X size={15} /></button>}
      </div>
      <div className="explain-tags">
        <span className="explain-kind">{data.kind}</span>
        {data.layer && !['file', 'parameter', 'field'].includes(data.kind) && <span className="explain-layer"><span className="dot" />{layerLabel(data.layer)}</span>}
      </div>
      <p className="explain-summary">{data.summary}</p>
      {!compact && data.facts?.length > 0 && (
        <ul className="explain-facts">
          {data.facts.slice(0, 7).map((f, i) => <li key={i}>{f}</li>)}
        </ul>
      )}
      {groups.map((g) => (
        <div key={g.name} className="explain-group">
          <span className="explain-group-name">{g.name}</span>
          <div>
            {g.items.map((l, i) => (
              <button key={l.id + i} className={`explain-link ${l.kind === 'flow' ? 'flow' : ''}`} onClick={() => onLink?.(l)} title={l.file ? `${l.file}:${l.line}` : ''}>
                {l.kind === 'flow' ? <Route size={12} /> : null}{l.label}
              </button>
            ))}
          </div>
        </div>
      ))}
      <div className="explain-foot">
        {data.file && data.line > 0 && data.kind !== 'table' && <button className="btn btn-sm" onClick={() => onLink?.({ kind: 'code', file: data.file, line: data.line, endLine: data.endLine })}><Code2 size={14} /> Show code</button>}
        {primary && <button className="btn btn-sm btn-primary" onClick={() => onLink?.(primary)}><Route size={14} /> Data flow</button>}
        {data.kind === 'endpoint' && !primary && null}
        {data.file && <span className="explain-loc"><Lock size={11} /> read-only</span>}
      </div>
    </div>
  );
}
