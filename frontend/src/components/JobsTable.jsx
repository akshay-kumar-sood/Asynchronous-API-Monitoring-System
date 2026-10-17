import React from 'react';
import { Link } from 'react-router-dom';
import { ExternalLink, Inbox, Clock, CheckCircle2, AlertCircle } from 'lucide-react';
import StatusBadge from './StatusBadge';

export default function JobsTable({ jobs = [], isLoading = false, emptyMessage = 'No jobs found in queue' }) {
  if (isLoading) {
    return (
      <div className="empty-state">
        <Clock size={36} className="empty-state-icon" style={{ animation: 'spin 2s linear infinite' }} />
        <div className="empty-state-title">Loading jobs...</div>
        <div className="empty-state-text">Fetching current status from backend.</div>
      </div>
    );
  }

  if (!jobs || jobs.length === 0) {
    return (
      <div className="empty-state">
        <Inbox size={42} className="empty-state-icon" />
        <div className="empty-state-title">{emptyMessage}</div>
        <div className="empty-state-text">
          Submit a job or wait for background workers to process existing queue items.
        </div>
      </div>
    );
  }

  function formatTime(isoString) {
    if (!isoString) return '-';
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' ' + date.toLocaleDateString([], { month: 'short', day: 'numeric' });
    } catch {
      return isoString;
    }
  }

  function getJobTarget(job) {
    if (job.payload && typeof job.payload === 'object') {
      if (job.payload.url) {
        return `${job.payload.method || 'GET'} ${job.payload.url}`;
      }
      if (job.payload.recipient) {
        return `To: ${job.payload.recipient}`;
      }
    }
    return job.type;
  }

  function getStatusBadgeClass(code) {
    if (!code) return '';
    if (code >= 200 && code < 300) return 'code-2xx';
    if (code >= 400 && code < 500) return 'code-4xx';
    if (code >= 500) return 'code-5xx';
    return '';
  }

  return (
    <div className="table-container">
      <table className="data-table">
        <thead>
          <tr>
            <th>Status</th>
            <th>Job ID</th>
            <th>Type / Target</th>
            <th>Attempts</th>
            <th>HTTP Status</th>
            <th>Created At</th>
            <th style={{ textAlign: 'right' }}>Action</th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((job) => (
            <tr key={job.id}>
              <td>
                <StatusBadge status={job.status} />
              </td>
              <td>
                <span className="font-mono" style={{ color: 'var(--text-main)', fontSize: '0.8rem' }} title={job.id}>
                  {job.id.substring(0, 8)}...
                </span>
              </td>
              <td>
                <div style={{ fontWeight: 600, color: 'var(--text-main)', marginBottom: '2px' }}>
                  {job.type}
                </div>
                <div
                  style={{
                    fontSize: '0.75rem',
                    color: 'var(--text-muted)',
                    maxWidth: '300px',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                  title={getJobTarget(job)}
                >
                  {getJobTarget(job)}
                </div>
              </td>
              <td>
                <span style={{ fontWeight: 600, color: job.attemptCount > 0 ? 'var(--color-warning)' : 'var(--text-secondary)' }}>
                  {job.attemptCount}
                </span>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}> / {job.maxAttempts}</span>
              </td>
              <td>
                {job.responseStatusCode ? (
                  <span className={`code-badge ${getStatusBadgeClass(job.responseStatusCode)}`}>
                    {job.responseStatusCode}
                  </span>
                ) : (
                  <span style={{ color: 'var(--text-muted)' }}>-</span>
                )}
              </td>
              <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                {formatTime(job.createdAt)}
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
  );
}
