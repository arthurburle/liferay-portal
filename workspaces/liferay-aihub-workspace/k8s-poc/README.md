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

Watch the Job and the pod it creates:

```
kubectl -n aihub-poc get jobs,pods -l app=aihub-crawler -w
```

Expected sequence: pod becomes `Running` (after ~30s cold start while the image pulls), then `Succeeded` once the crawl finishes.

Tail the crawler logs:

```
kubectl -n aihub-poc logs -f -l app=aihub-crawler --tail=100
```

You should see the `[YYYY-MM-DDTHH:MM:SS+00:00] Starting crawler` line from the entrypoint, then Open Crawler output, then `Crawler finished with exit code 0`.

### 8. Validate the index

Via `exec` into the ES pod:

```
kubectl -n aihub-poc exec elasticsearch-0 -- curl -s http://localhost:9200/aihub-test/_count
```

Or by port-forwarding the ES Service to your laptop, then querying from a second terminal:

```
kubectl -n aihub-poc port-forward svc/elasticsearch 9200:9200
```

```
curl -s http://localhost:9200/aihub-test/_count
```

```
curl -s http://localhost:9200/aihub-test/_search?size=1
```

Expected: `count` greater than zero. The first hit document should have a `body_content` field with text scraped from `https://docs.liferay.com`.

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

## Phase 2 — Java integration (next commit)

Will add to `liferay-aihub-etc-spring-boot`:

- `KubernetesJobCrawlerExecutor` implementing the existing `CrawlerExecutor` interface, gated by `@ConditionalOnProperty(name = "liferay.ai.hub.crawler.executor", havingValue = "kubernetes-jobs")`.
- `KubernetesJobsConfig` providing a `KubernetesClient` bean from `io.fabric8:kubernetes-client`.
- New properties in `application-default.properties` for the target image, namespace, and Elasticsearch endpoint.
- The same RBAC manifest already in this directory (`manifests/04-rbac.yaml`) — bind it to the CE pod's ServiceAccount when deploying.

The Phase 2 commit will be additive and feature-flagged; the Cloud Run Jobs path stays the default. Toggling `liferay.ai.hub.crawler.executor=kubernetes-jobs` activates the new executor.

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
- Direct CrawlJob status reporting from the container (still scoped under LPD-89153).

## References

- Cloud Run Jobs implementation: commits under LPD-89140 / LPD-89150.
- Architecture overview: `~/ObsidianVault/AI/AI Hub Crawler - Architecture (Confluence).md`.
- Container image source: `workspaces/liferay-aihub-workspace/crawler-image/`.
