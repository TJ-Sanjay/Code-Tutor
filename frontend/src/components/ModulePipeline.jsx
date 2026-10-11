import React from 'react';
import { Layers, CheckCircle2, Clock, Circle } from 'lucide-react';

const PHASES = [
  { name: 'Phase 1: Foundation & Auth', modules: '1-2', status: 'verified', label: 'Auth & JWT Security' },
  { name: 'Phase 2: Project Management', modules: '3-5', status: 'verified', label: 'GitHub & ZIP Input' },
  { name: 'Phase 3: Extraction & Scanning', modules: '6-10', status: 'verified', label: 'Workspace Scanner' },
  { name: 'Phase 4: Parsing & Analysis', modules: '11-15', status: 'verified', label: 'Tree-Sitter & IR Engine' },
  { name: 'Phase 5: Dependency Graph', modules: '16-20', status: 'verified', label: 'Graph Storage' },
  { name: 'Phase 6: API & DB Analyzers', modules: '21-23', status: 'verified', label: 'API & Database Schema' },
  { name: 'Phase 7: Frontend Explorer', modules: '24-27', status: 'verified', label: 'Monaco & React Flow' },
  { name: 'Phase 8: Embeddings & Vector', modules: '28-30', status: 'verified', label: 'PGVector Chunks' },
  { name: 'Phase 9: AI RAG & Retrieval', modules: '31-36', status: 'verified', label: 'LangGraph LLM Engine' },
  { name: 'Phase 10: Tutor Interactive', modules: '37-40', status: 'verified', label: 'Chat & Click-to-Explain' }
];

export default function ModulePipeline() {
  return (
    <div className="glass-card" style={{ marginTop: '2.5rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.4rem' }}>
        <Layers size={22} color="var(--primary)" />
        <div style={{ fontFamily: "'Outfit', sans-serif", fontSize: '1.25rem', fontWeight: 700, color: 'white' }}>
          40-Module Architecture Pipeline Progress
        </div>
      </div>
      <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
        Real-time system build progress tracking across all 10 core development phases.
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
        gap: '0.85rem'
      }}>
        {PHASES.map((p, idx) => (
          <div key={idx} style={{
            padding: '0.85rem 1rem', borderRadius: '10px',
            background: p.status === 'verified' ? 'rgba(16, 185, 129, 0.08)' : p.status === 'in-progress' ? 'rgba(245, 158, 11, 0.08)' : 'rgba(0, 0, 0, 0.3)',
            border: `1px solid ${p.status === 'verified' ? 'rgba(16, 185, 129, 0.3)' : p.status === 'in-progress' ? 'rgba(245, 158, 11, 0.3)' : 'var(--border-color)'}`
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.3rem' }}>
              <span style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                MODULES {p.modules}
              </span>
              {p.status === 'verified' && <CheckCircle2 size={14} color="#34d399" />}
              {p.status === 'in-progress' && <Clock size={14} color="#fbbf24" />}
              {p.status === 'pending' && <Circle size={14} color="#64748b" />}
            </div>

            <div style={{ fontSize: '0.9rem', fontWeight: 700, color: 'white' }}>{p.name}</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>{p.label}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
