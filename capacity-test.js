
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        capacity: {
            executor: 'ramping-arrival-rate',
            startRate: 5,
            timeUnit: '1s',
            stages: [
                { target: 5, duration: '30s' },
                { target: 10, duration: '30s' },
                { target: 20, duration: '30s' },
                { target: 30, duration: '30s' },
            ],
            preAllocatedVUs: 10,
            maxVUs: 50,
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000'],
        dropped_iterations: ['count==0'],
    },
};

export default function () {
    const res = http.get(
        'https://asynchronous-api-monitoring-system.onrender.com/api/jobs'
    );
    check(res, { 'status is 200': (r) => r.status === 200 });
}
