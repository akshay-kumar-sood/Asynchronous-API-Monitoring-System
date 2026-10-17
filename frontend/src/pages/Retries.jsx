import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { RefreshCw, Clock, AlertTriangle, Layers, ExternalLink } from 'lucide-react';
import { api } from '../services/api';
import StatusBadge from '../components/StatusBadge';

export default function Retries() {
  const [retryingJobs, setRetryingJobs] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  async function loadRetries() {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getJobs('RETRYING');
      setRetryingJobs(data || []);
    } catch (err) {
      setError(err.message || 'Failed to load retrying jobs');
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadRetries();
    const interval = setInterval(loadRetries, 3000); // Polling retries frequently
    return () => clearInterval(interval);
  }, []);

  function formatTime(isoString) {
    if (!isoString) return '-';
    try {
      const d = new Date(isoString);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return isoString;
    }
  }

  function getTarget(job) {
    if (job.payload && typeof job.payload === 'object' && job.payload.url) {
      return `${job.payload.method || 'GET'} ${job.payload.url}`;
    }
    return job.type;
  }

  return (
    <div className="content-wrapper">
      <div className="breadcrumbs">
        <Link to="/" style={{ color: 'var(--text-muted)', textDecoration: 'none' }}>
          APIRetry
        </Link>
        <span>&gt;</span>
        <span className="crumb-current">Retries</span>
      </div>

      <div className="page-header">
        <div>
          <h1 className="page-title">Active Retries</h1>
          <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '4px' }}>
            Jobs scheduled for retry with non-blocking exponential backoff (1s, 2s, 4s, 8s)
          </div>
        </div>
        <div className="page-actions">
          <button onClick={loadRetries} className="btn btn-secondary btn-sm">
            <RefreshCw size={14} className={isLoading ? 'animate-spin' : ''} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="alert alert-error">
          <AlertTriangle size={18} />
          <span>{error}</span>
        </div>
      )}

      {/* Info Card explaining exponential backoff policy */}
      <div className="card" style={{ marginBottom: '20px', borderColor: 'var(--color-warning-border)' }}>
        <div className="card-body" style={{ padding: '16px 20px', display: 'flex', alignItems: 'center', gap: '14px' }}>
          <Clock size={20} style={{ color: 'var(--color-warning)', flexShrink: 0 }} />
          <div style={{ fontSize: '0.825rem', color: 'var(--text-secondary)' }}>
            <strong>Exponential Backoff Policy:</strong> Worker threads are immediately released without blocking.
            The scheduler calculates <code style={{ color: 'var(--accent-yellow)' }}>nextRetryAt = NOW + (1s * 2^(attempt-1))</code> and polls for eligibility.
          </div>
        </div>
      </div>

      {/* Retries Table */}
      <div className="card">
        {retryingJobs.length === 0 && !isLoading ? (
          <div className="empty-state">
            <RefreshCw size={36} className="empty-state-icon" style={{ color: 'var(--color-success)' }} />
            <div className="empty-state-title">No Jobs Currently Retrying</div>
            <div className="empty-state-text">
              All active jobs have either succeeded or the queue is clear.
            </div>
          </div>
        ) : (
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Status</th>
                  <th>Job ID</th>
                  <th>Type / Target</th>
                  <th>Attempt</th>
                  <th>Next Retry Scheduled</th>
                  <th>Last Failure Reason</th>
                  <th style={{ textAlign: 'right' }}>Action</th>
                </tr>
              </thead>
              <tbody>
                {retryingJobs.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <StatusBadge status={job.status} />
                    </td>
                    <td>
                      <span className="font-mono" style={{ fontSize: '0.8rem', color: 'var(--text-main)' }}>
                        {job.id.substring(0, 8)}...
                      </span>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--text-main)' }}>{job.type}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{getTarget(job)}</div>
                    </td>
                    <td>
                      <span style={{ fontWeight: 600, color: 'var(--color-warning)' }}>
                        {job.attemptCount}
                      </span>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}> / {job.maxAttempts}</span>
                    </td>
                    <td>
                      <span style={{ color: 'var(--color-warning)', fontWeight: 500 }}>
                        {formatTime(job.nextRetryAt)}
                      </span>
                    </td>
                    <td>
                      <span
                        style={{
                          fontSize: '0.775rem',
                          color: '#fca5a5',
                          display: 'inline-block',
                          maxWidth: '280px',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                        title={job.lastError}
                      >
                        {job.lastError || 'Transient error'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <Link to={`/jobs/${job.id}`} className="details-link">
                        <span>Details</span>
                        <ExternalLink size={13} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
