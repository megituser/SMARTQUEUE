'use client';

import { useEffect, useState, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { queueApi } from '@/lib/api';
import { useWebSocket } from '@/hooks/useWebSocket';
import { QueueStatusResponse } from '@/lib/types';
import { motion, AnimatePresence } from 'framer-motion';
import { Monitor, Clock, Users, Zap } from 'lucide-react';

function QueueContent() {
  const searchParams = useSearchParams();
  const branchId = Number(searchParams.get('branch') || 1);
  const queryClient = useQueryClient();
  const { subscribeToQueue, subscribeToTokenCalled } = useWebSocket();
  const [calledToken, setCalledToken] = useState<any>(null);

  const { data: queueStatus } = useQuery<QueueStatusResponse>({
    queryKey: ['queueStatus', branchId],
    queryFn: async () => (await queueApi.getStatus(branchId)).data.data,
    refetchInterval: 10000,
  });

  useEffect(() => {
    const unsub1 = subscribeToQueue(branchId, () => {
      queryClient.invalidateQueries({ queryKey: ['queueStatus', branchId] });
    });
    const unsub2 = subscribeToTokenCalled(branchId, (data) => {
      setCalledToken(data.token);
      setTimeout(() => setCalledToken(null), 8000);
      queryClient.invalidateQueries({ queryKey: ['queueStatus', branchId] });
    });
    return () => { unsub1(); unsub2(); };
  }, [branchId, subscribeToQueue, subscribeToTokenCalled, queryClient]);

  const now = new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: 32 }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 32 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <Monitor size={24} className="gradient-text" />
            <h1 style={{ fontSize: 24, fontWeight: 700 }}>{queueStatus?.branchName || 'Queue Display'}</h1>
          </div>
          <p style={{ color: 'var(--text-muted)', fontSize: 14 }}>Live queue status • Updated in real-time</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--accent-green)', fontSize: 14 }}>
            <Zap size={14} /> LIVE
          </div>
          <div style={{ fontSize: 32, fontWeight: 300, color: 'var(--text-secondary)' }}>{now}</div>
        </div>
      </div>

      {/* Called Token Alert */}
      <AnimatePresence>
        {calledToken && (
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            style={{
              textAlign: 'center', marginBottom: 32, padding: '32px 40px',
              borderRadius: 20, background: 'linear-gradient(135deg, rgba(59,130,246,0.15), rgba(139,92,246,0.15))',
              border: '2px solid rgba(59,130,246,0.3)',
            }}
            className="pulse-serving"
          >
            <div style={{ fontSize: 14, color: 'var(--accent-cyan)', fontWeight: 600, marginBottom: 8 }}>NOW SERVING</div>
            <div className="token-display">{calledToken.tokenNumber}</div>
            <div style={{ fontSize: 18, color: 'var(--text-secondary)', marginTop: 8 }}>
              Please proceed to {calledToken.counterName || `Counter ${calledToken.counterId}`}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Stats Row */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 32 }}>
        <div className="stat-card" style={{ flex: 1, textAlign: 'center' }}>
          <div style={{ color: 'var(--accent-amber)', fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
            <Users size={16} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} /> WAITING
          </div>
          <div className="stat-value" style={{ color: 'var(--accent-amber)', fontSize: '2.5rem' }}>{queueStatus?.totalWaiting || 0}</div>
        </div>
        <div className="stat-card" style={{ flex: 1, textAlign: 'center' }}>
          <div style={{ color: 'var(--accent-blue)', fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
            <Zap size={16} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} /> SERVING
          </div>
          <div className="stat-value" style={{ color: 'var(--accent-blue)', fontSize: '2.5rem' }}>{queueStatus?.totalServing || 0}</div>
        </div>
        <div className="stat-card" style={{ flex: 1, textAlign: 'center' }}>
          <div style={{ color: 'var(--accent-green)', fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
            <Clock size={16} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} /> AVG WAIT
          </div>
          <div className="stat-value" style={{ color: 'var(--accent-green)', fontSize: '2.5rem' }}>{queueStatus?.averageWaitMinutes || 0}m</div>
        </div>
      </div>

      {/* Two-Column: Counters + Queue */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>
        {/* Now Serving at Counters */}
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 16, color: 'var(--text-secondary)' }}>NOW SERVING</h2>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            {queueStatus?.counters?.filter(c => c.status === 'OPEN').map((c) => (
              <div
                key={c.counterId}
                className={`glass-card ${c.currentToken ? 'pulse-serving' : ''}`}
                style={{ padding: 20, textAlign: 'center' }}
              >
                <div style={{ fontSize: 13, color: 'var(--text-muted)', fontWeight: 600, marginBottom: 8 }}>
                  {c.counterName || `COUNTER ${c.counterNumber}`}
                </div>
                {c.currentToken ? (
                  <div className="token-display" style={{ fontSize: '2.5rem' }}>
                    {c.currentToken.tokenNumber}
                  </div>
                ) : (
                  <div style={{ fontSize: 18, color: 'var(--text-muted)', padding: '10px 0' }}>—</div>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Waiting Queue */}
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 16, color: 'var(--text-secondary)' }}>WAITING QUEUE</h2>
          <div className="glass-card" style={{ padding: 20, maxHeight: 400, overflow: 'auto' }}>
            {queueStatus?.waitingTokens?.length ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {queueStatus.waitingTokens.map((t, i) => (
                  <motion.div
                    key={t.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: i * 0.03 }}
                    style={{
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      padding: '10px 14px', borderRadius: 10,
                      background: i === 0 ? 'rgba(59,130,246,0.1)' : 'var(--bg-secondary)',
                      border: `1px solid ${i === 0 ? 'rgba(59,130,246,0.2)' : 'var(--border-color)'}`,
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <span style={{ fontWeight: 800, color: i === 0 ? 'var(--accent-blue)' : 'var(--text-primary)', fontSize: 16, minWidth: 55 }}>
                        {t.tokenNumber}
                      </span>
                      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{t.serviceName}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      {t.priority !== 'NORMAL' && (
                        <span className={`badge badge-${t.priority.toLowerCase()}`} style={{ fontSize: 10 }}>{t.priority}</span>
                      )}
                      <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>~{t.estimatedWaitMinutes}m</span>
                    </div>
                  </motion.div>
                ))}
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>Queue is empty</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function QueuePage() {
  return (
    <Suspense fallback={<div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>Loading queue...</div>}>
      <QueueContent />
    </Suspense>
  );
}
