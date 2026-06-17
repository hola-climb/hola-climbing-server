import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const RUN_LABEL = __ENV.RUN_LABEL || 'local-baseline'
const PERF_PASSWORD = __ENV.PERF_PASSWORD || 'Password1!'
const TOKEN_USER_COUNT = Number(__ENV.TOKEN_USER_COUNT || '50')
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || '20')
const CURSOR_FOLLOW_RATE = Number(__ENV.CURSOR_FOLLOW_RATE || '0.35')
const MAX_CURSOR_DEPTH = Number(__ENV.MAX_CURSOR_DEPTH || '3')

const firstPageDuration = new Trend('recommendation_feed_first_page_duration', true)
const cursorPageDuration = new Trend('recommendation_feed_cursor_page_duration', true)
const feedFailureRate = new Rate('recommendation_feed_failed')

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    recommendation_feed: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.RAMP_UP || '2m', target: Number(__ENV.VUS || '20') },
        { duration: __ENV.STEADY || '5m', target: Number(__ENV.VUS || '20') },
        { duration: __ENV.RAMP_DOWN || '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    recommendation_feed_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
}

function perfEmail(index) {
  return `perf_user_${String(index).padStart(5, '0')}@hola.test`
}

function metricValue(metric, key) {
  if (!metric || !metric.values) {
    return 'n/a'
  }
  return metric.values[key] === undefined ? 'n/a' : metric.values[key]
}

function login(index) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: perfEmail(index), password: PERF_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { api: 'auth-login' } },
  )

  const ok = check(res, {
    'login status is 200': (r) => r.status === 200,
    'login has access token': (r) => Boolean(r.json('data.accessToken')),
  })

  if (!ok) {
    throw new Error(`Failed to login perf user ${index}: status=${res.status} body=${res.body}`)
  }

  return res.json('data.accessToken')
}

export function setup() {
  const tokens = []
  for (let i = 1; i <= TOKEN_USER_COUNT; i += 1) {
    tokens.push(login(i))
  }
  return { tokens }
}

function getFeed(token, cursor, depth) {
  const cursorParam = cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''
  const url = `${BASE_URL}/api/recommendations/videos?size=${PAGE_SIZE}${cursorParam}`
  const res = http.get(url, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'recommendation-feed', page_depth: String(depth) },
  })

  const ok = check(res, {
    'feed status is 200': (r) => r.status === 200,
    'feed response success': (r) => r.json('isSuccess') === true,
    'feed content is array': (r) => Array.isArray(r.json('data.content')),
  })

  feedFailureRate.add(!ok)
  if (depth === 1) {
    firstPageDuration.add(res.timings.duration)
  } else {
    cursorPageDuration.add(res.timings.duration)
  }

  return ok ? res.json('data.nextCursor') : null
}

export default function (data) {
  const token = data.tokens[(__VU + __ITER) % data.tokens.length]

  group('recommendation feed first page', () => {
    let cursor = getFeed(token, null, 1)

    if (cursor && Math.random() < CURSOR_FOLLOW_RATE) {
      for (let depth = 2; depth <= MAX_CURSOR_DEPTH; depth += 1) {
        cursor = getFeed(token, cursor, depth)
        if (!cursor) {
          break
        }
      }
    }
  })

  sleep(Number(__ENV.SLEEP_SECONDS || '0.2'))
}

export function handleSummary(data) {
  const summary = textSummary(data)
  const resultDir = `perf/results/recommendation-feed/${RUN_LABEL}`
  return {
    stdout: summary,
    [`${resultDir}/k6-summary.json`]: JSON.stringify(data, null, 2),
    [`${resultDir}/k6-summary.txt`]: summary,
  }
}

function textSummary(data) {
  const metrics = data.metrics
  const duration = metrics.http_req_duration
  const failed = metrics.http_req_failed
  const requests = metrics.http_reqs
  const firstPage = metrics.recommendation_feed_first_page_duration
  const cursorPage = metrics.recommendation_feed_cursor_page_duration

  return [
    '# k6 recommendation feed summary',
    `run_label=${RUN_LABEL}`,
    `base_url=${BASE_URL}`,
    `http_reqs=${metricValue(requests, 'count')}`,
    `http_req_failed_rate=${metricValue(failed, 'rate')}`,
    `http_req_duration_p50=${metricValue(duration, 'med')}`,
    `http_req_duration_p95=${metricValue(duration, 'p(95)')}`,
    `http_req_duration_p99=${metricValue(duration, 'p(99)')}`,
    `first_page_p95=${metricValue(firstPage, 'p(95)')}`,
    `cursor_page_p95=${metricValue(cursorPage, 'p(95)')}`,
    '',
  ].join('\n')
}
