import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Search, RefreshCw, PlusCircle, Filter } from 'lucide-react';
import { api } from '../services/api';
import JobsTable from '../components/JobsTable';

export default function Jobs() {
  const [jobs, setJobs] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  async function loadJobs() {
    setIsLoading(true);
    setError(null);
    try {
      // Call backend GET /api/jobs with or without status filter
      const data = await api.getJobs(statusFilter === 'ALL' ? null : statusFilter);
      setJobs(data || []);
    } catch (err) {
      setError(err.message || 'Failed to load jobs from backend');
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    loadJobs();
  }, [statusFilter]);

  // Client-side search filtering by UUID, type, or URL
  const displayedJobs = jobs.filter((job) => {
    if (!searchTerm.trim()) return true;
    const term = searchTerm.toLowerCase();
    const idMatch = job.id.toLowerCase().includes(term);
    const typeMatch = job.type.toLowerCase().includes(term);
    let urlMatch = false;
    if (job.payload && typeof job.payload === 'object' && job.payload.url) {
      urlMatch = job.payload.url.toLowerCase().includes(term);
    }
    return idMatch || typeMatch || urlMatch;
  });

  return (
    <div className="content-wrapper">
      <div className="breadcrumbs">
        <span>APIRetry</span>
        <span>&gt;</span>
        <span className="crumb-current">Jobs</span>
      </div>

      <div className="page-header">
        <div>
          <h1 className="page-title">Jobs Queue</h1>
          <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '4px' }}>
            Browse and search all background jobs tracked in PostgreSQL
          </div>
        </div>
        <div className="page-actions">
          <button onClick={loadJobs} className="btn btn-secondary">
            <RefreshCw size={15} className={isLoading ? 'animate-spin' : ''} />
            <span>Refresh</span>
          </button>
          <Link to="/submit" className="btn btn-primary">
            <PlusCircle size={16} />
            <span>Submit Job</span>
          </Link>
        </div>
      </div>

      {error && (
        <div className="alert alert-error">
          <span>{error}</span>
        </div>
      )}

      {/* Search and Filters */}
      <div className="card" style={{ marginBottom: '20px' }}>
        <div className="card-body" style={{ padding: '16px 20px', display: 'flex', gap: '14px', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }}>
          <div className="search-input-wrapper" style={{ flex: 1, minWidth: '260px' }}>
            <Search size={16} className="search-icon" />
            <input
              type="text"
              placeholder="Search by Job ID, Type, or Target URL..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="input-field"
              style={{ width: '100%' }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Filter size={15} style={{ color: 'var(--text-muted)' }} />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="form-control"
              style={{ width: '170px', padding: '7px 12px', fontSize: '0.825rem' }}
            >
              <option value="ALL">All Statuses</option>
              <option value="QUEUED">QUEUED</option>
              <option value="PROCESSING">PROCESSING</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="RETRYING">RETRYING</option>
              <option value="DEAD">DEAD</option>
            </select>
          </div>
        </div>
      </div>

      {/* Jobs Table */}
      <div className="card">
        <JobsTable
          jobs={displayedJobs}
          isLoading={isLoading}
          emptyMessage={
            searchTerm
              ? `No jobs found matching "${searchTerm}"`
              : 'No jobs found in the queue.'
          }
        />
      </div>
    </div>
  );
}
