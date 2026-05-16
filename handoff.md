# Session handoff — PE-feature UI live on remote

## Goal

Stand up the PE-feature parity layer (white-labeling, Scheduler, Reports,
Advanced RBAC, Platform Integrations, Data Converters, Device Library,
Solution Templates) on top of CE 4.4.0-SNAPSHOT in this internal fork, and
run a Mac→remote CI/CD pipeline so testing happens on the QNAP server
`192.168.69.16` rather than on local Docker.

This session: rebuild + redeploy with route paths and sidebar tree
restructured to mirror the PE conventions captured from
`preview.edgekinect.com` (read-only PE reference). Tidy repo, document the
manual build/scp/up sequence, free disk.

## Current state

**Deployed:** `thingsboard/tb-node:pe-local` is running on
`192.168.69.16` (containers `tb-node` + `tb-postgres`, postgres healthy,
tb-node started clean — no install loop). UI at
<http://192.168.69.16:8000>. Default tenant login
`tenant@thingsboard.org / tenant`.

**Schema:** 11 PE tables (`role`, `entity_group_permission`, `user_role`,
`scheduled_event`, `report_config`, `converter`, `integration`,
`payload_codec_library`, `solution_template`, `solution_install_record`).
Mirrored in `dao/src/main/resources/sql/schema-entities.sql` and
`application/src/main/data/upgrade/lts/schema_update.sql`.

**Backend:** entity → JdbcTemplate service → REST controller → runtime
hook (poller / lifecycle manager / runner) for all 8 features.

**Frontend:** all 8 PE features have admin pages under
`ui-ngx/src/app/modules/home/pages/admin/pe/` plus `white-labeling`
component at the root of `admin/`. Routes restructured to match PE paths
(no longer nested under `/settings/`):

| Feature              | Route                                    |
|----------------------|------------------------------------------|
| White-labeling       | `/white-labeling/whiteLabel`             |
| Scheduler            | `/features/scheduler`                    |
| Reports              | `/reporting/templates`                   |
| Solution templates   | `/solutionTemplates`                     |
| Integrations         | `/integrationsCenter/integrations`       |
| Data converters      | `/integrationsCenter/converters`         |
| Device library       | `/integrationsCenter/codec-library`      |
| Roles                | `/security-settings/roles`               |

Menu tree (`core/services/menu.models.ts`) reordered to match PE: Reporting
and Solution templates near top; Integration center as a grouped toggle;
Roles slotted under Security settings; Scheduler under Advanced features;
White-labeling as a top-level entry.

**CI/CD:** `pefeatures-build.sh` at repo root drives the full
build→scp→remote-up pipeline. Credentials are env-var-only (`TB_DEPLOY_*`,
loaded from gitignored `.env`). The build uses `DOCKER_DEFAULT_PLATFORM=
linux/amd64` so the image runs on the x86_64 QNAP. `instructions.md` §2a
documents the one-shot script; the new §2a.alt documents the manual
build/scp/remote-up commands for partial reruns and debugging.

**Compilation:** verified end-to-end in this session — `mvn clean install
-DskipTests` succeeds; `msa/tb-node` Docker build succeeds; image loads
and starts on the remote. UI bundle is included in the Docker image (the
frontend-maven-plugin runs as part of the `ui-ngx` module).

**Tests:** none added this session. Still listed as the priority for the
next agent.

## Changed (this session)

### Frontend route + menu restructure

- `ui-ngx/src/app/modules/home/pages/admin/admin-routing.module.ts` —
  removed PE routes from `/settings/` children; added top-level routes for
  white-labeling, reports, solution templates, and the
  `integrationsCenter` group. Slotted `roles` under existing
  `security-settings` children.
- `ui-ngx/src/app/modules/home/pages/features/features-routing.module.ts`
  — added `/features/scheduler` route, imports
  `SchedulerEventsComponent` from `@home/pages/admin/pe/`.
- `ui-ngx/src/app/core/services/menu.models.ts` — updated all
  `MenuId.pe_*` paths to PE-compatible URLs; reordered the TENANT_ADMIN
  menu tree to match PE's sidebar.

### Operational

- Deploy succeeded with `INSTALL_TB="false"` after the earlier first-run
  install ran the schema bootstrap. The named volume `tb-postgres-data`
  preserves the DB across `docker compose down/up`.
