'use client';

import { useEffect, useState, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useAuth } from '@/providers/AuthProvider';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { branchApi, queueApi } from '@/lib/api';
import { BranchResponse, ServiceResponse, TokenResponse } from '@/lib/types';
import { motion, AnimatePresence } from 'framer-motion';
import Link from 'next/link';
import {
  Ticket, ArrowLeft, User, Phone, Mail, FileText, CheckCircle,
  Zap, MapPin, Briefcase, AlertTriangle, Copy, Printer, Star,
  Crown, ShieldCheck
} from 'lucide-react';

/* ─── Token Result Card ─── */
function TokenResultCard({ token, onReset }: { token: TokenResponse; onReset: () => void }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(token.tokenNumber);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: 'spring', stiffness: 200, damping: 20 }}
      className="glass-card"
      style={{ padding: 40, textAlign: 'center', maxWidth: 480, margin: '0 auto' }}
    >
      {/* Success Icon */}
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ delay: 0.2, type: 'spring', stiffness: 300 }}
        style={{
          width: 72, height: 72, borderRadius: '50%',
          background: 'rgba(16,185,129,0.15)', display: 'flex',
          alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px',
        }}
      >
        <CheckCircle size={36} style={{ color: 'var(--accent-green)' }} />
      </motion.div>

      <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 4 }}>Token Issued Successfully!</h2>
      <p style={{ color: 'var(--text-secondary)', fontSize: 14, marginBottom: 24 }}>
        Hand this token number to the customer
      </p>

      {/* Token Number - Large Display */}
      <div
        className="pulse-serving"
        style={{
          padding: '20px 32px', borderRadius: 20, marginBottom: 24,
          background: 'linear-gradient(135deg, rgba(59,130,246,0.12), rgba(139,92,246,0.12))',
          border: '2px solid rgba(59,130,246,0.25)',
        }}
      >
        <div style={{ fontSize: 12, color: 'var(--accent-cyan)', fontWeight: 600, letterSpacing: 1.5, textTransform: 'uppercase', marginBottom: 8 }}>
          Token Number
        </div>
        <div className="token-display" style={{ fontSize: '3.5rem' }}>{token.tokenNumber}</div>
      </div>

      {/* Details Grid */}
      <div style={{
        background: 'var(--bg-secondary)', borderRadius: 14, padding: 20,
        textAlign: 'left', marginBottom: 24,
      }}>
        {[
          { label: 'Service', value: token.serviceName, icon: <Briefcase size={14} /> },
          { label: 'Customer', value: token.customerName || 'Walk-in', icon: <User size={14} /> },
          { label: 'Priority', value: token.priority, icon: <Star size={14} /> },
          { label: 'Position', value: `#${token.positionInQueue ?? '—'}`, icon: <Ticket size={14} /> },
          { label: 'Est. Wait', value: token.estimatedWaitMinutes ? `~${token.estimatedWaitMinutes} min` : 'N/A', icon: <Zap size={14} /> },
        ].map((r, i) => (
          <div
            key={i}
            style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '10px 0',
              borderBottom: i < 4 ? '1px solid var(--border-color)' : 'none',
            }}
          >
            <span style={{ color: 'var(--text-muted)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}>
              {r.icon} {r.label}
            </span>
            <span style={{ fontWeight: 600, fontSize: 13 }}>{r.value}</span>
          </div>
        ))}
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', gap: 12 }}>
        <button
          onClick={handleCopy}
          className="btn-secondary"
          style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
        >
          <Copy size={14} /> {copied ? 'Copied!' : 'Copy Token'}
        </button>
        <button
          onClick={onReset}
          className="btn-primary"
          style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
        >
          <Ticket size={14} /> Issue Another
        </button>
      </div>
    </motion.div>
  );
}

