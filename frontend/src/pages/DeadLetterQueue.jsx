import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  RefreshCw,
  Search,
  ExternalLink,
  ChevronDown,
  ChevronUp,
  RotateCcw,
  Clock,
  AlertCircle,
  CheckCircle2,
  FileCode,
} from 'lucide-react';
import { api } from '../services/api';

export default function DeadLetterQueue() {
  const [dlqJobs, setDlqJobs] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedJobId, setExpandedJobId] = useState(null);
  const [requeuingId, setRequeuingId] = useState(null);
  const [actionMessage, setActionMessage] = useState(null);

  async function loadDlqJobs() {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getDlqJobs();
      setDlqJobs(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to fetch DLQ records:', err);
      setError(err.message || 'Unable to load Dead Letter Queue');
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadDlqJobs();
  }, []);

  async function handleRequeue(originalJobId) {
    if (!originalJobId) return;
    setRequeuingId(originalJobId);
    setActionMessage(null);
    try {
      await api.retryDlqJob(originalJobId);
      setActionMessage({
        type: 'success',
        text: `Job ${originalJobId.substring(0, 8)}... successfully requeued back to QUEUED status.`,
      });
      // Refresh list
      await loadDlqJobs();
    } catch (err) {
      setActionMessage({
        type: 'error',
        text: `Failed to requeue job: ${err.message}`,
      });
    } finally {
      setRequeuingId(null);
    }
  }

  function formatTimestamp(isoString) {
    if (!isoString) return '-';
    try {
      const date = new Date(isoString);
      return (
        date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) +
        ' ' +
        date.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' })
      );
    } catch {
      return isoString;
    }
  }

  function formatPayload(rawPayload) {
    if (!rawPayload) return 'Empty payload';
    if (typeof rawPayload === 'object') {
      return JSON.stringify(rawPayload, null, 2);
    }
    try {
      const parsed = JSON.parse(rawPayload);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return String(rawPayload);
    }
  }

  const filteredJobs = dlqJobs.filter((job) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    const id = (job.originalJobId || '').toLowerCase();
    const type = (job.type || '').toLowerCase();
    const reason = (job.failureReason || '').toLowerCase();
    return id.includes(q) || type.includes(q) || reason.includes(q);
  });

  return (
    <div className="content-wrapper">
      <div className="breadcrumbs">
        <span>APIRetry</span>
        <span>/</span>
        <span className="crumb-current">Dead Letter Queue</span>
      </div>

      <div className="page-header">
        <div>
          <h1 className="page-title">Dead Letter Queue (DLQ)</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginTop: '4px' }}>
            Permanently exhausted jobs that exceeded maximum retry attempts or encountered unrecoverable errors.
          </p>
        </div>

        <div className="page-actions">
          <button
            onClick={loadDlqJobs}
            className="btn btn-secondary btn-sm"
            title="Refresh DLQ"
          >
            <RefreshCw size={14} className={isLoading ? 'spinning' : ''} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {actionMessage && (
        <div className={`alert ${actionMessage.type === 'success' ? 'alert-success' : 'alert-error'}`}>
          {actionMessage.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
          <div style={{ flex: 1 }}>{actionMessage.text}</div>
          <button
            onClick={() => setActionMessage(null)}
            style={{
              background: 'none',
              border: 'none',
              color: 'currentColor',
              cursor: 'pointer',
              fontWeight: 600,
            }}
          >
            ✕
          </button>
        </div>
      )}

      {error && (
        <div className="alert alert-error">
          <AlertCircle size={18} />
          <div>{error}</div>
        </div>
      )}

      <div className="card" style={{ marginBottom: '24px' }}>
        <div
          style={{
            padding: '16px 20px',
            borderBottom: '1px solid var(--border-subtle)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '16px',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: '4px 10px',
                borderRadius: 'var(--radius-full)',
                backgroundColor: 'var(--color-danger-bg)',
                border: '1px solid var(--color-danger-border)',
                color: 'var(--color-danger)',
                fontSize: '0.75rem',
                fontWeight: 600,
              }}
            >
              <AlertTriangle size={14} />
              {dlqJobs.length} Dead Job{dlqJobs.length === 1 ? '' : 's'}
            </span>
          </div>

          <div className="search-bar" style={{ minWidth: '260px' }}>
            <Search size={15} className="search-icon" />
            <input
              type="text"
              placeholder="Search by ID, type, or error..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="search-input"
            />
          </div>
        </div>

        {isLoading ? (
          <div className="empty-state">
            <Clock size={36} className="empty-state-icon spinning" />
            <div className="empty-state-title">Loading Dead Letter Queue...</div>
            <div className="empty-state-text">Fetching exhausted job records from database.</div>
          </div>
        ) : filteredJobs.length === 0 ? (
          <div className="empty-state">
            <CheckCircle2 size={42} style={{ color: 'var(--color-success)', marginBottom: '12px' }} />
            <div className="empty-state-title">DLQ is clean</div>
            <div className="empty-state-text">
              {searchQuery
                ? 'No dead jobs matched your search criteria.'
                : 'No dead jobs found. All submitted jobs either succeeded or are currently processing/retrying.'}
            </div>
          </div>
        ) : (
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Original Job ID</th>
                  <th>Job Type</th>
                  <th>Attempts</th>
                  <th>Failure Reason</th>
                  <th>Failed Timestamp</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredJobs.map((item) => {
                  const isExpanded = expandedJobId === item.id;
                  const isRequeuing = requeuingId === item.originalJobId;

                  return (
                    <React.Fragment key={item.id}>
                      <tr>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span
                              className="font-mono"
                              style={{ color: 'var(--text-main)', fontSize: '0.8rem', fontWeight: 600 }}
                              title={item.originalJobId}
                            >
                              {item.originalJobId ? `${item.originalJobId.substring(0, 8)}...` : '-'}
                            </span>
                            {item.originalJobId && (
                              <Link
                                to={`/jobs/${item.originalJobId}`}
                                title="Open original job record"
                                style={{ color: 'var(--text-muted)' }}
                              >
                                <ExternalLink size={13} />
                              </Link>
                            )}
                          </div>
                        </td>

                        <td>
                          <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>{item.type}</span>
                        </td>

                        <td>
                          <span
                            style={{
                              padding: '2px 8px',
                              borderRadius: 'var(--radius-sm)',
                              backgroundColor: 'var(--color-danger-bg)',
                              color: 'var(--color-danger)',
                              fontSize: '0.75rem',
                              fontWeight: 700,
                            }}
                          >
                            {item.attemptCount} tries
                          </span>
                        </td>

                        <td>
                          <div
                            style={{
                              maxWidth: '320px',
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              color: 'var(--color-danger)',
                              fontSize: '0.8rem',
                              fontFamily: 'JetBrains Mono, monospace',
                            }}
                            title={item.failureReason}
                          >
                            {item.failureReason || 'Exhausted retry attempts'}
                          </div>
                        </td>

                        <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                          {formatTimestamp(item.failedAt)}
                        </td>

                        <td style={{ textAlign: 'right' }}>
                          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
                            <button
                              onClick={() => setExpandedJobId(isExpanded ? null : item.id)}
                              className="btn btn-secondary btn-sm"
                              style={{
                                padding: '4px 10px',
                                fontSize: '0.75rem',
                              }}
                            >
                              <FileCode size={13} />
                              <span>Payload</span>
                              {isExpanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                            </button>

                            <button
                              onClick={() => handleRequeue(item.originalJobId || item.id)}
                              disabled={isRequeuing || (!item.originalJobId && !item.id)}
                              className="btn btn-primary btn-sm"
                              style={{
                                padding: '4px 12px',
                                fontSize: '0.75rem',
                              }}
                              title="Requeue this dead job back into processing"
                            >
                              <RotateCcw size={12} className={isRequeuing ? 'spinning' : ''} />
                              <span>{isRequeuing ? 'Requeuing...' : 'Requeue'}</span>
                            </button>
                          </div>
                        </td>
                      </tr>

                      {isExpanded && (
                        <tr>
                          <td colSpan="6" style={{ backgroundColor: 'var(--bg-subtle)', padding: '16px 24px' }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                              <div>
                                <div
                                  style={{
                                    fontSize: '0.75rem',
                                    fontWeight: 600,
                                    color: 'var(--text-muted)',
                                    marginBottom: '6px',
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.04em',
                                  }}
                                >
                                  Payload Body
                                </div>
                                <pre
                                  className="font-mono"
                                  style={{
                                    background: 'var(--bg-input)',
                                    border: '1px solid var(--border-subtle)',
                                    borderRadius: 'var(--radius-md)',
                                    padding: '12px',
                                    fontSize: '0.8rem',
                                    color: '#cbd5e1',
                                    maxHeight: '180px',
                                    overflowY: 'auto',
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-all',
                                  }}
                                >
                                  {formatPayload(item.payload)}
                                </pre>
                              </div>

                              <div>
                                <div
                                  style={{
                                    fontSize: '0.75rem',
                                    fontWeight: 600,
                                    color: 'var(--text-muted)',
                                    marginBottom: '6px',
                                    textTransform: 'uppercase',
                                    letterSpacing: '0.04em',
                                  }}
                                >
                                  Full Failure Diagnostic
                                </div>
                                <div
                                  className="font-mono"
                                  style={{
                                    background: 'var(--bg-input)',
                                    border: '1px solid var(--border-subtle)',
                                    borderRadius: 'var(--radius-md)',
                                    padding: '12px',
                                    fontSize: '0.8rem',
                                    color: 'var(--color-danger)',
                                    maxHeight: '180px',
                                    overflowY: 'auto',
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-all',
                                  }}
                                >
                                  {item.failureReason || 'No detailed stack trace captured.'}
                                </div>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
