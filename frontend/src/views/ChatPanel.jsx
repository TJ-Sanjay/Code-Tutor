import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ArrowUp, BookOpen, Bot, Code2, Eraser, Route, X } from 'lucide-react';
import { del, errMsg, get, post } from '../api';
import { useWorkspace } from '../pages/wsContext';
import { useAuth } from '../auth';
import { Chip, Markdown, Spinner } from '../ui';
import { initials } from '../lib/format';
import FlowDiagram from './FlowDiagram';

const AUTO_OPEN = new Set(['EXPLAIN', 'SELECTION', 'SEARCH', 'FLOW', 'SECURITY', 'DEPENDENCIES']);

export default function ChatPanel({ onClose }) {
  const { projectId, openCode, go, follow } = useWorkspace();
  const { user } = useAuth();
  const location = useLocation();
  const [messages, setMessages] = useState(null);
  const [suggestions, setSuggestions] = useState({ questions: [], provider: 'none' });
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const endRef = useRef(null);
  const taRef = useRef(null);
  const tab = location.pathname.split('/')[3];

  useEffect(() => {
    setMessages(null);
    get(`/projects/${projectId}/chat/history`).then(setMessages).catch(() => setMessages([]));
    get(`/projects/${projectId}/chat/suggestions`).then(setSuggestions).catch(() => {});
  }, [projectId]);

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }); }, [messages, busy]);

  const send = useCallback(async (text, selection) => {
    const query = (text ?? input).trim();
    if (!query || busy) return;
    setInput('');
    if (taRef.current) taRef.current.style.height = 'auto';
    setMessages((m) => [...(m || []), { id: `tmp${Date.now()}`, role: 'USER', content: query }]);
    setBusy(true);
    try {
      const a = await post(`/projects/${projectId}/chat`, { query, selection: selection || null });
      setMessages((m) => [...m, a]);
      // show the relevant code right away, highlighted — unless the user is already looking at a diagram,
      // or the answer is a project-wide listing (overview / schema / endpoint table) with no single place to show
      const first = a.sources?.[0];
      const onDiagramTab = tab === 'flow' || tab === 'architecture' || tab === 'database';
      const listing = /^\s*###\s*(Project overview|Database schema|\d+ endpoints?)/.test(a.content);
      if (first && !onDiagramTab && !listing) openCode(first.file, first.startLine, first.endLine);
    } catch (e) {
      setMessages((m) => [...m, { id: `err${Date.now()}`, role: 'ASSISTANT', content: `⚠️ ${errMsg(e)}`, error: true }]);
    } finally {
      setBusy(false);
    }
  }, [input, busy, projectId, openCode, tab]);

  // questions sent from elsewhere in the app ("Ask AI about this code", suggested questions…)
  useEffect(() => {
    const on = (e) => send(e.detail.query, e.detail.selection);
    window.addEventListener('ct-ask', on);
    return () => window.removeEventListener('ct-ask', on);
  }, [send]);

  const clear = async () => {
    await del(`/projects/${projectId}/chat/history`).catch(() => {});
    setMessages([]);
  };

  const onKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
  };
  const grow = (e) => {
    setInput(e.target.value);
    e.target.style.height = 'auto';
    e.target.style.height = `${Math.min(140, e.target.scrollHeight)}px`;
  };

  const empty = messages && messages.length === 0;

  return (
    <div className="chat">
      <div className="chat-head">
        <span className="chat-logo"><BookOpen size={15} /></span>
        <div><b>Ask CodeTutor</b><span>{suggestions.provider === 'none' ? 'Answers from your code’s knowledge graph' : `Powered by ${suggestions.provider}`}</span></div>
        <div style={{ flex: 1 }} />
        {!empty && <button className="icon-btn" onClick={clear} title="Clear conversation"><Eraser size={15} /></button>}
        <button className="icon-btn" onClick={onClose} title="Close"><X size={16} /></button>
      </div>

      <div className="chat-scroll">
        {messages === null && <div className="ws-center"><Spinner /></div>}
        {empty && (
          <div className="chat-empty">
            <div className="chat-empty-ico"><Bot size={26} /></div>
            <h3>Ask anything about this project</h3>
            <p>Answers come from the real code. The relevant file opens with the exact lines highlighted, and flows are drawn for you.</p>
            <div className="suggest-list">
              {suggestions.questions.map((s) => <button key={s} className="suggest" onClick={() => send(s)}>{s}</button>)}
            </div>
          </div>
        )}
        {messages?.map((m) => (
          <Message key={m.id} m={m} user={user} onSource={(s) => openCode(s.file, s.startLine, s.endLine)} onAction={(a) => follow({ tab: a.tab, id: a.id, kind: a.tab === 'flow' ? 'flow' : undefined })} go={go} />
        ))}
        {busy && (
          <div className="msg bot"><span className="msg-av bot"><Bot size={15} /></span><div className="typing"><i /><i /><i /><span>Reading the code…</span></div></div>
        )}
        <div ref={endRef} />
      </div>

      <div className="chat-input">
        <textarea ref={taRef} className="textarea" rows={1} value={input} onChange={grow} onKeyDown={onKey}
          placeholder="Ask about a class, an endpoint, how login works…" disabled={busy} />
        <button className="send" onClick={() => send()} disabled={busy || !input.trim()} title="Send"><ArrowUp size={18} /></button>
      </div>
    </div>
  );
}

function Message({ m, user, onSource, onAction }) {
  const [showFlow, setShowFlow] = useState(true);
  if (m.role === 'USER') {
    return (
      <div className="msg me">
        <div className="bubble me"><div className="bubble-text">{m.content}</div></div>
        <span className="msg-av me">{initials(user?.fullName)}</span>
      </div>
    );
  }
  return (
    <div className="msg bot">
      <span className="msg-av bot"><Bot size={15} /></span>
      <div className={`bubble bot ${m.error ? 'err' : ''}`}>
        <Markdown>{m.content}</Markdown>
        {m.ai != null && !m.error && (
          <span className={`ai-badge ${m.ai ? 'on' : ''}`} title={m.ai ? 'Written by the language model from the analysis of your code' : 'Built from the code analysis. The language model is off or was busy.'}>
            {m.ai ? '✦ AI-written answer' : 'Built-in answer'}
          </span>
        )}
        {m.flow && m.flow.steps?.length > 1 && (
          <div className="chat-flow">
            <div className="chat-flow-head">
              <b><Route size={14} /> {m.flow.title}</b>
              <button className="btn btn-sm btn-ghost" onClick={() => setShowFlow((s) => !s)}>{showFlow ? 'Hide diagram' : 'Show diagram'}</button>
            </div>
            {showFlow && <FlowDiagram flow={m.flow} compact tools={false} className="chat-flow-canvas" />}
          </div>
        )}
        {m.sources?.length > 0 && (
          <div className="sources">
            <span>Code</span>
            {m.sources.slice(0, 6).map((s, i) => (
              <button key={s.file + s.startLine + i} className="source" onClick={() => onSource(s)} title={`${s.file}:${s.startLine}-${s.endLine}`}>
                <Code2 size={12} />{s.file.substring(s.file.lastIndexOf('/') + 1)}<em>:{s.startLine}</em>
              </button>
            ))}
          </div>
        )}
        {m.actions?.length > 0 && (
          <div className="actions">
            {m.actions.map((a, i) => <Chip key={i} color="var(--accent)" onClick={() => onAction(a)}>{a.label} →</Chip>)}
          </div>
        )}
      </div>
    </div>
  );
}
