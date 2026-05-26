'use client';

import { useState } from 'react';
import { queueApi } from '@/lib/api';
import { TokenResponse } from '@/lib/types';
import { motion, AnimatePresence } from 'framer-motion';
import Link from 'next/link';
import {
  Search, Ticket, Clock, User, Briefcase, MapPin, ArrowLeft,
  AlertTriangle, CheckCircle, XCircle, Loader2, Zap
} from 'lucide-react';

const STATUS_CONFIG: Record<string, { color: string; bg: string; icon: React.ReactNode; label: string }> = {
  WAITING: { color: 'var(--accent-amber)', bg: 'rgba(245,158,11,0.12)', icon: <Clock size={20} />, label: 'Waiting in Queue' },
  SERVING: { color: 'var(--accent-blue)', bg: 'rgba(59,130,246,0.12)', icon: <Zap size={20} />, label: 'Now Being Served' },
  COMPLETED: { color: 'var(--accent-green)', bg: 'rgba(16,185,129,0.12)', icon: <CheckCircle size={20} />, label: 'Service Completed' },
  NO_SHOW: { color: 'var(--accent-red)', bg: 'rgba(239,68,68,0.12)', icon: <XCircle size={20} />, label: 'Marked No-Show' },
  CANCELLED: { color: 'var(--text-muted)', bg: 'rgba(107,107,128,0.12)', icon: <XCircle size={20} />, label: 'Cancelled' },
};

export default function TrackTokenPage() {
  const [tokenId, setTokenId] = useState('');
  const [token, setToken] = useState<TokenResponse | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!tokenId.trim()) return;
    setError('');
    setToken(null);
    setLoading(true);
    try {
      const res = await queueApi.getToken(Number(tokenId.trim()));
      setToken(res.data.data);
    } catch (err: any) {
      if (err?.response?.status === 404) setError('Token not found. Please check the ID and try again.');
      else setError(err?.response?.data?.message || 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const statusCfg = token ? (STATUS_CONFIG[token.status] || STATUS_CONFIG.WAITING) : null;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: '40px 20px' }}>
      <div style={{ maxWidth: 520, margin: '0 auto' }}>
        <Link href="/" style={{ color: 'var(--text-muted)', textDecoration: 'none', fontSize: 13, display: 'flex', alignItems: 'center', gap: 4, marginBottom: 24 }}>
          <ArrowLeft size={14} /> Back to Home
        </Link>

        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <Search size={32} style={{ color: 'var(--accent-blue)', marginBottom: 8 }} />
          <h1 style={{ fontSize: 24, fontWeight: 700 }}>Track Your Token</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: 14, marginTop: 4 }}>Enter your token ID to check your queue position</p>
        </div>

        {/* Search Form */}
        <form onSubmit={handleSearch} className="glass-card" style={{ padding: 24, marginBottom: 24 }}>
          <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 8 }}>Token ID</label>
          <div style={{ display: 'flex', gap: 10 }}>
            <div style={{ flex: 1, position: 'relative' }}>
              <Ticket size={16} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
              <input
                className="input-field"
                type="number"
                placeholder="e.g. 42"
                value={tokenId}
                onChange={e => setTokenId(e.target.value)}
                style={{ paddingLeft: 40 }}
                required
                min={1}
              />
            </div>
            <button type="submit" className="btn-primary" disabled={loading} style={{ padding: '12px 24px', display: 'flex', alignItems: 'center', gap: 6 }}>
              {loading ? <Loader2 size={16} className="spin" /> : <Search size={16} />} Track
            </button>
          </div>
        </form>

        {/* Error */}
        <AnimatePresence>
          {error && (
            <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
              style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 16px', borderRadius: 12, background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.2)', color: 'var(--accent-red)', fontSize: 14, marginBottom: 20 }}>
              <AlertTriangle size={16} /> {error}
            </motion.div>
          )}
        </AnimatePresence>

        {/* Token Result */}
        <AnimatePresence>
          {token && statusCfg && (
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }} className="glass-card" style={{ padding: 32 }}>
              {/* Status Banner */}
              <div style={{ textAlign: 'center', marginBottom: 24 }}>
                <div style={{ width: 56, height: 56, borderRadius: '50%', background: statusCfg.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 12px', color: statusCfg.color }}>
                  {statusCfg.icon}
                </div>
                <div className={token.status === 'SERVING' ? 'pulse-serving' : ''} style={{ display: 'inline-block', padding: '6px 20px', borderRadius: 20, background: statusCfg.bg, color: statusCfg.color, fontWeight: 700, fontSize: 14 }}>
                  {statusCfg.label}
                </div>
              </div>

              {/* Token Number */}
              <div style={{ textAlign: 'center', marginBottom: 24, padding: '16px 0', borderRadius: 16, background: 'linear-gradient(135deg, rgba(59,130,246,0.08), rgba(139,92,246,0.08))', border: '1px solid rgba(59,130,246,0.15)' }}>
                <div style={{ fontSize: 11, color: 'var(--accent-cyan)', fontWeight: 600, letterSpacing: 1.5, textTransform: 'uppercase', marginBottom: 4 }}>Token Number</div>
                <div className="token-display" style={{ fontSize: '2.5rem' }}>{token.tokenNumber}</div>
              </div>

              {/* Details */}
              <div style={{ background: 'var(--bg-secondary)', borderRadius: 14, padding: 16 }}>
                {[
                  { icon: <MapPin size={14} />, label: 'Branch', value: token.branchName },
                  { icon: <Briefcase size={14} />, label: 'Service', value: token.serviceName },
                  { icon: <User size={14} />, label: 'Customer', value: token.customerName || 'Walk-in' },
                  ...(token.counterName ? [{ icon: <Ticket size={14} />, label: 'Counter', value: token.counterName }] : []),
                  ...(token.positionInQueue != null ? [{ icon: <Zap size={14} />, label: 'Position', value: `#${token.positionInQueue}` }] : []),
                  ...(token.estimatedWaitMinutes != null ? [{ icon: <Clock size={14} />, label: 'Est. Wait', value: `~${token.estimatedWaitMinutes} min` }] : []),
                ].map((r, i, arr) => (
                  <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: i < arr.length - 1 ? '1px solid var(--border-color)' : 'none' }}>
                    <span style={{ color: 'var(--text-muted)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>{r.icon} {r.label}</span>
                    <span style={{ fontWeight: 600, fontSize: 13 }}>{r.value}</span>
                  </div>
                ))}
              </div>

              {/* Refresh */}
              <button onClick={handleSearch} className="btn-secondary" style={{ width: '100%', marginTop: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                <Search size={14} /> Refresh Status
              </button>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <style jsx>{`
        @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
        .spin { animation: spin 1s linear infinite; }
      `}</style>
    </div>
  );
}
