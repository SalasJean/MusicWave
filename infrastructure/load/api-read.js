import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://api:8080';

export const options = {
  scenarios: {
    read_api: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 25),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const responses = http.batch([
    ['GET', `${baseUrl}/api/v1/system/status`],
    ['GET', `${baseUrl}/api/v1/tools/status`],
    ['GET', `${baseUrl}/api/v1/downloads?page=0&size=20`],
  ]);
  responses.forEach((response) => check(response, { 'status is 200': (value) => value.status === 200 }));
  sleep(0.1);
}
