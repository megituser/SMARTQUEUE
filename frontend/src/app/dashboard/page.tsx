'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/providers/AuthProvider';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { branchApi, queueApi, counterApi, appointmentApi } from '@/lib/api';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useToast } from '@/hooks/useToast';
import { DashboardResponse, QueueStatusResponse } from '@/lib/types';
import { motion } from 'framer-motion';
import Link from 'next/link';
import {
  Users, Clock, CheckCircle, XCircle, AlertTriangle, Monitor,
  CalendarCheck, TrendingUp, LogOut, LayoutDashboard, ArrowRight,
  Ticket, Building2
} from 'lucide-react';

export default function DashboardPage() {
  const { user, isAuthenticated, logout, loading: authLoading } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { subscribeToQueue } = useWebSocket();
  const { addToast } = useToast();
  const [selectedBranch, setSelectedBranch] = useState<number | undefined>(user?.branchId);

  useEffect(() => {
    if (!authLoading && !isAuthenticated) {
      router.replace('/login');
    }
  }, [isAuthenticated, authLoading, router]);

  useEffect(() => {
    if (user?.branchId) setSelectedBranch(user.branchId);
  }, [user]);

  // Subscribe to WebSocket updates
  useEffect(() => {
    if (!selectedBranch) return;
    const unsub = subscribeToQueue(selectedBranch, () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard', selectedBranch] });
      queryClient.invalidateQueries({ queryKey: ['queueStatus', selectedBranch] });
      queryClient.invalidateQueries({ queryKey: ['appointments', selectedBranch] });
    });
    return unsub;
  }, [selectedBranch, subscribeToQueue, queryClient]);

  const { data: branches } = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await branchApi.getAll()).data.data,
    enabled: isAuthenticated,
  });

  useEffect(() => {
    if (!selectedBranch && branches?.length > 0) {
      setSelectedBranch(branches[0].id);
    }
  }, [branches, selectedBranch]);

  const { data: dashboard } = useQuery<DashboardResponse>({
    queryKey: ['dashboard', selectedBranch],
    queryFn: async () => {
      if (!selectedBranch) return null;
      return (await branchApi.getDashboard(selectedBranch)).data.data;
    },
    refetchInterval: 15000,
    enabled: !!selectedBranch && isAuthenticated,
  });

  const { data: queueStatus } = useQuery<QueueStatusResponse>({
    queryKey: ['queueStatus', selectedBranch],
    queryFn: async () => {
      if (!selectedBranch) return null;
      return (await queueApi.getStatus(selectedBranch)).data.data;
    },
    refetchInterval: 10000,
    enabled: !!selectedBranch && isAuthenticated,
  });

  const { data: appointments } = useQuery({
    queryKey: ['appointments', selectedBranch],
    queryFn: async () => {
      if (!selectedBranch) return [];

      // Use local timezone date instead of UTC to prevent date shifting
      const today = new Date().toLocaleDateString('en-CA'); // en-CA gives strictly YYYY-MM-DD

      return (await appointmentApi.getByBranch(selectedBranch, today)).data.data;
    },
    refetchInterval: 15000,
    enabled: !!selectedBranch && isAuthenticated,
  });

  if (authLoading || !isAuthenticated) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>Loading...</div>;
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      {/* Header */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 32px', borderBottom: '1px solid var(--border-color)', background: 'var(--bg-secondary)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: 8, background: 'var(--gradient-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 14 }}>S</div>
            <span style={{ fontSize: 18, fontWeight: 700 }}>Smart<span className="gradient-text">Queue</span></span>
          </div>
          <div style={{ width: 1, height: 24, background: 'var(--border-color)', margin: '0 8px' }} />
          <select
            value={selectedBranch}
            onChange={(e) => setSelectedBranch(Number(e.target.value))}
            className="input-field"
            style={{ width: 250, padding: '8px 14px', fontSize: 13 }}
          >
            {branches?.map((b: any) => (
              <option key={b.id} value={b.id}>{b.name}</option>
            ))}
          </select>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Link href={`/queue?branch=${selectedBranch}`} className="btn-secondary" style={{ textDecoration: 'none', padding: '8px 16px', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}>
            <Monitor size={14} /> Queue Display
          </Link>
          <Link href={`/counter?branch=${selectedBranch}`} className="btn-secondary" style={{ textDecoration: 'none', padding: '8px 16px', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}>
            <Ticket size={14} /> Counter
          </Link>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px', borderRadius: 10, background: 'var(--bg-elevated)' }}>
            <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--gradient-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 12 }}>
              {user?.firstName?.[0]}
            </div>
            <span style={{ fontSize: 13, fontWeight: 600 }}>{user?.firstName}</span>
            <span className="badge badge-open" style={{ fontSize: 10 }}>{user?.role}</span>
          </div>
          <button onClick={logout} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', padding: 6 }}>
            <LogOut size={18} />
          </button>
        </div>
      </header>

      {/* Dashboard Content */}
      <main style={{ padding: 32, maxWidth: 1400, margin: '0 auto' }}>
        <div style={{ marginBottom: 32 }}>
          <h1 style={{ fontSize: 24, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 10 }}>
            <LayoutDashboard size={24} /> Dashboard
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: 14, marginTop: 4 }}>
            {dashboard?.branchName || 'Select a branch'} — Real-time overview
          </p>
        </div>

        {/* Stats Grid */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 16, marginBottom: 32 }}>
          <StatCard icon={<Users size={20} />} value={dashboard?.currentWaiting || 0} label="Waiting" color="var(--accent-amber)" />
          <StatCard icon={<TrendingUp size={20} />} value={dashboard?.currentServing || 0} label="Serving" color="var(--accent-blue)" />
          <StatCard icon={<CheckCircle size={20} />} value={dashboard?.completedToday || 0} label="Completed" color="var(--accent-green)" />
          <StatCard icon={<XCircle size={20} />} value={dashboard?.noShowToday || 0} label="No-Show" color="var(--accent-red)" />
          <StatCard icon={<Clock size={20} />} value={`${dashboard?.averageWaitMinutes?.toFixed(0) || 0}m`} label="Avg Wait" color="var(--accent-cyan)" />
          <StatCard icon={<Monitor size={20} />} value={`${dashboard?.activeCounters || 0}/${dashboard?.totalCounters || 0}`} label="Counters" color="var(--accent-purple)" />
          <StatCard icon={<CalendarCheck size={20} />} value={dashboard?.appointmentsToday || 0} label="Appointments" color="var(--accent-pink)" />
          <StatCard icon={<Ticket size={20} />} value={dashboard?.totalTokensToday || 0} label="Total Today" color="var(--accent-blue)" />
        </div>

        {/* Queue, Counters & Appointments */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 24 }}>
          {/* Waiting Queue */}
          <div className="glass-card" style={{ padding: 24, maxHeight: 500, overflow: 'auto' }}>
            <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Users size={18} /> Waiting Queue
              <span className="badge badge-waiting" style={{ marginLeft: 'auto' }}>{queueStatus?.totalWaiting || 0}</span>
            </h2>
            {queueStatus?.waitingTokens?.length ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {queueStatus.waitingTokens.map((t, i) => (
                  <motion.div
                    key={t.id}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.05 }}
                    style={{
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      padding: '12px 16px', borderRadius: 12, background: 'var(--bg-secondary)',
                      border: '1px solid var(--border-color)'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <span style={{ fontSize: 18, fontWeight: 800, color: 'var(--accent-blue)', minWidth: 60 }}>{t.tokenNumber}</span>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600 }}>{t.customerName || 'Walk-in'}</div>
                        <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{t.serviceName}</div>
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span className={`badge badge-${t.priority.toLowerCase()}`}>{t.priority}</span>
                      <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>~{t.estimatedWaitMinutes}m</span>
                    </div>
                  </motion.div>
                ))}
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>No tokens waiting</div>
            )}
          </div>

          {/* Counters */}
          <div className="glass-card" style={{ padding: 24 }}>
            <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Monitor size={18} /> Counter Status
            </h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {queueStatus?.counters?.map((c) => (
                <div
                  key={c.counterId}
                  className={c.currentToken ? 'pulse-serving' : ''}
                  style={{
                    padding: '16px 20px', borderRadius: 14, background: 'var(--bg-secondary)',
                    border: `1px solid ${c.status === 'OPEN' ? 'rgba(16,185,129,0.3)' : 'var(--border-color)'}`,
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <span style={{ fontWeight: 700 }}>{c.counterName || `Counter ${c.counterNumber}`}</span>
                    <span className={`badge badge-${c.status.toLowerCase()}`}>{c.status}</span>
                  </div>
                  {c.currentToken ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <span style={{ fontSize: 24, fontWeight: 800, color: 'var(--accent-blue)' }}>{c.currentToken.tokenNumber}</span>
                      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>— {c.currentToken.customerName || 'Customer'}</span>
                    </div>
                  ) : (
                    <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
                      {c.status === 'OPEN' ? 'Ready for next customer' : 'Counter closed'}
                    </div>
                  )}
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 6 }}>
                    Services: {c.serviceNames?.join(', ') || 'None'}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Today's Appointments */}
          <div className="glass-card" style={{ padding: 24, maxHeight: 500, overflow: 'auto' }}>
            <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
              <CalendarCheck size={18} /> Today's Appointments
              <span className="badge" style={{ marginLeft: 'auto', background: 'rgba(59, 130, 246, 0.1)', color: 'var(--accent-blue)' }}>
                {appointments?.length || 0}
              </span>
            </h2>
            {appointments?.length ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {appointments.map((apt: any, i: number) => {
                  let badgeBg = 'rgba(156, 163, 175, 0.1)';
                  let badgeColor = 'var(--text-muted)';
                  if (apt.status === 'BOOKED') { badgeBg = 'rgba(59, 130, 246, 0.1)'; badgeColor = 'var(--accent-blue)'; }
                  else if (apt.status === 'CHECKED_IN') { badgeBg = 'rgba(16, 185, 129, 0.1)'; badgeColor = 'var(--accent-green)'; }
                  else if (apt.status === 'CANCELLED' || apt.status === 'NO_SHOW') { badgeBg = 'rgba(239, 68, 68, 0.1)'; badgeColor = 'var(--accent-red)'; }

                  // Format time if it's HH:mm:ss
                  let displayTime = apt.startTime;
                  if (displayTime && /^\d{2}:\d{2}(:\d{2})?$/.test(displayTime)) {
                    const [h, m] = displayTime.split(':');
                    const d = new Date();
                    d.setHours(parseInt(h, 10), parseInt(m, 10));
                    displayTime = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                  }

                  return (
                    <motion.div
                      key={apt.id}
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: i * 0.05 }}
                      style={{
                        display: 'flex', flexDirection: 'column', gap: 12,
                        padding: '12px 16px', borderRadius: 12, background: 'var(--bg-secondary)',
                        border: '1px solid var(--border-color)'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <div>
                          <div style={{ fontSize: 13, fontWeight: 600 }}>{apt.customerName}</div>
                          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{apt.serviceName}</div>
                          <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 4 }}>
                            <Clock size={12} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 4 }} />
                            {displayTime}
                          </div>
                        </div>
                        <span className="badge" style={{ background: badgeBg, color: badgeColor }}>{apt.status}</span>
                      </div>
                      {apt.status === 'BOOKED' && (
                        <button
                          className="btn-primary"
                          style={{ width: '100%', padding: '6px 12px', fontSize: 12 }}
                          onClick={async () => {
                            try {
                              await appointmentApi.checkIn(apt.id);
                              addToast({ message: 'Customer checked in to queue', type: 'success' });
                              queryClient.invalidateQueries({ queryKey: ['appointments', selectedBranch] });
                              queryClient.invalidateQueries({ queryKey: ['queueStatus', selectedBranch] });
                            } catch (err: any) {
                              if (err.response?.status === 400) {
                                addToast({ message: err.response.data?.message || 'Check in failed', type: 'error' });
                              } else {
                                addToast({ message: 'Something went wrong', type: 'error' });
                              }
                            }
                          }}
                        >
                          Check In
                        </button>
                      )}
                    </motion.div>
                  );
                })}
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>No appointments today</div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

function StatCard({ icon, value, label, color }: { icon: React.ReactNode; value: string | number; label: string; color: string }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
      className="stat-card"
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
        <div style={{ color }}>{icon}</div>
      </div>
      <div className="stat-value" style={{ color }}>{value}</div>
      <div className="stat-label">{label}</div>
    </motion.div>
  );
}
