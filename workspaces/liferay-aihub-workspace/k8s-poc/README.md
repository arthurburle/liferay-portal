# AI Hub Crawler — K8s Job POC

Step-by-step runbook to validate dispatching crawler runs as Kubernetes Jobs in a personal GKE Autopilot cluster, as an alternative to the Cloud Run Jobs path delivered under LPD-89140 / LPD-89150 / LPD-89153. Goal is to compare developer experience, security model, and operational footprint without involving the Liferay Cloud SRE team.

## Scope

This POC has two phases. Phase 1 is the infrastructure smoke test (manual `kubectl apply`). Phase 2 will add a `KubernetesJobCrawlerExecutor` Java implementation in `liferay-aihub-etc-spring-boot` and is tracked separately.

The POC stays inside one GCP project (`ai-hub-liferay`, region `europe-west3`) and never touches the production Liferay Cloud cluster.

## Cost awareness

GKE Autopilot pricing (May 2026): cluster management fee `$0.10/hour` plus per-pod resource fees (`$0.0445/vCPU-hour` and `$0.0049/GiB-RAM-hour`). The POC pods total roughly 1.5 vCPU and 4 GiB of RAM allocated, so expect about `$0.13/hour` while the cluster is up. Tear it down (Step 9) when you finish to avoid an unnecessary bill.

## Phase 1 — Cluster, Elasticsearch, manual Job dispatch

Each command below is a single line. Copy-paste them as-is.

### 1. Pre-conditions

- `gcloud` authenticated and pointing at `ai-hub-liferay`:

```
gcloud auth login
```

```
gcloud config set project ai-hub-liferay
```

- The crawler image `europe-west3-docker.pkg.dev/ai-hub-liferay/ai-hub/aihub-crawler:v1` exists in Artifact Registry (already pushed for the Cloud Run path).
- `kubectl` installed locally.

### 2. Enable the GKE API

Idempotent — does nothing if the API is already on:

```
gcloud services enable container.googleapis.com --project=ai-hub-liferay
```

### 3. Make sure a VPC network exists

The `ai-hub-liferay` project may not ship with a `default` network (org policy can disable it). Create it if missing:

```
gcloud compute networks create default --subnet-mode=auto --project=ai-hub-liferay
```

If org policy rejects this, fall back to a dedicated VPC instead:

```
gcloud compute networks create aihub-poc-vpc --subnet-mode=custom --project=ai-hub-liferay
```

```
gcloud compute networks subnets create aihub-poc-subnet --network=aihub-poc-vpc --region=europe-west3 --range=10.10.0.0/20 --project=ai-hub-liferay
```

In that case, append `--network=aihub-poc-vpc --subnetwork=aihub-poc-subnet` to the cluster create command in Step 4.

### 4. Create the GKE Autopilot cluster

Provisioning takes about 3–5 minutes:

```
gcloud container clusters create-auto aihub-poc --region=europe-west3 --project=ai-hub-liferay
```

Pull credentials into your local kubeconfig:

```
gcloud container clusters get-credentials aihub-poc --region=europe-west3 --project=ai-hub-liferay
```

Sanity check:

```
kubectl get nodes
```

Expected: at least one node listed in the `Ready` state.

### 5. Apply the namespace and Elasticsearch

```
cd workspaces/liferay-aihub-workspace/k8s-poc
```

```
kubectl apply -f manifests/01-namespace.yaml
```

```
kubectl apply -f manifests/02-elasticsearch.yaml
```

Watch the pod come up (Autopilot may take 1–2 minutes to schedule):

```
kubectl -n aihub-poc get pods -w
```

Wait until `elasticsearch-0` is `1/1 Running`.

Confirm the cluster is healthy:

```
kubectl -n aihub-poc exec elasticsearch-0 -- curl -s http://localhost:9200/_cluster/health
```

Expected: `"status":"green"` (or `"yellow"` is also acceptable for a single-node cluster).

### 6. Apply the RBAC for the future dispatcher

Phase 2 needs this; apply it now to keep the manifests in one place:

```
kubectl apply -f manifests/04-rbac.yaml
```

### 7. Dispatch a Job manually

```
kubectl apply -f manifests/03-job-example.yaml
```

Watch the pod the Job creates (`kubectl -w` only accepts one resource type at a time, so watch pods — that is where state changes are visible):

```
kubectl -n aihub-poc get pods -l app=aihub-crawler -w
```

Expected sequence: pod transitions `Pending` → `ContainerCreating` → `Running` (after ~30s cold start while the image pulls) → `Completed` once the crawl finishes.

To inspect the parent Job once or twice without watching:

```
kubectl -n aihub-poc get jobs -l app=aihub-crawler
```

Tail the crawler logs:

```
kubectl -n aihub-poc logs -f -l app=aihub-crawler --tail=100
```

You should see the `[YYYY-MM-DDTHH:MM:SS+00:00] Starting crawler` line from the entrypoint, then Open Crawler output, then `Crawler finished with exit code 0`.

### 8. Validate the index

The index name comes from `CRAWLER_OUTPUT_INDEX` in `manifests/03-job-example.yaml`; substitute `<index-name>` with that value in the commands below.

Via `exec` into the ES pod:

```
kubectl -n aihub-poc exec elasticsearch-0 -- curl -s http://localhost:9200/<index-name>/_count
```

Or by port-forwarding the ES Service to your laptop, then querying from a second terminal:

```
kubectl -n aihub-poc port-forward svc/elasticsearch 9200:9200
```

```
curl -s http://localhost:9200/<index-name>/_count
```

```
curl -s http://localhost:9200/<index-name>/_search?size=1
```

Expected: `count` greater than zero. The first hit document should have a `body_content` field with text scraped from the seed URL.

### 9. Cleanup (do this when finished)

