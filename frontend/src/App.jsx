import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { Server, Activity, RefreshCw } from 'lucide-react';
import Sidebar from './components/Sidebar';
import Dashboard from './pages/Dashboard';
import Jobs from './pages/Jobs';
import JobDetails from './pages/JobDetails';
import Retries from './pages/Retries';
import DeadLetterQueue from './pages/DeadLetterQueue';
import SubmitJob from './pages/SubmitJob';
import { api } from './services/api';

export default function App() {
  const [connectionStatus, setConnectionStatus] = useState({
    checked: false,
    online: false,
    latencyMs: null,
    error: null,
  });

  async function checkBackend() {
    const status = await api.checkConnection();
    setConnectionStatus({
      checked: true,
      online: status.online,
      latencyMs: status.latencyMs,
      error: status.error,
    });
  }

  useEffect(() => {
    checkBackend();
    const interval = setInterval(checkBackend, 15000);
    return () => clearInterval(interval);
  }, []);

  return (
    <BrowserRouter>
      <div className="app-container">
        {/* Left Navigation Sidebar */}
        <Sidebar />

        {/* Main Content Area */}
        <div className="main-layout">
          {/* Top Header */}
          <header className="top-header">
            <div className="brand-wrapper">
              <div className="brand-main-row">
                <Link to="/" style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center' }}>
                  <span className="brand-title">APIRetry</span>
                </Link>
                <span className="brand-separator">—</span>
                <span className="brand-tagline">Asynchronous Job Processing & API Monitoring</span>
              </div>
              <div className="brand-tech-stack">
                Java · Spring Boot · React · PostgreSQL · REST APIs · Exponential Backoff · Dead Letter Queue
              </div>
            </div>

            <div className="header-actions">
              {/* Backend Connection Indicator */}
              <div
                className="connection-pill"
                title={
                  connectionStatus.online
                    ? `Connected to Spring Boot backend (${connectionStatus.latencyMs}ms)`
                    : `Backend offline or unreachable: ${connectionStatus.error || 'Connection refused'}`
                }
              >
                <span className={`dot-indicator ${connectionStatus.online ? 'online' : 'offline'}`} />
                <span style={{ color: connectionStatus.online ? 'var(--color-success)' : 'var(--color-danger)' }}>
                  {connectionStatus.online ? `API Online (${connectionStatus.latencyMs}ms)` : 'API Offline'}
                </span>
                <button
                  onClick={checkBackend}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'var(--text-muted)',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    padding: 0,
                    marginLeft: '2px',
                  }}
                  title="Check connection now"
                >
                  <RefreshCw size={12} />
                </button>
              </div>
            </div>
          </header>

          {/* Page Routing */}
          <main style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/jobs" element={<Jobs />} />
              <Route path="/jobs/:id" element={<JobDetails />} />
              <Route path="/retries" element={<Retries />} />
              <Route path="/dlq" element={<DeadLetterQueue />} />
              <Route path="/submit" element={<SubmitJob />} />
            </Routes>
          </main>
        </div>
      </div>
    </BrowserRouter>
  );
}
