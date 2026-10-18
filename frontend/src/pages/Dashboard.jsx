import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  Layers,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  PlusCircle,
  Activity,
  Radio,
  Clock,
  ArrowRight,
  Play,
  Pause,
  Search,
  X,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { api } from '../services/api';
import StatCard from '../components/StatCard';
import JobsTable from '../components/JobsTable';

export default function Dashboard() {
  const [jobs, setJobs] = useState([]);
  const [dlqJobs, setDlqJobs] = useState([]);
  const [activeTab, setActiveTab] = useState('ALL');
  const [searchJobId, setSearchJobId] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [isFetching, setIsFetching] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [error, setError] = useState(null);
  const [lastRefreshed, setLastRefreshed] = useState(null);

  const PAGE_SIZE = 10;

  const isFetchingRef = useRef(false);
  const isMountedRef = useRef(true);

  // Fetch dashboard data without full page reload; guards against overlapping requests
  const loadData = useCallback(async (isBackground = false) => {
    if (isFetchingRef.current) return;
    isFetchingRef.current = true;
    setIsFetching(true);
    if (!isBackground) {
      setError(null);
    }
    try {
      const [jobsData, dlqData] = await Promise.all([
        api.getJobs(),
        api.getDlqJobs().catch(() => []),
      ]);
      if (isMountedRef.current) {
        setJobs(jobsData || []);
        setDlqJobs(dlqData || []);
        setLastRefreshed(new Date().toLocaleTimeString());
        setError(null);
      }
    } catch (err) {
      if (isMountedRef.current) {
        setError(err.message || 'Failed to connect to backend server');
      }
    } finally {
      if (isMountedRef.current) {
        setIsLoading(false);
        setIsFetching(false);
      }
      isFetchingRef.current = false;
    }
  }, []);

  // Initial data load on mount & cleanup on unmount
  useEffect(() => {
    isMountedRef.current = true;
    loadData(false);
    return () => {
      isMountedRef.current = false;
    };
  }, [loadData]);

  // Auto-refresh interval (10 seconds default); stops when autoRefresh is OFF
  useEffect(() => {
    if (!autoRefresh) return;

    const intervalId = setInterval(() => {
      loadData(true);
    }, 10000);

    return () => clearInterval(intervalId);
  }, [autoRefresh, loadData]);

  // Compute metrics from actual backend data
  const totalCount = jobs.length;
  const succeededCount = jobs.filter((j) => j.status === 'SUCCESS').length;
  const runningCount = jobs.filter(
    (j) => j.status === 'QUEUED' || j.status === 'PROCESSING'
  ).length;
  const retryingCount = jobs.filter((j) => j.status === 'RETRYING').length;
  const deadCount = jobs.filter((j) => j.status === 'DEAD').length || dlqJobs.length;

  // Filter jobs by selected tab
  const statusFilteredJobs = jobs.filter((job) => {
    if (activeTab === 'ALL') return true;
    if (activeTab === 'RUNNING') return job.status === 'QUEUED' || job.status === 'PROCESSING';
    if (activeTab === 'SUCCESS') return job.status === 'SUCCESS';
    if (activeTab === 'RETRYING') return job.status === 'RETRYING';
    if (activeTab === 'DEAD') return job.status === 'DEAD' || job.status === 'FAILED';
    return true;
  });

  // Filter jobs by full or partial Job ID search
  const filteredJobs = statusFilteredJobs.filter((job) => {
    if (!searchJobId.trim()) return true;
    return job.id && job.id.toLowerCase().includes(searchJobId.trim().toLowerCase());
  });

  const matchingCount = filteredJobs.length;
  const totalPages = Math.max(1, Math.ceil(matchingCount / PAGE_SIZE));
  const startIndex = (currentPage - 1) * PAGE_SIZE;
  const endIndex = Math.min(startIndex + PAGE_SIZE, matchingCount);
  const paginatedJobs = filteredJobs.slice(startIndex, endIndex);

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setCurrentPage(1);
  };

  const handleSearchChange = (e) => {
    setSearchJobId(e.target.value);
    setCurrentPage(1);
  };

  const handleClearSearch = () => {
    setSearchJobId('');
    setCurrentPage(1);
  };

  // Keep currentPage in valid range when results change
  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(Math.max(1, totalPages));
    }
  }, [totalPages, currentPage]);

  return (
    <div className="content-wrapper">
      {/* Breadcrumb & Title */}
      <div className="breadcrumbs">
        <span>APIRetry</span>
        <span>&gt;</span>
        <span className="crumb-current">Dashboard</span>
      </div>

      <div className="page-header">
        <div>
          <h1 className="page-title">Queued Jobs Monitor</h1>
        </div>
        <div className="page-actions" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {/* Auto-refresh toggle control */}
          <button
            type="button"
            onClick={() => setAutoRefresh((prev) => !prev)}
            className="btn btn-secondary"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              padding: '8px 14px',
              border: autoRefresh ? '1px solid rgba(250, 204, 21, 0.4)' : '1px solid var(--border-subtle)',
              backgroundColor: autoRefresh ? 'rgba(250, 204, 21, 0.08)' : 'var(--bg-card)',
              color: autoRefresh ? 'var(--accent-yellow)' : 'var(--text-muted)',
              fontSize: '0.825rem',
              fontWeight: 600,
            }}
            title={autoRefresh ? 'Click to stop auto-refresh' : 'Click to start 10s auto-refresh'}
          >
            {autoRefresh ? (
              <>
                <Pause size={14} style={{ color: 'var(--accent-yellow)' }} />
                <span>Auto-refresh ON</span>
              </>
            ) : (
              <>
                <Play size={14} style={{ color: 'var(--text-muted)' }} />
                <span>Auto-refresh OFF</span>
              </>
            )}
          </button>

          {/* Manual Refresh button */}
          <button
            type="button"
            onClick={() => loadData(false)}
            disabled={isFetching}
            className="btn btn-secondary"
            title="Immediately fetch latest data"
          >
            <RefreshCw size={15} className={isFetching ? 'animate-spin' : ''} />
            <span>{isFetching ? 'Refreshing...' : 'Refresh'}</span>
          </button>

          <Link to="/submit" className="btn btn-primary">
            <PlusCircle size={16} />
            <span>Submit Job</span>
          </Link>
        </div>
      </div>

      {error && (
        <div className="alert alert-error">
          <AlertTriangle size={18} />
          <div>
            <strong>Backend Connection Notice:</strong> {error}
            <div style={{ fontSize: '0.75rem', marginTop: '4px' }}>
              Verify your Spring Boot backend is running on <code>http://localhost:8080</code>.
            </div>
          </div>
        </div>
      )}

      {/* Top Stat Cards (Reference screenshot layout) */}
      <div className="stat-cards-grid">
        <StatCard
          label="Total Jobs Executed"
          value={totalCount}
          accent="yellow"
          pillText={`${totalCount} recorded`}
          pillType="warning"
          icon={Layers}
        />
        <StatCard
          label="Succeeded"
          value={succeededCount}
          accent="green"
          pillText="Completed successfully"
          pillType="success"
          icon={CheckCircle2}
        />
        <StatCard
          label="In Queue / Running"
          value={runningCount}
          accent="blue"
          pillText={runningCount > 0 ? 'Active workers' : 'Idle'}
          pillType="warning"
          icon={Activity}
        />
        <StatCard
          label="Retrying"
          value={retryingCount}
          accent="amber"
          pillText="Exponential backoff"
          pillType="warning"
          icon={RefreshCw}
        />
        <StatCard
          label="Dead Letter Queue"
          value={deadCount}
          accent="red"
          pillText={deadCount > 0 ? `${deadCount} pending in DLQ` : 'DLQ Empty'}
          pillType="danger"
          icon={AlertTriangle}
        />
      </div>

      {/* Filter Tabs Bar (matching reference pills) & Search Container */}
      <div className="filter-tabs-bar">
        <div className="filter-tabs-left">
          <div className="tabs-group">
            <button
              type="button"
              className={`tab-btn ${activeTab === 'ALL' ? 'active' : ''}`}
              onClick={() => handleTabChange('ALL')}
            >
              <span>All</span>
              <span className="tab-count">{totalCount}</span>
            </button>
            <button
              type="button"
              className={`tab-btn ${activeTab === 'RUNNING' ? 'active' : ''}`}
              onClick={() => handleTabChange('RUNNING')}
            >
              <span>Running / Queued</span>
              <span className="tab-count">{runningCount}</span>
            </button>
            <button
              type="button"
              className={`tab-btn ${activeTab === 'SUCCESS' ? 'active' : ''}`}
              onClick={() => handleTabChange('SUCCESS')}
            >
              <span>Succeeded</span>
              <span className="tab-count">{succeededCount}</span>
            </button>
            <button
              type="button"
              className={`tab-btn ${activeTab === 'RETRYING' ? 'active' : ''}`}
              onClick={() => handleTabChange('RETRYING')}
            >
              <span>Retrying</span>
              <span className="tab-count">{retryingCount}</span>
            </button>
            <button
              type="button"
              className={`tab-btn ${activeTab === 'DEAD' ? 'active' : ''}`}
              onClick={() => handleTabChange('DEAD')}
            >
              <span>Dead / Failed</span>
              <span className="tab-count">{deadCount}</span>
            </button>
          </div>

          {/* Search by Job ID Box */}
          <div className="dashboard-search-container">
            <Search size={15} className="search-icon" />
            <input
              type="text"
              placeholder="Search by Job ID..."
              value={searchJobId}
              onChange={handleSearchChange}
              className="dashboard-search-input"
              aria-label="Search by Job ID"
            />
            {searchJobId && (
              <button
                type="button"
                onClick={handleClearSearch}
                className="search-clear-btn"
                title="Clear search"
                aria-label="Clear search"
              >
                <X size={14} />
              </button>
            )}
          </div>
        </div>

        <div className="filter-tabs-right">
          <span style={{ color: autoRefresh ? 'var(--accent-yellow)' : 'var(--text-muted)', fontWeight: 500, display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
            <span className={`dot-indicator ${autoRefresh ? 'online' : 'offline'}`} style={{ width: '6px', height: '6px', backgroundColor: autoRefresh ? 'var(--accent-yellow)' : 'var(--text-muted)' }} />
            {autoRefresh ? 'Auto-refresh: 10s' : 'Auto-refresh: Off'}
          </span>
          {lastRefreshed && (
            <span>• Last updated at {lastRefreshed}</span>
          )}
        </div>
      </div>

      {/* Main Jobs Table with Pagination */}
      <div className="card" style={{ marginBottom: '24px' }}>
        <div className="card-header">
          <h2 className="card-title">Recent Queued Jobs</h2>
          <Link to="/jobs" className="details-link" style={{ fontSize: '0.8rem' }}>
            <span>View All Jobs</span>
            <ArrowRight size={14} />
          </Link>
        </div>
        <JobsTable
          jobs={paginatedJobs}
          isLoading={isLoading}
          emptyMessage={
            searchJobId.trim()
              ? `No jobs found matching Job ID "${searchJobId.trim()}"`
              : activeTab !== 'ALL'
              ? `No jobs found with status ${activeTab}.`
              : 'No jobs found matching the selected filter.'
          }
          emptySubtext={
            searchJobId.trim()
              ? 'Check the Job ID for typos or clear the search to view all jobs.'
              : 'Submit a job or wait for background workers to process existing queue items.'
          }
        />

        {/* Pagination Footer */}
        <div className="table-pagination-footer">
          <div className="pagination-info">
            {matchingCount > 0 ? (
              <>
                Showing <span className="pagination-bold">{startIndex + 1}</span>–<span className="pagination-bold">{endIndex}</span> of{' '}
                <span className="pagination-bold">{matchingCount}</span> {matchingCount === 1 ? 'matching job' : 'matching jobs'}
              </>
            ) : (
              <span className="pagination-empty-text">0 matching jobs</span>
            )}
          </div>

          <div className="pagination-controls">
            <button
              type="button"
              className="btn btn-secondary pagination-btn"
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={currentPage <= 1 || matchingCount === 0 || isLoading}
              aria-label="Previous page"
            >
              <ChevronLeft size={16} />
              <span>Previous</span>
            </button>

            <span className="pagination-page-indicator">
              Page <span className="pagination-bold">{currentPage}</span> of{' '}
              <span className="pagination-bold">{totalPages}</span>
            </span>

            <button
              type="button"
              className="btn btn-secondary pagination-btn"
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={currentPage >= totalPages || matchingCount === 0 || isLoading}
              aria-label="Next page"
            >
              <span>Next</span>
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </div>

      {/* DLQ Recent Failures Snapshot if dead jobs exist */}
      {dlqJobs.length > 0 && (
        <div className="card" style={{ borderColor: 'var(--color-danger-border)' }}>
          <div className="card-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <AlertTriangle size={18} style={{ color: 'var(--color-danger)' }} />
              <h2 className="card-title" style={{ color: '#f87171' }}>
                Dead Letter Queue Alert ({dlqJobs.length} permanent failure{dlqJobs.length > 1 ? 's' : ''})
              </h2>
            </div>
            <Link to="/dlq" className="btn btn-secondary btn-sm">
              <span>Inspect DLQ</span>
              <ArrowRight size={13} />
            </Link>
          </div>
          <div className="card-body" style={{ padding: '16px 24px' }}>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              Jobs in the Dead Letter Queue have exhausted all permitted retry attempts. You can inspect failure reasons or manually requeue them.
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
