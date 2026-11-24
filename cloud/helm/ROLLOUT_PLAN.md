# CNE rebase — rollout plan (2026-11-24)

**Source branches**: `master-pre-cne-rebase-2026-11-24` (backup pinning pre-rebase `origin/master`) · `cne-rebase-2026-11-24` (work branch off `cne/master`).

**Headline**: of the 17 fork-custom commits that lived on `arthurburle/liferay-portal:master`, **3 landed as useful commits** on the work branch; **14 were dropped** because upstream (`cne/master`) either absorbed the same idea with a refined shape, reorganised the chart layout, or implemented the equivalent differently. Per Arthur's directive — *"tudo q eles implementaram de forma diferente da gente, fica da forma deles"* — we align with upstream wherever they chose a different path.

This plan tracks three things: the migrated commits, the dropped commits (and whether something on the ai-hub side must move to compensate), and the rollout order across the three Liferay ApplicationSets (`liferay`, `client-extension`, `liferay-infrastructure`) and three envs (dev / uat / prd).

## Scope tag vocabulary (used inline across templates)

| Tag | When to use | Rollout rule |
|---|---|---|
| `ENV_SCOPED_LOW` | Namespaced resource, drop-in replacement, no cross-env blast radius. | Dev → uat → prd per ApplicationSet; no special gates. |
| `ENV_SCOPED_MEDIUM` | Namespaced but spans >1 chart or has coupled values. | Dev first + render check + runtime smoke, then uat, then prd. |
| `ENV_SCOPED_CRITICAL` | Namespaced resource, but a user-visible or product-critical feature depends on a values migration or the knob being set per env. | Migrate values in the ai-hub deploy repo per env *before* pointing that env at the new chart version. |
| `CLUSTER_SCOPED_MEDIUM` | Touches cluster-visible resources (WI SA, PodMonitoring, cluster-level NP) but not foundational infra. | Full dev soak; then uat with rollback window; prd last. |
| `CLUSTER_SCOPED_CRITICAL` | Crossplane composition, ECK StackConfigPolicy, GCS bucket (DR data), GCP LB policies the live LB depends on. | Dev + full validation + explicit backup of the live chart tag before uat. Prd only after full dev+uat soak and backup confirmation. |

## ApplicationSet migration order

The ai-hub cluster hosts three Liferay ApplicationSets per env (dev / uat / prd), **plus** one independent `overlays` ApplicationSet that we do not migrate as part of this rebase. Because chart versions are settable per ApplicationSet, each one migrates independently.

**Recommended order**:

1. **`liferay` ApplicationSet** (default + gcp charts) — env-scoped changes only (LCI-5246 base + review). Safe first mover.
2. **`client-extension` ApplicationSet** — env-scoped change (LCI-5403 client-extension NP for GKE L7). Depends on no other migration.
3. **`liferay-infrastructure` ApplicationSet** — nothing to migrate from our custom commits, but the chart itself ships upstream features that replace ours (elasticsearch-exporter, search-snapshots, etc). Move last so any regression in cluster-level resources lands with a clean env already observed.

**Overlays ApplicationSet**: do not migrate as part of this rebase. None of our 17 custom commits touched the overlays chart.

**Per env**: dev first with 72h soak, then uat with 48h soak, then prd. Every migration is rollbackable to the previous chart version by reverting the ApplicationSet's chart `targetRevision`.

## Backups to take before any prd move

1. The branch `master-pre-cne-rebase-2026-11-24` on this fork, already pinned at `origin/master` HEAD (`38a7741`). Push to a remote-hosted copy before deleting the local repo.
2. Chart tag snapshots of the currently-published `liferay-gcp` OCI artefact in `external-assets-nonprd` with suffix `pre-cne-rebase-20261124` (reference pattern in `~/liferay/charts/scripts/publish-helm-charts.sh`). Snapshot each of the three charts (`default`, `gcp`, `client-extension`) and `liferay-infrastructure` before uat touches them.
3. The current `ai-hub` deploy repo HEAD of each env's `values` file captured in an auditable branch so the values migrations below can be reverted atomically.

## Fate of the 17 custom commits

### Phase 1 — `liferay` + `client-extension` AppSets (env-scoped)

