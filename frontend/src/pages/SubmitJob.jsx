import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Send,
  CheckCircle2,
  AlertCircle,
  Clock,
  Sparkles,
  ExternalLink,
  Code,
  KeyRound,
  Globe,
} from 'lucide-react';
import { api } from '../services/api';

const PRESETS = [
  {
    name: 'HTTP 200 Success',
    description: 'Outbound GET that completes successfully on the first attempt.',
    method: 'GET',
    url: 'https://httpbin.org/status/200',
    headers: '{\n  "Accept": "application/json"\n}',
    body: '',
  },
  {
    name: 'Simulate 500 Internal Error (Auto-Retry & DLQ)',
    description: 'Triggers exponential backoff retries and ultimately moves to DLQ.',
    method: 'GET',
    url: 'https://httpbin.org/status/500',
    headers: '{\n  "Accept": "application/json"\n}',
    body: '',
  },
  {
    name: 'POST JSON Webhook Payload',
    description: 'Sends outbound POST with JSON body and custom authorization header.',
    method: 'POST',
    url: 'https://httpbin.org/post',
    headers: '{\n  "Content-Type": "application/json",\n  "X-Custom-Source": "APIRetry-Client"\n}',
    body: '{\n  "event": "order.completed",\n  "orderId": 9841,\n  "amount": 149.99\n}',
  },
];

