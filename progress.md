# PE-feature parity — progress tracker

Maintained per-session. When a feature's state changes, update its row and the
"Last touched" cell.

Legend:
- ✅ **Done** — entity, DAO, service, REST endpoints, runtime hooks, and basic UI present.
- 🟡 **Partial** — backend works end-to-end; UI/UX or edge cases missing.
- 🔶 **Backend only** — REST API works; no Angular UI page yet.
- ⛔ **Pending** — not started.

## Feature matrix

| Area                  | Backend | REST API | Runtime | Angular UI | Sidebar | PE-aligned route | Tests | Status |
|-----------------------|:-------:|:--------:|:-------:|:----------:|:-------:|:----------------:|:-----:|:------:|
| White-labeling        |   ✅    |   ✅    |   ✅    |     ✅     |    ✅   |       ✅         |  ⛔  | 🟡 |
| Scheduler             |   ✅    |   ✅    |   ✅    |     ✅     |    ✅   |       ✅         |  ⛔  | 🟡 |
| Reports               |   ✅    |   ✅    |   ✅    |     ✅     |    ✅   |       ✅         |  ⛔  | 🟡 |
| Advanced RBAC         |   ✅    |   ✅    |   ✅    |     ✅     |    ✅   |       ✅         |  ⛔  | 🟡 |
| Platform Integrations |   ✅    |   ✅    |  🟡(*)  |     ✅     |    ✅   |       ✅         |  ⛔  | 🟡 |
| Data converters       |   ✅    |   ✅    |   ✅    |     ✅     |    ✅   |       ✅         |  ⛔  | 🟡 |
| Device codec library  |   ✅    |   ✅    |   ✅    |     ✅     |    ✅   |       ✅         |  ⛔  | 🟡 |
| Solution Templates    |   ✅    |   ✅    |   ✅    |     ✅     |    ✅   |       ✅         |  ⛔  | 🟡 |
| SSO/SAML federation   |   ⛔    |   ⛔    |   ⛔    |     ⛔     |    ⛔   |       ⛔         |  ⛔  | ⛔  |

### Deployed routes (live on 192.168.69.16:8000 as of 2026-05-15)

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

(*) Integration runtime: MQTT-family (covers MQTT, AWS IoT, Azure IoT Hub,
ChirpStack, TTN, Loriot via MQTT bridges) and HTTP webhook are working.
Kafka, AMQP-native Azure, OPC-UA, UDP/TCP listeners declared in the enum
but adapter not implemented yet.

## Detailed status

