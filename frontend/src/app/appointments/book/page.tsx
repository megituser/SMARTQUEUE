'use client';

import { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { branchApi, appointmentApi } from '@/lib/api';
import { BranchResponse, ServiceResponse, SlotResponse } from '@/lib/types';
import { motion, AnimatePresence } from 'framer-motion';
import Link from 'next/link';
import { CalendarCheck, Clock, MapPin, ArrowLeft, CheckCircle, User, Phone, Mail, FileText } from 'lucide-react';

export default function BookAppointmentPage() {
  const [step, setStep] = useState(1);
  const [selectedBranch, setSelectedBranch] = useState<number | null>(null);
  const [selectedService, setSelectedService] = useState<number | null>(null);
  const [selectedDate, setSelectedDate] = useState('');
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [customerEmail, setCustomerEmail] = useState('');
  const [notes, setNotes] = useState('');
  const [bookingResult, setBookingResult] = useState<any>(null);
  const [error, setError] = useState('');
  const [today, setToday] = useState('');
  const [maxDate, setMaxDate] = useState('');

  useEffect(() => {
    setToday(new Date().toISOString().split('T')[0]);
    setMaxDate(new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]);
  }, []);
  const { data: branches } = useQuery<BranchResponse[]>({
    queryKey: ['branches'],
    queryFn: async () => (await branchApi.getAll()).data.data,
  });

  const { data: services } = useQuery<ServiceResponse[]>({
    queryKey: ['services', selectedBranch],
    queryFn: async () => (await branchApi.getServices(selectedBranch!)).data.data,
    enabled: !!selectedBranch,
  });

  const { data: slots, isLoading: slotsLoading } = useQuery<SlotResponse[]>({
    queryKey: ['slots', selectedBranch, selectedService, selectedDate],
    queryFn: async () => (await appointmentApi.getSlots(selectedBranch!, selectedService!, selectedDate)).data.data,
    enabled: !!selectedBranch && !!selectedService && !!selectedDate,
  });

  const bookMutation = useMutation({
    mutationFn: async () => {
      const res = await appointmentApi.book({
        branchId: selectedBranch,
        serviceId: selectedService,
        customerName,
        customerPhone,
        customerEmail,
        appointmentDate: selectedDate,
        startTime: selectedSlot,
        notes,
      });
      return res.data.data;
    },
    onSuccess: (data) => {
      setBookingResult(data);
      setStep(5);
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || 'Booking failed');
    },
  });



  const selectedBranchData = branches?.find(b => b.id === selectedBranch);
  const selectedServiceData = services?.find(s => s.id === selectedService);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', padding: '40px 20px' }}>
      <div style={{ maxWidth: 600, margin: '0 auto' }}>
        <Link href="/" style={{ color: 'var(--text-muted)', textDecoration: 'none', fontSize: 13, display: 'flex', alignItems: 'center', gap: 4, marginBottom: 24 }}>
          <ArrowLeft size={14} /> Back to Home
        </Link>

        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <CalendarCheck size={32} style={{ color: 'var(--accent-blue)', marginBottom: 8 }} />
          <h1 style={{ fontSize: 24, fontWeight: 700 }}>Book an Appointment</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: 14, marginTop: 4 }}>Select your branch, service, and preferred time</p>
        </div>

        {/* Progress Steps */}
        <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginBottom: 32 }}>
          {[1, 2, 3, 4].map(s => (
            <div key={s} style={{
              width: 40, height: 4, borderRadius: 2,
              background: s <= step ? 'var(--accent-blue)' : 'var(--bg-elevated)',
              transition: 'background 0.3s',
            }} />
          ))}
        </div>

        <AnimatePresence mode="wait">
          {/* Step 1: Branch & Service */}
          {step === 1 && (
            <motion.div key="step1" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} className="glass-card" style={{ padding: 28 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20 }}><MapPin size={16} style={{ display: 'inline', marginRight: 6 }} />Select Location & Service</h2>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Branch</label>
                <select className="input-field" value={selectedBranch || ''} onChange={e => { setSelectedBranch(Number(e.target.value)); setSelectedService(null); }}>
                  <option value="">Select a branch...</option>
                  {branches?.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select>
              </div>
              {selectedBranch && (
                <div style={{ marginBottom: 16 }}>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Service</label>
                  <select className="input-field" value={selectedService || ''} onChange={e => setSelectedService(Number(e.target.value))}>
                    <option value="">Select a service...</option>
                    {services?.map(s => <option key={s.id} value={s.id}>{s.name} (~{s.avgServiceTimeMinutes} min)</option>)}
                  </select>
                </div>
              )}
              <button className="btn-primary" disabled={!selectedBranch || !selectedService} onClick={() => setStep(2)} style={{ width: '100%', marginTop: 8 }}>
                Continue
              </button>
            </motion.div>
          )}

          {/* Step 2: Date & Time */}
          {step === 2 && (
            <motion.div key="step2" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} className="glass-card" style={{ padding: 28 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20 }}><Clock size={16} style={{ display: 'inline', marginRight: 6 }} />Select Date & Time</h2>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Date</label>
                <input type="date" className="input-field" value={selectedDate} onChange={e => { setSelectedDate(e.target.value); setSelectedSlot(null); }} min={today} max={maxDate} />
              </div>
              {selectedDate && (
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 10 }}>Available Times</label>
                  {slotsLoading ? (
                    <div style={{ textAlign: 'center', color: 'var(--text-muted)' }}>Loading slots...</div>
                  ) : (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
                      {slots?.map(slot => (
                        <button
                          key={slot.startTime}
                          disabled={!slot.available}
                          onClick={() => setSelectedSlot(slot.startTime)}
                          style={{
                            padding: '10px 8px', borderRadius: 10, fontSize: 13, fontWeight: 600,
                            cursor: slot.available ? 'pointer' : 'not-allowed',
                            opacity: slot.available ? 1 : 0.3,
                            background: selectedSlot === slot.startTime ? 'var(--accent-blue)' : 'var(--bg-secondary)',
                            color: selectedSlot === slot.startTime ? 'white' : 'var(--text-primary)',
                            border: `1px solid ${selectedSlot === slot.startTime ? 'var(--accent-blue)' : 'var(--border-color)'}`,
                            transition: 'all 0.2s',
                          }}
                        >
                          {slot.startTime?.substring(0, 5)}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}
              <div style={{ display: 'flex', gap: 12, marginTop: 20 }}>
                <button className="btn-secondary" onClick={() => setStep(1)} style={{ flex: 1 }}>Back</button>
                <button className="btn-primary" disabled={!selectedSlot} onClick={() => setStep(3)} style={{ flex: 1 }}>Continue</button>
              </div>
            </motion.div>
          )}

          {/* Step 3: Customer Details */}
          {step === 3 && (
            <motion.div key="step3" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} className="glass-card" style={{ padding: 28 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20 }}><User size={16} style={{ display: 'inline', marginRight: 6 }} />Your Details</h2>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Full Name *</label>
                <input className="input-field" placeholder="John Doe" value={customerName} onChange={e => setCustomerName(e.target.value)} required />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Phone</label>
                <input className="input-field" placeholder="+1-555-0100" value={customerPhone} onChange={e => setCustomerPhone(e.target.value)} />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Email</label>
                <input className="input-field" type="email" placeholder="john@example.com" value={customerEmail} onChange={e => setCustomerEmail(e.target.value)} />
              </div>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>Notes</label>
                <textarea className="input-field" rows={2} placeholder="Optional notes..." value={notes} onChange={e => setNotes(e.target.value)} style={{ resize: 'vertical' }} />
              </div>
              <div style={{ display: 'flex', gap: 12, marginTop: 8 }}>
                <button className="btn-secondary" onClick={() => setStep(2)} style={{ flex: 1 }}>Back</button>
                <button className="btn-primary" disabled={!customerName} onClick={() => setStep(4)} style={{ flex: 1 }}>Review</button>
              </div>
            </motion.div>
          )}

          {/* Step 4: Review & Confirm */}
          {step === 4 && (
            <motion.div key="step4" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} className="glass-card" style={{ padding: 28 }}>
              <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20 }}>Review & Confirm</h2>
              {error && <div style={{ padding: '10px 14px', borderRadius: 10, background: 'rgba(239,68,68,0.1)', color: 'var(--accent-red)', fontSize: 13, marginBottom: 16 }}>{error}</div>}
              <div style={{ background: 'var(--bg-secondary)', borderRadius: 14, padding: 20, marginBottom: 20 }}>
                {[
                  { label: 'Branch', value: selectedBranchData?.name },
                  { label: 'Service', value: selectedServiceData?.name },
                  { label: 'Date', value: selectedDate },
                  { label: 'Time', value: selectedSlot?.substring(0, 5) },
                  { label: 'Name', value: customerName },
                  { label: 'Phone', value: customerPhone || '—' },
                  { label: 'Email', value: customerEmail || '—' },
                ].map((r, i) => (
                  <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: i < 6 ? '1px solid var(--border-color)' : 'none' }}>
                    <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>{r.label}</span>
                    <span style={{ fontWeight: 600, fontSize: 13 }}>{r.value}</span>
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <button className="btn-secondary" onClick={() => setStep(3)} style={{ flex: 1 }}>Back</button>
                <button className="btn-success" disabled={bookMutation.isPending} onClick={() => bookMutation.mutate()} style={{ flex: 1 }}>
                  {bookMutation.isPending ? 'Booking...' : 'Confirm Booking'}
                </button>
              </div>
            </motion.div>
          )}

          {/* Step 5: Confirmation */}
          {step === 5 && bookingResult && (
            <motion.div key="step5" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="glass-card" style={{ padding: 40, textAlign: 'center' }}>
              <div style={{ width: 64, height: 64, borderRadius: '50%', background: 'rgba(16,185,129,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }}>
                <CheckCircle size={32} style={{ color: 'var(--accent-green)' }} />
              </div>
              <h2 style={{ fontSize: 22, fontWeight: 700, marginBottom: 8 }}>Appointment Booked!</h2>
              <p style={{ color: 'var(--text-secondary)', fontSize: 14, marginBottom: 24 }}>Your appointment has been confirmed</p>
              <div style={{ background: 'var(--bg-secondary)', borderRadius: 14, padding: 20, textAlign: 'left', marginBottom: 24 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
                  <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>Booking ID</span>
                  <span style={{ fontWeight: 700, color: 'var(--accent-blue)' }}>#{bookingResult.id}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
                  <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>Date & Time</span>
                  <span style={{ fontWeight: 600, fontSize: 13 }}>{bookingResult.appointmentDate} at {bookingResult.startTime?.substring(0, 5)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>Service</span>
                  <span style={{ fontWeight: 600, fontSize: 13 }}>{bookingResult.serviceName}</span>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <Link href="/" className="btn-secondary" style={{ flex: 1, textDecoration: 'none', textAlign: 'center' }}>Home</Link>
                <button className="btn-primary" onClick={() => { setStep(1); setBookingResult(null); setSelectedSlot(null); setCustomerName(''); }} style={{ flex: 1 }}>
                  Book Another
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
