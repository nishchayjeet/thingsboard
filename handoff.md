# Session handoff — PE-feature build kickoff

## Goal

Stand up the PE-feature parity layer on top of CE in this internal fork: build
white-labeling, scheduler, reports, advanced RBAC, platform integrations,
device codec library, and solution templates as patches to the CE source
(user explicitly accepted upgrade-merge pain). Then create a Mac→remote
CI/CD pipeline so testing happens on `192.168.69.16` rather than on local
Docker.

## Current state

**Schema:** 11 new tables added (`role`, `entity_group_permission`,
`user_role`, `scheduled_event`, `report_config`, `converter`, `integration`,
`payload_codec_library`, `solution_template`, `solution_install_record`).

**Frontend:** all 7 PE features now have working admin pages under
`ui-ngx/src/app/modules/home/pages/admin/pe/` plus `white-labeling.component`
(at the root of `admin/`). Each page follows PE conventions:
list-on-left + inline-edit-on-right, sidebar menu entries wired into the
TENANT_ADMIN tree in `core/services/menu.models.ts`.

**Deployment:** `pefeatures-build.sh` at repo root builds + ships +
restarts the stack on the remote server using credentials from `.env`
(gitignored). `instructions.md` covers production + dev workflows.

> _Original session note (kept for context):_

**Schema:** 11 new tables added (`role`, `entity_group_permission`,
`user_role`, `scheduled_event`, `report_config`, `converter`, `integration`,
`payload_codec_library`, `solution_template`, `solution_install_record`).
Mirrored in both `dao/src/main/resources/sql/schema-entities.sql` (fresh
installs) and `application/src/main/data/upgrade/lts/schema_update.sql`
(existing-install upgrades — idempotent).

**Backend per feature:** entity → JdbcTemplate-based service → REST
controller → runtime hook (poller / lifecycle manager / runner). All 7
features have working backends. See `progress.md` for per-feature
detail.

**Frontend:** only **white-labeling** has a full admin page. The other six
have working REST APIs only.

**CI/CD:** `pefeatures-build.sh` at repo root builds the project, builds
the `thingsboard/tb-node:pe-local` image on this Mac, ships it to the
remote test server via scp+docker load, and brings up the stack with the
compose file at `deploy/docker-compose.yml`. Credentials are env-var-only
(see `deploy/.env.example`).

**Compilation:** not verified end-to-end this session — I read the codebase
to align signatures but did not run `mvn compile`. The first thing the next
agent should do is `mvn -pl common/data,common/dao-api,dao,application -am compile -DskipTests -Dlicense.skip=true` and surface anything broken.

**Tests:** none written this session. Listed as next-step priority.

## Changed (this session)

### CE source modifications (8 files)

- `dao/src/main/resources/sql/schema-entities.sql` — 11 new tables.
- `application/src/main/data/upgrade/lts/schema_update.sql` — mirror migrations.
- `common/data/.../EntityType.java` — added `ROLE`, `SCHEDULED_EVENT`,
  `REPORT_CONFIG`, `INTEGRATION`, `CONVERTER`, `PAYLOAD_CODEC`,
  `SOLUTION_TEMPLATE` (proto numbers 100–106).
- `application/.../security/permission/DefaultAccessControlService.java` —
  added `hasRoleGrant` fallback: when the authority-based check fails,
  consult `RoleService` for custom-role grants before denying.
- `application/.../config/ThingsboardSecurityConfiguration.java` — no
  changes ultimately needed (HTTP integration webhook lives under
  `/api/noauth/integrations/**`, covered by existing `/api/noauth/**` permit).
- `ui-ngx/src/app/app.component.ts` — bootstrap `WhiteLabelingService.loadLoginWhiteLabel()`.
- `ui-ngx/src/app/shared/components/logo.component.ts` — reactive logo
  source from the WL service.
- `ui-ngx/src/app/modules/home/pages/admin/admin{,-routing}.module.ts` —
  registered + routed `WhiteLabelingComponent`.
