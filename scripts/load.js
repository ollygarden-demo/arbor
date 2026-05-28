import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  vus: 5,
  duration: '60s',
};

const BASE = __ENV.GATEWAY || 'http://localhost:8080';

export default function () {
  const owners = http.get(`${BASE}/api/customer/owners`);
  check(owners, { 'owners 200': r => r.status === 200 });

  const vets = http.get(`${BASE}/api/vet/vets`);
  check(vets, { 'vets 200': r => r.status === 200 });

  const visits = http.post(
    `${BASE}/api/visit/owners/1/pets/1/visits`,
    JSON.stringify({ date: '2026-05-28', description: 'checkup' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(visits, { 'visit 200': r => [200,201,204].includes(r.status) });

  sleep(1);
}
