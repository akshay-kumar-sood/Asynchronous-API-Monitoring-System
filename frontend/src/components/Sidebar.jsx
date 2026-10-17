import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Layers,
  RefreshCw,
  AlertTriangle,
  PlusCircle,
  Activity,
  Server,
} from 'lucide-react';

export default function Sidebar() {
  return (
    <aside className="sidebar">
      <nav className="sidebar-nav">
        <div className="sidebar-category">Overview</div>
        <NavLink
          to="/"
          className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          end
        >
          <LayoutDashboard size={18} className="nav-icon" />
          <span>Dashboard</span>
        </NavLink>

        <div className="sidebar-category">Job Queue</div>
        <NavLink
          to="/jobs"
          className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
        >
          <Layers size={18} className="nav-icon" />
          <span>All Jobs</span>
        </NavLink>

        <NavLink
          to="/retries"
          className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
        >
          <RefreshCw size={18} className="nav-icon" />
          <span>Retries</span>
        </NavLink>

        <NavLink
          to="/dlq"
          className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
        >
          <AlertTriangle size={18} className="nav-icon" />
          <span>Dead Letter Queue</span>
        </NavLink>

        <div className="sidebar-category">Actions</div>
        <NavLink
          to="/submit"
          className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
        >
          <PlusCircle size={18} className="nav-icon" />
          <span>Submit Job</span>
        </NavLink>
      </nav>

      <div className="sidebar-footer">
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
          <Server size={14} style={{ color: 'var(--accent-yellow)' }} />
          <span style={{ fontWeight: 600, color: 'var(--text-secondary)' }}>APIRetry Architecture</span>
        </div>
        <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', lineHeight: '1.4', marginBottom: '8px' }}>
          Java · Spring Boot · React · PostgreSQL · REST APIs · Exponential Backoff · Dead Letter Queue
        </div>
        <div style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>Worker Pool: 4 Core / 8 Max</div>
      </div>
    </aside>
  );
}
