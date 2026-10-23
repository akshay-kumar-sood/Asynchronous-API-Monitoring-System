import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 10,
    duration: '30s'
};

export default function () {
    const res = http.get('http://localhost:8080/api/jobs');

    check(res, {
        'request allowed or rate limited': (r) =>
            r.status === 200 || r.status === 429,
    });

    if (res.status === 429) {
        console.log('Rate limit triggered: HTTP 429');
    }

    sleep(0.05);
}