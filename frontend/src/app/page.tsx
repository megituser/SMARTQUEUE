'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import { CalendarCheck, ShieldCheck, Ticket, Monitor, ArrowRight } from 'lucide-react';

export default function LandingPage() {
  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)', display: 'flex', flexDirection: 'column' }}>
      {/* Header */}
      <header style={{ padding: '20px 40px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border-color)', background: 'var(--bg-secondary)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 32, height: 32, borderRadius: 8, background: 'var(--gradient-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 14 }}>S</div>
          <span style={{ fontSize: 18, fontWeight: 700 }}>Smart<span className="gradient-text">Queue</span></span>
        </div>
        <div>
          <Link href="/login" className="btn-secondary" style={{ textDecoration: 'none', padding: '8px 16px', fontSize: 14, fontWeight: 600 }}>
            Staff Login
          </Link>
        </div>
      </header>

      {/* Main Content */}
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '40px 20px', textAlign: 'center' }}>
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          style={{ maxWidth: 800 }}
        >
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '8px 16px', borderRadius: 20, background: 'rgba(59, 130, 246, 0.1)', color: 'var(--accent-blue)', fontSize: 14, fontWeight: 600, marginBottom: 24 }}>
            <Ticket size={16} /> Welcome to SmartQueue
          </div>

          <h1 style={{ fontSize: 48, fontWeight: 800, marginBottom: 24, lineHeight: 1.2 }}>
            Intelligent Queue <br />
            <span className="gradient-text">Management System</span>
          </h1>

          <p style={{ fontSize: 18, color: 'var(--text-secondary)', marginBottom: 40, maxWidth: 600, margin: '0 auto 40px auto' }}>
            Book appointments, join queues remotely, and experience seamless service delivery without the wait.
          </p>

          <div style={{ display: 'flex', gap: 16, justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link href="/appointments/book" style={{ textDecoration: 'none' }}>
              <button className="btn-primary" style={{ padding: '16px 32px', fontSize: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
                <CalendarCheck size={20} />
                Book an Appointment
                <ArrowRight size={18} />
              </button>
            </Link>

            <Link href="/login" style={{ textDecoration: 'none' }}>
              <button className="btn-secondary" style={{ padding: '16px 32px', fontSize: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
                <ShieldCheck size={20} />
                Staff Access
              </button>
            </Link>
          </div>
        </motion.div>

        {/* Feature Cards */}
        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: 24, marginTop: 80, width: '100%', maxWidth: 1000 }}
        >
          <div className="glass-card" style={{ padding: 32, textAlign: 'left' }}>
            <div style={{ width: 48, height: 48, borderRadius: 12, background: 'rgba(59, 130, 246, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent-blue)', marginBottom: 20 }}>
              <CalendarCheck size={24} />
            </div>
            <h3 style={{ fontSize: 18, fontWeight: 700, marginBottom: 12 }}>Online Booking</h3>
            <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6 }}>Schedule your visit in advance and skip the waiting room altogether with precise time slots.</p>
          </div>

          <div className="glass-card" style={{ padding: 32, textAlign: 'left' }}>
            <div style={{ width: 48, height: 48, borderRadius: 12, background: 'rgba(16, 185, 129, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent-green)', marginBottom: 20 }}>
              <Ticket size={24} />
            </div>
            <h3 style={{ fontSize: 18, fontWeight: 700, marginBottom: 12 }}>Digital Tokens</h3>
            <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6 }}>Get your queue ticket directly on your phone and track your position in real-time.</p>
          </div>

          <div className="glass-card" style={{ padding: 32, textAlign: 'left' }}>
            <div style={{ width: 48, height: 48, borderRadius: 12, background: 'rgba(139, 92, 246, 0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent-purple)', marginBottom: 20 }}>
              <Monitor size={24} />
            </div>
            <h3 style={{ fontSize: 18, fontWeight: 700, marginBottom: 12 }}>Live Displays</h3>
            <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6 }}>Clear, real-time counter displays ensure you never miss your turn when it's time to be served.</p>
          </div>
        </motion.div>
      </main>
    </div>
  );
}
