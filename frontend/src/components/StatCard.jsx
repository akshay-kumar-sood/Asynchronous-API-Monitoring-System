import React from 'react';

export default function StatCard({
  label,
  value,
  subtext,
  accent = 'yellow',
  pillText,
  pillType = 'success',
  icon: Icon,
}) {
  return (
    <div className={`stat-card accent-${accent}`}>
      <div className="stat-card-label">
        <span>{label}</span>
        {Icon && <Icon size={16} className="text-muted" />}
      </div>
      <div className="stat-card-value">
        {value !== undefined && value !== null ? value : 0}
      </div>
      <div className="stat-card-footer">
        {pillText && (
          <span className={`stat-pill ${pillType}`}>
            {pillText}
          </span>
        )}
        {subtext && <span>{subtext}</span>}
      </div>
    </div>
  );
}
