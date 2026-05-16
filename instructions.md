# ThingsBoard PE-features fork — Deployment & operations guide

End-to-end instructions for building, deploying, and operating this fork.
Covers two modes:

- **A. Production / self-hosted** — single-node Docker on a Linux host you own.
- **B. Local development** — app runs on your Mac, services on the remote box.

If you're new to the repo, read `CLAUDE.md` first (agent/contributor
conventions). Feature status is tracked in `progress.md`; latest session
status in `handoff.md`.

---

## 0. Prerequisites

### On the build machine (Mac)
- macOS 13+ with admin rights.
- **Java 25** (the project's `pom.xml` requires JDK 25). Verify with `java -version`.
- **Maven 3.9+** (`brew install maven`).
- **Node 18+** and **Yarn** (`brew install node`, `corepack enable`) — the UI
  module uses Yarn under the hood; Maven invokes it automatically.
- **Docker Desktop** with BuildKit enabled (default).
- **OpenSSH client** (built-in on macOS).
- For password-based deploys only: `brew install hudochenkov/sshpass/sshpass`.

### On the deploy host (Linux server)
- Linux x86_64 with **Docker** and **`docker compose`** (v2 plugin).
- Reachable on the LAN with SSH (port 22).
- Optional but recommended: a non-root deploy user with `docker` group
  membership and a writable home directory.
- At least 4 GB RAM and 10 GB free disk space for the running stack.

---

## 1. First-time setup (one-off)

### 1a. Clone the fork

```bash
git clone <your-internal-git-url> thingsboard
cd thingsboard
```

### 1b. Create the env file

```bash
cp deploy/.env.example .env
# Edit .env and fill in real values for TB_DEPLOY_HOST / TB_DEPLOY_USER /
# TB_DEPLOY_KEY (preferred) or TB_DEPLOY_PASSWORD, etc.
#
# .env is gitignored — keep it out of version control.
```

> **Where credentials live.** Only in `.env` (on your Mac) and the running
> process's environment. They never appear in committed files or commit
> messages.

### 1c. Set up SSH access to the deploy host (recommended)

```bash
# generate a key if you don't have one
ssh-keygen -t ed25519 -f ~/.ssh/tb_deploy_key -N ''

# copy it to the deploy host
ssh-copy-id -i ~/.ssh/tb_deploy_key container@192.168.69.16

# point the env file at it
echo 'TB_DEPLOY_KEY=~/.ssh/tb_deploy_key' >> .env
```

(Password auth via `TB_DEPLOY_PASSWORD` works as a fallback but is slower and
prompts more.)

### 1d. First-run install flag

The first time the stack starts against an empty Postgres, the platform must
run its DB installer. Edit `deploy/docker-compose.yml`:

```yaml
INSTALL_TB: "true"    # change to true ONCE on a brand-new database
LOAD_DEMO: "false"    # set to "true" if you want demo tenants/data
```

After the first successful boot, flip `INSTALL_TB` back to `"false"` and
redeploy. Leaving it `"true"` is harmless but slows every restart.

---

## 2. Mode A: Production / self-hosted deployment

### 2a. One-shot build + ship

From the repo root on your Mac:

```bash
# Loads .env, builds the deb, builds the Docker image, scp's it,
# loads it on the server, brings the stack up.
./pefeatures-build.sh
```

What it does, step by step (each step is logged with timestamps):

1. `mvn clean install -DskipTests -Dlicense.skip=true` → produces
   `application/target/thingsboard.deb`.
2. `mvn -f msa/tb-node/pom.xml verify -Ddockerfile.skip=false` → builds
   `thingsboard/tb-node:latest`, tagged locally as `thingsboard/tb-node:pe-local`.
3. `docker save | gzip` → writes `build-artifacts/tb-node-pe.tar.gz`
   (~ 600 MB).
4. `scp` the tarball + `deploy/docker-compose.yml` to
   `${TB_DEPLOY_HOST}:${TB_DEPLOY_PATH}`.
5. SSH-runs on the remote:
   `gunzip | docker load` → `docker compose down` → `docker compose up -d`.

On a clean build that takes ~ 5 minutes of Maven + ~ 2 minutes for the
image. Subsequent rebuilds (Java-only changes) are ~ 90 seconds.

### 2a.alt. Manual three-step deploy (build → scp → remote up)

If you'd rather run the phases by hand — for debugging, partial rebuilds,
or because `pefeatures-build.sh` failed mid-way — here are the explicit
commands the script wraps. Run them from the repo root on your Mac:

```bash
# 0. Load env vars (host/user/path/key) into the current shell
set -a; source .env; set +a

# Cross-arch: build a linux/amd64 image even on Apple Silicon
export DOCKER_DEFAULT_PLATFORM=linux/amd64

# 1. BUILD: maven artifact + docker image, then save to a tarball
mvn clean install -DskipTests -Dlicense.skip=true
mvn -f msa/tb-node/pom.xml verify -Ddockerfile.skip=false \
    -Dmain.dir="$(pwd)" -DskipTests -Dlicense.skip=true
docker tag thingsboard/tb-node:latest thingsboard/tb-node:pe-local
mkdir -p build-artifacts
docker save thingsboard/tb-node:pe-local | gzip > build-artifacts/tb-node-pe.tar.gz

# 2. SCP: ship the image + compose file to the remote (key-based SSH)
ssh -i "${TB_DEPLOY_KEY}" "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}" \
    "mkdir -p ${TB_DEPLOY_PATH}"
scp -i "${TB_DEPLOY_KEY}" \
    build-artifacts/tb-node-pe.tar.gz \
    deploy/docker-compose.yml \
    "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}:${TB_DEPLOY_PATH}/"

# 3. REMOTE UP: load the image and recycle the stack
ssh -i "${TB_DEPLOY_KEY}" "${TB_DEPLOY_USER}@${TB_DEPLOY_HOST}" bash <<'REMOTE'
  set -e
  cd "${TB_DEPLOY_PATH:-/home/container/thingsboard}"
  gunzip -c tb-node-pe.tar.gz | docker load
  docker compose down            # safe even if nothing was running
  docker compose up -d
  docker compose ps
REMOTE

# 4. (Optional) Reclaim disk on the Mac after a successful deploy
rm -f build-artifacts/tb-node-pe.tar.gz
docker image rm \
  thingsboard/tb-node:pe-local \
  thingsboard/tb-node:latest \
  thingsboard/tb-node:4.4.0-SNAPSHOT 2>/dev/null || true
```

If you're using password auth instead of a key, swap `ssh -i "${TB_DEPLOY_KEY}" …`
and `scp -i "${TB_DEPLOY_KEY}" …` for `sshpass -e ssh …` and `sshpass -e scp …`,
with `SSHPASS="${TB_DEPLOY_PASSWORD}"` exported in the shell.

**First-time deploy only:** flip `INSTALL_TB: "true"` in
`deploy/docker-compose.yml` before step 3 so the platform creates the DB
schema, then flip it back to `"false"` and `docker compose up -d` again.
Leaving it `"true"` causes `start-tb-node.sh` to re-run the installer on
every restart and the container crash-loops on the duplicate sysadmin row.

### 2b. Smoke test

Open the UI in your browser. On the deploy server use the LAN URL; from the
machine running the stack itself, `http://localhost:8080` works:

```bash
open http://192.168.69.16:8000   # remote stack
# or, if you're on the host running docker compose:
open http://localhost:8080
```

You should see the ThingsBoard login page. Use the following default
credentials:

| Role                  | Email                       | Password   | With demo data | Clean install |
|-----------------------|-----------------------------|------------|:--------------:|:-------------:|
| System Administrator  | `sysadmin@thingsboard.org`  | `sysadmin` | ✅             | ✅            |
| Tenant Administrator  | `tenant@thingsboard.org`    | `tenant`   | ✅             | ❌            |
| Customer User         | `customer@thingsboard.org`  | `customer` | ✅             | ❌            |

> **Clean install:** only the System Administrator exists. Log in, create
> a tenant + tenant admin user, then proceed with PE-feature testing.
> **Demo data:** start the first boot with `LOAD_DEMO: "true"` in the
> compose file to also get the seeded Tenant / Customer accounts above.

REST sanity (no UI):

```bash
curl -s http://192.168.69.16:8000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"tenant@thingsboard.org","password":"tenant"}' | jq .token
```

**Change every default password immediately in production.**

Port map of the deployed stack:

| Port  | Purpose                              |
|-------|--------------------------------------|
| 8000  | HTTP UI + REST API                   |
| 7090  | gRPC (edge / TB-MQTT-gateway)        |
| 1883  | MQTT (plain)                         |
| 8883  | MQTT/TLS                             |
| 5683-5688/udp | CoAP, LwM2M, SNMP            |
| 55432 | Postgres (exposed for dev access)    |

### 2c. PE features quick-check

After login as `tenant@thingsboard.org` the left-nav should now include:

- **Roles** — Advanced RBAC (create custom roles, assign to users).
- **Integration center** → Integrations, Data converters, Device library.
- **Scheduler** — recurring/cron triggered events.
- **Reports** — scheduled PDF/PNG dashboard snapshots.
- **Solution templates** — pre-bundled dashboards + rule chains.
- **Settings → White labeling** — branding (logo, favicon, palette, custom CSS).

Test each via the UI; or hit the REST endpoints directly (see `progress.md`
for paths).

### 2d. Production hardening checklist

These steps are *not* automated — apply them once per deployment.

1. **Change all default passwords.** Sysadmin first, then create real
   tenant + customer accounts and delete the demo ones.
2. **TLS termination.** Front the stack with nginx/Caddy/Traefik on 443 →
   8000. Do not expose 8000 directly to the internet.
3. **Bind Postgres to localhost** if it doesn't need external access.
   Remove the `"55432:5432"` line from `deploy/docker-compose.yml`.
4. **Set a real Postgres password.** Replace `POSTGRES_PASSWORD: postgres`
   in `deploy/docker-compose.yml` with a generated value (e.g.
   `openssl rand -base64 24`). Mirror in `SPRING_DATASOURCE_PASSWORD`.
5. **Restrict MQTT.** Disable plain 1883 if you only need TLS; require
   client certificates via the device-profile transport configuration.
6. **Configure SMTP.** Settings → Outgoing mail → set your real SMTP host;
   without it, password reset and report email won't work.
7. **Report rendering** — handled by the `tb-web-report` sidecar
   container (Node + puppeteer + Chromium), built and shipped by
   `pefeatures-build.sh` alongside tb-node. The compose file wires tb-node
   to it via `REPORTS_SERVER_ENDPOINT_URL=http://tb-web-report:8383`; no
   manual chromium install is required. (Set `SKIP_WEB_REPORT=1` to skip
   building the sidecar; unset `REPORTS_SERVER_ENDPOINT_URL` in the
   compose env to force the in-process Java chromium fork fallback.)
8. **Set up backups.** Daily `pg_dump thingsboard` of the Postgres volume
   (`tb-postgres-data`), retained 30 days, plus logical export of dashboards
   and rule chains via the platform UI.
9. **Logs.** The compose file already caps json-file logs at 10×100 MB. For
   long-term retention, ship to a syslog endpoint or run Loki/Grafana.
10. **JVM heap.** Edit `JAVA_OPTS` in `deploy/docker-compose.yml`. Default
    is unbounded; set `-Xms2g -Xmx2g` for a 4 GB host or `-Xms4g -Xmx4g` for
    8 GB+.

### 2e. Updating production

When you change code:

```bash
# from the repo root, after pulling latest commits
./pefeatures-build.sh
```

The script is idempotent: `docker compose down && up -d` only restarts
changed containers. Data lives in the named volume `tb-postgres-data` and
is preserved across image swaps.

Schema migrations from new PE-feature tables are applied automatically by
`application/src/main/data/upgrade/lts/schema_update.sql` (idempotent
`CREATE TABLE IF NOT EXISTS …` statements).

### 2f. Rolling back

```bash
# locally: rebuild against the previous commit
git checkout <previous-commit>
./pefeatures-build.sh

# or on the server: switch back to the prior image tag
ssh container@192.168.69.16 'cd /home/container/thingsboard && \
   docker tag thingsboard/tb-node:pe-local-previous thingsboard/tb-node:pe-local && \
   docker compose up -d'
```

(If you want hands-off rollback, tag images with the commit SHA in
`TB_IMAGE_TAG` for each build: `TB_IMAGE_TAG=thingsboard/tb-node:pe-$(git rev-parse --short HEAD)`.)

---

## 3. Mode B: Local development on Mac

For tight iteration, run only the **app** on your Mac and let it talk to
Postgres on the deploy server. UI hot-reload, fast Java rebuilds, no
re-deploy round-trip.

### 3a. Make sure the remote stack is running

```bash
./pefeatures-build.sh   # one-time, or whenever your image is stale
```

You only need Postgres really, but bringing the whole stack up keeps
multiple developers sharing one consistent set of test data.

### 3b. Run the app locally

```bash
./deploy/dev-run.sh
```

What it does:

1. Loads `.env` (or `~/.tb-deploy.env` if present).
2. Starts `mvn -pl application spring-boot:run` with these JVM args:
   - `spring.datasource.url=jdbc:postgresql://192.168.69.16:55432/thingsboard`
   - `server.address=127.0.0.1` (LAN-safe)
   - `queue.type=in-memory` (no Kafka needed for dev)
3. Hot UI changes: in a second terminal, `cd ui-ngx && yarn start` and
   open <http://localhost:4200>. The dev server proxies `/api` calls to
   your locally-running Spring Boot (`localhost:8080`).

### 3c. Common dev tasks

| Task                              | Command                                                                  |
|-----------------------------------|--------------------------------------------------------------------------|
| Compile Java only                 | `mvn -pl common/data,common/dao-api,dao,application -am compile -DskipTests` |
| Run Java tests for one module     | `mvn -pl dao test`                                                       |
| Run UI dev server                 | `cd ui-ngx && yarn start`                                                |
| Run UI tests                      | `cd ui-ngx && yarn test`                                                 |
| Reset remote Postgres             | `ssh container@192.168.69.16 'docker volume rm -f tb-postgres-data && cd /home/container/thingsboard && docker compose up -d'` |
| Tail tb-node logs on the server   | `ssh container@192.168.69.16 'docker logs -f --tail=200 tb-node'`        |
| Inspect a running container       | `ssh container@192.168.69.16 'docker exec -it tb-node bash'`             |

### 3d. Adding a new PE-feature page

Follow the pattern used in
`ui-ngx/src/app/modules/home/pages/admin/pe/`:

1. Add the service in `ui-ngx/src/app/core/http/<feature>.service.ts`.
2. Add `<feature>.component.{ts,html}` next to the existing PE pages.
3. Register the component in `admin.module.ts`.
4. Add a route in `admin-routing.module.ts`.
5. Add a `MenuId.pe_<feature>` entry and slot it into the TENANT_ADMIN tree
   in `core/services/menu.models.ts`.

---

## 4. Feature operation notes

### Scheduler
- UI at **Scheduler** in the left nav.
- Backend cron resolver lives in `ScheduleEvaluator`; poller runs every
  10 s by default (configurable via `scheduler.poll.delay-ms`).
- Multi-node deployments: the current poller doesn't take a row lock — if
  you run more than one tb-node container, edit
  `ScheduledEventServiceImpl.findDueEvents` to add
  `FOR UPDATE SKIP LOCKED`.

### Reports
- UI at **Reports**. Each report points at a dashboard UUID + optional
  state ID, lists email recipients, and picks PDF or PNG.
- "Run now" generates the report synchronously. For scheduled runs, also
  create a Scheduler event of type `GENERATE_REPORT` with
  `{"reportId": "<report-uuid>"}` in its configuration JSON.
- **Renderer:** by default tb-node delegates to the `tb-web-report`
  sidecar (PE-equivalent Node + puppeteer microservice on port 8383).
  Auth handoff: tb-node mints a tenant-admin access JWT via
  `JwtTokenFactory`, hands it to the microservice, and the headless
  browser stuffs it into `localStorage` before navigating to the
  dashboard route. Falls back to an in-process chromium fork if
  `REPORTS_SERVER_ENDPOINT_URL` is unset.
- Requires **SMTP** configured at Settings → Outgoing mail.

### Roles (Advanced RBAC)
- UI at **Roles** in the left nav.
- Two role types per PE:
  - **GENERIC** — resource → operation matrix applied platform-wide for
    users assigned this role.
  - **GROUP** — flat operation list, applied to entities surfaced via
    `entity_group_permission` rows.
- Assignment endpoint: `POST /api/user/{userId}/role/{roleId}`.
- Permission enforcement: `DefaultAccessControlService.hasRoleGrant` runs
  after the standard authority check fails — i.e. roles *grant* extra
  access, they don't *restrict*.

### Integrations
- UI at **Integration center → Integrations**. PE 4-step wizard rendered
  as collapsible sections (Basic Settings, Uplink Converter, Downlink
  Converter, Connection Settings).
- HTTP integrations expose `POST /api/noauth/integrations/http/{routingKey}`
  with optional `X-Integration-Secret` header.
- MQTT integrations connect outbound to a remote broker (AWS IoT, Azure
  IoT Hub, ChirpStack, TTN, Loriot — all MQTT-bridged).
- Lifecycle: `IntegrationManager` starts adapters on tb-node boot and on
  save/delete REST events. Restart tb-node if an adapter wedges.

### Data converters
- UI at **Integration center → Data converters**.
- Uplink/downlink JavaScript functions; in-browser test runner uses the
  browser's JS engine for previews. Production execution happens in the
  rule engine via CE's existing `JsInvokeService` (sandboxed, clustered).

### Device library (Payload codecs)
- UI at **Integration center → Device library**.
- 10 reference codecs are seeded automatically on first boot
  (`PayloadCodecCatalogSeeder`).
- Click a codec → **Copy decoder** → paste the function into a device
  profile's script transformer.

### Solution templates
- UI at **Solution templates**.
- The seeder ships an empty catalog out of the box — add system templates
  via `POST /api/solution/template` (SYS_ADMIN required).
- Install creates device profiles + rule chains + dashboards in the
  current tenant and records the result in `solution_install_record`.

### White-labeling
- UI at **Settings → White labeling**.
- Scope: System (SYS_ADMIN) and Tenant (TENANT_ADMIN). Tenant overrides
  fall back to system; both apply at app bootstrap via
  `WhiteLabelingService` in the Angular app.
- Configurable: logo URL, logo height, favicon URL, app title, primary
  color, accent color, custom CSS, help link base, enable-help-links flag.

---

## 5. Troubleshooting

### Maven build fails with `JDK 25 required`
Install OpenJDK 25 via `brew install openjdk@25` and set
`JAVA_HOME=$(/usr/libexec/java_home -v 25)`.

### `docker save` produces a tarball but `docker load` fails on the server
Most often a Linux/amd64 vs Mac/arm64 mismatch. Force x86_64 in the build:
```bash
DOCKER_DEFAULT_PLATFORM=linux/amd64 ./pefeatures-build.sh
```

### `connection refused` on port 8000 after deploy
- `ssh ... 'docker compose ps'` — is `tb-node` actually running?
- `ssh ... 'docker logs --tail=200 tb-node'` — look for Java stack traces.
- Most common cause on first deploy: `INSTALL_TB=false` against an empty
  Postgres. Flip to `"true"` once and redeploy.

### `PSQL ERROR: relation "scheduled_event" does not exist`
The LTS upgrade migration didn't run. Either:
- Set `INSTALL_TB=true` on this deploy and restart, or
- Apply the deltas manually:
  ```bash
  ssh ... 'docker exec -u postgres tb-postgres psql -d thingsboard' \
    < application/src/main/data/upgrade/lts/schema_update.sql
  ```

### Reports endpoint returns "Headless render failed: command not found"
Falling back to in-process chromium because `REPORTS_SERVER_ENDPOINT_URL`
is unset or the tb-web-report sidecar isn't reachable. Either:
- Confirm the sidecar is up: `ssh ... 'docker compose ps tb-web-report'`,
  and that tb-node's env has `REPORTS_SERVER_ENDPOINT_URL=http://tb-web-report:8383`.
- Or install chromium in tb-node to make the fallback work:
  `docker exec -u root tb-node apt-get update && apt-get install -y chromium`.

### tb-web-report renders a blank login page instead of the dashboard
The render JWT failed to take effect. Check:
- `docker logs tb-web-report` for navigation errors.
- The tenant has at least one TENANT_ADMIN user (ReportRunner mints the
  token as the tenant's first admin; without one it falls back to an
  unauthenticated page load).
- The `REPORT_BASE_URL` resolves from inside the tb-web-report container
  to the same host that issued the JWT — must be the docker-compose
  service name `thingsboard-ce`, not `localhost`.

### MQTT integration shows `enabled` but no messages flow
- Check `docker logs tb-node` for `MQTT integration <name> connected to …`.
  If absent, broker creds are wrong.
- Verify the `topics` array in the integration's configuration JSON;
  default subscribes to `#` (everything) which some brokers reject.

### "Routing key already exists" on save
Routing keys are globally unique (across all tenants). Either delete the
conflicting integration or let the system generate a UUID by clearing the
field on save.

### Login page is unbranded after white-label save
The login page fetches `/api/noauth/whiteLabel/loginWhiteLabelParams`
once at app bootstrap. Hard-reload (Cmd-Shift-R) to bypass cache.

---

## 6. Reference: env vars

Defined in `deploy/.env.example`. Pull the full list from there.

| Variable                       | Used by                       | Default                                            |
|--------------------------------|-------------------------------|----------------------------------------------------|
| `TB_DEPLOY_HOST`               | build script                  | (required)                                         |
| `TB_DEPLOY_USER`               | build script                  | (required)                                         |
| `TB_DEPLOY_PATH`               | build script                  | (required)                                         |
| `TB_DEPLOY_KEY`                | build script (preferred auth) | `~/.ssh/id_ed25519`                                |
| `TB_DEPLOY_PASSWORD`           | build script (sshpass fallback) | none                                             |
| `TB_IMAGE_TAG`                 | build script                  | `thingsboard/tb-node:pe-local`                     |
| `SKIP_BUILD`, `SKIP_DEPLOY`    | build script                  | `0`                                                |
| `SPRING_DATASOURCE_URL`        | dev-run + container env       | `jdbc:postgresql://<host>:55432/thingsboard`       |
| `SPRING_DATASOURCE_PASSWORD`   | dev-run + container env       | `postgres`                                         |
| `TB_SERVICE_ID`                | container env                 | `tb-ce-node`                                       |
| `SCHEDULER_POLL_DELAY_MS`      | container env                 | `10000`                                            |
| `REPORTS_SERVER_ENDPOINT_URL`  | container env                 | `http://tb-web-report:8383` (unset → use local chromium fork) |
| `REPORT_CHROME_BINARY`         | container env (fallback only) | `chromium`                                         |
| `REPORT_BASE_URL`              | container env                 | `http://thingsboard-ce:8080`                       |
| `INSTALL_TB`                   | container env                 | `false` (set `true` on first run only)             |
| `LOAD_DEMO`                    | container env                 | `false`                                            |

---

## 7. Where to look when things break

| Symptom                                    | First place to check                                        |
|--------------------------------------------|-------------------------------------------------------------|
| Build script fails on Mac                  | `mvn` output above the `FAILED` line                        |
| Container won't start                      | `docker logs --tail=300 tb-node`                            |
| API returns 401 / 403 unexpectedly         | `application/.../security/permission/DefaultAccessControlService.java` |
| Scheduled event never fires                | `docker logs tb-node | grep SchedulerPoller`                |
| Integration uplink not reaching rule chain | Enable debug mode on the integration; check `IntegrationContext` logs |
| Report PDF blank                           | `docker exec tb-node which chromium` — must resolve         |
| Postgres slow                              | `ssh ... 'docker exec tb-postgres psql -U postgres -d thingsboard -c "SELECT * FROM pg_stat_activity;"'` |
| White-label change didn't apply            | Browser DevTools → Network → confirm `/api/noauth/whiteLabel/...` returns your values; hard-refresh |

---

## 8. Going further

- **Add another integration type** — implement `IntegrationAdapter`,
  register in `IntegrationManager.createAdapter`. See
  `MqttIntegrationAdapter` and `HttpIntegrationAdapter` for the patterns.
- **Add more codecs** — extend `PayloadCodecCatalogSeeder.SEED`. Each
  entry is a self-contained JS decoder. Aim for vendor + model + category
  consistency.
- **Add a Solution Template** — `POST /api/solution/template` with a JSON
  bundle (the `bundle` field shape is documented on `SolutionTemplate.java`).
- **High availability** — out of scope for v1; the design notes in
  `progress.md` flag the multi-node hardening required (poller row-locks,
  integration adapter ownership).

---

When you finish a session, update `handoff.md` and `progress.md` and
commit. See `CLAUDE.md` for commit-message conventions.
