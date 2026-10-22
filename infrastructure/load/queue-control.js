import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://api:8080';

export const options = {
  scenarios: {
    empty_queue_controls: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 5),
      iterations: Number(__ENV.ITERATIONS || 50),
      maxDuration: '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const response = http.post(`${baseUrl}/api/v1/queue/start`, null);
  check(response, { 'queue start is idempotent': (value) => value.status === 200 });
  sleep(0.05);
}