Delete the cluster (this also removes ES, all Jobs, and the namespace):

```
gcloud container clusters delete aihub-poc --region=europe-west3 --project=ai-hub-liferay --quiet
```

Verify the bill stops:

```
gcloud container clusters list --project=ai-hub-liferay
```

Expected: empty list (or no row for `aihub-poc`).

If you created the dedicated VPC in Step 3, also remove it:

```
gcloud compute networks subnets delete aihub-poc-subnet --region=europe-west3 --project=ai-hub-liferay --quiet
```

```
gcloud compute networks delete aihub-poc-vpc --project=ai-hub-liferay --quiet
```

## Phase 2 — Java integration (KubernetesJobCrawlerExecutor)

Phase 2 adds a third `CrawlerExecutor` implementation in `liferay-aihub-etc-spring-boot` (commit `32ff4b8`) plus a smoke-test endpoint that bypasses the headless / OAuth flow so the dispatcher can be exercised end-to-end without a Liferay portal in the cluster.

Components:

- `KubernetesJobCrawlerExecutor` (`@ConditionalOnProperty(... kubernetes-jobs)`) — builds and submits a `batch/v1.Job` via fabric8 client.
- `KubernetesJobsConfig` — provides a `KubernetesClient` bean (picks up in-cluster ServiceAccount token automatically).
- `CrawlerSmokeDispatchRestController` (`@ConditionalOnProperty(... smoke.endpoint.enabled=true)`) — POST `/smoke/dispatch` calls `CrawlerExecutor.execute()` directly.
- Properties: `liferay.ai.hub.crawler.k8s.image`, `liferay.ai.hub.crawler.k8s.namespace`, `liferay.ai.hub.crawler.smoke.endpoint.enabled`.
- `/smoke/dispatch` and `/error` added to `liferay.oauth.urls.excludes`.

### 10. Build and push the CE image

The slim CE Dockerfile lives in `client-extensions/liferay-aihub-etc-spring-boot/`. Build for `linux/amd64` (Apple Silicon hosts) without provenance attestation (Cloud Run rejects OCI image index manifests; Autopilot accepts but stays consistent):

```
cd ~/liferay/projects/liferay-portal/workspaces/liferay-aihub-workspace/client-extensions/liferay-aihub-etc-spring-boot
```

```
../../gradlew bootJar
```

```
docker buildx build --platform=linux/amd64 --provenance=false --push -t europe-west3-docker.pkg.dev/ai-hub-liferay/ai-hub/aihub-ce:v1 .
```

### 11. Deploy the CE

```
cd ~/liferay/projects/liferay-portal/workspaces/liferay-aihub-workspace/k8s-poc
```

```
kubectl apply -f manifests/05-ce-deployment.yaml
```

```
kubectl -n aihub-poc get pods -l app=liferay-aihub-etc-spring-boot -w
```

Wait for `Running` and `1/1 Ready` (readiness probe hits `/ready`). The CE pod runs as the SA `aihub-crawler-dispatcher` (already created in Step 6) so it can create Jobs in the namespace.

Sanity check the boot log:

```
kubectl -n aihub-poc logs -l app=liferay-aihub-etc-spring-boot --tail=50
```

Expected lines:

- `Active crawler executor: KubernetesJobCrawlerExecutor`
- `Smoke dispatch endpoint enabled — POC use only, not for production`
- `Started AIHubSpringBootApplication`

### 12. Trigger a crawl through the CE

Port-forward the Service to your laptop:

```
kubectl -n aihub-poc port-forward svc/liferay-aihub-etc-spring-boot 58081:58081
```

In a second terminal:

```
curl -X POST http://localhost:58081/smoke/dispatch -H 'Content-Type: application/json' -d '{"domainUrl":"https://learn.liferay.com","seedUrl":"https://learn.liferay.com","indexName":"aihub-smoke"}'
```

Expected response: `{"executionId":"k8s:aihub-crawler-XXXXXXXX"}`.

A new Job appears in the namespace:

```
kubectl -n aihub-poc get jobs -l app=aihub-crawler
```

Tail the new Job's logs:

```
kubectl -n aihub-poc logs -f -l job-name=aihub-crawler-XXXXXXXX
```

Substitute `XXXXXXXX` with the suffix from the `executionId` returned above.

Validate the index:

```
kubectl -n aihub-poc exec elasticsearch-0 -- curl -s http://localhost:9200/aihub-smoke/_count
```

### 13. Cleanup additions for Phase 2

The Phase 1 cleanup (Step 9) deletes the cluster, which removes the CE Deployment, the Service, and any dispatcher-spawned Jobs in one go. No extra steps for Phase 2.

## What this POC does and does not validate

**Validates**:

- The crawler container image runs unchanged in K8s (same env contract as Cloud Run).
- A Kubernetes Job can dispatch the crawler and produce documents in Elasticsearch.
- The `aihub-poc` namespace can host both the crawler runtime and the index target without cross-cluster networking.
- RBAC scoped to the namespace lets a future dispatcher submit Jobs.

**Does not validate** (out of scope here):

- Multi-tenant isolation in a shared cluster (this POC uses a dedicated cluster).
- Failure-domain coupling between the crawler and an in-cluster Liferay portal (no Liferay deployed here).
- Resource quota collision under concurrent crawls (single Job per dispatch).
- The Java executor flow end-to-end (Phase 2).
- Direct CrawlerJob status reporting from the container (still scoped under LPD-89153).

## References

- Cloud Run Jobs implementation: commits under LPD-89140 / LPD-89150.
- Architecture overview: `~/ObsidianVault/AI/AI Hub Crawler - Architecture (Confluence).md`.
- Container image source: `workspaces/liferay-aihub-workspace/crawler-image/`.
