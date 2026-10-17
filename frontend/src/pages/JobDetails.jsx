import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Clock,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  Copy,
  Check,
  Server,
  FileCode,
  Calendar,
} from 'lucide-react';
import { api } from '../services/api';
import StatusBadge from '../components/StatusBadge';

export default function JobDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [job, setJob] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [copied, setCopied] = useState(false);
  const [isRequeuing, setIsRequeuing] = useState(false);
  const [requeueSuccess, setRequeueSuccess] = useState(false);

  async function loadJob() {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getJobById(id);
      setJob(data);
    } catch (err) {
      setError(err.message || 'Job not found');
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadJob();
  }, [id]);

  function copyId() {
    if (job?.id) {
      navigator.clipboard.writeText(job.id);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  async function handleRequeue() {
    if (!job?.id) return;
    setIsRequeuing(true);
    setRequeueSuccess(false);
    try {
      await api.retryDlqJob(job.id);
      setRequeueSuccess(true);
      await loadJob();
    } catch (err) {
      alert(`Failed to requeue: ${err.message}`);
    } finally {
      setIsRequeuing(false);
    }
  }

  function formatTime(isoString) {
    if (!isoString) return 'N/A';
    try {
      const d = new Date(isoString);
      return d.toLocaleString();
    } catch {
      return isoString;
    }
  }

  if (isLoading) {
    return (
      <div className="empty-state" style={{ marginTop: '60px' }}>
        <Clock size={36} className="empty-state-icon" style={{ animation: 'spin 2s linear infinite' }} />
        <div className="empty-state-title">Loading Job Details...</div>
      </div>
    );
  }

  if (error || !job) {
    return (
      <div className="content-wrapper">
        <div className="breadcrumbs">
          <Link to="/jobs">Jobs</Link>
          <span>&gt;</span>
          <span className="crumb-current">Details</span>
        </div>
        <div className="alert alert-error" style={{ marginTop: '20px' }}>
          <AlertTriangle size={18} />
          <div>
            <strong>Error:</strong> {error || 'Job not found with the specified ID'}
          </div>
        </div>
        <Link to="/jobs" className="btn btn-secondary">
          <ArrowLeft size={16} />
          <span>Back to Jobs</span>
        </Link>
      </div>
    );
  }

  return (
    <div className="content-wrapper">
      <div className="breadcrumbs">
        <Link to="/jobs" style={{ color: 'var(--text-muted)', textDecoration: 'none' }}>
          Jobs
        </Link>
        <span>&gt;</span>
        <span className="crumb-current">{job.id.substring(0, 8)}...</span>
      </div>

      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
          <button onClick={() => navigate(-1)} className="btn btn-secondary btn-sm" title="Back">
            <ArrowLeft size={15} />
          </button>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h1 className="page-title" style={{ fontSize: '1.4rem' }}>
                Job {job.id}
              </h1>
              <button
                onClick={copyId}
                className="btn btn-secondary btn-sm"
                style={{ padding: '3px 8px', fontSize: '0.75rem' }}
                title="Copy Full UUID"
              >
                {copied ? <Check size={12} style={{ color: 'var(--color-success)' }} /> : <Copy size={12} />}
                <span>{copied ? 'Copied' : 'Copy UUID'}</span>
              </button>
            </div>
            <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginTop: '3px' }}>
              Type: <strong style={{ color: 'var(--text-main)' }}>{job.type}</strong>
            </div>
          </div>
        </div>

        <div className="page-actions">
          <button onClick={loadJob} className="btn btn-secondary btn-sm">
            <RefreshCw size={14} />
            <span>Refresh</span>
          </button>
          {job.status === 'DEAD' && (
            <button
              onClick={handleRequeue}
              disabled={isRequeuing}
              className="btn btn-primary btn-sm"
            >
              <RefreshCw size={14} className={isRequeuing ? 'animate-spin' : ''} />
              <span>{isRequeuing ? 'Requeuing...' : 'Requeue from DLQ'}</span>
            </button>
          )}
        </div>
      </div>

      {requeueSuccess && (
        <div className="alert alert-success">
          <CheckCircle2 size={18} />
          <span>Job has been successfully requeued from DLQ back into the active processing queue!</span>
        </div>
      )}

      {/* Overview Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        {/* Status Card */}
        <div className="card">
          <div className="card-header">
            <span className="card-title" style={{ fontSize: '0.875rem' }}>Lifecycle State</span>
            <StatusBadge status={job.status} />
          </div>
          <div className="card-body" style={{ padding: '18px 24px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '0.85rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)' }}>Attempt Progress:</span>
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>
                  {job.attemptCount} / {job.maxAttempts} attempts
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)' }}>HTTP Status Code:</span>
                <span style={{ fontWeight: 600 }}>
                  {job.responseStatusCode ? (
                    <span className="code-badge" style={{ color: 'var(--accent-yellow)' }}>
                      {job.responseStatusCode}
                    </span>
                  ) : (
                    <span style={{ color: 'var(--text-muted)' }}>Not executed / Pending</span>
                  )}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-muted)' }}>Idempotency Key:</span>
                <span className="font-mono" style={{ fontSize: '0.8rem', color: job.idempotencyKey ? 'var(--text-main)' : 'var(--text-muted)' }}>
                  {job.idempotencyKey || 'None (null)'}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Timestamps Card */}
        <div className="card">
          <div className="card-header">
            <span className="card-title" style={{ fontSize: '0.875rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Calendar size={15} style={{ color: 'var(--accent-yellow)' }} />
              <span>Timestamps</span>
            </span>
          </div>
          <div className="card-body" style={{ padding: '18px 24px', fontSize: '0.85rem', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-muted)' }}>Created At:</span>
              <span style={{ color: 'var(--text-main)' }}>{formatTime(job.createdAt)}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-muted)' }}>Last Updated:</span>
              <span style={{ color: 'var(--text-main)' }}>{formatTime(job.updatedAt)}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-muted)' }}>Next Retry At:</span>
              <span style={{ color: job.nextRetryAt ? 'var(--color-warning)' : 'var(--text-muted)' }}>
                {formatTime(job.nextRetryAt)}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-muted)' }}>Completed At:</span>
              <span style={{ color: job.completedAt ? 'var(--color-success)' : 'var(--text-muted)' }}>
                {formatTime(job.completedAt)}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Last Error Card (if error present) */}
      {job.lastError && (
        <div className="card" style={{ marginBottom: '24px', borderColor: 'var(--color-danger-border)' }}>
          <div className="card-header" style={{ backgroundColor: 'rgba(239, 68, 68, 0.08)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#f87171' }}>
              <AlertTriangle size={16} />
              <span className="card-title" style={{ color: '#f87171', fontSize: '0.875rem' }}>
                Last Error Message
              </span>
            </div>
          </div>
          <div className="card-body">
            <pre style={{ color: '#fca5a5', background: 'var(--bg-subtle)', padding: '14px', borderRadius: 'var(--radius-md)', fontSize: '0.8rem', whiteSpace: 'pre-wrap' }}>
              {job.lastError}
            </pre>
          </div>
        </div>
      )}

      {/* Payload JSON */}
      <div className="card">
        <div className="card-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <FileCode size={16} style={{ color: 'var(--accent-yellow)' }} />
            <h2 className="card-title" style={{ fontSize: '0.9rem' }}>Job Payload</h2>
          </div>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>JSON Format</span>
        </div>
        <div className="card-body" style={{ padding: 0 }}>
          <pre
            style={{
              padding: '20px 24px',
              backgroundColor: 'var(--bg-subtle)',
              color: '#f8fafc',
              fontSize: '0.825rem',
              overflowX: 'auto',
              margin: 0,
            }}
          >
            {job.payload ? JSON.stringify(job.payload, null, 2) : '{}'}
          </pre>
        </div>
      </div>
    </div>
  );
}
