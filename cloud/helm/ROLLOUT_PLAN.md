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

## Cluster-scoped delta: chart currently deployed on ai-hub vs the target

The `liferay-{gcp,gcp-infrastructure,client-extension}` OCI charts the ai-hub ApplicationSets point at today are pinned at fork commit **`12fd4aef2313b` (LCI-5346 overlay chart schema Phase 1, published 2026-07-30/31)**. The rebased target (this work branch) sits on `cne/master` HEAD (2026-11-24) plus the 5 commits above. Between those two points **cne/master advanced roughly 4 months**, which means the migration carries far more than the 5 commits that show up in this PR's diff. This section catalogues the **cluster-scoped** risks in that larger delta so the rollout can be sequenced defensively.

Scope was derived by `git diff 12fd4aef2313b..HEAD -- cloud/helm/` and filtered to resources that live outside a single namespace or that take effect cluster-wide.

### `CLUSTER_SCOPED_CRITICAL`

1. **`CustomResourceDefinition` changes on `dxp-operator`** — specifically `cx.liferay.com_clientextensions.yaml` and `licensing.liferay.com_liferayenvironments.yaml` (controller-gen v0.18.0). Helm does **not** reapply resources under a chart's `crds/` directory on upgrade (see `project_dxp_operator_crd_divergence`). The in-cluster CRD stays at the deployed version until it is reapplied manually. If the new chart renders `ClientExtension` / `LiferayEnvironment` resources that use fields the deployed CRD does not yet validate, they land as `Invalid` — ArgoCD syncs show OutOfSync forever. **Mitigation**: apply the two CRDs from the target chart manually under `kubectl apply --server-side` **before** pointing any ApplicationSet at the new chart, in that order: dev → uat → prd. Check `kubectl explain` output against existing live `LiferayEnvironment` resources first.

2. **`ValidatingAdmissionPolicy` + `ValidatingAdmissionPolicyBinding` on `dxp-operator`** — the chart ships two VAPs (`validating-admission-policy.yaml` and `validating-admission-policy-single-environment.yaml`) that were absent in the deployed version. VAPs take effect **retroactively** on the next admission of an existing resource (edits, label patches, Argo sync) and can reject it. **Mitigation**: render the target chart and read both VAPs. For each, dry-run what happens if an existing in-cluster `LiferayEnvironment` / `ClientExtension` were resubmitted under the new policy. Only roll to dev once the VAPs accept the shape of the resources already in prd.

3. **Crossplane `Composition` schema changes in `gcp-infrastructure-provider`** — `20-sql.gotmpl` (CloudSQL instance), `40-storage.gotmpl` (GCS doclib bucket), `45-search-snapshots.gotmpl` (GCS snapshots bucket, new), `35-elasticsearch.gotmpl` (new), `60-kms.gotmpl`, `70-backup.gotmpl`, `99-composition-context.gotmpl`, `99-status.gotmpl`, and the `liferayoverlay-composition/50-overlay.gotmpl`. A composition change that renames or removes a `forProvider` field triggers Crossplane to interpret the live resource as drift and attempt to reconcile — which for CloudSQL and GCS buckets can mean **destructive recreation**. **Mitigation**: before any env flips, render the target compositions against a dev `LiferayInfrastructure` claim and diff `kubectl get managed` output (`DatabaseInstance`, `Database`, `Bucket`, `ServiceAccount`, `ServiceAccountIAMMember`). If any field change would force a non-updateable property, pause the Crossplane reconciler (`crossplane.io/paused: "true"` annotation on the live XR) before upgrading and then rebase the live resource onto the new composition manually. CloudSQL deletion is irreversible on this timescale.

4. **`gcp-infrastructure` chart deletion** — the entire `cloud/helm/gcp-infrastructure/` chart no longer exists upstream. Its functionality moved to `cloud/helm/infrastructure/`. The two charts render **different resource names and labels**: if the AppSet simply flips from `liferay-gcp-infrastructure` to `liferay-infrastructure`, ArgoCD will treat everything the old chart owned (ECK `Elasticsearch`, `StackConfigPolicy` for snapshots, `ServiceAccount` for WI, `ExternalSecret` for license/certificate, `Bucket`/`BucketIAMMember`) as orphan and prune it — **search cluster disappears, snapshot bucket IAM detaches, cert externalsecret drops**. **Mitigation**: set `preserveResourcesOnDeletion: true` on the current `liferay-gcp-infrastructure` AppSet before deleting it; stand up the new `liferay-infrastructure` AppSet alongside; after both are reconciled, delete the orphaned old AppSet. Alternatively, map the new chart's resource names/labels to match the old ones so Argo sees them as updates instead of create+prune.

