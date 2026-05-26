'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/providers/AuthProvider';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { branchApi, counterApi, authApi } from '@/lib/api';
import { BranchResponse, ServiceResponse, CounterStatusResponse } from '@/lib/types';
import { motion, AnimatePresence } from 'framer-motion';
import Link from 'next/link';
import {
  Settings, Building2, Briefcase, Monitor, UserPlus, ArrowLeft,
  Plus, X, CheckCircle, AlertTriangle, ChevronRight, Shield, Bell
} from 'lucide-react';

type Tab = 'branches' | 'services' | 'counters' | 'staff' | 'notifications';

/* ─── Toast ─── */
function useLocalToast() {
  const [msg, setMsg] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
  const show = (text: string, type: 'success' | 'error' = 'success') => {
    setMsg({ text, type });
    setTimeout(() => setMsg(null), 4000);
  };
  return { msg, show };
}

function Toast({ msg }: { msg: { text: string; type: string } | null }) {
  if (!msg) return null;
  const isErr = msg.type === 'error';
  return (
    <motion.div initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
      style={{ position: 'fixed', top: 20, right: 20, zIndex: 9999, padding: '12px 20px', borderRadius: 12,
        background: isErr ? 'rgba(239,68,68,0.15)' : 'rgba(16,185,129,0.15)',
        border: `1px solid ${isErr ? 'rgba(239,68,68,0.3)' : 'rgba(16,185,129,0.3)'}`,
        color: isErr ? 'var(--accent-red)' : 'var(--accent-green)', fontSize: 14, fontWeight: 600,
        display: 'flex', alignItems: 'center', gap: 8, backdropFilter: 'blur(12px)' }}>
      {isErr ? <AlertTriangle size={16} /> : <CheckCircle size={16} />} {msg.text}
    </motion.div>
  );
}

/* ─── Modal Wrapper ─── */
function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: React.ReactNode }) {
  if (!open) return null;
  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}>
      <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
        className="glass-card" style={{ padding: 28, width: '100%', maxWidth: 480, maxHeight: '90vh', overflow: 'auto' }}
        onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700 }}>{title}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', padding: 4 }}><X size={18} /></button>
        </div>
        {children}
      </motion.div>
    </div>
  );
}

