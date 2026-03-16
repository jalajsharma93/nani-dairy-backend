# NANI Backend Monitoring Baseline

## Enabled Endpoints
- `GET /actuator/health` (public)
- `GET /actuator/info` (public)
- `GET /actuator/metrics` (authenticated)
- `GET /actuator/prometheus` (authenticated)

## Baseline SLO Targets
- API success rate: `>= 99.5%` (5xx excluded from expected business 4xx errors)
- P95 API latency: `< 800ms`
- Auth login P95 latency: `< 500ms`
- Connector ingest normalization failure ratio: `< 2%` over 15 minutes

## Recommended Alerts
- `5xx error rate > 2%` for 5 minutes
- JVM heap usage `> 85%` for 10 minutes
- DB connection pool saturation `> 90%` for 5 minutes
- Integration ingest failures `> 5%` for 10 minutes
- Approval request backlog (pending > threshold by role)

## Daily Review
- Health endpoint status
- Failed connector events (`/api/integrations/events?status=FAILED`)
- Pending approval queue (`/api/governance/approvals?status=PENDING`)
- Latest immutable audits (`/api/governance/audits?limit=100`)
