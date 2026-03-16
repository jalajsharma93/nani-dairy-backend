# NANI Backend Backup/Restore Runbook

## Scope
- Database engine: H2 file mode (`NANI_DB_PATH`, default `./data/nani_dairy`)
- Backup artifacts:
  - `${NANI_DB_PATH}.mv.db`
  - `${NANI_DB_PATH}.trace.db` (if present)

## Daily Backup (Cold Copy)
1. Stop backend process (`Ctrl+C` or service stop).
2. Create dated backup directory:
   - `mkdir -p backups/$(date +%F_%H%M)`
3. Copy DB files:
   - `cp data/nani_dairy.mv.db backups/$(date +%F_%H%M)/`
   - `cp data/nani_dairy.trace.db backups/$(date +%F_%H%M)/ 2>/dev/null || true`
4. Restart backend.

## Restore Drill (Weekly)
1. Stop backend.
2. Take safety snapshot of current DB:
   - `cp data/nani_dairy.mv.db data/nani_dairy.mv.db.pre-restore`
3. Restore chosen backup:
   - `cp backups/<backup_folder>/nani_dairy.mv.db data/nani_dairy.mv.db`
4. Start backend.
5. Verify:
   - Login works (`/api/auth/login`)
   - Health endpoint works (`/actuator/health`)
   - Core entities load (animals/sales/milk list APIs)

## Drill Log Template
- Date:
- Backup used:
- Restore duration:
- Verification checks passed:
- Issues found:
- Corrective actions:

## Retention
- Keep 14 daily backups.
- Keep 8 weekly restore points.
- Keep latest monthly archive for 12 months.