/* ─── Field ─── */
function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>{label} {required && '*'}</label>
      {children}
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
export default function AdminPage() {
  const { user, isAuthenticated, loading: authLoading } = useAuth();
  const router = useRouter();
  const qc = useQueryClient();
  const { msg, show } = useLocalToast();
  const [tab, setTab] = useState<Tab>('branches');
  const [selBranch, setSelBranch] = useState<number | null>(null);
  const [modal, setModal] = useState<string | null>(null);

  useEffect(() => { if (!authLoading && !isAuthenticated) router.replace('/login'); }, [isAuthenticated, authLoading, router]);
  useEffect(() => {
    if (user && user.role !== 'SUPER_ADMIN' && user.role !== 'BRANCH_ADMIN') router.replace('/dashboard');
  }, [user, router]);

  const { data: branches } = useQuery<BranchResponse[]>({ queryKey: ['branches'], queryFn: async () => (await branchApi.getAll()).data.data, enabled: isAuthenticated });
  const { data: services } = useQuery<ServiceResponse[]>({ queryKey: ['services', selBranch], queryFn: async () => (await branchApi.getServices(selBranch!)).data.data, enabled: !!selBranch });
  const { data: counters } = useQuery<CounterStatusResponse[]>({ queryKey: ['counters', selBranch], queryFn: async () => (await counterApi.getByBranch(selBranch!)).data.data, enabled: !!selBranch });

  useEffect(() => { if (!selBranch && branches?.length) setSelBranch(branches[0].id); }, [branches, selBranch]);

  /* ── Branch Form State ── */
  const [bf, setBf] = useState({ name: '', code: '', address: '', phone: '', timezone: '' });
  const [editingBranchId, setEditingBranchId] = useState<number | null>(null);

  const branchMut = useMutation({
    mutationFn: () => editingBranchId ? branchApi.update(editingBranchId, bf) : branchApi.create(bf),
    onSuccess: () => { 
      qc.invalidateQueries({ queryKey: ['branches'] }); 
      setModal(null); 
      setBf({ name: '', code: '', address: '', phone: '', timezone: '' }); 
      setEditingBranchId(null);
      show(editingBranchId ? 'Branch updated' : 'Branch created'); 
    },
    onError: (e: any) => show(e?.response?.data?.message || 'Failed', 'error'),
  });

  const handleEditBranch = (b: BranchResponse) => {
    setBf({ name: b.name, code: b.code, address: b.address || '', phone: b.phone || '', timezone: b.timezone || '' });
    setEditingBranchId(b.id);
    setModal('branch');
  };

  /* ── Service Form State ── */
  const [sf, setSf] = useState({ name: '', code: '', description: '', avgServiceTimeMinutes: 15 });
  const serviceMut = useMutation({
    mutationFn: () => branchApi.createService(selBranch!, sf),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['services', selBranch] }); setModal(null); setSf({ name: '', code: '', description: '', avgServiceTimeMinutes: 15 }); show('Service created'); },
    onError: (e: any) => show(e?.response?.data?.message || 'Failed', 'error'),
  });

  /* ── Counter Form State ── */
  const [cf, setCf] = useState({ counterNumber: 1, name: '', serviceIds: [] as number[] });
  const counterMut = useMutation({
    mutationFn: () => counterApi.create({ branchId: selBranch, counterNumber: cf.counterNumber, name: cf.name, serviceIds: cf.serviceIds }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['counters', selBranch] }); setModal(null); setCf({ counterNumber: 1, name: '', serviceIds: [] }); show('Counter created'); },
    onError: (e: any) => show(e?.response?.data?.message || 'Failed', 'error'),
  });

  /* ── Staff Form State ── */
  const [uf, setUf] = useState({ email: '', password: '', firstName: '', lastName: '', phone: '', role: 'STAFF' as string, branchId: null as number | null });
  const staffMut = useMutation({
    mutationFn: () => authApi.register({ ...uf, branchId: uf.branchId || selBranch }),
    onSuccess: () => { setModal(null); setUf({ email: '', password: '', firstName: '', lastName: '', phone: '', role: 'STAFF', branchId: null }); show('Staff registered'); },
    onError: (e: any) => show(e?.response?.data?.message || 'Registration failed', 'error'),
  });

  if (authLoading || !isAuthenticated) return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>Loading...</div>;

  const tabs: { key: Tab; label: string; icon: React.ReactNode }[] = [
    { key: 'branches', label: 'Branches', icon: <Building2 size={16} /> },
    { key: 'services', label: 'Services', icon: <Briefcase size={16} /> },
    { key: 'counters', label: 'Counters', icon: <Monitor size={16} /> },
    { key: 'staff', label: 'Add Staff', icon: <UserPlus size={16} /> },
    { key: 'notifications', label: 'Notifications', icon: <Bell size={16} /> },
  ];

  const toggleSvcId = (id: number) => setCf(p => ({ ...p, serviceIds: p.serviceIds.includes(id) ? p.serviceIds.filter(x => x !== id) : [...p.serviceIds, id] }));

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: 32 }}>
      <Toast msg={msg} />

      {/* Header */}
      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 32 }}>
          <Link href="/dashboard" style={{ color: 'var(--text-muted)', display: 'flex' }}><ArrowLeft size={20} /></Link>
          <div>
            <h1 style={{ fontSize: 22, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 8 }}><Settings size={22} /> Admin Panel</h1>
            <p style={{ color: 'var(--text-secondary)', fontSize: 13, marginTop: 2 }}>Manage branches, services, counters & staff</p>
          </div>
          <span className="badge badge-open" style={{ marginLeft: 'auto', fontSize: 11 }}><Shield size={12} style={{ marginRight: 4 }} />{user?.role}</span>
        </div>

        {/* Branch Selector */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
          <select className="input-field" value={selBranch || ''} onChange={e => setSelBranch(Number(e.target.value))} style={{ maxWidth: 300 }}>
            {branches?.map(b => <option key={b.id} value={b.id}>{b.name} ({b.code})</option>)}
          </select>
        </div>

        {/* Tabs */}
        <div style={{ display: 'flex', gap: 4, marginBottom: 24, background: 'var(--bg-secondary)', padding: 4, borderRadius: 14 }}>
          {tabs.map(t => (
            <button key={t.key} onClick={() => setTab(t.key)}
              style={{ flex: 1, padding: '10px 16px', borderRadius: 10, border: 'none', cursor: 'pointer', fontSize: 13, fontWeight: 600,
                background: tab === t.key ? 'var(--accent-blue)' : 'transparent',
                color: tab === t.key ? 'white' : 'var(--text-muted)', transition: 'all 0.2s',
                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
              {t.icon} {t.label}
            </button>
          ))}
        </div>

        {/* ═══ BRANCHES TAB ═══ */}
        {tab === 'branches' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700 }}>All Branches</h2>
              {user?.role === 'SUPER_ADMIN' && (
                <button className="btn-primary" onClick={() => { setEditingBranchId(null); setBf({ name: '', code: '', address: '', phone: '', timezone: '' }); setModal('branch'); }} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 16px', fontSize: 13 }}>
                  <Plus size={14} /> New Branch
                </button>
              )}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12 }}>
              {branches?.map(b => (
                <div key={b.id} className="glass-card glass-card-hover" style={{ padding: 20, cursor: 'pointer' }} onClick={() => setSelBranch(b.id)}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                    <span style={{ fontWeight: 700, fontSize: 15 }}>{b.name}</span>
                    <span className={`badge ${b.isActive ? 'badge-open' : 'badge-closed'}`}>{b.isActive ? 'Active' : 'Inactive'}</span>
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Code: {b.code}</div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      {b.address && <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>{b.address}</div>}
                      {b.phone && <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>📞 {b.phone}</div>}
                    </div>
                    {user?.role === 'SUPER_ADMIN' && (
                      <button 
                        onClick={(e) => { e.stopPropagation(); handleEditBranch(b); }} 
                        style={{ background: 'var(--bg-elevated)', border: '1px solid var(--border-color)', color: 'var(--text-primary)', padding: '6px 12px', borderRadius: 8, fontSize: 12, cursor: 'pointer', marginTop: 4 }}
                      >
                        Edit
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ═══ SERVICES TAB ═══ */}
        {tab === 'services' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700 }}>Services — {branches?.find(b => b.id === selBranch)?.name}</h2>
              <button className="btn-primary" onClick={() => setModal('service')} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 16px', fontSize: 13 }}>
                <Plus size={14} /> New Service
              </button>
            </div>
            {services?.length ? (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 12 }}>
                {services.map(s => (
                  <div key={s.id} className="glass-card" style={{ padding: 20 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                      <span style={{ fontWeight: 700 }}>{s.name}</span>
                      <span className={`badge ${s.isActive ? 'badge-open' : 'badge-closed'}`}>{s.isActive ? 'Active' : 'Inactive'}</span>
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Code: {s.code}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>Avg Time: ~{s.avgServiceTimeMinutes} min</div>
                    {s.description && <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 4 }}>{s.description}</div>}
                  </div>
                ))}
              </div>
            ) : <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>No services yet. Create one to get started.</div>}
          </div>
        )}

        {/* ═══ COUNTERS TAB ═══ */}
        {tab === 'counters' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700 }}>Counters — {branches?.find(b => b.id === selBranch)?.name}</h2>
              <button className="btn-primary" onClick={() => setModal('counter')} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 16px', fontSize: 13 }}>
                <Plus size={14} /> New Counter
              </button>
            </div>
            {counters?.length ? (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 12 }}>
                {counters.map(c => (
                  <div key={c.counterId} className="glass-card" style={{ padding: 20 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                      <span style={{ fontWeight: 700 }}>{c.counterName || `Counter ${c.counterNumber}`}</span>
                      <span className={`badge badge-${c.status.toLowerCase()}`}>{c.status}</span>
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Number: {c.counterNumber}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>Services: {c.serviceNames?.join(', ') || 'None'}</div>
                    {c.currentToken && <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent-blue)', marginTop: 6 }}>Serving: {c.currentToken.tokenNumber}</div>}
                  </div>
                ))}
              </div>
            ) : <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>No counters yet. Create one to get started.</div>}
          </div>
        )}

        {/* ═══ STAFF TAB ═══ */}
        {tab === 'staff' && (
          <div>
            <div style={{ marginBottom: 16 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 4 }}>Register New Staff</h2>
              <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>Create accounts for staff, counter agents, and branch admins</p>
            </div>
            <div className="glass-card" style={{ padding: 28, maxWidth: 480 }}>
              <form onSubmit={e => { e.preventDefault(); staffMut.mutate(); }}>
                <Field label="Email" required><input className="input-field" type="email" value={uf.email} onChange={e => setUf(p => ({ ...p, email: e.target.value }))} placeholder="staff@smartqueue.com" required /></Field>
                <Field label="Password" required><input className="input-field" type="password" value={uf.password} onChange={e => setUf(p => ({ ...p, password: e.target.value }))} placeholder="Min 8 characters" required minLength={8} /></Field>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <Field label="First Name" required><input className="input-field" value={uf.firstName} onChange={e => setUf(p => ({ ...p, firstName: e.target.value }))} placeholder="John" required /></Field>
                  <Field label="Last Name"><input className="input-field" value={uf.lastName} onChange={e => setUf(p => ({ ...p, lastName: e.target.value }))} placeholder="Doe" /></Field>
                </div>
                <Field label="Phone"><input className="input-field" value={uf.phone} onChange={e => setUf(p => ({ ...p, phone: e.target.value }))} placeholder="+1-555-0100" /></Field>
                <Field label="Role" required>
                  <select className="input-field" value={uf.role} onChange={e => setUf(p => ({ ...p, role: e.target.value }))}>
                    <option value="STAFF">Staff</option>
                    <option value="COUNTER_AGENT">Counter Agent</option>
                    <option value="BRANCH_ADMIN">Branch Admin</option>
                    {user?.role === 'SUPER_ADMIN' && <option value="SUPER_ADMIN">Super Admin</option>}
                  </select>
                </Field>
                <Field label="Assign to Branch">
                  <select className="input-field" value={uf.branchId || ''} onChange={e => setUf(p => ({ ...p, branchId: e.target.value ? Number(e.target.value) : null }))}>
                    <option value="">Current branch ({branches?.find(b => b.id === selBranch)?.name})</option>
                    {branches?.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
                  </select>
                </Field>
                <button type="submit" className="btn-success" disabled={staffMut.isPending} style={{ width: '100%', marginTop: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                  <UserPlus size={16} /> {staffMut.isPending ? 'Registering...' : 'Register Staff'}
                </button>
              </form>
            </div>
          </div>
        )}

        {/* ═══ NOTIFICATIONS TAB ═══ */}
        {tab === 'notifications' && (
          <div>
            <div style={{ marginBottom: 16 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 4 }}>Notification Preferences</h2>
              <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>Configure automated alerts for customers. Settings are synced with the backend NotificationService.</p>
            </div>
            <div className="glass-card" style={{ padding: 28, maxWidth: 600 }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', background: 'var(--bg-secondary)', borderRadius: 12, border: '1px solid var(--border-color)' }}>
                  <div>
                    <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Email Notifications</h3>
                    <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>Send booking confirmations and status updates via email</p>
                  </div>
                  <div style={{ width: 44, height: 24, background: 'var(--accent-green)', borderRadius: 20, position: 'relative', cursor: 'pointer' }}>
                    <div style={{ position: 'absolute', right: 2, top: 2, width: 20, height: 20, background: 'white', borderRadius: '50%' }}></div>
                  </div>
                </div>
                
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', background: 'var(--bg-secondary)', borderRadius: 12, border: '1px solid var(--border-color)' }}>
                  <div>
                    <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>SMS Alerts</h3>
                    <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>Send waitlist updates and counter calls via SMS</p>
                  </div>
                  <div style={{ width: 44, height: 24, background: 'var(--accent-green)', borderRadius: 20, position: 'relative', cursor: 'pointer' }}>
                    <div style={{ position: 'absolute', right: 2, top: 2, width: 20, height: 20, background: 'white', borderRadius: '50%' }}></div>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', background: 'var(--bg-secondary)', borderRadius: 12, border: '1px solid var(--border-color)' }}>
                  <div>
                    <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>WhatsApp Updates</h3>
                    <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>Send rich media updates via WhatsApp</p>
                  </div>
                  <div style={{ width: 44, height: 24, background: 'var(--bg-elevated)', borderRadius: 20, position: 'relative', cursor: 'pointer', opacity: 0.7 }}>
                    <div style={{ position: 'absolute', left: 2, top: 2, width: 20, height: 20, background: 'var(--text-muted)', borderRadius: '50%' }}></div>
                  </div>
                </div>

                <div style={{ marginTop: 12, padding: 12, background: 'rgba(59,130,246,0.1)', borderRadius: 8, border: '1px solid rgba(59,130,246,0.2)' }}>
                  <p style={{ fontSize: 12, color: 'var(--text-secondary)' }}><span style={{ color: 'var(--accent-blue)', fontWeight: 600 }}>Note:</span> These settings are currently in Mock mode for this environment.</p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ═══ MODALS ═══ */}
      <Modal open={modal === 'branch'} onClose={() => { setModal(null); setEditingBranchId(null); }} title={editingBranchId ? "Edit Branch" : "Create New Branch"}>
        <form onSubmit={e => { e.preventDefault(); branchMut.mutate(); }}>
          <Field label="Name" required><input className="input-field" value={bf.name} onChange={e => setBf(p => ({ ...p, name: e.target.value }))} placeholder="Downtown HQ" required /></Field>
          <Field label="Code" required><input className="input-field" value={bf.code} onChange={e => setBf(p => ({ ...p, code: e.target.value }))} placeholder="HQ" required disabled={!!editingBranchId} /></Field>
          <Field label="Address"><input className="input-field" value={bf.address} onChange={e => setBf(p => ({ ...p, address: e.target.value }))} placeholder="123 Main St" /></Field>
          <Field label="Phone"><input className="input-field" value={bf.phone} onChange={e => setBf(p => ({ ...p, phone: e.target.value }))} placeholder="+1-555-0100" /></Field>
          <Field label="Timezone"><input className="input-field" value={bf.timezone} onChange={e => setBf(p => ({ ...p, timezone: e.target.value }))} placeholder="Asia/Kolkata" /></Field>
          <button type="submit" className="btn-success" disabled={branchMut.isPending} style={{ width: '100%', marginTop: 8 }}>
            {branchMut.isPending ? 'Saving...' : (editingBranchId ? 'Update Branch' : 'Create Branch')}
          </button>
        </form>
      </Modal>

      <Modal open={modal === 'service'} onClose={() => setModal(null)} title="Create New Service">
        <form onSubmit={e => { e.preventDefault(); serviceMut.mutate(); }}>
          <Field label="Name" required><input className="input-field" value={sf.name} onChange={e => setSf(p => ({ ...p, name: e.target.value }))} placeholder="General Consultation" required /></Field>
          <Field label="Code" required><input className="input-field" value={sf.code} onChange={e => setSf(p => ({ ...p, code: e.target.value }))} placeholder="GEN" required /></Field>
          <Field label="Description"><textarea className="input-field" rows={2} value={sf.description} onChange={e => setSf(p => ({ ...p, description: e.target.value }))} placeholder="Optional description" style={{ resize: 'vertical' }} /></Field>
          <Field label="Avg Service Time (min)"><input className="input-field" type="number" value={sf.avgServiceTimeMinutes} onChange={e => setSf(p => ({ ...p, avgServiceTimeMinutes: Number(e.target.value) }))} min={1} max={120} /></Field>
          <button type="submit" className="btn-success" disabled={serviceMut.isPending} style={{ width: '100%', marginTop: 8 }}>
            {serviceMut.isPending ? 'Creating...' : 'Create Service'}
          </button>
        </form>
      </Modal>

      <Modal open={modal === 'counter'} onClose={() => setModal(null)} title="Create New Counter">
        <form onSubmit={e => { e.preventDefault(); counterMut.mutate(); }}>
          <Field label="Counter Number" required><input className="input-field" type="number" value={cf.counterNumber} onChange={e => setCf(p => ({ ...p, counterNumber: Number(e.target.value) }))} min={1} required /></Field>
          <Field label="Name"><input className="input-field" value={cf.name} onChange={e => setCf(p => ({ ...p, name: e.target.value }))} placeholder="Counter 1" /></Field>
          <Field label="Assign Services">
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {services?.map(s => (
                <button key={s.id} type="button" onClick={() => toggleSvcId(s.id)}
                  style={{ padding: '6px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: `1px solid ${cf.serviceIds.includes(s.id) ? 'var(--accent-blue)' : 'var(--border-color)'}`,
                    background: cf.serviceIds.includes(s.id) ? 'rgba(59,130,246,0.15)' : 'var(--bg-secondary)',
                    color: cf.serviceIds.includes(s.id) ? 'var(--accent-blue)' : 'var(--text-muted)', transition: 'all 0.2s' }}>
                  {s.name}
                </button>
              ))}
              {!services?.length && <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Create services first</span>}
            </div>
          </Field>
          <button type="submit" className="btn-success" disabled={counterMut.isPending} style={{ width: '100%', marginTop: 8 }}>
            {counterMut.isPending ? 'Creating...' : 'Create Counter'}
          </button>
        </form>
      </Modal>
    </div>
  );
}