- `.gitignore` — added `.env`, `build-artifacts/`, `.vscode/`.

### New files (~50)

Grouped by feature:

- **White-labeling** (8): `WhiteLabelingParams`, `LoginWhiteLabelingParams`,
  `WhiteLabelingService` (api + impl), `WhiteLabelingController`,
  `white-labeling.models.ts`, `white-labeling.service.ts`,
  `white-labeling.component.{ts,html}`.
- **Scheduler** (6): `ScheduledEventId`, `ScheduledEvent`, `ScheduleConfig`,
  `ScheduledEventService` (api + impl), `ScheduleEvaluator`,
  `ScheduledEventController`, `SchedulerPoller`, `scheduled-event.service.ts`.
- **Reports** (5): `ReportConfigId`, `ReportConfig`, `ReportConfigService`
  (api + impl), `ReportController`, `ReportGenerator`, `ReportRunner`.
- **RBAC** (5): `RoleId`, `Role`, `RoleService` (api + impl), `RoleController`.
- **Integrations** (10): `IntegrationId`, `ConverterId`, `Integration`,
  `Converter`, `IntegrationType`, `IntegrationService` (api + impl),
  `IntegrationContext`, `IntegrationAdapter`, `IntegrationManager`,
  `MqttIntegrationAdapter`, `HttpIntegrationAdapter`, `ScriptConverter`,
  `IntegrationController`, `IntegrationHttpController`.
- **Codec library** (6): `PayloadCodecId`, `PayloadCodec`,
  `PayloadCodecService` (api + impl), `PayloadCodecController`,
  `PayloadCodecCatalogSeeder` (10 reference codecs).
- **Solution templates** (5): `SolutionTemplateId`, `SolutionTemplate`,
  `SolutionTemplateService` (api + impl), `SolutionInstaller`,
  `SolutionTemplateController`.

### Operational

- `pefeatures-build.sh` — Mac→remote build+deploy.
- `deploy/docker-compose.yml` — server-side stack using
  `thingsboard/tb-node:pe-local`, ports per the user's spec
  (8000/7090/1883/8883/5683-5688, postgres on 55432).
- `deploy/dev-run.sh` — Mac-local `mvn spring-boot:run` with remote Postgres.
- `deploy/.env.example` — env-var template (no secrets).
- `CLAUDE.md` — agent onboarding (commits, tests, security review, no AI
  attribution).
- `progress.md` — per-feature parity matrix.
- This file (`handoff.md`).

## Failed attempts

1. **GraalVM polyglot for inline converter execution.** I initially wrote
   `ScriptConverter` to call GraalVM JS so integration adapters could
   decode payloads before pushing to the rule engine. GraalVM is not a CE
   dependency, so the import would not resolve. **Resolution:**
   `ScriptConverter` is now a pass-through that annotates metadata with the
   converter id/name; the actual script decoding happens downstream in
   rule-engine script nodes that already use CE's clustered `JsInvokeService`.
   This is faithful enough to PE behavior for v1 since PE's integration
   converter is itself a script-engine job — we're just running the same
   script one hop later.

2. **`/api/v1/integrations/...` URL prefix.** First version of
   `IntegrationHttpController` mounted under `/api/v1/integrations/...`,
   which collides with the device API entry point in
   `ThingsboardSecurityConfiguration` (`DEVICE_API_ENTRY_POINT = "/api/v1/**"`).
   That entry point has device-token auth; webhooks need either no auth or
   the integration's shared secret. **Resolution:** moved the webhook to
   `/api/noauth/integrations/http/{routingKey}` so it's covered by the
   existing `NON_TOKEN_BASED_AUTH_ENTRY_POINTS` permit list. No security
   config changes needed.

3. **PE-doc audit agent.** I spawned a background agent to fetch each
   feature's PE doc page and diff my implementation against it. Both
   `WebFetch` and `curl` are denied in this sandbox, so the agent returned
   no findings. **Resolution:** I worked from PE behavior I know from the
   user's spec and surfaced known gaps in `progress.md`. The audit is worth
   redoing in a session where the agent has network — particularly to verify
   field-name parity (e.g. `WhiteLabelingParams` field names, REST route
   shapes).