5. **`GatewayClass` resource is now managed by the chart** — the deployed version did not render one; the target renders `kind: GatewayClass` and binds `gatewayClassName: {{ $gateway.name }}-gateway-class` onto the `Gateway` resources. Creating a new GatewayClass does not disrupt the existing Gateway on its own, but **any Gateway whose `gatewayClassName` changes under this upgrade is reconfigured by the gateway controller** — that reconfiguration can cause brief LB downtime and in-flight requests to drop. **Mitigation**: inspect the live `Gateway` resources before upgrade. If the current `gatewayClassName` differs from what the target chart renders, plan a maintenance window for that env before the upgrade.

### `CLUSTER_SCOPED_MEDIUM`

6. **`ClusterRole` / `ClusterRoleBinding` on `dxp-operator`, `gcp-infrastructure-provider`, `observability`** — RBAC changes land on first sync. Permissions can be added without risk; permissions **removed** from a role whose subjects actively use them break their workloads silently. **Mitigation**: `kubectl auth can-i --list` on the live operator and provider ServiceAccounts against the target ClusterRole's verbs. If anything drops, decide whether to patch the live role before upgrade or carry a chart values override to reinstate it.

7. **`ClusterPodMonitoring` + `MonitorAlertPrometheusRuleGroup` on `observability`** — new or edited collection endpoints and alert rules. New collection endpoints increase Prometheus write cost; new alerts page oncall. **Mitigation**: before dev, read the diff and silence noisy new alerts via Alertmanager until tuning is done. Check cost impact on the metrics ingestion budget for the project.

8. **`CompositeResourceDefinition` (`XRD`) + `ClusterProviderConfig` + `DeploymentRuntimeConfig` on `gcp-infrastructure-provider` and `platform-components`** — these define the extension surface Crossplane exposes. XRD schema widening is backward-compatible; narrowing (removing a field or changing its type) breaks existing XRs instantly. **Mitigation**: diff the XRD schema — if any existing XR relies on a field that's being narrowed or removed, migrate the XR first.

9. **`ClusterSecretStore` on `platform-components`** — points External Secrets at a backend (GSM/SM). Change in auth config or backend location means `ExternalSecret` resources stop syncing. **Mitigation**: render and compare `spec.provider` against the live `ClusterSecretStore`; confirm the workload identity binding still works.

### Non-cluster but worth watching in the same window

10. **`infrastructure` chart `Namespace` resource** — the new chart may create namespaces that already exist (handled fine with `syncOptions: CreateNamespace=true`) but can also set namespace labels (pod security, environmentId) that conflict with existing labels the deployed state set differently. Argo will update them silently. Low risk, but check Pod Security Admission labels because tightening the mode (e.g. `baseline` → `restricted`) will immediately reject pods on first restart.

11. **`infrastructure` chart `Elasticsearch` resource (ECK CR)** — technically namespaced, but ECK's reconciler responds to changes with rolling pod restarts and can trigger cluster re-bootstrap if `cluster-uuid` or `orchestration-hints` annotations aren't preserved (see `reference_eck_cold_start_bootstrap`). **Mitigation**: before the first `infrastructure` AppSet sync in any env, snapshot ES, and if the ECK CR in the new chart lacks the preservation annotations the current chart has, port them.

## Env-scoped delta: deployed vs target (default, gcp, client-extension)

Same comparison basis — `12fd4aef2313b` vs the work-branch HEAD — on the three env-scoped charts the ai-hub ApplicationSets point at. Lower blast radius than the cluster-scoped section above (every ApplicationSet/env can be rolled independently), but a handful of items here *are* breaking at the values-schema level and will silently misbehave if the deploy repo doesn't migrate its values first.

### `default` chart (48 files changed)