- `pefeatures-build.sh` deployment timing (this run): Maven ≈ 4 min,
  Docker image ≈ 10 sec, save+gzip ≈ 15 sec, scp ≈ 1 min 49 sec, remote
  load+up ≈ 1 min 25 sec — total ≈ 7 min 30 sec wall clock.

### Tidy (this session, after deploy)

- Moved PE reference screenshots (`pe-01-home.png` … `pe-08-solutions.png`)
  out of repo root into `.playwright-mcp/`.
- Added `.playwright-mcp/` to `.gitignore`.
- Added §2a.alt "Manual three-step deploy (build → scp → remote up)" to
  `instructions.md` with explicit commands per phase (env-loading,
  cross-arch build, scp, remote `docker load` + `docker compose up -d`,
  optional Mac cleanup).
- Reclaimed ~ 3.7 GB on the Mac: deleted `build-artifacts/tb-node-pe.tar.gz`
  (882 MB) and removed local images `thingsboard/tb-node:{pe-local,latest,
  4.4.0-SNAPSHOT}` (926 MB each; same layer hash so the actual reclaim is
  one image's worth plus the tarball).

## Failed attempts (this session)

1. **Route paths still under `/settings/`.** First attempt left the PE
   pages nested under `/settings/<feature>`. Login screen showed
   "Settings → White labeling, …" only, with no top-level PE entries.
   **Diagnosis:** PE uses top-level routes per the screenshots from
   `preview.edgekinect.com`. **Fix:** moved routes to top level (table
   above) and reordered the menu tree.

2. **`docker compose stop tb-node` failed during a mid-deploy recovery.**
   I used the *container name* (`tb-node`) instead of the *service name*
   (`thingsboard-ce`). **Fix:** use `docker compose down` (no service
   name) when recycling the whole stack.

3. **INSTALL_TB=true left on for a normal redeploy** earlier caused a
   crash loop on the duplicate sysadmin row (the installer ran again on a
   non-empty DB, and `start-tb-node.sh` doesn't transition from installer
   mode to server mode). **Fix:** flip `INSTALL_TB` back to `"false"`
   after the first successful schema install; documented in §2a.alt of
   `instructions.md`.

## Earlier session entries (kept for context)

- GraalVM polyglot was abandoned — `ScriptConverter` is now a pass-through
  that annotates metadata; actual JS decoding happens downstream in
  rule-engine script nodes via CE's `JsInvokeService`.
- `IntegrationHttpController` is mounted under
  `/api/noauth/integrations/http/{routingKey}` (not `/api/v1/...`, which
  collides with the device API entry point).
- `WhiteLabelingServiceImpl` uses `findAdminSettingsByTenantIdAndKey`, not
  `findAdminSettingsByKey` (the latter silently ignores tenantId and
  always queries SYS_TENANT_ID).
- 8 files in CE source modified: schema-entities.sql, schema_update.sql,
  EntityType.java, EntityIdFactory.java, DefaultAccessControlService.java,
  app.component.ts, logo.component.ts, admin{,-routing}.module.ts.
- ~50 new files in the PE feature folders.

## Added after the route restructure: tb-web-report sidecar

Reverse-engineered the PE `docker-compose.yml` (image
`thingsboard/tb-pe-web-report:4.3.1.1PE`) and shipped an Apache-2.0
equivalent.

- **New service**: `tb-web-report/` at repo root — Node 22 +
  `puppeteer-core` + system Chromium, HTTP API on `:8383`. Endpoint
  `POST /api/generateReport` takes `{ url, jwt, format, viewport,
  navigationTimeoutMs, idleWaitMs }` and returns the rendered binary.
  `Dockerfile` baselines on `node:22-bookworm-slim` + apt chromium +
  fonts + dumb-init.
- **Java client**: `application/.../service/report/WebReportClient.java`
  posts via `java.net.http.HttpClient`, streams the response to a temp
  file, surfaces non-2xx as IOException. Enabled by env
  `REPORTS_SERVER_ENDPOINT_URL` (Spring property
  `reports.server.endpoint-url`).
- **ReportGenerator** delegates to `WebReportClient` when configured;
  in-process `ProcessBuilder` chromium fork retained as fallback.
- **ReportRunner.issueRenderToken**: looks up the tenant's first
  TENANT_ADMIN via `UserService.findTenantAdmins(...)`, builds a
  `SecurityUser`, and mints an access JWT via `JwtTokenFactory`. The
  microservice injects the token via
  `page.evaluateOnNewDocument(t => localStorage.setItem('jwt_token', t))`
  before navigating to the dashboard.
- **Compose**: added `tb-web-report` service + `REPORTS_SERVER_ENDPOINT_URL`
  / `REPORT_BASE_URL` env wired into tb-node. `REPORT_BASE_URL` now
  points at `http://thingsboard-ce:8080` (docker-compose service name)
  so the URL the renderer dereferences resolves inside the network.
- **Build script**: `build_web_report()` builds the sidecar image with
  `--platform linux/amd64`; `package` saves a second tarball
  `tb-web-report-pe.tar.gz`; `deploy` scps it and runs `docker load` on
  the remote. New env knobs: `SKIP_WEB_REPORT=1`, `TB_WEB_REPORT_TAG`.
- **Docs**: `instructions.md` §2d and the per-feature Reports note
  updated; new troubleshooting entries for blank login renders.
  `progress.md` Reports row bumped from 🔶 to 🟡 with the new details.
- **Compile check**: `mvn -pl application -am compile` clean.
- **Not yet redeployed** — code/compose/docs only. Next deploy run
  needs to build and ship the new sidecar image.

## Added after the tb-web-report sidecar: migrate PE pages to CE's EntityTableConfig pattern

User feedback: "still only the sidebar is matching with pe but not all functionality and other views". Root cause: the 7 PE pages were built on a custom `<div class="pe-list">` layout rather than CE's standard `EntitiesTableComponent` + `EntityTableConfig` resolver pattern that every other CE/PE page uses (Tenants, Devices, Queues, …). Result: PE-aligned menu but pages that visually diverge — no sortable headers, no search/refresh icons, no `Items per page` paginator, no checkbox bulk-select, no dialog-based create flow.

**Rewrote all 7 PE pages** to the EntityTableConfig pattern. Now they share the same table chrome as the rest of the app.

- New files: `<feature>-table-config.resolver.ts` (column definitions, fetch/save/delete wiring, custom row actions) + `<feature>-entity.component.{ts,html}` (form panel) under `ui-ngx/src/app/modules/home/pages/admin/pe/`.
- Custom row actions: `Run now` button on the Reports row; `Install` button on Solution templates row.
- Each entity-component extends `EntityComponent<T>` and implements `buildForm` / `updateForm` / `prepareFormValue`.
- Forms use Angular Material with PE-style fields (Name, Type, etc.). Complex sub-objects (permissions, schedule.configuration, integration.configuration, solution.bundle) are JSON textareas in v1 — production polish (matrix editor for permissions, cron builder for schedule) is a follow-up.
- Added 7 PE entity types to the front-end `EntityType` enum (`ROLE`, `SCHEDULED_EVENT`, `REPORT_CONFIG`, `INTEGRATION`, `CONVERTER`, `PAYLOAD_CODEC`, `SOLUTION_TEMPLATE`) with translation map and resource map entries.
- Added 7 sections of i18n keys to `assets/locale/locale.constant-en_US.json` (`role.*`, `scheduler.*`, `report.*`, `integration.*`, `converter.*`, `codec.*`, `solution.*`).
- Updated `admin-routing.module.ts` and `features-routing.module.ts` to point routes at `EntitiesTableComponent` with the new resolvers (rather than the old custom components).
- Updated `admin.module.ts` declarations to register only the new entity-components.
- Deleted obsolete custom components: `pe/{roles,scheduler-events,reports,integrations,converters,codec-library,solution-templates}.component.{ts,html}` and `pe-pages.scss`.
- **Compile check passed**: `ng build` succeeds in ~48 s with only pre-existing warnings (no errors from this change).

### Migrated routes

| Feature              | Route                                    | Resolver                          |
|----------------------|------------------------------------------|-----------------------------------|
| Roles                | `/security-settings/roles`               | `RolesTableConfigResolver`        |
| Scheduler events     | `/features/scheduler`                    | `ScheduledEventsTableConfigResolver` |
| Reports              | `/reporting/templates`                   | `ReportsTableConfigResolver`      |
| Integrations         | `/integrationsCenter/integrations`       | `IntegrationsTableConfigResolver` |
| Data converters      | `/integrationsCenter/converters`         | `ConvertersTableConfigResolver`   |
| Device library       | `/integrationsCenter/codec-library`      | `CodecsTableConfigResolver`       |
| Solution templates   | `/solutionTemplates`                     | `SolutionsTableConfigResolver`    |

### Known follow-ups for production polish

- **Permission matrix editor** for Roles (currently a JSON textarea).
- **Cron-builder widget** for Scheduler events (currently free-text cron string).
- **Dashboard picker** for Reports (currently a UUID input — should use CE's `tb-dashboard-autocomplete`).
- **Type-conditional configuration fields** for Integrations (each IntegrationType has its own config shape — currently one JSON textarea).
- **Type-conditional schedule fields** (TIMER → interval; CRON → expression; WEEKLY/MONTHLY → cron-style picker).

## Next steps

In rough priority order:

1. **Login + click through each PE menu entry.** Confirm pages render,
   forms accept input, REST round-trips succeed. The restructured paths
   are deployed but not yet smoke-tested in the browser.
2. **Field-level UI alignment with PE.** The reference screenshots show
   PE-specific columns and filters that our tables don't have yet:
   - Integrations list: `Created time`, `Name`, `Integration type`,
     `Daily activity`, `Status`, `Remote`, action column.
   - Filters: `Name`, `Integration type`, `Uplink data converter`.
   - Converters list: `Type`, `Created time`.
   - Apply the same audit to Reports, Roles, Solution templates.
3. **Tests.** Start with `ScheduleEvaluator` (pure logic, easiest) and
   `RoleService.findGrantedOperations`. Then WebMvc tests for the new
   controllers.
4. **`/security-review`** the new controllers. Pre-flagged sharp edges:
   - `IntegrationHttpController` is JWT-unauthenticated (shared secret
     optional). Make secret check mandatory in prod.
   - `ReportGenerator` shell-forks Chrome with a URL from
     server-stored config; any future change that lets users control the
     URL needs SSRF defenses.
   - `RoleServiceImpl.findGrantedOperations` does
     `jsonb_array_elements_text` — safe today but worth re-reviewing when
     entity-group filtering lands.
5. **`EntityGroup`** entity + group-scoped permission enforcement to
   round out PE-RBAC parity.
6. **Native Kafka / OPC-UA / UDP / TCP integration adapters.** Types are
   in the enum; adapter classes need writing; branch in
   `IntegrationManager.createAdapter`.
7. **Authenticated dashboard URL for `ReportRunner.buildDashboardUrl`.**
   Needs a short-lived service-account JWT for headless Chrome.
8. **PE doc field-by-field audit** with WebFetch enabled — verify
   `WhiteLabelingParams` field names and REST route shapes against PE.

## Server prerequisites (reference)

When the stack is up on the remote:

- Postgres on 55432 (external) / 5432 (internal). DB `thingsboard`,
  user/password `postgres`/`postgres` (replace in prod).
- UI on <http://192.168.69.16:8000>. Default
  `sysadmin@thingsboard.org/sysadmin`, `tenant@thingsboard.org/tenant`.
- MQTT 1883, MQTT/TLS 8883, CoAP/LWM2M 5683–5688 UDP, gRPC 7090.
- For Reports: `chromium` must be installed in the container (the base
  `tb-node` image doesn't ship it). Either `docker exec -u root tb-node
  apt-get install -y chromium`, or bake it into
  `msa/tb-node/docker/Dockerfile`.

## How to redeploy (cheat sheet)

```bash
# One-shot (recommended)
./pefeatures-build.sh

# Or manually — see instructions.md §2a.alt for the full sequence
set -a; source .env; set +a
export DOCKER_DEFAULT_PLATFORM=linux/amd64
mvn clean install -DskipTests -Dlicense.skip=true
mvn -f msa/tb-node/pom.xml verify -Ddockerfile.skip=false \
    -Dmain.dir="$(pwd)" -DskipTests -Dlicense.skip=true
docker tag thingsboard/tb-node:latest thingsboard/tb-node:pe-local
docker save thingsboard/tb-node:pe-local | gzip > build-artifacts/tb-node-pe.tar.gz
scp -i "${TB_DEPLOY_KEY}" build-artifacts/tb-node-pe.tar.gz \
    deploy/docker-compose.yml "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}:${TB_DEPLOY_PATH}/"
ssh -i "${TB_DEPLOY_KEY}" "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}" \
    "cd ${TB_DEPLOY_PATH} && gunzip -c tb-node-pe.tar.gz | docker load && \
     docker compose down && docker compose up -d"
```
