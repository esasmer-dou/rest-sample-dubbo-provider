# Docker Desktop Runbook

[English](README.md) | [Türkçe](README.tr.md)

This Docker Compose setup starts:

- `postgres`: PostgreSQL 16 for the sample database.
- `provider`: `rest-sample-dubbo-provider` on `dubbo://localhost:20880`.

ZooKeeper is disabled by default. The consumer can use the static provider address or Kubernetes
Service DNS.

## Start

Run from the project root:

```powershell
docker compose -f docker/docker-compose.yml up --build
```

If port `15432` is already used, stop that container or change the published PostgreSQL port. Verify
both containers before starting the consumer:

```powershell
docker compose -f docker/docker-compose.yml ps
docker logs rest-sample-dubbo-provider
```

## Consumer Settings

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=127.0.0.1:20880
```

When the consumer also runs in this Compose network, use the provider service name instead of
`127.0.0.1`.

## Stop

Keep the database volume:

```powershell
docker compose -f docker/docker-compose.yml down
```

Remove the sample database volume as well:

```powershell
docker compose -f docker/docker-compose.yml down -v
```

## PostgreSQL Safety

The sample keeps durable commits enabled. It changes only checkpoint pacing:

```text
checkpoint_timeout=15min
checkpoint_completion_target=0.9
min_wal_size=256MB
max_wal_size=1GB
```

It does not disable `fsync`, `synchronous_commit`, or `full_page_writes`. For an external database,
the database owner must size WAL and checkpoints from disk latency, recovery target, and write
volume. Do not use `synchronous_commit=off` to hide API p99 variance.

Image definitions are documented in [`images/README.md`](images/README.md).
