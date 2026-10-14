import React, { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Code2, KeyRound, Route, Search, ShieldCheck } from 'lucide-react';
import { get } from '../api';
import { useWorkspace } from '../pages/wsContext';
import { layerColor, layerLabel, accessInfo, methodColor } from '../lib/colors';
import { Empty, Markdown, Spinner } from '../ui';
import FlowDiagram from './FlowDiagram';

export default function FlowTab() {
  const { projectId, overview, openCode } = useWorkspace();
  const [params, setParams] = useSearchParams();
  const [endpoints, setEndpoints] = useState(null);
  const [flow, setFlow] = useState(null);
  const [loading, setLoading] = useState(false);
  const [q, setQ] = useState('');
  const [sel, setSel] = useState(null);
  const [collapse, setCollapse] = useState(true);
  const sec = overview.security;
  const current = params.get('endpoint');

  useEffect(() => { get(`/projects/${projectId}/endpoints`).then(setEndpoints); }, [projectId]);

  // choose a sensible default: the login flow if there is one, otherwise the endpoint with the richest flow
  useEffect(() => {
    if (!endpoints || current) return;
    const def = sec?.loginEndpoint || (endpoints[0] && endpoints[0].id) || (sec?.enabled ? 'security' : null);
    if (def) setParams({ endpoint: def }, { replace: true });
  }, [endpoints, current, sec, setParams]);

  useEffect(() => {
    if (!current) return;
    setLoading(true); setSel(null);
    get(`/projects/${projectId}/flow`, { endpoint: current }).then(setFlow).catch(() => setFlow(null)).finally(() => setLoading(false));
  }, [current, projectId]);

  const grouped = useMemo(() => {
    if (!endpoints) return [];
    const needle = q.trim().toLowerCase();
    const map = new Map();
    endpoints.forEach((e) => {
      const ctrl = e.controller.substring(e.controller.lastIndexOf('.') + 1);
      if (needle && !(`${e.httpMethod} ${e.path} ${ctrl} ${e.handler}`).toLowerCase().includes(needle)) return;
      if (!map.has(ctrl)) map.set(ctrl, []);
      map.get(ctrl).push(e);
    });
    return [...map.entries()];
  }, [endpoints, q]);

  if (!endpoints) return <div className="ws-center"><Spinner lg /></div>;
  if (endpoints.length === 0 && !sec?.enabled) {
    return <Empty icon={<Route size={26} />} title="No endpoints found">Data flows are traced from REST / web endpoints. This project has none that could be detected.</Empty>;
  }

  const step = flow && sel != null ? flow.steps.find((s) => s.index === sel) : null;

  return (
    <div className="split">
      <aside className="split-side">
        <div className="side-search"><Search size={14} /><input placeholder="Filter endpoints…" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div className="side-list">
          <div className="side-group">Special flows</div>
          <button className={`flow-item ${current === 'security' ? 'on' : ''}`} onClick={() => setParams({ endpoint: 'security' })}>
            <ShieldCheck size={15} color="#f87171" /><div><b>How security works</b><span>Filter chain &amp; authorization rules</span></div>
          </button>
          {sec?.loginEndpoint && (
            <button className={`flow-item ${current === sec.loginEndpoint ? 'on' : ''}`} onClick={() => setParams({ endpoint: sec.loginEndpoint })}>
              <KeyRound size={15} color="#fbbf24" /><div><b>Login flow</b><span>From the form to the token</span></div>
            </button>
          )}
          {grouped.map(([ctrl, list]) => (
            <div key={ctrl}>
              <div className="side-group">{ctrl}</div>
              {list.map((e) => {
                const a = accessInfo(e.access);
                return (
                  <button key={e.id} className={`flow-item ${current === e.id ? 'on' : ''}`} onClick={() => setParams({ endpoint: e.id })}>
                    <span className="badge-method" style={{ '--c': methodColor(e.httpMethod) }}>{e.httpMethod}</span>
                    <div><b className="mono">{e.path}</b></div>
                    {sec?.enabled && <span className={`dot tone-${a.tone}`} title={a.label} />}
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      </aside>

      <section className="split-main">
        {loading && <div className="ws-center"><Spinner lg /></div>}
        {!loading && flow && (
          <>
            <div className="flow-head">
              <div>
                <h2>{flow.title}</h2>
                <span className="mono dim">{flow.subtitle}</span>
              </div>
              {flow.steps.filter((x) => x.kind === 'FILTER').length > 1 && (
                  <label className="check"><input type="checkbox" checked={!collapse} onChange={(e) => setCollapse(!e.target.checked)} /> show each security filter</label>
                )}
                <div className="flow-legend">
                {['CLIENT', 'FILTER', 'CONTROLLER', 'SERVICE', 'REPOSITORY', 'TABLE'].map((k) => <span key={k}><i style={{ background: layerColor(k) }} />{layerLabel(k)}</span>)}
              </div>
            </div>
            <div className="flow-body">
              <FlowDiagram flow={flow} selected={sel} onSelect={setSel} collapseFilters={collapse} filename={`flow-${flow.title.replace(/[^a-z0-9]+/gi, '-')}`} />
              <aside className="flow-steps">
                {flow.notes?.length > 0 && <div className="flow-notes">{flow.notes.map((n, i) => <p key={i}>{n}</p>)}</div>}
                <div className="card-title" style={{ margin: '4px 0 8px' }}>Step by step</div>
                <ol>
                  {flow.steps.filter((s) => s.kind !== 'TABLE' || true).map((s) => (
                    <li key={s.index} className={sel === s.index ? 'on' : ''} onClick={() => setSel(sel === s.index ? null : s.index)} style={{ '--c': layerColor(s.kind) }}>
                      <span className="num">{s.index + 1}</span>
                      <div>
                        <b>{s.title}</b>
                        <em>{layerLabel(s.kind)}{s.edgeLabel ? ` · ${s.edgeLabel}` : ''}</em>
                        {sel === s.index && (
                          <div className="step-detail">
                            <Markdown>{s.description}</Markdown>
                            {s.file && s.line > 0 && (
                              <button className="btn btn-sm" onClick={(e) => { e.stopPropagation(); openCode(s.file, s.line, Math.max(s.line, s.endLine)); }}><Code2 size={14} /> Show code ({s.file.substring(s.file.lastIndexOf('/') + 1)}:{s.line})</button>
                            )}
                          </div>
                        )}
                      </div>
                    </li>
                  ))}
                </ol>
              </aside>
            </div>
          </>
        )}
        {!loading && !flow && <Empty icon={<Route size={26} />} title="Pick a flow on the left">Choose an endpoint to see exactly how a request travels through the code.</Empty>}
      </section>
    </div>
  );
}
