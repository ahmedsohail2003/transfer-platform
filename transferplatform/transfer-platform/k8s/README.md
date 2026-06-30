# Kubernetes manifests

Deploys the two-service platform to any Kubernetes cluster (minikube, kind, EKS/GKE/AKS).

## Layout

| File | Purpose |
|---|---|
| `namespace.yaml` | A `transfer-platform` namespace to isolate the workloads |
| `account-service.yaml` | Deployment + **ClusterIP** Service (internal only) |
| `transfer-service.yaml` | Deployment + **LoadBalancer** Service (the only public entry point) |
| `kustomization.yaml` | Ties the three together for `kubectl apply -k` |

## Design choices

- **account-service is `ClusterIP`** (internal), so only `transfer-service` can reach it — the
  account API is never exposed to the public internet. Only `transfer-service` is a
  `LoadBalancer`.
- **Service discovery via DNS** — `transfer-service` finds `account-service` at
  `http://account-service:8081` (the Service's in-cluster DNS name), injected as
  `ACCOUNT_SERVICE_URL`.
- **Health probes** — readiness/liveness both use Spring Boot Actuator's `/actuator/health`.
- **Resource requests/limits** are set on every container so the scheduler can place them and
  they can't starve the node.

## Apply

Build and make the images available to your cluster first (e.g. for kind:
`kind load docker-image account-service:0.1.0` and likewise for transfer-service), then:

```bash
kubectl apply -k k8s/
kubectl -n transfer-platform get pods,svc
```

The platform is reachable through the `transfer-service` LoadBalancer; `account-service`
stays internal.
