/**
 * Central API Client for APIRetry Spring Boot Backend
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

async function request(endpoint, options = {}) {
  const url = `${BASE_URL}${endpoint}`;
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  try {
    const res = await fetch(url, { ...options, headers });
    if (!res.ok) {
      let errorMessage = `HTTP ${res.status}: ${res.statusText}`;
      try {
        const errorJson = await res.json();
        if (errorJson.message) {
          errorMessage = errorJson.message;
        } else if (errorJson.error) {
          errorMessage = errorJson.error;
        }
      } catch {
        // Response was not JSON
      }
      throw new Error(errorMessage);
    }
    return await res.json();
  } catch (error) {
    console.error(`API request failed: ${options.method || 'GET'} ${endpoint}`, error);
    throw error;
  }
}

export const api = {
  /**
   * List jobs, optionally filtered by status (QUEUED, PROCESSING, SUCCESS, RETRYING, DEAD)
   */
  async getJobs(status = null) {
    const query = status ? `?status=${encodeURIComponent(status)}` : '';
    return await request(`/api/jobs${query}`);
  },

  /**
   * Retrieve a single job's details and payload by UUID
   */
  async getJobById(id) {
    return await request(`/api/jobs/${encodeURIComponent(id)}`);
  },

  /**
   * Submit a new asynchronous job with optional idempotency key
   */
  async submitJob(jobData, idempotencyKey = null) {
    const headers = {};
    if (idempotencyKey && idempotencyKey.trim()) {
      headers['Idempotency-Key'] = idempotencyKey.trim();
    }
    return await request('/api/jobs', {
      method: 'POST',
      headers,
      body: JSON.stringify(jobData),
    });
  },

  /**
   * List all permanently failed dead letter jobs
   */
  async getDlqJobs() {
    return await request('/api/dlq');
  },

  /**
   * Manually requeue a dead job from DLQ
   */
  async retryDlqJob(jobId) {
    return await request(`/api/dlq/${encodeURIComponent(jobId)}/retry`, {
      method: 'POST',
    });
  },

  /**
   * Health ping to detect backend connectivity and round-trip latency
   */
  async checkConnection() {
    const start = Date.now();
    try {
      await request('/api/jobs');
      return { online: true, latencyMs: Date.now() - start };
    } catch (e) {
      return { online: false, error: e.message };
    }
  },
};