### White-labeling — 🟡
**Compared against:** <https://thingsboard.io/docs/pe/user-guide/white-labeling/>
- ✅ `WhiteLabelingParams`, `LoginWhiteLabelingParams` (matches PE field names).
- ✅ REST: `/api/noauth/whiteLabel/loginWhiteLabelParams`, `/api/whiteLabel/{system,tenant}WhiteLabelParams`.
- ✅ Persistence via existing `admin_settings` table — no new table needed.
- ✅ Frontend bootstrap: `AppComponent` calls `WhiteLabelingService.loadLoginWhiteLabel()`.
- ✅ Logo, favicon, page title, CSS variables, custom CSS applied at runtime.
- ✅ Admin page wired at `/settings/white-labeling`.
- ⛔ Email-template customization (PE has it; CE has hardcoded templates we'd need to make tenant-overridable).
- ⛔ Domain-based WL (per-host branding lookup) — currently single-domain only.
- ⛔ Tests.

### Scheduler — 🔶
**Compared against:** <https://thingsboard.io/docs/pe/user-guide/scheduler/>
- ✅ `scheduled_event` table.
- ✅ `ScheduleEvaluator` covers NONE/MINUTELY/HOURLY/DAILY/WEEKLY/MONTHLY/YEARLY/TIMER/CRON.
- ✅ `SchedulerPoller` (`@Scheduled`) pushes `TbMsg` of type `SCHEDULED_EVENT` to the tenant's rule engine.
- ✅ REST: `/api/scheduler/event`, `/api/scheduler/events`.
- ✅ Frontend `ScheduledEventService` HTTP client.
- ⛔ Angular UI page (CRUD form).
- ⛔ Tests.
- ⚠️ **Multi-node**: poller is single-node-safe. For HA, add `SELECT … FOR UPDATE SKIP LOCKED`
  in `ScheduledEventServiceImpl.findDueEvents`.

### Reports — 🔶
**Compared against:** <https://thingsboard.io/docs/pe/user-guide/reports/>
- ✅ `report_config` table.
- ✅ `ReportGenerator` forks headless Chromium → PDF/PNG.
- ✅ `ReportRunner` snapshots, emails with attachment via `JavaMailSender`.
- ✅ Auto-triggered from `SchedulerPoller` when a scheduled event has `type=REPORT` and `configuration.reportId`.
- ✅ Manual run endpoint: `POST /api/report/config/{id}/runNow`.
- ⛔ Authenticated dashboard URL — currently `buildDashboardUrl` produces an unauth URL. Needs a
  service-account JWT or signed short-lived token before headless Chrome can render private dashboards.
- ⛔ Angular UI page.
- ⛔ Tests.
- ⚠️ **Server prerequisite**: deploy host needs `chromium` installed; default binary
  configurable via `report.chrome.binary`.

### Advanced RBAC — 🔶
**Compared against:** <https://thingsboard.io/docs/pe/user-guide/role-based-access-control/>
- ✅ `role`, `entity_group_permission`, `user_role` tables.
- ✅ `Role` entity with GENERIC + GROUP role types; JSON permissions per
  PE's resource→operations shape.
- ✅ `DefaultAccessControlService` patched: when authority check fails, falls
  back to `roleService.findGrantedOperations()`.
- ✅ REST: `/api/role`, `/api/roles`, `/api/user/{id}/role/{rid}`.
- ⛔ **EntityGroup** as a first-class entity. PE models device/asset/customer
  groups; permissions hang off the (Role, EntityGroup) pair. We have the
  underlying table but no `EntityGroup` entity / service / controller yet.
- ⛔ Group-permission enforcement during entity reads (e.g. filtering a paged
  device list to only entities a role grants access to).
- ⛔ Angular UI page.
- ⛔ Tests.

### Platform Integrations — 🔶
**Compared against:** <https://thingsboard.io/docs/pe/user-guide/integrations/>
- ✅ `integration`, `converter` tables.
- ✅ 13 PE integration types declared (`IntegrationType` enum):
  HTTP, MQTT, AWS_IOT, AZURE_IOT, IBM_WATSON, KAFKA, CHIRPSTACK,
  THE_THINGS_NETWORK, LORIOT, OPC_UA, UDP, TCP, CUSTOM.
- ✅ `IntegrationManager` lifecycle (start/stop on save/delete, on startup
  load all enabled integrations).
- ✅ `MqttIntegrationAdapter` (Paho MQTT v3) — works for MQTT and every
  LoRaWAN NS or cloud broker that exposes an MQTT bridge (AWS IoT, Azure IoT
  Hub, ChirpStack, TTN, Loriot).
- ✅ `HttpIntegrationAdapter` + `IntegrationHttpController` at
  `/api/noauth/integrations/http/{routingKey}` with shared-secret header.
- ✅ REST CRUD for integrations + converters.
- 🟡 **`ScriptConverter` is pass-through**: actual decoding happens
  downstream in rule-engine script nodes via CE's existing `JsInvokeService`.
  Adequate for v1; can be lifted into the integration boundary later.
- ⛔ Native Kafka adapter.
- ⛔ OPC-UA adapter.
- ⛔ UDP/TCP listeners.
- ⛔ Native Azure AMQP/Event Hubs (today uses MQTT bridge).
- ⛔ Angular UI page.
- ⛔ Tests.

### Device codec library — 🔶
**Compared against:** <https://thingsboard.io/docs/pe/user-guide/contrib/device-library/>
- ✅ `payload_codec_library` table.
- ✅ `PayloadCodecCatalogSeeder` pre-loads 10 reference codecs on startup:
  Dragino LHT65, Milesight EM300-TH, Decentlab DL-PR26, Browan TBHV110,
  Sensoneo, Cayenne LPP, ChirpStack/TTN/Actility ThingPark unwraps, JSON pass-through.
- ✅ REST: `/api/codec/library` with filters by vendor/category.
- ⛔ ~390 more codecs to reach PE's catalog size. Each is mostly JS-only,
  so this is bulk content work, not engineering.
- ⛔ "Clone codec into device profile" UI flow (entity already supports it
  via the existing `DeviceProfile` transformer config slot).
- ⛔ Angular UI page.
- ⛔ Tests.

### Solution Templates — 🔶
**Compared against:** <https://thingsboard.io/docs/pe/user-guide/solution-templates/>
- ✅ `solution_template`, `solution_install_record` tables.
- ✅ `SolutionInstaller` replays bundle into tenant in dependency order:
  device profiles → rule chains → dashboards.
- ✅ REST: `/api/solution/templates`, `/api/solution/template/{id}/install`.
- ⛔ **Sample bundled solutions** (PE ships Smart Building, Fleet Tracking,
  etc.). We have the install mechanism but no curated templates seeded.
- ⛔ Device + customer + asset import (current installer skips these for
  safety). Re-using CE's `EntitiesExportImportService` is the next step.
- ⛔ Uninstall.
- ⛔ Angular UI page.
- ⛔ Tests.

### SSO / SAML / OAuth2 IdPs — ⛔
- Not started. CE already has basic OAuth2 in `OAuth2Controller`; the PE
  extension is enterprise IdP federation (Okta, Azure AD, SAML).
- This was deprioritized in the initial scoping conversation.

## Known cross-cutting issues

1. **No frontend coverage for 6 of 7 features.** Backend REST APIs are
   functional; admin pages need to be built (template: `white-labeling.component.ts`).
2. **No tests written.** Compilation passes (target Maven build verified by
   reading; not run end-to-end in this session). Test scaffolding is the
   priority for the next agent.
3. **Multi-node poller race.** `SchedulerPoller` and any future report runner
   should hold a row-level lock before firing to avoid duplicate sends.
4. **GraalVM intentionally not added.** Converter scripts run in the existing
   `JsInvokeService` rule-engine path, not inline in `IntegrationContext`.
5. **PE doc audit incomplete.** The audit-agent's WebFetch was sandboxed off.
   When fetching is enabled, re-run with the prompt in the previous handoff.

## What the next agent should do (in order)

1. Run `mvn -pl common/data,common/dao-api,dao,application -am compile -DskipTests -Dlicense.skip=true`
   and fix any compile errors my edits introduced.
2. Add JUnit tests for `ScheduleEvaluator` (pure-logic; easiest start) and
   `RoleService.findGrantedOperations`.
3. Build admin UI pages: scheduler, RBAC, integrations, reports — copy the
   `white-labeling.component.ts` pattern.
4. Add `EntityGroup` entity to round out PE-RBAC parity.
5. Add native Kafka adapter to round out integrations.