export default function SubmitJob() {
  const [jobType, setJobType] = useState('HTTP_REQUEST');
  const [method, setMethod] = useState('GET');
  const [url, setUrl] = useState('https://httpbin.org/status/200');
  const [headersText, setHeadersText] = useState('{\n  "Accept": "application/json"\n}');
  const [bodyText, setBodyText] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successJob, setSuccessJob] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  function generateRandomKey() {
    const randomHex = Math.random().toString(36).substring(2, 10);
    setIdempotencyKey(`req-${Date.now()}-${randomHex}`);
  }

  function applyPreset(preset) {
    setMethod(preset.method);
    setUrl(preset.url);
    setHeadersText(preset.headers);
    setBodyText(preset.body);
    setErrorMessage(null);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessJob(null);

    if (!url.trim()) {
      setErrorMessage('Target URL is required.');
      return;
    }

    let parsedHeaders = {};
    if (headersText.trim()) {
      try {
        parsedHeaders = JSON.parse(headersText);
        if (typeof parsedHeaders !== 'object' || Array.isArray(parsedHeaders)) {
          throw new Error('Headers must be a JSON key-value object.');
        }
      } catch (err) {
        setErrorMessage(`Invalid JSON in Headers: ${err.message}`);
        return;
      }
    }

    let parsedBody = null;
    if (bodyText.trim()) {
      try {
        parsedBody = JSON.parse(bodyText);
      } catch {
        // Fall back to plain string
        parsedBody = bodyText.trim();
      }
    }

    const payload = {
      url: url.trim(),
      method: method.toUpperCase(),
      headers: parsedHeaders,
    };

    if (parsedBody !== null) {
      payload.body = parsedBody;
    }

    const jobData = {
      type: jobType.trim() || 'HTTP_REQUEST',
      payload,
    };

    if (idempotencyKey.trim()) {
      jobData.idempotencyKey = idempotencyKey.trim();
    }

    setIsSubmitting(true);
    try {
      const response = await api.submitJob(jobData, idempotencyKey.trim());
      setSuccessJob(response);
    } catch (err) {
      console.error('Job submission failed:', err);
      setErrorMessage(err.message || 'Failed to submit job to backend.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="content-wrapper">
      <div className="breadcrumbs">
        <span>APIRetry</span>
        <span>/</span>
        <span className="crumb-current">Submit Job</span>
      </div>

      <div className="page-header">
        <div>
          <h1 className="page-title">Submit Asynchronous Job</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginTop: '4px' }}>
            Queue background tasks with custom HTTP methods, headers, payloads and idempotency keys.
          </p>
        </div>
      </div>

      {/* Preset selection bar */}
      <div style={{ marginBottom: '24px' }}>
        <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '10px' }}>
          QUICK TEMPLATES
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '12px' }}>
          {PRESETS.map((preset, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => applyPreset(preset)}
              style={{
                backgroundColor: 'var(--bg-card)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '12px 16px',
                textAlign: 'left',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
              className="tab-btn"
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                <Sparkles size={14} style={{ color: 'var(--accent-yellow)' }} />
                <span style={{ fontWeight: 600, color: '#ffffff', fontSize: '0.85rem' }}>{preset.name}</span>
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{preset.description}</div>
            </button>
          ))}
        </div>
      </div>

      {successJob && (
        <div className="alert alert-success">
          <CheckCircle2 size={20} />
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 600, fontSize: '0.95rem', marginBottom: '4px' }}>
              Job Successfully Queued (HTTP 202 Accepted)
            </div>
            <div style={{ fontSize: '0.85rem' }}>
              Job ID:{' '}
              <span className="font-mono" style={{ fontWeight: 600 }}>
                {successJob.id}
              </span>{' '}
              | Status: <span style={{ textTransform: 'uppercase', fontWeight: 700 }}>{successJob.status}</span>
            </div>
            <div style={{ marginTop: '8px' }}>
              <Link
                to={`/jobs/${successJob.id}`}
                className="details-link"
                style={{ fontWeight: 600, color: 'var(--text-main)', textDecoration: 'underline' }}
              >
                <span>View Job Details & Live Status</span>
                <ExternalLink size={13} />
              </Link>
            </div>
          </div>
          <button
            onClick={() => setSuccessJob(null)}
            style={{ background: 'none', border: 'none', color: 'currentColor', cursor: 'pointer', fontWeight: 600 }}
          >
            ✕
          </button>
        </div>
      )}

      {errorMessage && (
        <div className="alert alert-error">
          <AlertCircle size={20} />
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 600 }}>Submission Failed</div>
            <div style={{ fontSize: '0.85rem', marginTop: '2px' }}>{errorMessage}</div>
          </div>
          <button
            onClick={() => setErrorMessage(null)}
            style={{ background: 'none', border: 'none', color: 'currentColor', cursor: 'pointer', fontWeight: 600 }}
          >
            ✕
          </button>
        </div>
      )}

      <div className="card">
        <div className="card-header">
          <div className="card-title">Job Execution Parameters</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            Processed asynchronously by Spring Boot worker pool
          </div>
        </div>

        <div className="card-body">
          <form onSubmit={handleSubmit}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '20px' }}>
              <div className="form-group">
                <label className="form-label">Job Type</label>
                <input
                  type="text"
                  className="form-control"
                  value={jobType}
                  onChange={(e) => setJobType(e.target.value)}
                  placeholder="e.g. HTTP_REQUEST"
                  required
                />
                <div className="form-hint">Backend handler routing classification</div>
              </div>

              <div className="form-group">
                <label className="form-label">Idempotency Key (Optional)</label>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <input
                    type="text"
                    className="form-control"
                    value={idempotencyKey}
                    onChange={(e) => setIdempotencyKey(e.target.value)}
                    placeholder="e.g. key-8291f0a"
                  />
                  <button
                    type="button"
                    onClick={generateRandomKey}
                    className="tab-btn"
                    style={{
                      backgroundColor: 'var(--bg-subtle)',
                      border: '1px solid var(--border-subtle)',
                      whiteSpace: 'nowrap',
                      padding: '8px 12px',
                    }}
                    title="Generate unique key"
                  >
                    <KeyRound size={14} />
                    <span>Generate</span>
                  </button>
                </div>
                <div className="form-hint">Prevents duplicate executions for repeated submissions</div>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: '16px' }}>
              <div className="form-group">
                <label className="form-label">HTTP Method</label>
                <select
                  className="form-control"
                  value={method}
                  onChange={(e) => setMethod(e.target.value)}
                  style={{ fontWeight: 600, color: 'var(--accent-yellow)' }}
                >
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                  <option value="PUT">PUT</option>
                  <option value="DELETE">DELETE</option>
                  <option value="PATCH">PATCH</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Target URL</label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="url"
                    className="form-control"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                    placeholder="https://api.example.com/endpoint"
                    required
                  />
                </div>
                <div className="form-hint">Public HTTP/HTTPS destination URL executed by backend</div>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">HTTP Headers (JSON format)</label>
              <textarea
                className="form-control"
                rows={3}
                value={headersText}
                onChange={(e) => setHeadersText(e.target.value)}
                placeholder='{\n  "Authorization": "Bearer token",\n  "Content-Type": "application/json"\n}'
              />
              <div className="form-hint">Must be valid JSON object with key-value strings</div>
            </div>

            {method !== 'GET' && method !== 'DELETE' && (
              <div className="form-group">
                <label className="form-label">Request Body (Optional)</label>
                <textarea
                  className="form-control"
                  rows={4}
                  value={bodyText}
                  onChange={(e) => setBodyText(e.target.value)}
                  placeholder='{\n  "key": "value"\n}'
                />
                <div className="form-hint">Sent as the HTTP request body payload</div>
              </div>
            )}

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                gap: '12px',
                marginTop: '28px',
                paddingTop: '20px',
                borderTop: '1px solid var(--border-subtle)',
              }}
            >
              <button
                type="submit"
                disabled={isSubmitting}
                className="btn btn-primary"
                style={{
                  padding: '10px 24px',
                  fontSize: '0.9rem',
                }}
              >
                {isSubmitting ? (
                  <>
                    <Clock size={16} className="spinning" />
                    <span>Submitting Job...</span>
                  </>
                ) : (
                  <>
                    <Send size={16} />
                    <span>Submit Asynchronous Job</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
