import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Layers,
  RefreshCw,
  AlertTriangle,
  PlusCircle,
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
    </aside>
  );
}