| SHA (orig) | LCI | Title | Fate | Notes |
|---|---|---|---|---|
| 95906be | 5246 | Disable envoy + enable gateway-api | **LANDED (cherry-pick, conflicts resolved)** | HSTS filter ordering + `weight: 1` + `sessionPersistence` all taken from CNE's refinement. Our gate `gke.enabled` still wraps `timeouts` only (not `sessionPersistence`). HSTS default is CNE's conservative one (`enabled: false`); the ai-hub deploy must override to `enabled: true, value: "max-age=31536000; includeSubDomains; preload"` per env. gcp-infrastructure/{_helpers,gateway}.yaml edits were against folders upstream deleted — see Phase 2 note. |
| f8d01e4 | 5246 | Reviewer suggestions | **LANDED (cherry-pick)** | Adds `gcpgatewaypolicy.yaml`, refines health check to use readinessProbe. HSTS default again CNE's. |
| adb3b76 | 5832 | Overlay init-container delete old files | **DROP** (shape rewrite) | Upstream reimplemented `overlay-sync.sh` as a 3-arg script (`provider-type from-path into-path`) with no manifest/prune concept. **Risk**: overlay bucket may accumulate stale files across redeploys in ai-hub. ⚠️ If this becomes real, port a prune pass as a separate PR on top of CNE's script. See `ENV_SCOPED_MEDIUM` risk section below. |
| 5878 | 5878 | transportTimeoutMs | **DROP** (superseded) | CNE exposes `tomcat.sessionReplication.transportTimeoutMs` (with `int64` cast). **ai-hub values migration**: `cluster.sessionReplication.transportTimeoutMs` → `tomcat.sessionReplication.transportTimeoutMs`. `ENV_SCOPED_CRITICAL`. |
| 5884 | 5884 | DXP remote IP valve | **DROP** (superseded) | CNE exposes `tomcat.remoteIP.*` (note capitalisation: `remoteIP` not `remoteIp`; `remoteIPHeader` not `remoteIpHeader`). **ai-hub values migration**: `network.remoteIp.enabled/internalProxies/protocolHeader/remoteIpHeader` → `tomcat.remoteIP.enabled/internalProxies/protocolHeader/remoteIPHeader`. `ENV_SCOPED_CRITICAL` — Arthur: *"a questao dos IPs propagarem o do uso do client ainda eh necessaria"*. Missing this migration means X-Forwarded-For stops working and clients see the GKE LB IP instead of the real client IP. |
| 4cb5f2d | 5403 | Default NP for gateway-api | **LANDED (manual rewrite)** | 1 new commit: `LCI-5403 ... (rebased onto cne/master 2026-11-24)`. default/_helpers + default/_statefulset gain the helper and NP ingress block gated on `gke.enabled`. client-extension chart gets a new flat `networkpolicy.yaml` (shape change: single-ext-per-chart, no more `range .Values.extensions`) + `network.gke.enabled` values flag. `ENV_SCOPED_LOW`. |
| ec3456c | 5346 | Probes to extensions | **DROP** (CNE has equivalent) | CNE's `_podspec.tpl` exposes `.Values.workload.{liveness,readiness,startup}Probe` as single-ext knobs. Our per-ext `dig` pattern doesn't fit the new shape. ai-hub deploy sets probes via `workload.*Probe`. |
| 4601b89 | 5559 | Auto-restart feature | **DROP** (shape rewrite) | Our changes were against the old cx shape (`templates/workload/_helpers.tpl`, `templates/workload/deployment.yaml`). CNE rebuilt both as `_podspec.tpl` + flat `deployment.yaml`. Auto-restart in ai-hub must be verified against the new shape; if missing, re-port as a focused PR. |
| f942dc6 | 5580 | RBAC + ext-init-metadata for OAuth2 factory writeback | **DROP** (model change) ⚠️ **NEEDS VALIDATION BEFORE UAT**. The CNE `rolebinding.yaml` uses `client-extension-delivery-cluster-role` (ClusterRole) + `operatorServiceAccount` (dxp-operator). Our `grantLiferayAccess` flag that granted ConfigMap verbs to the Liferay SA directly is **not retained**. **Before uat**: verify the delivery ClusterRole has `configmaps: create/get/list/patch/update/watch` in the extension namespace AND that the dxp-operator SA (not the Liferay SA) is what projects OAuth2 clientId/clientSecret through `PortalK8sConfigMapModifier`. If either answer is no, the factory writeback flow silently breaks and OAuth2 provisioning is broken. `CLUSTER_SCOPED_CRITICAL`-level validation even though the RBAC is namespaced. |
| 91355f3 | 5744 | Chart fixes (overlay+cx) | **DROP** (sub-features absorbed or shape-incompatible) | HTTPRoute `backendRefs` defaults (sub-feature 3): already in CNE. `dxp-metadata.yaml` deletion (sub-feature 1): CNE also removed, agent-owned ConfigMap wins. Ext-init-metadata via Secret (sub-feature 4): CNE kept ConfigMap mount. Ext-provision CM (sub-feature 2): depended on shape multi-ext — not portable. |
| cfc08f0 | 5332 | Springboot + crawlers | **DROP** (shape rewrite) | Our 17 cx template files are all against the old hierarchy. Flat shape in CNE replaces them all. See the "Features at risk" section for what sub-pieces may be at risk in ai-hub. |
| 0b8a21a | 5425 | Full-deploy fluidity | **DROP** (shape rewrite + schema adopted) | Overlay chart schema (Phase 1) absorbed by CNE's `overlay.copy` schema. Cx scripts (`helpers.sh`, `overlay-sync.sh`, `wait-for-liferay.sh`) sit in the old hierarchy — not portable. |

