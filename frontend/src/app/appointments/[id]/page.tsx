'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { appointmentApi } from '@/lib/api';
import { AppointmentResponse } from '@/lib/types';
import { motion, AnimatePresence } from 'framer-motion';
import Link from 'next/link';
import {
  CalendarCheck, User, MapPin, Briefcase, Clock, FileText,
  AlertTriangle, ArrowLeft, CheckCircle, XCircle, Loader2, Zap
} from 'lucide-react';

const APPT_STATUS_CONFIG: Record<string, { color: string; bg: string; icon: React.ReactNode; label: string }> = {
  BOOKED: { color: 'var(--accent-blue)', bg: 'rgba(59,130,246,0.12)', icon: <CalendarCheck size={20} />, label: 'Booked' },
  CONFIRMED: { color: 'var(--accent-green)', bg: 'rgba(16,185,129,0.12)', icon: <CheckCircle size={20} />, label: 'Confirmed' },
  CHECKED_IN: { color: 'var(--accent-purple)', bg: 'rgba(139,92,246,0.12)', icon: <Zap size={20} />, label: 'Checked In' },
  COMPLETED: { color: 'var(--accent-green)', bg: 'rgba(16,185,129,0.12)', icon: <CheckCircle size={20} />, label: 'Completed' },
  CANCELLED: { color: 'var(--text-muted)', bg: 'rgba(107,107,128,0.12)', icon: <XCircle size={20} />, label: 'Cancelled' },
  NO_SHOW: { color: 'var(--accent-red)', bg: 'rgba(239,68,68,0.12)', icon: <XCircle size={20} />, label: 'No Show' },
};

export default function AppointmentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = Number(params.id);
  const queryClient = useQueryClient();
  const [error, setError] = useState('');

  const { data: appointment, isLoading, isError, error: fetchError } = useQuery<AppointmentResponse>({
    queryKey: ['appointment', id],
    queryFn: async () => {
      const res = await appointmentApi.getById(id);
      return res.data.data;
    },
    retry: false,
    enabled: !!id,
  });

  const cancelMutation = useMutation({
    mutationFn: () => appointmentApi.cancel(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointment', id] });
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Failed to cancel appointment');
    }
  });

  if (isLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-muted)' }}>
        <Loader2 size={24} className="spin" />
        <span style={{ marginLeft: 8 }}>Loading appointment...</span>
        <style jsx>{`@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } } .spin { animation: spin 1s linear infinite; }`}</style>
      </div>
    );
  }

  if (isError || !appointment) {
    return (
      <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: '40px 20px', textAlign: 'center' }}>
        <div style={{ maxWidth: 520, margin: '0 auto' }}>
          <Link href="/" style={{ color: 'var(--text-muted)', textDecoration: 'none', fontSize: 13, display: 'flex', alignItems: 'center', gap: 4, marginBottom: 24 }}>
            <ArrowLeft size={14} /> Back to Home
          </Link>
          <div className="glass-card" style={{ padding: 40 }}>
            <AlertTriangle size={48} style={{ color: 'var(--accent-red)', margin: '0 auto 16px' }} />
            <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 8 }}>Appointment Not Found</h2>
            <p style={{ color: 'var(--text-secondary)' }}>
              {((fetchError as any)?.response?.status === 404) ? 'The appointment ID provided does not exist.' : 'An error occurred while fetching the appointment.'}
            </p>
          </div>
        </div>
      </div>
    );
  }

  const statusCfg = APPT_STATUS_CONFIG[appointment.status] || APPT_STATUS_CONFIG.BOOKED;
  const canCancel = ['BOOKED', 'CONFIRMED'].includes(appointment.status);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: '40px 20px' }}>
      <div style={{ maxWidth: 560, margin: '0 auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
          <button onClick={() => router.back()} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 4, cursor: 'pointer' }}>
            <ArrowLeft size={14} /> Back
          </button>
          <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>ID: {appointment.id}</div>
        </div>

        {/* Error */}
        <AnimatePresence>
          {error && (
            <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
              style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 16px', borderRadius: 12, background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.2)', color: 'var(--accent-red)', fontSize: 14, marginBottom: 20 }}>
              <AlertTriangle size={16} /> {error}
            </motion.div>
          )}
        </AnimatePresence>

        <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="glass-card" style={{ padding: 32 }}>
          
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <div style={{ width: 56, height: 56, borderRadius: '50%', background: statusCfg.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 12px', color: statusCfg.color }}>
              {statusCfg.icon}
            </div>
            <div style={{ display: 'inline-block', padding: '6px 20px', borderRadius: 20, background: statusCfg.bg, color: statusCfg.color, fontWeight: 700, fontSize: 14 }}>
              {statusCfg.label}
            </div>
          </div>

          <div style={{ background: 'var(--bg-secondary)', borderRadius: 14, padding: 20, marginBottom: 24 }}>
            <h3 style={{ fontSize: 15, fontWeight: 700, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
              <CalendarCheck size={16} style={{ color: 'var(--accent-blue)' }} /> Details
            </h3>
            
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}><Clock size={14} /> Date & Time</span>
                <span style={{ fontWeight: 600, fontSize: 13 }}>{appointment.appointmentDate} at {appointment.startTime}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}><MapPin size={14} /> Branch</span>
                <span style={{ fontWeight: 600, fontSize: 13 }}>{appointment.branchName}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}><Briefcase size={14} /> Service</span>
                <span style={{ fontWeight: 600, fontSize: 13 }}>{appointment.serviceName}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}><User size={14} /> Customer</span>
                <span style={{ fontWeight: 600, fontSize: 13 }}>{appointment.customerName}</span>
              </div>
              {appointment.customerPhone && (
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-muted)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}><FileText size={14} /> Phone</span>
                  <span style={{ fontWeight: 600, fontSize: 13 }}>{appointment.customerPhone}</span>
                </div>
              )}
            </div>
          </div>

          {canCancel && (
            <button 
              onClick={() => {
                if(confirm('Are you sure you want to cancel this appointment? This action cannot be undone.')) {
                  cancelMutation.mutate();
                }
              }}
              className="btn-danger" 
              style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}
              disabled={cancelMutation.isPending}
            >
              <XCircle size={16} /> {cancelMutation.isPending ? 'Cancelling...' : 'Cancel Appointment'}
            </button>
          )}

          {!canCancel && appointment.status !== 'CANCELLED' && (
             <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: 13, marginTop: 12 }}>
                This appointment can no longer be cancelled.
             </div>
          )}
        </motion.div>
      </div>
    </div>
  );
}
