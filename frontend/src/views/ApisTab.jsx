import React, { useEffect, useMemo, useState } from 'react';
import { ChevronRight, Code2, Plug, Route, Search } from 'lucide-react';
import { get } from '../api';
import { useWorkspace } from '../pages/wsContext';
import { accessInfo, methodColor } from '../lib/colors';
import { Chip, Empty, Spinner } from '../ui';

export default function ApisTab() {
  const { projectId, overview, go, openCode } = useWorkspace();
  const [eps, setEps] = useState(null);
  const [q, setQ] = useState('');
  const [method, setMethod] = useState('ALL');
  const [ctrl, setCtrl] = useState('ALL');
  const [open, setOpen] = useState(null);
  const secOn = overview.security?.enabled;

  useEffect(() => { get(`/projects/${projectId}/endpoints`).then(setEps); }, [projectId]);

  const rows = useMemo(() => {
    if (!eps) return [];
    const needle = q.trim().toLowerCase();
    return eps.filter((e) => (method === 'ALL' || e.httpMethod === method)
      && (ctrl === 'ALL' || e.controller === ctrl)
      && (!needle || `${e.httpMethod} ${e.path} ${e.handler} ${e.description || ''}`.toLowerCase().includes(needle)));
  }, [eps, q, method, ctrl]);

  if (!eps) return <div className="ws-center"><Spinner lg /></div>;
  if (!eps.length) return <Empty icon={<Plug size={26} />} title="No endpoints detected">No <code>@RestController</code> / <code>@Controller</code> request mappings were found in this project.</Empty>;

  const methods = ['ALL', ...new Set(eps.map((e) => e.httpMethod))];
  const controllers = [...new Set(eps.map((e) => e.controller))];
  const short = (id) => id.substring(id.lastIndexOf('.') + 1);

  return (
    <div className="page">
      <div className="page-head">
        <div><h2>API endpoints</h2><p>{eps.length} endpoints exposed by {controllers.length} controller{controllers.length > 1 ? 's' : ''}{secOn ? ' · access level comes from the security rules' : ''}</p></div>
      </div>
      <div className="filter-row">
        <div className="toolbar-search wide"><Search size={14} /><input placeholder="Search path, handler or description…" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div className="seg">{methods.map((m) => <button key={m} className={method === m ? 'on' : ''} onClick={() => setMethod(m)}>{m === 'ALL' ? 'All' : m}</button>)}</div>
        <select className="select toolbar-select" value={ctrl} onChange={(e) => setCtrl(e.target.value)}>
          <option value="ALL">All controllers</option>
          {controllers.map((c) => <option key={c} value={c}>{short(c)}</option>)}
        </select>
      </div>

      <div className="card api-table">
        <div className="api-row api-row-head"><span /><span>Method</span><span>Path</span><span>Handler</span><span>{secOn ? 'Access' : ''}</span><span /></div>
        {rows.map((e) => {
          const a = accessInfo(e.access);
          const isOpen = open === e.id;
          return (
            <div key={e.id} className={`api-item ${isOpen ? 'open' : ''}`}>
              <div className="api-row" onClick={() => setOpen(isOpen ? null : e.id)}>
                <ChevronRight size={15} className="chev" />
                <span className="badge-method" style={{ '--c': methodColor(e.httpMethod) }}>{e.httpMethod}</span>
                <code className="api-path">{e.fullPath}</code>
                <span className="api-handler">{short(e.controller)}.{e.handler.substring(e.handler.indexOf('#') + 1, e.handler.indexOf('('))}()</span>
                {secOn ? <Chip color={`var(--${a.tone})`}>{a.label}</Chip> : <span />}
                <span className="api-actions" onClick={(ev) => ev.stopPropagation()}>
                  <button className="btn btn-sm" onClick={() => go('flow', { endpoint: e.id })}><Route size={13} /> Flow</button>
                  <button className="btn btn-sm btn-ghost" onClick={() => openCode(e.file, e.line, e.line + 8)}><Code2 size={13} /> Code</button>
                </span>
              </div>
              {isOpen && (
                <div className="api-detail">
                  {e.description && <p>{e.description}</p>}
                  <div className="api-meta">
                    <div><span>Returns</span><b>{e.viewResponse ? 'an HTML page (view)' : e.returns || 'void'}</b></div>
                    {e.accessNote && <div><span>Method security</span><code>{e.accessNote}</code></div>}
                    <div><span>Defined in</span><code>{e.file.substring(e.file.lastIndexOf('/') + 1)}:{e.line}</code></div>
                  </div>
                  {e.params?.length > 0 && (
                    <table className="mini-table"><thead><tr><th>Input</th><th>Type</th><th>Where</th><th>Required</th></tr></thead>
                      <tbody>{e.params.map((p) => <tr key={p.name + p.source}><td><code>{p.name}</code></td><td><code>{p.type}</code></td><td>{p.source}</td><td>{p.required ? 'yes' : 'no'}</td></tr>)}</tbody></table>
                  )}
                </div>
              )}
            </div>
          );
        })}
        {rows.length === 0 && <div className="empty" style={{ padding: 28 }}>No endpoint matches your filters.</div>}
      </div>
    </div>
  );
}