### Phase 2 — `liferay-infrastructure` AppSet (cluster-scoped)

| SHA (orig) | LCI | Title | Fate | Notes |
|---|---|---|---|---|
| 7102ee8 | 5265 | Export search metrics via code | **DROP** (CNE has) | `infrastructure/templates/elasticsearch-exporter.yaml` + `tests/elasticsearch-exporter_test.yaml`. **ai-hub validation**: check that `infrastructure.values.yaml` schema supports the exporter knobs our values used. |
| 00e917e | 5266 | Manage search snapshots via code | **DROP** (CNE has) | `infrastructure/templates/search-snapshots.yaml` + `infrastructure/scripts/search-snapshots.sh` + `liferayinfrastructure-composition/45-search-snapshots.gotmpl`. **ai-hub validation**: GCS bucket name scheme, retention policy, ECK StackConfigPolicy shape — verify CNE's defaults match or override via values. |
| 295c59a | 5266 | Snapshot improvements + bug fixes | **DROP** (CNE has) | Same path. |
| e3a6b4b | 5347 | JWT SA token for Vertex AI search | **DROP** (CNE has) | `infrastructure/templates/elasticsearch-service-account.yaml` is the CNE equivalent. **ai-hub validation**: confirm the Workload Identity binding uses the same GSA email format and the Vertex AI permissions (`aiplatform.endpoints.predict`) still bind. |
| 5964538 | 5893 | prd database size (Enterprise+ / REGIONAL) | **DROP** (CNE derives) | CNE's `gcp-infrastructure-provider/liferayinfrastructure-composition/20-sql.gotmpl` derives `edition` from `database.instanceType` prefix (`db-perf-optimized-*` → ENTERPRISE_PLUS) and `availabilityType` from `database.highAvailability` (true → REGIONAL). **ai-hub values migration prd env**: set `database.highAvailability: true` AND `database.instanceType: db-perf-optimized-*`. These replace the explicit `database.edition: ENTERPRISE_PLUS` / `availabilityType: REGIONAL` our fork exposed. `CLUSTER_SCOPED_CRITICAL`. |

## Dropped-but-need-action punch list

Before each env flips to the rebased chart, verify the ai-hub deploy carries these migrations. Order matters: migrate values first, then point the ApplicationSet at the new chart version.

| # | Env impact | Values / config change in the ai-hub deploy repo | Tag |
|---|---|---|---|
| 1 | All envs (`liferay` AppSet) | `cluster.sessionReplication.transportTimeoutMs` → `tomcat.sessionReplication.transportTimeoutMs` | `ENV_SCOPED_CRITICAL` |
| 2 | All envs (`liferay` AppSet) | `network.remoteIp.enabled/internalProxies/protocolHeader/remoteIpHeader` → `tomcat.remoteIP.enabled/internalProxies/protocolHeader/remoteIPHeader` (capitalisation changed) | `ENV_SCOPED_CRITICAL` |
| 3 | All envs (`liferay` AppSet) | `network.hsts.enabled: true, value: "max-age=31536000; includeSubDomains; preload"` must be set explicitly if the previous aggressive HSTS was desired. CNE default is conservative. | `ENV_SCOPED_MEDIUM` |
| 4 | prd (`liferay-infrastructure` AppSet) | `database.edition / availabilityType` (explicit) → `database.highAvailability: true` + `database.instanceType: db-perf-optimized-*` (derived) | `CLUSTER_SCOPED_CRITICAL` |
| 5 | All envs (`client-extension` AppSet) | Per-ext probes (if used) → `workload.livenessProbe / readinessProbe / startupProbe` (single-ext per release). | `ENV_SCOPED_LOW` |
| 6 | All envs (`client-extension` AppSet) | Our `grantLiferayAccess` was dropped. Confirm that `client-extension-delivery-cluster-role` + `operatorServiceAccount.*` provide the ConfigMap permissions the OAuth2 factory needs. If not, the factory writeback breaks silently. | `CLUSTER_SCOPED_CRITICAL` validation |

