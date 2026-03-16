# NANI Backend Security Hardening Checklist

## Authentication & Secrets
- [ ] Replace default JWT secret (`app.auth.jwt-secret`) in all non-dev environments.
- [ ] Keep JWT expiry aligned with role risk (`app.auth.jwt-exp-hours`).
- [ ] Rotate integration connector tokens on schedule and after incidents.
- [ ] Disable test seed credentials for production deployments.

## Authorization
- [ ] Validate role rules for admin-only APIs (`/api/auth/users`, connector rotation, etc.).
- [ ] Enforce approval workflow for high-impact actions:
  - QC override
  - sales backdated/price edits
- [ ] Review all `@PreAuthorize` annotations on newly added endpoints.

## API Surface
- [ ] Limit public endpoints to required set:
  - `/api/auth/login`
  - `/api/integrations/ingest/**`
  - `/actuator/health`, `/actuator/info`
- [ ] Keep actuator metrics/prometheus authenticated.
- [ ] Validate payload sizes for ingest and uploads.

## Data Protection
- [ ] Store integration tokens only as hashes (`BCrypt`).
- [ ] Ensure audit logs are append-only (no update/delete APIs).
- [ ] Back up H2 DB files with retention policy and restore drill logs.

## Operational Controls
- [ ] Enable HTTPS/TLS termination at ingress/load balancer.
- [ ] Configure request logging/redaction for sensitive fields.
- [ ] Set alert thresholds for:
  - auth failures spikes
  - connector ingest failure rate
  - error rate / latency / JVM memory
- [ ] Define incident playbook and owner contacts.
