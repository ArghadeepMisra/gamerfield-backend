import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const ids = new SharedArray('ids', () => JSON.parse(open('./player-ids.json')));
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SOURCE = __ENV.SOURCE || 'redis';

export const options = {
  scenarios: {
    warmup: { executor: 'constant-vus', vus: 50, duration: '15s', tags: { phase: 'warmup' } },
    measure: { executor: 'constant-vus', vus: 200, duration: '60s', startTime: '15s', tags: { phase: 'measure' } },
  },
  thresholds: { 'http_req_duration{phase:measure}': ['p(95)<2000'] },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)'],
};

export default function () {
  const id = ids[Math.floor(Math.random() * ids.length)];
  const res = http.get(`${BASE}/api/v1/leaderboard/players/${id}?source=${SOURCE}`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
