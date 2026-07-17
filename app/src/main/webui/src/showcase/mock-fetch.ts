/**
 * Intercepts fetch() calls and returns mock data for all AML workbench endpoints.
 * The UI code is completely unaware — it calls fetch() normally and gets JSON back.
 */
import {
  INVESTIGATIONS, LAYER6_RESPONSE, PRIOR_CONTEXT, FINDINGS, GATES,
  COMPLIANCE_EVIDENCE, AUDIT_TRAIL, THROUGHPUT_METRICS, TRUST_SCORE_METRICS,
  GATE_METRICS, INCLUSION_PROOF,
} from './mock-data.js';

type RouteHandler = (url: URL, params: Record<string, string>) => unknown;

const routes: Array<{ pattern: RegExp; handler: RouteHandler }> = [
  {
    pattern: /^\/api\/investigations$/,
    handler: () => INVESTIGATIONS,
  },
  {
    pattern: /^\/api\/layer6\/investigations\/([^/]+)$/,
    handler: (_url, params) => ({ ...LAYER6_RESPONSE, caseId: params['1'] }),
  },
  {
    pattern: /^\/api\/investigations\/([^/]+)\/prior-context$/,
    handler: () => PRIOR_CONTEXT,
  },
  {
    pattern: /^\/api\/investigations\/([^/]+)\/findings$/,
    handler: () => FINDINGS,
  },
  {
    pattern: /^\/api\/investigations\/([^/]+)\/gates$/,
    handler: () => GATES,
  },
  {
    pattern: /^\/api\/investigations\/([^/]+)\/compliance-evidence$/,
    handler: () => COMPLIANCE_EVIDENCE,
  },
  {
    pattern: /^\/api\/investigations\/([^/]+)\/audit-trail$/,
    handler: () => AUDIT_TRAIL,
  },
  {
    pattern: /^\/api\/investigations\/([^/]+)\/audit-trail\/([^/]+)\/proof$/,
    handler: (_url, params) => ({ ...INCLUSION_PROOF, entryId: params['2'] }),
  },
  {
    pattern: /^\/api\/metrics\/throughput$/,
    handler: () => THROUGHPUT_METRICS,
  },
  {
    pattern: /^\/api\/metrics\/trust-scores$/,
    handler: () => TRUST_SCORE_METRICS,
  },
  {
    pattern: /^\/api\/metrics\/gates$/,
    handler: () => GATE_METRICS,
  },
  {
    pattern: /^\/api\/layer6\/investigations\/([^/]+)\/(suspend|resume)$/,
    handler: (_url, params) => ({ status: 'ok', action: params['2'] }),
  },
  {
    pattern: /^\/workitems\/inbox$/,
    handler: () => ([
      { id: 'wi-001', title: 'Compliance review — SAR for TXN-2024-001', status: 'PENDING', priority: 'HIGH', candidateGroups: 'compliance-officers', callerRef: 'aml:investigation:c1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6', createdAt: '2024-11-15T14:00:00Z', claimDeadline: '2024-12-15T14:00:00Z' },
      { id: 'wi-002', title: 'Compliance review — SAR for TXN-2024-005', status: 'ASSIGNED', priority: 'HIGH', candidateGroups: 'compliance-officers', assigneeId: 'officer-001', callerRef: 'aml:investigation:a5e6f7a8-b9c0-d1e2-f3a4-b5c6d7e8f9a0', createdAt: '2024-11-19T11:30:00Z', claimDeadline: '2024-12-19T11:30:00Z' },
      { id: 'wi-003', title: 'Compliance review — SAR for TXN-2024-003', status: 'PENDING', priority: 'CRITICAL', candidateGroups: 'compliance-officers', callerRef: 'aml:investigation:e3c4d5e6-f7a8-b9c0-d1e2-f3a4b5c6d7e8', createdAt: '2024-11-17T09:00:00Z', claimDeadline: '2024-12-17T09:00:00Z' },
    ]),
  },
  {
    pattern: /^\/workitems\/inbox\/summary$/,
    handler: () => ({ total: 3, pending: 2, assigned: 1, overdue: 0, breached: 0 }),
  },
  {
    pattern: /^\/workitems\/events$/,
    handler: () => [],
  },
  {
    pattern: /^\/workitems\/([^/]+)$/,
    handler: (_url, params) => ({ id: params['1'], title: 'Work Item', status: 'PENDING' }),
  },
];

const originalFetch = window.fetch.bind(window);

window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
  const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, window.location.origin);
  const pathname = url.pathname;

  for (const route of routes) {
    const match = pathname.match(route.pattern);
    if (match) {
      const params: Record<string, string> = {};
      match.forEach((val, idx) => { if (idx > 0 && val) params[String(idx)] = val; });

      // Simulate network latency (50-200ms)
      await new Promise(r => setTimeout(r, 50 + Math.random() * 150));

      const body = route.handler(url, params);
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
  }

  // Fall through to real fetch for non-API requests (CSS, JS, etc.)
  return originalFetch(input, init);
};

console.log('%c[AML Showcase] Mock fetch active — all /api/* calls return mock data', 'color: #3b82f6; font-weight: bold');
