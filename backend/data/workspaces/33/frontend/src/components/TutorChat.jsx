import React, { useState, useRef, useEffect } from 'react';
import { Send, Bot, User, Loader2, Code, Sparkles, MessageSquare } from 'lucide-react';
import api from '../api';

export default function TutorChat({ project, selectedFile, onHighlightCode }) {
  const [messages, setMessages] = useState([
    { role: 'assistant', text: 'Hello! I am CodeTutor. How can I help you with this project today?', mode: 'QA' }
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSend = async (overrideText, mode = 'QA') => {
    const textToSend = overrideText || input;
    if (!textToSend.trim()) return;

    if (!overrideText) {
      setInput('');
    }

    const newMsg = { role: 'user', text: textToSend, mode };
    setMessages(prev => [...prev, newMsg]);
    setLoading(true);

    try {
      const res = await api.post(`/projects/${project.id}/chat`, {
        query: textToSend,
        selectedFile: selectedFile,
        mode: mode
      });
      
      const reply = res.data.data.answer;
      setMessages(prev => [...prev, { role: 'assistant', text: reply, sources: res.data.data.retrievedSources }]);
    } catch (err) {
      console.error(err);
      setMessages(prev => [...prev, { role: 'assistant', text: 'Sorry, I encountered an error communicating with the backend.' }]);
    } finally {
      setLoading(false);
    }
  };

  // Expose this method so CodeViewerModal can call it when user highlights code and clicks "Explain"
  // In React, we usually do this via refs, but for now we'll just let the parent pass a prop or we trigger it via global event.
  // Actually, we can listen for a custom event from the Monaco editor.
  useEffect(() => {
    const handleContextAction = (e) => {
      const { action, text, file } = e.detail;
      let prompt = text;
      if (action === 'explain') {
        prompt = `Please explain the following code from ${file}:\n\n` + text;
        handleSend(prompt, 'EXPLAIN');
      } else if (action === 'refactor') {
        prompt = `Please suggest a refactoring for the following code from ${file}:\n\n` + text;
        handleSend(prompt, 'REFACTOR');
      }
    };
    window.addEventListener('tutor-context-action', handleContextAction);
    return () => window.removeEventListener('tutor-context-action', handleContextAction);
  }, [project.id]);

  return (
    <div style={{
      width: '380px', background: '#0b0f19', borderLeft: '1px solid var(--border-color)',
      display: 'flex', flexDirection: 'column'
    }}>
      <div style={{ padding: '1rem', borderBottom: '1px solid var(--border-color)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <Sparkles size={18} color="var(--accent)" />
        <div style={{ fontSize: '0.85rem', fontWeight: 700, color: 'white', textTransform: 'uppercase' }}>
          CodeTutor AI
        </div>
      </div>

      {/* Messages */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        {messages.map((m, i) => (
          <div key={i} style={{ display: 'flex', gap: '0.75rem', flexDirection: m.role === 'user' ? 'row-reverse' : 'row' }}>
            <div style={{ 
              width: '28px', height: '28px', borderRadius: '14px', flexShrink: 0,
              background: m.role === 'user' ? 'var(--primary)' : '#1e1b4b',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              border: m.role === 'user' ? 'none' : '1px solid var(--accent)'
            }}>
              {m.role === 'user' ? <User size={16} color="white" /> : <Bot size={16} color="var(--accent)" />}
            </div>
            
            <div style={{ 
              background: m.role === 'user' ? 'var(--primary)' : 'rgba(255,255,255,0.03)',
              color: m.role === 'user' ? 'white' : 'var(--text-main)',
              padding: '0.75rem', borderRadius: '8px', fontSize: '0.85rem',
              border: m.role === 'user' ? 'none' : '1px solid var(--border-color)',
              maxWidth: '85%', lineHeight: 1.5,
              borderTopRightRadius: m.role === 'user' ? 0 : '8px',
              borderTopLeftRadius: m.role === 'user' ? '8px' : 0,
              whiteSpace: 'pre-wrap'
            }}>
              {m.text}
              
              {m.sources && m.sources.length > 0 && (
                <div style={{ marginTop: '0.75rem', paddingTop: '0.5rem', borderTop: '1px solid rgba(255,255,255,0.1)', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  <strong>Sources:</strong>
                  <ul style={{ margin: '0.25rem 0 0 0', paddingLeft: '1.25rem' }}>
                    {m.sources.map((s, idx) => (
                      <li key={idx}>
                        <button 
                          onClick={() => onHighlightCode && onHighlightCode(s)}
                          style={{ background: 'none', border: 'none', color: 'var(--accent)', textDecoration: 'underline', cursor: 'pointer', padding: 0, fontSize: '0.75rem' }}
                        >
                          {s}
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </div>
        ))}
        {loading && (
          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <div style={{ width: '28px', height: '28px', borderRadius: '14px', background: '#1e1b4b', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '1px solid var(--accent)' }}>
              <Bot size={16} color="var(--accent)" />
            </div>
            <div style={{ padding: '0.75rem', color: 'var(--text-muted)', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Loader2 size={14} className="spin" /> CodeTutor is thinking...
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div style={{ padding: '1rem', borderTop: '1px solid var(--border-color)', background: '#090d16' }}>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <input 
            type="text" 
            className="form-input" 
            placeholder={selectedFile ? `Ask about ${selectedFile}...` : "Ask a question..."}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSend()}
            style={{ flex: 1 }}
          />
          <button className="btn btn-primary" style={{ padding: '0 1rem' }} onClick={() => handleSend()} disabled={loading || !input.trim()}>
            <Send size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
