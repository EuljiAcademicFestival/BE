import http from 'k6/http';
import ws from 'k6/ws';
import { check, fail } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const base = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const endpoint = base.replace(/^http/, 'ws') + '/ws/festival';
const vus = Number(__ENV.VUS || 2);
const seconds = Number(__ENV.WS_DURATION_SECONDS || 15);
const withLikes = __ENV.WS_WITH_LIKES === 'true';
if (!Number.isInteger(vus) || vus < 1 || vus > 200 || !Number.isFinite(seconds) || seconds < 1 || seconds > 300)
  throw new Error('VUS: 1..200; WS_DURATION_SECONDS: 1..300');
const authOk = new Rate('festival_ws_auth_ok');
const receivedState = new Rate('festival_ws_received_state');
const errors = new Rate('festival_ws_error_rate');
const joinLatency = new Trend('festival_ws_join_ms', true);
const movementEcho = new Trend('festival_ws_movement_echo_ms', true);
const states = new Counter('festival_ws_state_frames');
const engagements = new Counter('festival_ws_engagement_frames');
const receivedEngagement = new Rate('festival_ws_received_engagement');
const engagementContractFailed = new Rate('festival_ws_engagement_contract_failed');
const engagementEcho = new Trend('festival_ws_engagement_echo_ms', true);

export const options = {
  setupTimeout: '10m',
  scenarios: { realtime: { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '10m' } },
  thresholds: {
    festival_ws_auth_ok: ['rate==1'], festival_ws_received_state: ['rate==1'],
    festival_ws_error_rate: ['rate<0.01'], checks: ['rate==1'],
    festival_ws_received_engagement: ['rate==1'], festival_ws_engagement_contract_failed: ['rate<0.01'],
  },
};

export function setup() {
  const users = [];
  for (let i = 1; i <= vus; i++) {
    const email = `k6-user-${i}@loadtest.invalid`, password = 'K6-local-test-only-2026!';
    const params = { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'setup' } };
    const signup = http.post(base + '/api/auth/signup', JSON.stringify({ email, password, characterId: 1,
      name: `부하테스트${i}`, grade: '게스트', guestbookMessage: 'k6 WebSocket 테스트 계정입니다.' }), params);
    if (![201, 409].includes(signup.status)) fail(`Cannot prepare WebSocket account ${i}: HTTP ${signup.status}`);
    const login = http.post(base + '/api/auth/login', JSON.stringify({ email, password }), params);
    if (login.status !== 200) fail(`Cannot log in WebSocket account ${i}: HTTP ${login.status}`);
    const data = login.json().data;
    if (!data?.accessToken || !data.memberId) fail('Invalid login response');
    users.push({ token: data.accessToken, memberId: data.memberId });
  }
  return { users };
}

export default function (data) {
  const user = data.users[__VU - 1];
  let authenticated = false, stateCount = 0, engagementCount = 0, failed = false, sequence = 0, finished = false;
  let liked = false, pendingLike = null;
  const likeTarget = data.users[__VU % data.users.length].memberId;
  const sent = new Map();
  const response = ws.connect(endpoint, { headers: { Origin: __ENV.WS_ORIGIN || 'http://localhost:3000' } }, socket => {
    let opened;
    socket.on('open', () => {
      opened = Date.now(); socket.send(JSON.stringify({ type: 'AUTH', accessToken: user.token }));
      socket.setTimeout(() => { if (!authenticated) { failed = true; socket.close(); } }, 6000);
      socket.setTimeout(() => { if (!finished) { failed = true; socket.close(); } }, seconds * 1000 + 10000);
    });
    socket.on('message', raw => {
      let event;
      try { event = JSON.parse(raw); } catch (_) { failed = true; socket.close(); return; }
      if (event.type === 'WELCOME') {
        authenticated = event.data.memberId === user.memberId;
        if (!authenticated) { failed = true; socket.close(); return; }
        joinLatency.add(Date.now() - opened);
        socket.setInterval(() => {
          const seq = sequence++, angle = seq / 30;
          sent.set(seq, Date.now());
          for (const old of sent.keys()) if (old < seq - 64) sent.delete(old);
          socket.send(JSON.stringify({ type: 'MOVE', sequence: seq, x: Math.cos(angle) * 10,
            y: 0, z: Math.sin(angle) * 10, yaw: angle }));
        }, 100);
        socket.setTimeout(() => { finished = true; socket.close(); }, seconds * 1000);
        if (withLikes) socket.setInterval(() => {
          liked = !liked;
          const started = Date.now();
          const response = http.request(liked ? 'PUT' : 'DELETE', `${base}/api/guestbook/${likeTarget}/like`, null,
            { headers: { Authorization: `Bearer ${user.token}` }, tags: { phase: 'workload', name: 'REST like during WebSocket' } });
          const ok = check(response, { 'Like REST response': r => r.status === 200
            && r.json().data?.likedByMe === liked && r.json().data?.likeCount >= 0 });
          if (!ok) failed = true;
          else pendingLike = { started, count: response.json().data.likeCount };
        }, 1000);
      } else if (event.type === 'STATE') {
        stateCount++; states.add(1);
        const own = event.data.players.find(p => p.memberId === user.memberId);
        if (own && sent.has(own.sequence)) {
          movementEcho.add(Date.now() - sent.get(own.sequence));
          for (const seq of sent.keys()) if (seq <= own.sequence) sent.delete(seq);
        }
      } else if (event.type === 'ENGAGEMENT_STATE') {
        engagementCount++; engagements.add(1);
        const payload = event.data || {};
        const valid = Array.isArray(payload.counts) && Array.isArray(payload.rankings)
          && payload.counts.every(c => c.likeCount >= 0 && !('likedByMe' in c))
          && payload.rankings.every(r => [1, 2, 3].includes(r.rank) && !('likedByMe' in r));
        engagementContractFailed.add(!valid);
        if (!valid) { failed = true; socket.close(); return; }
        if (valid && pendingLike && event.data.counts.some(c => c.memberId === likeTarget && c.likeCount === pendingLike.count)) {
          engagementEcho.add(Date.now() - pendingLike.started); pendingLike = null;
        }
      }
    });
    socket.on('error', () => { failed = true; });
    socket.on('close', () => { if (!finished) failed = true; });
  });
  check(response, { 'WebSocket handshake 101': r => r && r.status === 101 });
  authOk.add(authenticated); receivedState.add(stateCount > 0); errors.add(failed);
  receivedEngagement.add(engagementCount > 0);
}

export function handleSummary(data) {
  const safe = { ...data }; delete safe.setup_data;
  return { [`k6/results/${Date.now()}-websocket-summary.json`]: JSON.stringify(safe, null, 2),
    stdout: `WebSocket: auth=${safe.metrics.festival_ws_auth_ok?.values.rate}, states=${safe.metrics.festival_ws_state_frames?.values.count}, errors=${safe.metrics.festival_ws_error_rate?.values.rate}\n` };
}