/* ═══════════════════════════════════════════════════════════════════ */
/*                       TOKEN ISSUE CONTENT                         */
/* ═══════════════════════════════════════════════════════════════════ */
function TokenIssueContent() {
  const searchParams = useSearchParams();
  const branchIdParam = searchParams.get('branch');
  const router = useRouter();
  const { user, isAuthenticated, loading: authLoading } = useAuth();
  const queryClient = useQueryClient();

  /* ── Form State ── */
  const [selectedBranch, setSelectedBranch] = useState<number | null>(
    branchIdParam ? Number(branchIdParam) : (user?.branchId ?? null)
  );
  const [selectedService, setSelectedService] = useState<number | null>(null);
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [customerEmail, setCustomerEmail] = useState('');
  const [priority, setPriority] = useState<'NORMAL' | 'HIGH' | 'VIP'>('NORMAL');
  const [notes, setNotes] = useState('');
  const [issuedToken, setIssuedToken] = useState<TokenResponse | null>(null);
  const [error, setError] = useState('');

  /* ── Auth guard ── */
  useEffect(() => {
    if (!authLoading && !isAuthenticated) router.replace('/login');
  }, [isAuthenticated, authLoading, router]);

  /* ── Set default branch from user ── */
  useEffect(() => {
    if (!selectedBranch && user?.branchId) setSelectedBranch(user.branchId);
  }, [user, selectedBranch]);

  /* ── Data queries ── */
  const { data: branches } = useQuery<BranchResponse[]>({
    queryKey: ['branches'],
    queryFn: async () => (await branchApi.getAll()).data.data,
    enabled: isAuthenticated,
  });

  const { data: services } = useQuery<ServiceResponse[]>({
    queryKey: ['services', selectedBranch],
    queryFn: async () => (await branchApi.getServices(selectedBranch!)).data.data,
    enabled: !!selectedBranch,
  });

  /* ── Issue Token Mutation ── */
  const issueMutation = useMutation({
    mutationFn: async () => {
      const res = await queueApi.issueToken({
        branchId: selectedBranch,
        serviceId: selectedService,
        customerName: customerName.trim() || undefined,
        customerPhone: customerPhone.trim() || undefined,
        customerEmail: customerEmail.trim() || undefined,
        priority,
        source: 'WALK_IN',
        notes: notes.trim() || undefined,
      });
      return res.data.data;
    },
    onSuccess: (data: TokenResponse) => {
      setIssuedToken(data);
      setError('');
      queryClient.invalidateQueries({ queryKey: ['queueStatus'] });
    },
    onError: (err: any) => {
      const msg =
        err?.response?.data?.message ||
        (err?.response?.status === 400 ? 'Invalid request. Check your inputs.' : 'Failed to issue token. Please try again.');
      setError(msg);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!selectedBranch || !selectedService) {
      setError('Please select a branch and service');
      return;
    }
    issueMutation.mutate();
  };

  const resetForm = () => {
    setIssuedToken(null);
    setSelectedService(null);
    setCustomerName('');
    setCustomerPhone('');
    setCustomerEmail('');
    setPriority('NORMAL');
    setNotes('');
    setError('');
  };

  /* ── Loading state ── */
  if (authLoading || !isAuthenticated) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>Loading...</div>;
  }

  /* ── Token Issued → show result ── */
  if (issuedToken) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: 32 }}>
        <div style={{ maxWidth: 600, margin: '0 auto' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 32 }}>
            <Link href="/dashboard" style={{ color: 'var(--text-muted)', display: 'flex' }}>
              <ArrowLeft size={20} />
            </Link>
            <div>
              <h1 style={{ fontSize: 22, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Ticket size={22} /> Token Issued
              </h1>
            </div>
          </div>
          <TokenResultCard token={issuedToken} onReset={resetForm} />
        </div>
      </div>
    );
  }

  /* ── Main Form ── */
  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: 32 }}>
      <div style={{ maxWidth: 600, margin: '0 auto' }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 32 }}>
          <Link href="/dashboard" style={{ color: 'var(--text-muted)', display: 'flex' }}>
            <ArrowLeft size={20} />
          </Link>
          <div>
            <h1 style={{ fontSize: 22, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Ticket size={22} /> Issue Walk-in Token
            </h1>
            <p style={{ color: 'var(--text-secondary)', fontSize: 13, marginTop: 2 }}>
              Issue a queue token for walk-in customers
            </p>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          {/* Error */}
          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                style={{
                  display: 'flex', alignItems: 'center', gap: 8,
                  padding: '12px 16px', borderRadius: 12,
                  background: 'rgba(239,68,68,0.1)',
                  border: '1px solid rgba(239,68,68,0.2)',
                  color: 'var(--accent-red)', fontSize: 14, marginBottom: 20,
                }}
              >
                <AlertTriangle size={16} /> {error}
              </motion.div>
            )}
          </AnimatePresence>

          {/* Branch & Service Selection */}
          <div className="glass-card" style={{ padding: 24, marginBottom: 16 }}>
            <h2 style={{ fontSize: 15, fontWeight: 700, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
              <MapPin size={16} style={{ color: 'var(--accent-blue)' }} /> Service Selection
            </h2>

            <div style={{ marginBottom: 14 }}>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                Branch *
              </label>
              <select
                className="input-field"
                value={selectedBranch || ''}
                onChange={e => { setSelectedBranch(Number(e.target.value)); setSelectedService(null); }}
                required
              >
                <option value="">Select a branch...</option>
                {branches?.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                Service *
              </label>
              <select
                className="input-field"
                value={selectedService || ''}
                onChange={e => setSelectedService(Number(e.target.value))}
                required
                disabled={!selectedBranch}
              >
                <option value="">Select a service...</option>
                {services?.filter(s => s.isActive).map(s => (
                  <option key={s.id} value={s.id}>{s.name} (~{s.avgServiceTimeMinutes} min)</option>
                ))}
              </select>
            </div>
          </div>

          {/* Priority Selection */}
          <div className="glass-card" style={{ padding: 24, marginBottom: 16 }}>
            <h2 style={{ fontSize: 15, fontWeight: 700, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Star size={16} style={{ color: 'var(--accent-amber)' }} /> Priority
            </h2>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
              {([
                { value: 'NORMAL', label: 'Normal', icon: <Ticket size={18} />, color: 'var(--accent-blue)', bg: 'rgba(59,130,246,0.1)' },
                { value: 'HIGH', label: 'High', icon: <Zap size={18} />, color: 'var(--accent-purple)', bg: 'rgba(139,92,246,0.1)' },
                { value: 'VIP', label: 'VIP', icon: <Crown size={18} />, color: 'var(--accent-pink)', bg: 'rgba(236,72,153,0.1)' },
              ] as const).map(p => (
                <motion.button
                  key={p.value}
                  type="button"
                  whileTap={{ scale: 0.97 }}
                  onClick={() => setPriority(p.value)}
                  style={{
                    padding: '16px 12px', borderRadius: 14, cursor: 'pointer',
                    display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
                    background: priority === p.value ? p.bg : 'var(--bg-secondary)',
                    border: `2px solid ${priority === p.value ? p.color : 'var(--border-color)'}`,
                    color: priority === p.value ? p.color : 'var(--text-secondary)',
                    transition: 'all 0.2s ease',
                    fontWeight: 600, fontSize: 13,
                  }}
                >
                  {p.icon}
                  {p.label}
                </motion.button>
              ))}
            </div>
          </div>

          {/* Customer Details (Optional) */}
          <div className="glass-card" style={{ padding: 24, marginBottom: 16 }}>
            <h2 style={{ fontSize: 15, fontWeight: 700, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 8 }}>
              <User size={16} style={{ color: 'var(--accent-green)' }} /> Customer Details
            </h2>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 16 }}>Optional — leave blank for anonymous walk-in</p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Name</label>
                <div style={{ position: 'relative' }}>
                  <User size={16} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                  <input
                    className="input-field"
                    placeholder="Customer name"
                    value={customerName}
                    onChange={e => setCustomerName(e.target.value)}
                    style={{ paddingLeft: 40 }}
                  />
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Phone</label>
                <div style={{ position: 'relative' }}>
                  <Phone size={16} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                  <input
                    className="input-field"
                    placeholder="+1-555-0100"
                    value={customerPhone}
                    onChange={e => setCustomerPhone(e.target.value)}
                    style={{ paddingLeft: 40 }}
                  />
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Email</label>
                <div style={{ position: 'relative' }}>
                  <Mail size={16} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                  <input
                    className="input-field"
                    type="email"
                    placeholder="customer@example.com"
                    value={customerEmail}
                    onChange={e => setCustomerEmail(e.target.value)}
                    style={{ paddingLeft: 40 }}
                  />
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Notes</label>
                <div style={{ position: 'relative' }}>
                  <FileText size={16} style={{ position: 'absolute', left: 14, top: 14, color: 'var(--text-muted)' }} />
                  <textarea
                    className="input-field"
                    rows={2}
                    placeholder="Any special notes..."
                    value={notes}
                    onChange={e => setNotes(e.target.value)}
                    style={{ paddingLeft: 40, resize: 'vertical' }}
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Submit */}
          <motion.button
            type="submit"
            className="btn-success"
            disabled={issueMutation.isPending || !selectedBranch || !selectedService}
            whileTap={{ scale: 0.98 }}
            style={{
              width: '100%', padding: '16px 24px', fontSize: 16,
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10,
            }}
          >
            <Ticket size={20} />
            {issueMutation.isPending ? 'Issuing Token...' : 'Issue Token'}
          </motion.button>
        </form>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════════ */
/*                           PAGE EXPORT                             */
/* ═══════════════════════════════════════════════════════════════════ */
export default function TokenIssuePage() {
  return (
    <Suspense fallback={<div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>Loading...</div>}>
      <TokenIssueContent />
    </Suspense>
  );
}