1. **New top-level values keys**: `clientExtensions`, `database`, `licensing`, `marketplace`, `persistence`, `search`, `tomcat`. These are **opt-in surfaces**: existing values files that do not set them keep working. However: the chart now **reads session replication and remote-IP settings from `tomcat.*`**, so the previously-set `cluster.sessionReplication.*` and `network.remoteIp.*` keys land as inert — see rows 1 and 2 of the "Dropped-but-need-action punch list". `ENV_SCOPED_CRITICAL`.
2. **New `required` validation**: `tomcat.remoteIP.internalProxies` is now mandatory whenever `tomcat.remoteIP.enabled: true`. If the ai-hub deploy flips `tomcat.remoteIP.enabled` without also setting `internalProxies`, the chart renders fail-closed. `ENV_SCOPED_LOW` as long as the values migration ships the proxy ranges from the current `network.remoteIp.internalProxies`.
3. **StatefulSet `_statefulset.tpl` refactors** — upstream advanced the HTTPRoute, NetworkPolicy and volume-mount shapes significantly. The LCI-5246 rebase above reconciled the parts we touched, but other blocks (unicast mounts moved to top-level `volumeMounts` per LCI-6109, defaults-cm layout change) have not been exercised against the exact ai-hub values profile. `ENV_SCOPED_MEDIUM`. **Mitigation**: render the target default chart against each env's actual values file and diff against the live `kubectl get sts liferay-default -o yaml` resource. Any field that changes shape (volumeMounts, envFrom, lifecycle hooks) should be reviewed before uat.
4. **LCD-53376 session replication implementation details** — upstream chose `timeout="{{ dig "sessionReplication" "transportTimeoutMs" 300000 (.tomcat | default dict) | int64 }}"` on the Tomcat Channel. The `| int64` cast is new. Any prior override at `cluster.sessionReplication.transportTimeoutMs` still won't take effect post-rebase (see #1). `ENV_SCOPED_CRITICAL` (same as #1, documented here for traceability).
5. **HSTS now configurable through `network.hsts.value`** (as restored by the HSTS re-bind commit on this branch). Chart default is conservative (`enabled: false, value: max-age=31536000`). ai-hub deploy must explicitly set the aggressive HSTS if required. `ENV_SCOPED_MEDIUM`.
6. **New admission policies / ValidatingAdmissionPolicy references** fed from the `dxp-operator` chart (cluster-scoped section #2) will admission-check `LiferayEnvironment` CRs the `default` chart renders. Even though this chart is env-scoped, a VAP mismatch rejects its resources. Cross-ref cluster-scoped #2.

### `gcp` chart (5 files changed)

7. **`global:` top-level values key removed**. Any deploy values file that nests settings under `global.*` for the `gcp` sub-chart will have those settings silently ignored post-rebase. **Mitigation**: grep each env's values for `global:` top-level under the gcp block and rewrite into the new shape. `ENV_SCOPED_MEDIUM`.
8. **Small chart — low risk otherwise**. The 5 files are search-config-cm, Chart.yaml, tests, values. Validate the rendered Liferay search config matches the live ConfigMap post-upgrade; an ES endpoint rewrite here is what reconnects the portal to the (renamed, see cluster-scoped #4) ES cluster. `ENV_SCOPED_MEDIUM`.

### `client-extension` chart (37 files changed) — **BREAKING SCHEMA REWRITE**

The entire values contract was redesigned upstream from a multi-extension array to a single-extension-per-chart model. The ai-hub deploy has to be rewritten end-to-end for this chart before the AppSet can be pointed at the new version.

9. **`extensions: []` array is gone**. The former shape where a single chart release covered N extensions is no longer supported. The new contract is **one chart release per extension**. **Mitigation**: the ai-hub AppSet rendering the client-extension chart needs to become an ApplicationSet-per-extension (or a List generator with one entry per extension). Until that refactor lands, the client-extension AppSet **cannot** be moved to the new chart. `ENV_SCOPED_CRITICAL` — the single highest-impact env-scoped change in this whole delta.
10. **Top-level keys renamed/removed**: gone — `cloudProjectId`, `dxp`, `enabled: true`, `environmentId`, `extensions`, `main`, `projectId`, `rbac`, `resourceQuota`. Added — `clientExtension`, `descriptor`, `fullnameOverride`, `image`, `imagePullSecrets`, `labels`, `nameOverride`, `network`, `operatorServiceAccount`, `podAnnotations`, `podLabels`, `service`, `workload`. The deploy-side values migration map is roughly:

    | Old key (per element of `extensions[]`) | New key (per chart release) |
    |---|---|
    | `name` | `clientExtension.serviceId` + chart release name |
    | `port` | `workload.containerPort` + `service.port` |
    | `image.repository` / `image.tag` | `image.repository` / `image.tag` (no longer under `extensions[].image`) |
    | `route.parent.name`, `route.hostnames` | `network.gatewayName`, `network.hostnames` or `clientExtension.domain` |
    | `route.enabled` | `network.enabled` |
    | `service.enabled` | `service.enabled` |
    | `configJson` | `clientExtension.configs` (map, not JSON string) |
    | `kind` (Deployment/CronJob) | `workload.kind` |
    | `schedule` (CronJob) | `workload.schedule` (now `required` when kind=CronJob) |
    | `livenessProbe`/`readinessProbe`/`startupProbe` on the ext | `workload.livenessProbe`/`readinessProbe`/`startupProbe` |
    | `main.serviceAccountName` + `rbac.grantLiferayAccess` | **gone** — replaced by cluster `client-extension-delivery-cluster-role` + `operatorServiceAccount.*` (see punch list #6; cluster-scoped validation) |
    | `networkPolicy.*`, `resourceQuota.*`, `namespace.*` | **no longer shipped by the chart** — if these are still required, port them as separate PRs on top of the new chart (see "Features at risk" #4, #5) |

    `ENV_SCOPED_CRITICAL`.
11. **Chart no longer renders `templates/namespace/*`** — the namespace NetworkPolicy and ResourceQuota the old shape owned are gone. Per cluster-scoped #10, if the ai-hub depends on namespace-level PSS labels or NetworkPolicy isolation set by this chart, those are no longer produced. `CLUSTER_SCOPED_MEDIUM` (isolation leakage) + `ENV_SCOPED_LOW` (quota accounting).
12. **`workload.kind` required**; `workload.schedule` required when `workload.kind: CronJob`; `network.gatewayName` and `network.hostnames` / `clientExtension.domain` required when `network.enabled: true`. Any deploy that leaves these blank fails chart rendering. `ENV_SCOPED_LOW`.
13. **`image.repository` + `image.tag` are top-level** (not under `extensions[]`). Trivial after the array rewrite, but worth calling out — a missed migration here leaves the extension pod without an image. `ENV_SCOPED_LOW`.
14. **`descriptor` and `operatorServiceAccount`** — new required inputs the deploy repo hasn't provided before. Descriptor describes the extension type (batch/configuration/frontend/service) and must align with how the Portal K8s Agent registers it. `ENV_SCOPED_MEDIUM`.

### Env-scoped rollout sequence (per env, in order)

For each env, when migrating a given env-scoped AppSet:

   a. In the ai-hub deploy repo, rewrite the AppSet's values file to the new schema. For `default` and `gcp` this is additive + the migrations listed above. For `client-extension` this is a near-total rewrite + a potential AppSet-per-extension refactor (#9).
   b. Render the chart offline (`helm template` with the new values) and diff against `kubectl get all -o yaml -n <ns>` from the live env. Resolve any diff that touches a non-safely-updateable field.
   c. Point the AppSet at the new chart tag.
   d. Observe sync in Argo. Watch for VAP rejections (cluster-scoped #2) and chart `fail`/`required` errors.
   e. Runtime smoke per the "Validation checklist" section below.
   f. Soak the env's window before moving to the next.

### Rollout sequencing implied by this delta

The env-scoped rollout order in the earlier section still holds (**liferay → client-extension → liferay-infrastructure**). Inside the **liferay-infrastructure** step for each env, the cluster-scoped delta forces a longer sub-sequence:

   a. Apply the two target CRDs manually (`kubectl apply --server-side`) and confirm existing live CRs validate.
   b. Review and if necessary pre-apply the two VAPs with permissive `failurePolicy: Ignore` (so a rejection logs but doesn't block), observe for 24h, then flip to `Fail` once confident.
   c. Pause Crossplane reconciler on the live `LiferayInfrastructure` XR (`crossplane.io/paused=true`).
   d. Stand up the new `liferay-infrastructure` AppSet alongside the old `liferay-gcp-infrastructure` one (both reconciling, old one with `preserveResourcesOnDeletion: true`).
   e. After both show Synced+Healthy and the new chart's managed resources match the old chart's live state, delete the old AppSet.
   f. Unpause the Crossplane reconciler and confirm no `UPDATE`/`DELETE` events on CloudSQL or buckets.

Only after the above completes clean on dev (full 72h soak) should the same sequence run on uat, then on prd.

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
