import React, { useEffect, useState } from 'react';
import { ArrowRight, Boxes, Braces, Database, FileCode2, FolderTree, KeyRound, Layers, MessageSquareCode, Network, Plug, Route, ShieldAlert, ShieldCheck, Sparkles } from 'lucide-react';
import { get, post, errMsg } from '../api';
import { useWorkspace } from '../pages/wsContext';
import { LAYERS, langColor, layerColor, layerLabel } from '../lib/colors';
import { formatNumber } from '../lib/format';
import { Chip, useToast } from '../ui';

export default function OverviewTab() {
  const { projectId, overview: data, go, ask, reload } = useWorkspace();
  const toast = useToast();
  const o = data.overview;
  const [suggestions, setSuggestions] = useState([]);
  const [granting, setGranting] = useState(false);

  useEffect(() => { get(`/projects/${projectId}/chat/suggestions`).then((s) => setSuggestions(s.questions)).catch(() => {}); }, [projectId]);

  const langs = Object.entries(o.languages).slice(0, 7);
  const layerRows = Object.entries(o.layerCounts).filter(([l]) => l !== 'TEST').sort((a, b) => b[1] - a[1]);
  const maxLayer = Math.max(1, ...layerRows.map(([, n]) => n));
  const sec = data.security;

  const grant = async () => {
    setGranting(true);
    try { await post(`/projects/${projectId}/sensitive-access`, { paths: o.sensitiveFiles }); toast('Access granted — re-analysing the project'); await reload(); window.location.reload(); }
    catch (e) { toast(errMsg(e), 'bad'); setGranting(false); }
  };

  const stats = [
    { icon: FileCode2, label: 'Files', value: o.totalFiles },
    { icon: Braces, label: 'Lines of code', value: o.totalLines },
    { icon: Boxes, label: 'Classes', value: o.classCount },
    { icon: Layers, label: 'Methods', value: o.methodCount },
    { icon: Plug, label: 'Endpoints', value: o.endpointCount },
    { icon: Database, label: 'Tables', value: o.tableCount },
    { icon: Network, label: 'Modules', value: o.moduleCount },
  ];

  return (
    <div className="page">
      <section className="hero card">
        <div className="hero-text">
          <span className="eyebrow"><Sparkles size={13} /> Project summary</span>
          <h1>{o.name}</h1>
          <p>{o.summary}</p>
          <div className="chip-row">
            {[...o.frameworks, ...o.databases].map((t) => <Chip key={t} color="var(--accent)">{t}</Chip>)}
            {o.buildTools.map((t) => <Chip key={t} color="#2dd4bf">{t}</Chip>)}
            {o.tools.map((t) => <Chip key={t} color="#9aa3b5">{t}</Chip>)}
          </div>
          <div className="hero-actions">
            <button className="btn btn-primary" onClick={() => go('explorer')}><FolderTree size={16} /> Browse the code</button>
            <button className="btn" onClick={() => go('architecture')}><Network size={16} /> See architecture</button>
            <button className="btn" onClick={() => go('flow')}><Route size={16} /> Trace a data flow</button>
          </div>
        </div>
      </section>

      <div className="stat-grid">
        {stats.map(({ icon: Icon, label, value }) => (
          <div key={label} className="stat card"><span className="stat-ico"><Icon size={17} /></span><b>{formatNumber(value)}</b><span>{label}</span></div>
        ))}
      </div>

      {o.sensitiveFiles?.length > 0 && (
        <div className="info-box" style={{ marginTop: 16 }}>
          <ShieldAlert size={18} color="var(--warn)" style={{ flexShrink: 0, marginTop: 2 }} />
          <div style={{ flex: 1 }}>
            <b>{o.sensitiveFiles.length} sensitive file{o.sensitiveFiles.length > 1 ? 's were' : ' was'} skipped</b>
            <div style={{ color: 'var(--text-dim)', fontSize: 13 }}>{o.sensitiveFiles.slice(0, 4).join(', ')}{o.sensitiveFiles.length > 4 ? '…' : ''} — they may contain passwords or keys, so they are never read unless you allow it.</div>
          </div>
          <button className="btn btn-sm" disabled={granting} onClick={grant}>Allow access</button>
        </div>
      )}

      <div className="grid-2">
        <section className="card card-pad">
          <div className="card-title">Languages</div>
          <div className="langbar big">{langs.map(([l, v]) => <span key={l} style={{ width: `${v}%`, background: langColor(l) }} title={`${l} ${v}%`} />)}</div>
          <div className="lang-list">
            {langs.map(([l, v]) => <div key={l}><span className="dot" style={{ '--c': langColor(l) }} />{l}<b>{v}%</b></div>)}
          </div>
        </section>

        <section className="card card-pad">
          <div className="card-title">How the code is organised</div>
          <div className="layer-rows">
            {layerRows.map(([l, n]) => (
              <div key={l} className="layer-row" onClick={() => go('architecture')}>
                <span className="layer-name"><span className="dot" style={{ '--c': layerColor(l) }} />{layerLabel(l)}</span>
                <div className="layer-bar"><span style={{ width: `${(n / maxLayer) * 100}%`, background: layerColor(l) }} /></div>
                <b>{n}</b>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="grid-2">
        <section className="card card-pad">
          <div className="card-title">Security</div>
          <div className="sec-row">
            <span className={`sec-ico ${sec.enabled ? 'on' : ''}`}>{sec.enabled ? <ShieldCheck size={22} /> : <ShieldAlert size={22} />}</span>
            <div>
              <b>{sec.enabled ? 'Protected' : 'Not protected'}</b>
              <p>{sec.summary}</p>
              {sec.enabled && <div className="chip-row">{sec.mechanisms.map((m) => <Chip key={m} color="#f87171"><KeyRound size={12} />{m}</Chip>)}{sec.csrf && <Chip color="#9aa3b5">CSRF {sec.csrf}</Chip>}</div>}
            </div>
          </div>
          <button className="btn btn-sm" style={{ marginTop: 12 }} onClick={() => go('flow', { endpoint: 'security' })}>See the security flow <ArrowRight size={14} /></button>
        </section>

        <section className="card card-pad">
          <div className="card-title">Key facts</div>
          <ul className="facts">{o.highlights.map((h, i) => <li key={i}>{h}</li>)}</ul>
        </section>
      </div>

      {suggestions.length > 0 && (
        <section className="card card-pad" style={{ marginTop: 16 }}>
          <div className="card-title">Try asking</div>
          <div className="chip-row">
            {suggestions.map((s) => <button key={s} className="suggest" onClick={() => ask(s)}><MessageSquareCode size={14} />{s}</button>)}
          </div>
        </section>
      )}

      <div className="section-head"><h2>Modules</h2><span className="hint">{data.modules.length} detected from the package structure</span></div>
      <div className="module-grid">
        {data.modules.map((m) => (
          <div key={m.name} className="card module-card" onClick={() => go('architecture')}>
            <div className="module-top"><b>{m.name}</b><span>{m.classes.length} classes</span></div>
            <div className="mnode-bars">{Object.entries(m.layers).map(([l, names]) => <span key={l} style={{ background: layerColor(l), flexGrow: names.length }} title={`${names.length} ${layerLabel(l)}`} />)}</div>
            <p>{m.description.replace(/^Module “[^”]*”: /, '')}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
