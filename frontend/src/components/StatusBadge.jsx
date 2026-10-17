import React from 'react';

export default function StatusBadge({ status }) {
  if (!status) return null;

  const statusLower = status.toLowerCase();
  let badgeClass = 'badge-queued';

  if (statusLower === 'success') {
    badgeClass = 'badge-success';
  } else if (statusLower === 'processing') {
    badgeClass = 'badge-processing';
  } else if (statusLower === 'retrying') {
    badgeClass = 'badge-retrying';
  } else if (statusLower === 'dead' || statusLower === 'failed') {
    badgeClass = 'badge-dead';
  }

  return (
    <span className={`badge ${badgeClass}`}>
      <span className="badge-dot" />
      {status}
    </span>
  );
}
