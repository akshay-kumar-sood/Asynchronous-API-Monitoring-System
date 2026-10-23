
import http from 'k6/http';

export const options = {
    stages: [
        { duration: '10s', target: 5 },
        { duration: '20s', target: 5 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000'],
    },
};

export default function () {
    http.get('https://asynchronous-api-monitoring-system.onrender.com/api/jobs');
}