## Features at risk (need validation, may need a follow-up PR)

These are features our fork carried that have no clean replacement in the CNE shape. They may still be live in ai-hub prd; dropping them in the rebase means ai-hub will silently lose them unless we validate or reintroduce.

1. **Overlay bucket prune (LCI-5832)** — stale files may accumulate across redeploys. Rollout check: inspect `/opt/liferay/overlay/` on a dev pod after N redeploys; if growth is visible, port a prune step as a focused PR on top of CNE's script. `ENV_SCOPED_MEDIUM`.
2. **client-extension `grantLiferayAccess` (LCI-5580)** — the entire OAuth2 factory writeback flow for ai-hub depended on the Liferay SA having ConfigMap write access in the extension ns. CNE delegates to a ClusterRole + operator SA. Validate end-to-end OAuth2 provisioning in dev before uat. `CLUSTER_SCOPED_CRITICAL` validation. See #6 above.
3. **client-extension auto-restart (LCI-5559)** — CNE did not port the specific auto-restart mechanic. If ai-hub relies on it, re-port as a follow-up focused PR against the new flat shape. `ENV_SCOPED_MEDIUM`.
4. **client-extension namespace NetworkPolicy (LCI-5332 sub-feature)** — CNE doesn't ship a namespace-level deny-by-default NP in `client-extension`. If ai-hub relies on it for isolation, re-port it as a separate NP template. `CLUSTER_SCOPED_MEDIUM` (namespace isolation leakage risk).
5. **client-extension namespace ResourceQuota (LCI-5332 sub-feature)** — CNE doesn't ship a ResourceQuota template. If ai-hub relies on it for cost control, re-port. `ENV_SCOPED_LOW`.
6. **LCI-5246 gcp-infrastructure/_helpers.tpl + gateway.yaml edits** — these edits added `liferay.gateway.isEnvoy` helper and `isEnvoy` gate in the gateway YAML. Upstream deleted the folder, and we did not port the equivalent into `infrastructure/`. CNE's `infrastructure/templates/gateway.yaml` is envoy-hardcoded. If GKE L7 (`gke.enabled=true`) at the infra level becomes required, port these helpers into `infrastructure/templates/` as a Phase 2b. `ENV_SCOPED_MEDIUM`.

## Rollback

Per env, rollback is: revert the ApplicationSet `targetRevision` to the pre-rebase chart tag captured in step 2 of "Backups to take". If values were migrated per the punch list above, also revert those value changes atomically (this is why we captured ai-hub deploy HEAD per env).

If we need to abandon the rebase on this fork entirely: `git reset --hard master-pre-cne-rebase-2026-11-24` (destructive — confirm no work in flight). A safer fallback is to keep the two branches side by side and simply never point ArgoCD at the rebased charts.

## Validation checklist per env (dev → uat → prd)

For each ApplicationSet × env tuple:

- [ ] Backup the current chart tag.
- [ ] Apply the ai-hub values migrations relevant to this AppSet (punch list items above).
- [ ] Render the chart with the new version using the env's values (`helm template` offline).
- [ ] Diff the rendered output against the previous render — verify any resource marked `_CRITICAL` is accounted for.
- [ ] Point the ApplicationSet at the new chart tag. Observe the sync in Argo.
- [ ] Runtime smoke — in dev, cover: portal boot, OAuth2 extension provisioning, extension reaches Liferay, licensing active, search healthy, HSTS header on a response, RemoteIpValve forwarding client IP, GKE L7 healthchecks passing.
- [ ] Wait the env's soak window (dev 72h, uat 48h, prd per release train).
- [ ] Only then proceed to the next env.