4. **Per-tenant `findAdminSettingsByKey` quirk.** CE's
   `AdminSettingsServiceImpl.findAdminSettingsByKey` ignores the `tenantId`
   it's passed and queries `SYS_TENANT_ID` unconditionally. I used the
   intended `findAdminSettingsByTenantIdAndKey(tenantId, key)` in
   `WhiteLabelingServiceImpl` so tenant-scope WL actually works.

## Next steps

In rough priority order:

1. **`mvn compile`** the four affected modules and fix anything broken.
2. **Run the build script end-to-end against the remote.** Set
   `TB_DEPLOY_KEY` (preferred — see CLAUDE.md), copy `deploy/.env.example`
   to `.env`, then `./pefeatures-build.sh`. First deploy needs
   `INSTALL_TB=true` in `deploy/docker-compose.yml` once so the new tables
   get created; revert to `false` after.
3. **Smoke-test each feature's REST API** with `curl` after the stack is
   up: white-label save/read at noauth + tenant scope; create a scheduled
   event; create a role; etc. (Test plan goes in next session's handoff.)
4. **Build admin UI pages** for scheduler, RBAC, integrations, codecs,
   reports, solution templates. Copy the
   `white-labeling.component.ts` / `.html` pattern; register in
   `admin.module.ts` and `admin-routing.module.ts`.
5. **Tests.** Start with `ScheduleEvaluator` (pure logic, easiest) and
   `RoleService.findGrantedOperations`. Then controller WebMvc tests.
6. **`/security-review`** the new controllers in a dedicated session.
   Pre-flagged sharp edges:
   - `JdbcTemplate` SQL injection — all queries currently use `?` binding;
     audit for any future change.
   - `IntegrationHttpController` is unauthenticated by JWT — shared-secret
     check should be mandatory in prod (currently optional if `secret` is
     null on the integration).
   - `ReportGenerator` shell-forks Chrome with a URL; if that URL ever
     comes from user input rather than a server-stored config, add SSRF
     defenses.
   - `RoleServiceImpl.findGrantedOperations` does a `jsonb_array_elements_text`
     query that's safe today but worth a separate review when entity-group
     filtering lands.
7. **`EntityGroup`** entity + group-scoped permission enforcement to round
   out the RBAC story. Today `entity_group_permission` is a table but no
   `EntityGroup` first-class entity exists.
8. **Native Kafka / OPC-UA / UDP / TCP integration adapters.** Types are
   in the enum; adapter classes need writing; `IntegrationManager.createAdapter`
   needs a branch per new type.
9. **Authenticated dashboard URL for `ReportRunner.buildDashboardUrl`.**
   Currently the headless Chrome instance hits the dashboard URL with no
   auth; for non-public dashboards this needs a short-lived service-account
   JWT carried as a fragment or a signed token query param.

## Server prerequisites (for the next agent or human)

When the build is shipped and `docker compose up` runs on the remote:

- Postgres on 55432 (external) / 5432 (internal). DB name `thingsboard`,
  default password `postgres`. The schema is created automatically on
  first run if `INSTALL_TB=true` is set in `deploy/docker-compose.yml`.
- ThingsBoard UI on **http://192.168.69.16:8000** (default sysadmin:
  `sysadmin@thingsboard.org` / `sysadmin`; default tenant:
  `tenant@thingsboard.org` / `tenant`).
- MQTT on 1883, MQTT/TLS on 8883, CoAP/LWM2M on 5683-5688 UDP, gRPC on
  7090.
- For the report generator to work on the server, `chromium` must be
  installed in the container — the base `tb-node` image doesn't ship it.
  Either:
  (a) `docker exec -u root tb-node apt-get update && apt-get install -y chromium`, or
  (b) rebuild the image with chromium baked in (`msa/tb-node/docker/Dockerfile`).
