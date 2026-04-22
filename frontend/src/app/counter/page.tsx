'use client';

import { useEffect, useState, useRef, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '@/providers/AuthProvider';
import { useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { counterApi, queueApi } from '@/lib/api';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useToast } from '@/hooks/useToast';
import { CounterStatusResponse, QueueStatusResponse } from '@/lib/types';
import { ToastContainer } from './ToastContainer';
import { motion, AnimatePresence } from 'framer-motion';
import Link from 'next/link';
import {
  Play, Square, SkipForward, CheckCircle, XCircle, ArrowLeft,
  Ticket, Monitor, Users, Clock, User, Briefcase, AlertTriangle
} from 'lucide-react';

/* ─── Elapsed-time helper ─── */
function useElapsedTime(sinceIso: string | undefined | null) {
  const [elapsed, setElapsed] = useState('');
  useEffect(() => {
    if (!sinceIso) { setElapsed(''); return; }
    const tick = () => {
      const diff = Math.max(0, Math.floor((Date.now() - new Date(sinceIso).getTime()) / 1000));
      const m = Math.floor(diff / 60);
      const s = diff % 60;
      setElapsed(`${m}m ${s.toString().padStart(2, '0')}s`);
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [sinceIso]);
  return elapsed;
}

/* ─── API error handler ─── */
function extractApiError(err: any): { status: number; message: string } {
  const status = err?.response?.status ?? 500;
  const message =
    err?.response?.data?.message ||
    err?.response?.data?.error ||
    (status === 400 ? 'Bad request' : 'An unexpected error occurred. Please try again later.');
  return { status, message };
}

/* ═══════════════════════════════════════════════════════════════════ */
/*                         COUNTER CONTENT                          */
/* ═══════════════════════════════════════════════════════════════════ */
function CounterContent() {
  const searchParams = useSearchParams();
  const branchId = Number(searchParams.get('branch') || 1);
  const { user, isAuthenticated, loading: authLoading } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { subscribeToQueue, subscribeToCounter } = useWebSocket();
  const [selectedCounter, setSelectedCounter] = useState<number | null>(null);
  const [actionLoading, setActionLoading] = useState('');
  const { toasts, addToast, removeToast } = useToast();

  /* ── Auth guard ── */
  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace('/login');
  }, [isAuthenticated, authLoading, router]);

  /* ── WebSocket: branch-level queue updates ── */
  useEffect(() => {
    const unsub = subscribeToQueue(branchId, () => {
      queryClient.invalidateQueries({ queryKey: ['counters', branchId] });
      queryClient.invalidateQueries({ queryKey: ['queueStatus', branchId] });
    });
    return unsub;
  }, [branchId, subscribeToQueue, queryClient]);

  /* ── WebSocket: counter-specific updates (FIX 5) ── */
  useEffect(() => {
    if (!selectedCounter) return;
    const unsub = subscribeToCounter(branchId, selectedCounter, () => {
      queryClient.invalidateQueries({ queryKey: ['counters', branchId] });
      queryClient.invalidateQueries({ queryKey: ['queueStatus', branchId] });
    });
    return unsub;
  }, [branchId, selectedCounter, subscribeToCounter, queryClient]);

  /* ── Data queries ── */
  const { data: counters } = useQuery<CounterStatusResponse[]>({
    queryKey: ['counters', branchId],
    queryFn: async () => (await counterApi.getByBranch(branchId)).data.data,
    refetchInterval: 5000,
  });

  const { data: queueStatus } = useQuery<QueueStatusResponse>({
    queryKey: ['queueStatus', branchId],
    queryFn: async () => (await queueApi.getStatus(branchId)).data.data,
    refetchInterval: 10000,
  });

  /* ── Derived counter state ── */
  const currentCounter = counters?.find(c => c.counterId === selectedCounter);
  const isServing = !!currentCounter?.currentToken;
  const isOpen = currentCounter?.status === 'OPEN';
  const isClosed = currentCounter?.status === 'CLOSED';

  const relevantTokens = queueStatus?.waitingTokens?.filter(token =>
    currentCounter?.serviceNames?.includes(token.serviceName)
  ) || [];

  /* ── Live timer ── */
  const elapsedTime = useElapsedTime(currentCounter?.currentToken?.calledAt);

  /* ── Action dispatcher with proper error handling (FIX 4) ── */
  const doAction = async (action: string, counterId: number) => {
    setActionLoading(action);
    try {
      switch (action) {
        case 'open': await counterApi.open(counterId); break;
        case 'close': await counterApi.close(counterId); break;
        case 'next': await counterApi.callNext(counterId); break;
        case 'complete': await counterApi.complete(counterId); break;
        case 'noshow': await counterApi.noShow(counterId); break;
      }
      queryClient.invalidateQueries({ queryKey: ['counters', branchId] });
      queryClient.invalidateQueries({ queryKey: ['queueStatus', branchId] });

      // Success feedback
      const successMessages: Record<string, string> = {
        open: 'Counter opened successfully',
        close: 'Counter closed',
        next: 'Next customer called',
        complete: 'Service completed',
        noshow: 'Customer marked as no-show',
      };
      addToast({ message: successMessages[action] || 'Action completed', type: 'success', duration: 3000 });
    } catch (err: any) {
      const { status, message } = extractApiError(err);

      if (status === 401) {
        // Token expired and refresh failed → redirect to login
        router.push('/login');
        return;
      }

      if (status >= 400 && status < 500) {
        // Client errors (400, 409, etc.) → show the backend's message
        addToast({ message, type: 'warning', duration: 5000 });
      } else {
        // 500+ → generic fallback
        addToast({ message: 'Something went wrong. Please try again.', type: 'error', duration: 5000 });
      }
    } finally {
      setActionLoading('');
    }
  };

  /* ── Loading state ── */
  if (authLoading || !isAuthenticated) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>Loading...</div>;
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: 32 }}>
      <ToastContainer toasts={toasts} onRemove={removeToast} />

      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 32 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Link href="/dashboard" style={{ color: 'var(--text-muted)', display: 'flex' }}><ArrowLeft size={20} /></Link>
          <div>
            <h1 style={{ fontSize: 22, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Ticket size={22} /> Counter Operations
            </h1>
            <p style={{ color: 'var(--text-secondary)', fontSize: 13, marginTop: 2 }}>
              Logged in as {user?.firstName} • {selectedCounter ? `${relevantTokens.length} waiting for your counter` : `${queueStatus?.totalWaiting || 0} waiting`}
            </p>
          </div>
        </div>
      </div>

      {!selectedCounter ? (
        /* ════════ Counter Selection Grid ════════ */
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 16 }}>Select your counter</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: 16 }}>
            {counters?.map((c) => (
              <motion.div
                key={c.counterId}
                whileHover={{ scale: 1.02 }}
                onClick={() => setSelectedCounter(c.counterId)}
                className="glass-card glass-card-hover"
                style={{ padding: 24, cursor: 'pointer' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <span style={{ fontSize: 18, fontWeight: 700 }}>{c.counterName || `Counter ${c.counterNumber}`}</span>
                  <span className={`badge badge-${c.status.toLowerCase()}`}>{c.status}</span>
                </div>
                {c.currentToken && (
                  <div style={{ fontSize: 24, fontWeight: 800, color: 'var(--accent-blue)' }}>{c.currentToken.tokenNumber}</div>
                )}
                <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 8 }}>
                  Services: {c.serviceNames?.join(', ')}
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      ) : (
        /* ════════ Active Counter View ════════ */
        <div style={{ maxWidth: 700, margin: '0 auto' }}>
          <button onClick={() => setSelectedCounter(null)} className="btn-secondary" style={{ marginBottom: 24, fontSize: 13 }}>
            ← Change Counter
          </button>

          {/* Counter Status Card */}
          <div className="glass-card" style={{ padding: 32, textAlign: 'center', marginBottom: 24 }}>
            <div style={{ fontSize: 14, color: 'var(--text-muted)', fontWeight: 600, marginBottom: 4 }}>
              {currentCounter?.counterName || `Counter ${currentCounter?.counterNumber}`}
            </div>
            <span className={`badge badge-${currentCounter?.status?.toLowerCase()}`} style={{ marginBottom: 16, display: 'inline-flex' }}>
              {currentCounter?.status}
            </span>

            <AnimatePresence mode="wait">
              {isServing ? (
                /* ── Serving State: show customer details ── */
                <motion.div
                  key="serving"
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -20 }}
                  transition={{ duration: 0.3 }}
                  style={{ marginTop: 20 }}
                >
                  <div style={{ fontSize: 13, color: 'var(--accent-cyan)', fontWeight: 600, letterSpacing: 1.5, textTransform: 'uppercase' }}>Now Serving</div>
                  <div className="token-display pulse-serving" style={{ margin: '8px 0', display: 'inline-block', padding: '4px 16px', borderRadius: 12 }}>
                    {currentCounter!.currentToken!.tokenNumber}
                  </div>

                  {/* Customer details grid */}
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr',
                    gap: 12,
                    marginTop: 16,
                    textAlign: 'left',
                    background: 'rgba(255,255,255,0.03)',
                    borderRadius: 12,
                    padding: 16,
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <User size={14} style={{ color: 'var(--accent-purple)', flexShrink: 0 }} />
                      <div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 500 }}>Customer</div>
                        <div style={{ fontSize: 14, fontWeight: 600 }}>
                          {currentCounter!.currentToken!.customerName || 'Walk-in Customer'}
                        </div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Briefcase size={14} style={{ color: 'var(--accent-cyan)', flexShrink: 0 }} />
                      <div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 500 }}>Service</div>
                        <div style={{ fontSize: 14, fontWeight: 600 }}>
                          {currentCounter!.currentToken!.serviceName}
                        </div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Ticket size={14} style={{ color: 'var(--accent-amber)', flexShrink: 0 }} />
                      <div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 500 }}>Priority</div>
                        <div style={{ fontSize: 14, fontWeight: 600 }}>
                          {currentCounter!.currentToken!.priority}
                        </div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Clock size={14} style={{ color: 'var(--accent-green)', flexShrink: 0 }} />
                      <div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 500 }}>Time Elapsed</div>
                        <div style={{ fontSize: 14, fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: 'var(--accent-green)' }}>
                          {elapsedTime || '—'}
                        </div>
                      </div>
                    </div>
                  </div>
                </motion.div>
              ) : (
                /* ── Idle State: no customer ── */
                <motion.div
                  key="idle"
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -20 }}
                  transition={{ duration: 0.3 }}
                  style={{ padding: '30px 0', color: 'var(--text-muted)', fontSize: 16 }}
                >
                  {isOpen ? 'No customer — Call next' : 'Counter is closed'}
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* ═══ Action Buttons — State Machine (FIX 3) ═══ */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            {isClosed ? (
              /* CLOSED → only show Open */
              <button
                id="btn-open-counter"
                onClick={() => doAction('open', selectedCounter)}
                className="btn-success"
                disabled={!!actionLoading}
                style={{ gridColumn: 'span 2', padding: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, fontSize: 16 }}
              >
                <Play size={20} /> {actionLoading === 'open' ? 'Opening...' : 'Open Counter'}
              </button>
            ) : (
              <>
                {!isServing ? (
                  /* OPEN + no current token → show Call Next */
                  <motion.button
                    id="btn-call-next"
                    key="call-next"
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    onClick={() => doAction('next', selectedCounter)}
                    className="btn-primary"
                    disabled={!!actionLoading || relevantTokens.length === 0}
                    style={{
                      gridColumn: 'span 2', padding: 16, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4, fontSize: 16,
                      opacity: relevantTokens.length === 0 ? 0.5 : 1
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <SkipForward size={20} /> {actionLoading === 'next' ? 'Calling...' : 'Call Next'}
                    </div>
                    {relevantTokens.length === 0 && (
                      <div style={{ fontSize: 12, fontWeight: 400, opacity: 0.8 }}>No customers waiting for your services</div>
                    )}
                  </motion.button>
                ) : (
                  /* OPEN + serving → show Complete + No-Show, HIDE Call Next */
                  <>
                    <motion.button
                      id="btn-complete-service"
                      key="complete"
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      onClick={() => doAction('complete', selectedCounter)}
                      className="btn-success"
                      disabled={!!actionLoading}
                      style={{ padding: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, fontSize: 15 }}
                    >
                      <CheckCircle size={18} /> {actionLoading === 'complete' ? 'Completing...' : 'Complete Service'}
                    </motion.button>
                    <motion.button
                      id="btn-no-show"
                      key="noshow"
                      initial={{ opacity: 0, x: 20 }}
                      animate={{ opacity: 1, x: 0 }}
                      className="btn-danger"
                      onClick={() => doAction('noshow', selectedCounter)}
                      disabled={!!actionLoading}
                      style={{ padding: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, fontSize: 15 }}
                    >
                      <XCircle size={18} /> {actionLoading === 'noshow' ? 'Marking...' : 'No-Show'}
                    </motion.button>
                  </>
                )}

                {/* Close Counter — ONLY when idle (no current token) */}
                {!isServing && (
                  <motion.button
                    id="btn-close-counter"
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    onClick={() => doAction('close', selectedCounter)}
                    className="btn-secondary"
                    disabled={!!actionLoading}
                    style={{ gridColumn: 'span 2', padding: 12, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                  >
                    <Square size={16} /> Close Counter
                  </motion.button>
                )}
              </>
            )}
          </div>

          {/* Waiting Queue Preview */}
          <div className="glass-card" style={{ padding: 20, marginTop: 24 }}>
            <h3 style={{ fontSize: 14, fontWeight: 700, marginBottom: 12, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Users size={16} /> Waiting Queue ({relevantTokens.length})
            </h3>
            {relevantTokens.slice(0, 5).map((t, i) => (
              <div key={t.id} style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '8px 12px', borderRadius: 8, background: i === 0 ? 'rgba(59,130,246,0.08)' : 'transparent',
              }}>
                <span style={{ fontWeight: 700, fontSize: 14, color: i === 0 ? 'var(--accent-blue)' : 'var(--text-primary)' }}>
                  {t.tokenNumber}
                </span>
                <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t.serviceName}</span>
                {t.priority !== 'NORMAL' && <span className={`badge badge-${t.priority.toLowerCase()}`} style={{ fontSize: 10 }}>{t.priority}</span>}
              </div>
            ))}
            {!relevantTokens.length && (
              isOpen ? (
                <div style={{ color: 'var(--text-muted)', fontSize: 13, padding: 16, border: '1px solid var(--border-color)', borderRadius: 8, background: 'rgba(255,255,255,0.02)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, color: 'var(--accent-green)' }}>
                    <CheckCircle size={16} /> No customers waiting
                  </div>
                  <div style={{ marginBottom: 4 }}>This counter handles:</div>
                  <ul style={{ margin: 0, paddingLeft: 20, marginBottom: 12 }}>
                    {currentCounter?.serviceNames?.map(s => <li key={s}>{s}</li>)}
                  </ul>
                  <div style={{ color: 'var(--text-secondary)' }}>
                    Customers for other services are handled by different counters.
                  </div>
                </div>
              ) : (
                <div style={{ color: 'var(--text-muted)', fontSize: 13, textAlign: 'center', padding: 16 }}>
                  Open the counter to start serving customers
                </div>
              )
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default function CounterPage() {
  return (
    <Suspense fallback={<div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>Loading...</div>}>
      <CounterContent />
    </Suspense>
  );
}
