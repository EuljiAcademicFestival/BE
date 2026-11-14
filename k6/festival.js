import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import execution from 'k6/execution';
import { Rate } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const profile = __ENV.PROFILE || 'smoke';
const workload = __ENV.WORKLOAD || (profile === 'capacity' ? 'mixed' : 'read');
const vus = Number(__ENV.VUS || (profile === 'capacity' ? 200 : profile === 'smoke' ? 1 : 10));
const pauseSeconds = Number(__ENV.PAUSE_SECONDS || 1);
const p95Limit = Number(__ENV.P95_MS || 500);
const runId = `${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
const contractFailures = new Rate('api_contract_failed');
const measuredEndpoints = [
  'GET /api/guestbook popular', 'GET /api/guestbook latest', 'GET /api/characters',
  'GET /api/characters/:memberId', 'GET /api/rankings/top', 'GET /api/guestbook cursor',
  ...(workload !== 'read' ? ['PUT /api/guestbook/:memberId/like', 'DELETE /api/guestbook/:memberId/like'] : []),
  ...(workload === 'mixed' ? ['PUT /api/members/me/profile'] : []),
];

if (!['smoke', 'load', 'capacity'].includes(profile) || !['read', 'mixed', 'hotspot'].includes(workload)) {
  throw new Error('PROFILE: smoke/load/capacity, WORKLOAD: read/mixed/hotspot');
}
if (!Number.isInteger(vus) || vus < 1 || vus > 1000 || !Number.isFinite(pauseSeconds) || pauseSeconds <= 0
    || !Number.isFinite(p95Limit) || p95Limit <= 0) {
  throw new Error('VUS must be 1..1000; PAUSE_SECONDS and P95_MS must be positive');
}

// Hold each intermediate step so server metrics can be compared as concurrency rises.
const capacityStages = [...new Set([10, 50, 100, vus].filter(target => target <= vus))]
  .flatMap(target => [
    { duration: '30s', target },
    { duration: target === vus ? (__ENV.HOLD_DURATION || '3m') : '30s', target },
  ]);
capacityStages.push({ duration: '30s', target: 0 });

export const options = {
  setupTimeout: '10m',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count'],
  scenarios: {
    festival: profile === 'smoke'
      ? { executor: 'constant-vus', vus, duration: __ENV.SMOKE_DURATION || '15s', gracefulStop: '10s' }
      : { executor: 'ramping-vus', startVUs: 0, gracefulRampDown: '10s', gracefulStop: '10s',
        stages: profile === 'capacity' ? capacityStages : [
        { duration: '30s', target: Math.min(5, vus) },
        { duration: '30s', target: vus },
        { duration: __ENV.HOLD_DURATION || '2m', target: vus },
        { duration: '30s', target: 0 },
      ] },
  },
  thresholds: {
    'http_req_failed{phase:workload}': ['rate<0.01'],
    'http_req_duration{phase:workload}': [`p(95)<${p95Limit}`, `p(99)<${p95Limit * 2}`],
    'api_contract_failed{phase:workload}': ['rate<0.01'],
    'checks{phase:workload}': ['rate>0.99'],
    ...Object.fromEntries(measuredEndpoints.map(name => [
      `http_req_duration{name:${name},phase:workload}`, [`p(95)<${p95Limit}`],
    ])),
  },
};

function params(token, name, phase = 'workload') {
  return {
    timeout: '10s',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    tags: { name, phase },
  };
}

function json(response) {
  try { return response.json(); } catch (_) { return null; }
}

function success(response, code, validate = () => true) {
  const body = json(response);
  let valid = false;
  try { valid = response.status === 200 && body.status === 200 && body.code === code && validate(body.data); }
  catch (_) { valid = false; }
  check(response, { [code]: () => valid }, { phase: 'workload' });
  contractFailures.add(!valid, { phase: 'workload' });
  return valid ? body.data : null;
}

export function setup() {
  const health = http.get(`${baseUrl}/actuator/health`, params(null, 'GET /actuator/health', 'setup'));
  if (health.status !== 200) fail(`Server is not ready: HTTP ${health.status}. Start BackendApplication first.`);

  // Dedicated test accounts are reused on later runs; no real account credentials are needed.
  const password = 'K6-local-test-only-2026!';
  const users = [];
  for (let i = 0; i <= vus; i++) {
    const email = `k6-user-${i + 1}@loadtest.invalid`;
    const signupParams = params(null, 'POST /api/auth/signup', 'setup');
    signupParams.responseCallback = http.expectedStatuses(201, 409);
    const signup = http.post(`${baseUrl}/api/auth/signup`, JSON.stringify({
      email, password, characterId: (i % 6) + 1, name: `부하테스트${i + 1}`,
      grade: '게스트', guestbookMessage: 'k6 부하 테스트 전용 방명록입니다.',
    }), signupParams);
    const signupBody = json(signup);
    if (!(signup.status === 201 && signupBody?.code === 'AUTH_SIGNUP_201')
        && !(signup.status === 409 && signupBody?.code === 'AUTH_DUPLICATE_MEMBER')) {
      fail(`Cannot prepare test account ${i + 1}: HTTP ${signup.status}`);
    }
    const login = http.post(`${baseUrl}/api/auth/login`, JSON.stringify({ email, password }),
      params(null, 'POST /api/auth/login', 'setup'));
    const body = json(login);
    if (login.status !== 200 || body?.code !== 'AUTH_LOGIN_200' || !body.data?.accessToken || !body.data?.memberId) {
      fail(`Cannot log in test account ${i + 1}: HTTP ${login.status}`);
    }
    users.push({ memberId: body.data.memberId, token: body.data.accessToken });
  }
  console.log(`Prepared ${users.length} test accounts. profile=${profile}, workload=${workload}, maxVUs=${vus}`);
  return { users };
}

export default function (data) {
  const index = execution.vu.idInTest - 1;
  const viewer = data.users[index];
  const target = workload === 'hotspot' ? data.users[vus]
    : data.users[(index + 1 + execution.vu.iterationInScenario % vus) % data.users.length];
  const requests = [
    ['GET', `${baseUrl}/api/guestbook?sort=popular&limit=20`, null, params(viewer.token, 'GET /api/guestbook popular')],
    ['GET', `${baseUrl}/api/guestbook?sort=latest&limit=20`, null, params(viewer.token, 'GET /api/guestbook latest')],
    ['GET', `${baseUrl}/api/characters?memberIds=${viewer.memberId},${target.memberId}`, null,
      params(viewer.token, 'GET /api/characters')],
    ['GET', `${baseUrl}/api/characters/${target.memberId}`, null, params(viewer.token, 'GET /api/characters/:memberId')],
    ['GET', `${baseUrl}/api/rankings/top`, null, params(viewer.token, 'GET /api/rankings/top')],
  ];
  const results = http.batch(requests);
  const validEntry = entry => entry && Number.isInteger(entry.memberId) && entry.likeCount >= 0
    && (entry.rank === null || [1, 2, 3].includes(entry.rank));
  const popular = success(results[0], 'GUESTBOOK_LIST_200', d => Array.isArray(d.entries) && d.entries.every(validEntry));
  success(results[1], 'GUESTBOOK_LIST_200', d => Array.isArray(d.entries) && d.entries.every(validEntry));
  success(results[2], 'CHARACTER_LABELS_200', d => Array.isArray(d.characters)
    && d.characters.some(c => c.memberId === target.memberId));
  success(results[3], 'CHARACTER_DETAIL_200', d => validEntry(d) && d.memberId === target.memberId);
  success(results[4], 'RANKING_TOP_200', d => Array.isArray(d.rankings)
    && d.rankings.every(entry => validEntry(entry) && entry.rank !== null));

  if (popular?.nextCursor) {
    const next = http.get(`${baseUrl}/api/guestbook?sort=popular&limit=20&cursor=${encodeURIComponent(popular.nextCursor)}`,
      params(viewer.token, 'GET /api/guestbook cursor'));
    success(next, 'GUESTBOOK_LIST_200', d => Array.isArray(d.entries) && d.entries.every(validEntry));
  }
  if (workload !== 'read') {
    const like = http.put(`${baseUrl}/api/guestbook/${target.memberId}/like`, null,
      params(viewer.token, 'PUT /api/guestbook/:memberId/like'));
    success(like, 'GUESTBOOK_LIKE_SET_200', d => d.memberId === target.memberId && d.likedByMe === true && d.likeCount >= 1);
    const unlike = http.del(`${baseUrl}/api/guestbook/${target.memberId}/like`, null,
      params(viewer.token, 'DELETE /api/guestbook/:memberId/like'));
    success(unlike, 'GUESTBOOK_LIKE_REMOVED_200', d => d.memberId === target.memberId && d.likedByMe === false && d.likeCount >= 0);
    if (workload === 'mixed' && execution.vu.iterationInScenario % 10 === 0) {
      const profileUpdate = http.put(`${baseUrl}/api/members/me/profile`, JSON.stringify({
        characterId: (index % 6) + 1, name: `부하테스트${index + 1}`, grade: '게스트',
        guestbookMessage: 'k6 부하 테스트 전용 방명록입니다.',
      }), params(viewer.token, 'PUT /api/members/me/profile'));
      success(profileUpdate, 'MEMBER_PROFILE_UPDATE_200', d => d.memberId === viewer.memberId && d.likeCount >= 0);
    }
  }
  sleep(pauseSeconds);
}

export function handleSummary(data) {
  // k6 includes setup()'s return value here; it contains live login tokens.
  const safeData = { ...data };
  delete safeData.setup_data;
  const values = name => data.metrics[name]?.values || {};
  const latency = values('http_req_duration{phase:workload}');
  const errors = values('http_req_failed{phase:workload}');
  const contracts = values('api_contract_failed{phase:workload}');
  const failed = Object.entries(data.metrics).flatMap(([name, metric]) => Object.entries(metric.thresholds || {})
    .filter(([, result]) => !result.ok).map(([threshold]) => `${name}: ${threshold}`));
  const summary = [
    `profile=${profile} workload=${workload} maxVUs=${vus}`,
    `HTTP workload latency: p95=${(latency['p(95)'] || 0).toFixed(2)}ms p99=${(latency['p(99)'] || 0).toFixed(2)}ms`,
    `HTTP failure rate: ${((errors.rate || 0) * 100).toFixed(2)}%`,
    `API contract failure rate: ${((contracts.rate || 0) * 100).toFixed(2)}%`,
    `Thresholds: ${!latency.count ? 'NOT RUN (no workload requests)' : failed.length ? 'FAIL\n' + failed.join('\n') : 'PASS'}`,
    ...measuredEndpoints.map(name => {
      const stats = values(`http_req_duration{name:${name},phase:workload}`);
      return `${name}: ${stats.count || 0} requests, p95=${(stats['p(95)'] || 0).toFixed(2)}ms`;
    }),
    `Results: k6/results/${runId}-${profile}-${workload}-summary.json`,
  ].join('\n') + '\n';
  return { stdout: summary, [`k6/results/${runId}-${profile}-${workload}-summary.json`]: JSON.stringify(safeData, null, 2) };
}
