# rest-sample-dubbo-provider

English | [Turkish](README.tr.md)

Minimal plain Java Dubbo provider sample for the Rust-Java REST consumer demo, with ZooKeeper
registration, PostgreSQL/HikariCP data access, and ready-to-forward JSON responses.

This repository exists so `rest-sample-dubbo-consumer` can be tested against a real Dubbo provider
without bringing Spring Boot or Dubbo Spring Boot starter into the sample.

## What This Sample Is For

Use this sample when you want to understand:

- How to expose a classic `dubbo://` provider in plain Java.
- How to export more than one Dubbo interface from the same small provider process.
- How to register provider URLs in ZooKeeper.
- How to keep provider dependencies explicit and limited.
- How to return JSON bytes from a provider for low-overhead HTTP forwarding by a Rust-Java consumer.
- How to back POST/PATCH/DELETE REST use cases with small Dubbo command methods.
- How to add PostgreSQL access through HikariCP and ActiveJDBC without Spring.
- How to separate runtime classes from record DTOs.

This is not a generic enterprise Dubbo provider template. It is a focused provider used to validate
the Rust-Java REST Dubbo consumer path.

## Start Here: Pick Your Provider Shape

This provider has no `rust-java-rest` runtime profile because it is not a REST application. Its
production behavior is controlled by plain provider properties: Dubbo export settings, ZooKeeper
registration, HikariCP pool size, and per-interface/per-method concurrency limits.

| Scenario | Provider design | Key setting | Consumer impact |
|----------|-----------------|-------------|-----------------|
| Read-heavy lookup/catalog | Small read interface<br>returns UTF-8 JSON `byte[]` | <small><code>dubbo.provider.service.NestedCatalogService.max-concurrent=16</code></small> | Native handle: `RawResponse.nativeResponse(...)`<br>Java bytes: `RawResponse.json(bytes)`<br>no DTO graph |
| Typed lookup/small page | `record`, `String`, primitive,<br>`List<record>`, `Map<String,String>` | <small><code>dubbo.provider.service.NestedCatalogService.method.&lt;method&gt;.max-concurrent=4-16</code><br>strict result limit</small> | Typed business decisions<br>Hessian/object allocation |
| DB-backed query | `CustomerQueryService`<br>small DB pool | <small><code>sample.db.maximum-pool-size=2</code><br><code>dubbo.provider.service.CustomerQueryService.max-concurrent=1-2</code></small> | p99 bounded by DB capacity |
| Write command | Compact JSON command bytes | <small><code>dubbo.provider.service.CustomerCommandService.method.&lt;method&gt;.max-concurrent=1</code><br><code>sample.db.auto-commit=true</code></small> | Retries off, fail-fast on saturation |
| Typed command | `CreateCustomerCommand -> CustomerMutationResult` | <small><code>dubbo.provider.service.CustomerCommandService.method.createCustomerTyped.max-concurrent=1</code><br>aligned with Hikari</small> | Cleaner contract<br>costlier than byte pass-through |
| Kubernetes discovery | Register every interface in ZooKeeper | <small><code>reactor.dubbo.registry-enabled=true</code><br><code>reactor.dubbo.registry-address=zookeeper://...:2181</code></small> | `zookeeper-discovery` can reconnect |
| Static Service DNS | No ZooKeeper registration; provider only exposes `dubbo://` | <small><code>reactor.dubbo.registry-enabled=false</code><br><code>dubbo.provider.bind-host=0.0.0.0</code></small> | Consumer uses `reactor.dubbo.providers=service-name:20880` |
| Local/static test | Bind `127.0.0.1:20880` or container DNS | `dubbo.provider.host`<br>`bind-host`, `port` | Static provider list points here |

Recommended starting point: keep provider interfaces small and return ready JSON bytes for
pass-through read APIs. Returning records, lists, maps, strings, and primitive values is valid when
the consumer must make typed business decisions. Avoid returning a large nested object graph when the
consumer immediately serializes it back to JSON.

## Scenario-Specific Provider Images

The provider also has scenario-specific images. Do not use the DB/ZooKeeper-capable image for a
catalog-only static JSON demo.

| Image | Build command | Exports | Intentionally absent | Local smoke evidence |
|-------|---------------|---------|----------------------|----------------------|
| `rest-sample-dubbo-provider:catalog-static-jlink` | `docker build -f docker/images/Dockerfile.jlink.catalog-static -t rest-sample-dubbo-provider:catalog-static-jlink .` | `CatalogJsonService#getNestedCatalogJson()` only. | PostgreSQL, HikariCP, ActiveJDBC, ZooKeeper registration, customer services, typed catalog DTO methods. | App jar about `9.3M`, JRE `80M`, RSS about `45 MiB` after idle. |
| `rest-sample-dubbo-provider:db-query-jlink` | `docker build -f docker/images/Dockerfile.jlink.db-query -t rest-sample-dubbo-provider:db-query-jlink .` | `CustomerQueryService` only, backed by PostgreSQL/HikariCP. ZooKeeper registration can be enabled or disabled. | Catalog services and `CustomerCommandService`; no POST/PATCH/DELETE Dubbo command surface. | Local smoke: query REST calls returned `200`; command REST call returned expected `503`; provider RSS about `59 MiB`. |
| `rest-sample-dubbo-provider:jlink` | `docker build -f docker/images/Dockerfile.jlink -t rest-sample-dubbo-provider:jlink .` | Catalog, customer query, customer command interfaces. | Nothing sample-related; this is the full DB-capable provider image. | Image about `179MB`; use when DB/customer examples are needed. |

Why `catalog-static-jlink` still includes `java.desktop` and `java.sql`: Apache Dubbo's official
provider runtime initializes Java Beans and Hessian/SQL-aware serialization classes even for a
raw `byte[]` method. The image removes DB/ZooKeeper/customer application dependencies, but it cannot
remove those JDK modules while still using the official Dubbo provider stack. Removing them caused
runtime provider failures during smoke testing.

Best pairing:

```text
rest-sample-dubbo-provider:catalog-static-jlink
rest-sample-dubbo-consumer:native-static-jlink
```

This pair is for one static provider address and one ready-JSON read API. If you need typed DTOs,
DB queries, write commands, or ZooKeeper registration, use the full provider image and the matching
consumer image instead.

Use `db-query-jlink` when the provider should read PostgreSQL but must not expose write commands.
That image is useful for query-only bounded services: customer lookup, customer list, segment filter,
and stats. The same container can run with `REACTOR_DUBBO_REGISTRY_ENABLED=false` behind a Kubernetes
Service DNS name, or with `REACTOR_DUBBO_REGISTRY_ENABLED=true` when the consumer must discover
providers through ZooKeeper.

## Typed DTO And Hessian Security

Dubbo/Hessian deserialization is intentionally strict. If a provider method accepts a typed DTO such
as `CreateCustomerCommand`, the DTO package must be explicitly allowlisted:

```text
src/main/resources/security/serialize.allowlist
```

Current sample entry:

```text
com.reactor.rust.dubbo.sample.dto
```

Keep this narrow. Add only DTO packages that your provider really accepts over Dubbo. Do not disable
Dubbo serialization security globally just to make a sample request pass. If this file is missing,
typed command calls can fail with provider status `40` and a log similar to "Serialized class ... is
not in allow list".

## Production Recipes

### Recipe 1: Provider For Kubernetes ZooKeeper Discovery

Use this when the consumer runs with `zookeeper-discovery` and providers can be restarted or moved.

```properties
dubbo.provider.application-name=rest-sample-dubbo-provider
dubbo.provider.host=provider-pod-ip-or-headless-service-dns
dubbo.provider.bind-host=0.0.0.0
dubbo.provider.port=20880
reactor.dubbo.registry-enabled=true
reactor.dubbo.registry-address=zookeeper://zookeeper-client.platform.svc.cluster.local:2181
reactor.dubbo.registry-root=dubbo
```

Effect:

| Property | What it does | Production note |
|----------|--------------|-----------------|
| `dubbo.provider.host` | ZooKeeper provider URL host | Must be reachable by consumer pods.<br>Use per-pod IP or headless DNS for real discovery.<br>Do not publish `127.0.0.1` in K8s. |
| `dubbo.provider.bind-host` | Local interface the provider listens on | Use `0.0.0.0` in containers. |
| `reactor.dubbo.registry-enabled` | Turns ZooKeeper registration on/off | Keep `true` for discovery mode. Set `false` when consumers use static K8s Service DNS. |
| `reactor.dubbo.registry-address` | ZooKeeper registry endpoint | Use Kubernetes DNS, not a local desktop address. |
| `reactor.dubbo.registry-root` | Registry namespace | Keep it aligned with consumer `reactor.dubbo.registry-root`. |

### Recipe 1B: Provider With Static Kubernetes Service DNS

Use this when you do not want ZooKeeper and your consumer calls a stable Kubernetes Service such as
`rest-sample-dubbo-provider:20880`. In this mode Kubernetes owns endpoint load balancing and the
provider does not create a ZooKeeper session.

```properties
dubbo.provider.application-name=rest-sample-dubbo-provider
dubbo.provider.host=rest-sample-dubbo-provider
dubbo.provider.bind-host=0.0.0.0
dubbo.provider.port=20880
reactor.dubbo.registry-enabled=false
```

Consumer side:

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=rest-sample-dubbo-provider:20880
```

Effect: the provider still exports the same Dubbo interfaces, but it skips ZooKeeper registration.
This removes the provider-side ZooKeeper runtime dependency from the active path. Use it when all
provider replicas are reachable behind one stable Service name. Use ZooKeeper discovery when
interfaces move independently, multiple provider groups must be discovered dynamically, or you need
registry-driven provider membership instead of Kubernetes Service membership.

### Recipe 2: DB-Backed Query With HikariCP

Use this when the provider reads PostgreSQL and returns ready JSON bytes to the consumer.

```properties
sample.db.jdbc-url=jdbc:postgresql://postgresql.platform.svc.cluster.local:5432/reactor_sample
sample.db.username=reactor
sample.db.password=${DB_PASSWORD}
sample.db.maximum-pool-size=2
sample.db.minimum-idle=0
sample.db.connection-timeout-ms=3000
sample.db.validation-timeout-ms=1000
dubbo.provider.service.CustomerQueryService.max-concurrent=2
dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=1
```

Effect:

| Setting | What it controls | Why the sample keeps it small |
|---------|------------------|-------------------------------|
| `sample.db.maximum-pool-size` | Physical PostgreSQL connections | DB is usually the bottleneck; oversized pools increase memory and DB contention. |
| `sample.db.minimum-idle=0` | Idle DB connections retained | Good for low-RSS samples; set higher only if cold DB acquisition hurts p99. |
| `CustomerQueryService.max-concurrent` | Provider-side query bulkhead | Keeps provider queues aligned with DB capacity. |
| Method override | Per-method hard cap | Protects one expensive method without lowering every method on the interface. |

### Recipe 2B: DB Query-Only Provider Image

Use this when the provider only serves read/query APIs and must not publish command methods. This is
the right shape for services where POST/PATCH/DELETE are owned by another bounded command service or
by a workflow/queue, but REST consumers still need fast customer reads.

Build:

```powershell
docker build -f docker/images/Dockerfile.jlink.db-query -t rest-sample-dubbo-provider:db-query-jlink .
```

Static Kubernetes Service DNS mode:

```yaml
env:
  - name: REACTOR_DUBBO_REGISTRY_ENABLED
    value: "false"
  - name: DUBBO_PROVIDER_HOST
    value: "rest-sample-dubbo-provider"
  - name: DUBBO_PROVIDER_BIND_HOST
    value: "0.0.0.0"
  - name: SAMPLE_DB_JDBC_URL
    value: "jdbc:postgresql://postgresql.platform.svc.cluster.local:5432/reactor_sample"
  - name: SAMPLE_DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: customer-db
        key: username
  - name: SAMPLE_DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: customer-db
        key: password
  - name: SAMPLE_DB_MAXIMUM_POOL_SIZE
    value: "2"
  - name: SAMPLE_DB_MINIMUM_IDLE
    value: "0"
  - name: DUBBO_PROVIDER_SERVICE_CUSTOMERQUERYSERVICE_MAX_CONCURRENT
    value: "2"
```

ZooKeeper discovery mode:

```yaml
env:
  - name: REACTOR_DUBBO_REGISTRY_ENABLED
    value: "true"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_DUBBO_REGISTRY_ROOT
    value: "dubbo"
  - name: DUBBO_PROVIDER_HOST
    valueFrom:
      fieldRef:
        fieldPath: status.podIP
  - name: DUBBO_PROVIDER_BIND_HOST
    value: "0.0.0.0"
```

Expected behavior:

| Call type | Result with `db-query-jlink` | Why |
|-----------|------------------------------|-----|
| `CustomerQueryService#getDatabaseCustomersJson` | Works | Query interface is exported and DB/Hikari is active. |
| `CustomerQueryService#getCustomerStats` | Works | Small stats query, method cap defaults to `1`. |
| `CustomerCommandService#createCustomer*` | Fails fast | Command interface is intentionally not exported. The consumer should return an unavailable/503-style error instead of hiding a write path in the query provider. |

Do not use this image if the same provider must handle POST/PATCH/DELETE commands. Use the full
`Dockerfile.jlink` image or split commands into a separate provider with its own DB pool and
concurrency limits.

### Recipe 3: Write Commands Without Queue Growth

Use this when POST/PATCH/DELETE requests are translated into provider command methods.

```properties
dubbo.provider.service.CustomerCommandService.max-concurrent=2
dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.createCustomerTyped.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.patchCustomerSegment.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.patchCustomerStatus.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.patchCustomerStatusTyped.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.deleteCustomer.max-concurrent=1
sample.db.maximum-pool-size=2
```

Effect: command methods fail fast when the provider is saturated instead of building an unbounded
queue. This is safer for memory and tail latency. If callers require guaranteed command completion,
put a durable queue/workflow in front of the provider; do not hide it inside Dubbo request queues.

### Recipe 4: Docker Desktop With PostgreSQL

```powershell
docker compose -f docker/docker-compose.yml up --build
```

This starts:

- PostgreSQL 16 on host port `15432`.
- `rest-sample-dubbo-provider` on `dubbo://localhost:20880`.
- ZooKeeper registration disabled with `REACTOR_DUBBO_REGISTRY_ENABLED=false`.

If Docker Desktop already has a manually started PostgreSQL container on port `15432`, stop that
container first or change the published PostgreSQL port in `docker/docker-compose.yml`.

Stop it with:

```powershell
docker compose -f docker/docker-compose.yml down
```

Effect: this is the simplest Docker Desktop path for the sample consumer. Start the consumer with
`sample.dubbo.discovery=static` and `reactor.dubbo.providers=127.0.0.1:20880`. If you need
ZooKeeper discovery, use Recipe 1 instead and explicitly enable registry registration.

### Recipe 5: Read-Heavy Precomputed Catalog

Use this when catalog data changes rarely and the consumer only needs to expose it as REST JSON.

```java
public final class NestedCatalogServiceImpl implements NestedCatalogService {
    private final byte[] currentCatalogJson;

    public NestedCatalogServiceImpl() {
        this.currentCatalogJson = buildCatalogJson().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] getNestedCatalogJson() {
        return currentCatalogJson;
    }
}
```

```properties
dubbo.provider.service.NestedCatalogService.max-concurrent=32
```

Effect: the provider does not rebuild the same JSON on every call. If the consumer receives a native
response handle, it forwards it with `RawResponse.nativeResponse(handle.nativeId())`. If Java must
inspect the payload and therefore already has bytes, it forwards them with `RawResponse.json(bytes)`.
If the catalog changes, refresh this byte array through a clear timer, event, or admin operation.

### Recipe 6: Multiple Interfaces With Separate Capacity

Use this when one provider process exposes both cheap reads and DB/write operations.

```properties
dubbo.provider.service.NestedCatalogService.max-concurrent=32
dubbo.provider.service.CustomerQueryService.max-concurrent=2
dubbo.provider.service.CustomerCommandService.max-concurrent=2
dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent=1
```

Effect: a busy DB/write method does not consume the whole provider execution budget for cheap read
methods. Keep the split visible in the interface design; it makes consumer route admission easier to
tune.

### Recipe 7: Provider Rolling Restart

Use this when provider pods are restarted by deployment rollout or node movement.

```properties
dubbo.provider.bind-host=0.0.0.0
dubbo.provider.host=provider-pod-ip-or-headless-service-dns
reactor.dubbo.registry-enabled=true
reactor.dubbo.registry-address=zookeeper://zookeeper-client.platform.svc.cluster.local:2181
reactor.dubbo.registry-root=dubbo
```

Effect: each provider registers an ephemeral ZooKeeper node. When the pod stops, ZooKeeper removes
the node after session expiry. When the pod starts again, it registers the new reachable address.
Keep the consumer `REACTOR_DUBBO_TIMEOUT_MS` bounded so a disappearing provider does not create long
request stalls.

### Recipe 8: Turkish Characters From Database To REST

Use this when database values include Turkish characters or any non-ASCII text.

```java
byte[] json = """
        {"city":"İstanbul","district":"Şişli","customer":"Mustafa Korkmaz"}
        """.getBytes(StandardCharsets.UTF_8);
return json;
```

Effect: the provider controls encoding once, and the consumer forwards the same UTF-8 bytes. Avoid
`String#getBytes()` without an explicit charset.

### Recipe 9: Raise DB Throughput Carefully

Use this only after you see DB pool wait or provider-side rejects and the database has spare CPU.

```properties
sample.db.maximum-pool-size=4
sample.db.minimum-idle=1
dubbo.provider.service.CustomerQueryService.max-concurrent=4
dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=4
```

Effect: more DB-backed calls can run at the same time. Memory, PostgreSQL connection count, and DB
CPU also increase. Tune the consumer DB route admission at the same time; otherwise the consumer can
send more work than the provider/database can finish.

### Recipe 10: Production Schema Management Boundary

The sample can initialize schema for local demos. In production, prefer migrations outside this hot
provider process.

```properties
sample.db.schema-init=false
sample.db.warmup=true
sample.db.initialization-fail-timeout-ms=3000
```

Effect: startup does not run schema DDL inside every provider pod. Keep schema migration in a
deployment job, Flyway/Liquibase step, or platform migration pipeline. Provider startup should only
verify that required tables are reachable.

## Relationship With Other Projects

This provider is designed to be used by:

```text
git@github.com:esasmer-dou/rest-sample-dubbo-consumer.git
```

Runtime relation:

```text
rest-sample-dubbo-consumer
  -> java-rust-dubbo native consumer
  -> dubbo:// provider on this project
  -> optional PostgreSQL query
  -> JSON byte[]
```

The provider registers each interface under its own ZooKeeper path:

```text
/dubbo/com.reactor.rust.dubbo.sample.NestedCatalogService/providers
/dubbo/com.reactor.rust.dubbo.sample.CustomerQueryService/providers
```

## How `rust-java-rest` 3.2.x Affects This Provider

This provider does not depend on `rust-java-rest`, and it should stay that way. Its job is to expose
a small Dubbo contract that lets the `rest-sample-dubbo-consumer` use the v3.2.x low-overhead response
path.

| Provider choice | Effect on the v3.2.x consumer |
|-----------------|----------------------------|
| Return UTF-8 JSON as `byte[]` | Consumer can use `RawResponse.nativeResponse(handle.nativeId())` on native handle routes, or `RawResponse.json(bytes)` when Java must inspect the bytes. Both avoid a second DTO graph. |
| Keep interfaces small | Consumer can tune timeouts, backpressure, and metrics per RPC area. |
| Keep method concurrency bounded | Provider overload becomes explicit instead of turning into heap, DB pool, or Netty queue growth. |
| Align DB method limits with Hikari | Consumer p99 is more stable because DB saturation fails fast instead of queueing deeply. |
| Match provider limits with consumer route admission | A slow provider method cannot fill the consumer global JNI queue. |
| Avoid huge object graphs for pass-through responses | v3.2.x improvements are preserved instead of being erased by Hessian materialization and JSON reserialization. |
| Keep consumer on normal framework dependency | The consumer avoids framework sample/demo classes in production-like RSS measurements. |
| Respect explicit consumer properties | Consumer `rust-spring.properties` values are not overwritten by runtime profile defaults. |
| Keep benchmark-only routes out of production | Consumer route diagnostics can prove provider-facing production routes are not polluted by legacy comparison routes. |
| Measure anon with the minimal app | Consumer pod sizing can separate heap, class metadata, JIT, direct buffers, Rust-accounted memory, and residual anon. |

Turkish characters are safe in this flow when the provider writes UTF-8 JSON bytes and the consumer
returns them with JSON content type. Do not build JSON through platform-default encodings.

BEST: return `byte[]` for read-heavy pass-through JSON and let the consumer use
`RawResponse.nativeResponse(handle.nativeId())` when it receives a native handle. ACCEPTABLE: return
records when the consumer must make typed business decisions, or use `RawResponse.json(bytes)` when
Java already has bytes because it inspected them. ANTI-PATTERN: return a large nested object graph
only for the consumer to convert it back to JSON.

### Production Dependency Boundary

This provider intentionally does not depend on `rust-java-rest`. The REST framework runs in the
consumer process, not in the provider process.

For memory and performance analysis, keep these scopes separate:

| Component | What it should contain | What it should not contain |
|-----------|------------------------|----------------------------|
| Provider | Plain Java Dubbo provider, HikariCP/ActiveJDBC if DB access is needed | `rust-java-rest` runtime |
| Consumer | Normal `rust-java-rest` dependency and `java-rust-dubbo` adapter | Framework `rust-java-rest-*-sample.jar` |
| Framework sample jar | Bundled demo/benchmark routes | Provider or consumer pod sizing evidence |

The `rust-java-rest` `3.2.x` normal jar and `core-runtime` jar exclude framework sample/benchmark
packages. That helps the consumer stay production-like, but it does not change this provider's class
path because the provider never consumes the framework artifact.

### How To Align Provider Limits With The Consumer

The consumer sample protects Dubbo routes with `@RouteAdmission`. Keep these values related to the
provider limits instead of tuning them independently:

| Provider capacity | Consumer setting to check | Practical rule |
|-------------------|---------------------------|----------------|
| `dubbo.provider.service.NestedCatalogService.max-concurrent=16` | `reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16` | Simple catalog calls can match provider concurrency. |
| `dubbo.provider.service.CustomerQueryService.max-concurrent=2` | `reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8` | Acceptable for async/short calls.<br>If p99 grows, lower this first. |
| `sample.db.maximum-pool-size=2` | DB method provider limit | DB method concurrency should not exceed the Hikari pool unless you intentionally want provider-side waiting. |

BEST: start with small provider limits and increase only after measuring provider CPU, DB pool wait,
consumer 503 rate, p99 latency, and RSS together. ANTI-PATTERN: increase consumer workers while the
provider DB pool is already saturated.

### Provider Hot Path Notes

The provider keeps HikariCP and ActiveJDBC dependencies because this sample also shows how an
existing database provider can be wired without Spring. That does not mean every hot query should go
through an ActiveJDBC `Map` path.

The current provider implementation uses the lighter path for benchmarked DB-backed routes:

| Area | Current implementation | Why it matters |
|------|------------------------|----------------|
| `GET /api/v1/customers/db` provider method | Hikari `PreparedStatement` + direct `ResultSet` to `SampleCustomer` records | Avoids ActiveJDBC `Map` allocation on the hot read path. |
| `customerExists` / `getCustomerDisplayName` | Narrow SQL selecting only `1` or `full_name` | Avoids reading a full customer row for scalar REST responses. |
| `patchCustomerSegment` / `patchCustomerStatus` SQL | Fixed prepared update statements | Avoids dynamic SQL formatting on the command hot path. |
| Command JSON response | `StringBuilder` JSON writer | Avoids `String.formatted(...)` parser/allocation overhead per command response. |
| Read-heavy pass-through JSON | `byte[]` UTF-8 JSON | Lets the consumer use native response handles or `RawResponse.json(bytes)` without a DTO graph. |

This is still a sample provider, not a universal ORM recommendation. ActiveJDBC remains useful for
simple examples and legacy provider code, but if a provider method is in the c64/c256 hot path, use
explicit SQL, narrow selected columns, bounded result sizes, and aligned provider/consumer
admission. A slow provider cannot be fixed by increasing consumer workers.

### Write Contention Rule

Hot-row write pressure must be treated differently from distributed writes:

| Write pattern | Provider behavior | Consumer behavior |
|---------------|-------------------|-------------------|
| Many updates to one customer id | PostgreSQL row lock becomes the bottleneck. | `sample.command.customer-key-admission.max-concurrent-per-key=1` should reject excess work early. |
| Updates spread across many ids | DB pool and provider command bulkhead are the main limits. | Route admission can be wider if useful 2xx RPS rises without p99/RSS regression. |
| Retried create/patch/delete | Can duplicate side effects unless idempotency exists. | Keep `reactor.dubbo.retries=0` for command routes. |

BEST: make write commands idempotent with a `requestId`, keep provider method concurrency close to
the DB pool, and let the consumer fail fast when one business key is overloaded. ANTI-PATTERN:
raising global queues so hot-row lock waits become second-level p99 spikes.

## Architecture

```text
Dubbo consumer
  -> ZooKeeper provider URL or static host:port
  -> PlainDubboProvider
  -> selected service implementation
  -> JSON byte[]
```

The provider intentionally exposes small cohesive interfaces instead of one large RPC surface:

```java
public interface NestedCatalogService {
    byte[] getNestedCatalogJson();

    String getCatalogTitle();

    int countCatalogItems();

    CatalogInfo getCatalogInfo();

    List<CatalogItem> listFeaturedItems(int limit);

    Map<String, String> getCatalogAttributes();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();

    CustomerSummary getCustomer(long customerId);

    List<CustomerSummary> findCustomersBySegment(String segment, int limit);

    CustomerStats getCustomerStats();

    boolean customerExists(long customerId);

    String getCustomerDisplayName(long customerId);
}

public interface CustomerCommandService {
    byte[] createCustomer(byte[] commandJson);
    byte[] patchCustomerSegment(long customerId, byte[] commandJson);
    byte[] patchCustomerStatus(long customerId, byte[] commandJson);
    byte[] deleteCustomer(long customerId, byte[] commandJson);

    CustomerMutationResult createCustomerTyped(CreateCustomerCommand command);

    CustomerMutationResult patchCustomerStatusTyped(long customerId, String status, String requestId);
}
```

The consumer can still forward the byte-array methods through `RawResponse.json(...)` without
building another DTO graph. The typed methods intentionally demonstrate normal Dubbo object
contracts for smaller business responses.

### Provider Data Shape Decision

The provider method signature directly affects consumer memory and p99 behavior. Choose the method
shape by data flow and ownership boundaries, not only by API readability.

| Provider method | Provider owns | Consumer impact | Use when |
|-----------------|---------------|-----------------|----------|
| Ready JSON `byte[]` | JSON shape + domain validation | `RawResponse`, no DTO graph | Read-heavy pass-through |
| `record` | Domain object creation | Hessian decode + HTTP JSON serialize | Consumer inspects fields |
| `List<record>` | Small bounded page | List + item allocation | Limited list/page |
| `byte[] command` | JSON parse + command validation | Thin consumer | Lowest-allocation command |
| `record command` | Typed command contract | Request encode + response decode | Readable business command |

Ownership boundary:

| Topic | Owner |
|-------|-------|
| DB constraint, uniqueness, mutation rule | Provider |
| Ready response JSON format | Provider method returning `byte[]` |
| HTTP auth, tenant, basic request reject | Consumer/gateway |
| Large response streaming/file decision | Provider + consumer contract |
| Contract compatibility | Shared API jar + contract tests |

BEST: for hot reads and pass-through responses, return UTF-8 JSON `byte[]` from the provider and let
the consumer forward the native handle with `RawResponse.nativeResponse(handle.nativeId())`.
ACCEPTABLE: use `RawResponse.json(bytes)` when the consumer intentionally received Java bytes for
inspection or transformation, or return `record` for small typed business responses. ANTI-PATTERN:
produce a large nested `List<record>` and force the consumer JVM to serialize it again.

Method shape catalog:

| Method shape | Sample method | Why it exists | Cost |
|--------------|---------------|---------------|------|
| `byte[]` UTF-8 JSON | `getNestedCatalogJson()`<br>`getDatabaseCustomersJson()` | Lowest-overhead pass-through JSON | Lowest consumer allocation<br>no DTO graph |
| `String` | `getCatalogTitle()`<br>`getCustomerDisplayName(id)` | Small scalar data | Small allocation + Hessian decode |
| Primitive | `countCatalogItems()`<br>`customerExists(id)` | Counts and yes/no lookup | Very small object graph |
| `record` | `getCatalogInfo()`<br>`getCustomer(id)`, `getCustomerStats()` | Typed business data | Hessian materializes one record |
| `List<record>` | `listFeaturedItems(limit)`<br>`findCustomersBySegment(...)` | Small bounded pages | List + item records<br>strict limit |
| `Map<String,String>` | `getCatalogAttributes()` | Small metadata | Map allocation<br>avoid large maps on hot paths |
| `record -> record` command | `createCustomerTyped(...)` | Readable typed command contract | Request encode + response decode |
| `byte[] -> byte[]` command | `createCustomer(byte[])` | Lowest-allocation command pass-through | Provider owns validation/JSON shape |

Interface split:

| Interface | Implementation | Responsibility |
|-----------|----------------|----------------|
| `NestedCatalogService` | `NestedCatalogServiceImpl` | Static catalog examples: raw JSON bytes, scalar values, record, list, and map. |
| `CustomerQueryService` | `CustomerQueryServiceImpl` | PostgreSQL-backed query examples: raw JSON bytes, record lookup, list page, stats, string, boolean. |
| `CustomerCommandService` | `CustomerCommandServiceImpl` | POST/PATCH/DELETE style DB commands with both compact JSON bytes and typed command records. |

BEST: keep interfaces cohesive and small. ACCEPTABLE: one provider process can export multiple
interfaces on the same Dubbo port for a sample or a tightly related bounded context. ANTI-PATTERN:
put unrelated read/write operations into one god interface because it is easier to wire.

The provider uses one ZooKeeper session to register all interface nodes. Opening one ZooKeeper
client per exported interface would work, but it is unnecessary thread/session overhead.

## Per-Interface and Per-Method Execution Limits

Each exported Dubbo interface has its own execution bulkhead, and individual methods can override
that interface default. This answers two production questions:

- "How many concurrent service method executions can this interface implementation run by default?"
- "Does this specific method need a lower or higher limit than the rest of the interface?"

This is intentionally not a new thread pool per interface or method. A separate executor for every
service/method would add thread stack RSS and can hide overload in queues. The sample uses
low-overhead semaphore gates in front of the implementation:

```text
Dubbo request
  -> PlainDubboProvider ReflectiveInvoker
  -> method override gate if configured
  -> otherwise interface default gate
  -> service implementation method
```

If the limit is full, the provider rejects the invocation immediately. That fail-fast behavior is
better for p99 and memory than letting an unbounded queue grow.

Default sample limits:

| Scope | Property | Default | Reason |
|-------|----------|---------|--------|
| All services | `dubbo.provider.service.default.max-concurrent` | `16` | Safe fallback |
| `NestedCatalogService` | `dubbo.provider.service.NestedCatalogService.max-concurrent` | `16` | Catalog JSON CPU/allocation bound |
| `NestedCatalogService` typed methods | `dubbo.provider.service.NestedCatalogService.method.<method>.max-concurrent` | `8` | Typed DTO/list stays bounded |
| `CustomerQueryService` | `dubbo.provider.service.CustomerQueryService.max-concurrent` | `2` | Aligned with `sample.db.maximum-pool-size=2` |
| `getDatabaseCustomersJson` | `dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent` | `1` | DB-backed method override |
| Typed DB methods | `dubbo.provider.service.CustomerQueryService.method.<method>.max-concurrent` | `1-2` | Record/list/stats aligned with Hikari |
| `CustomerCommandService` | `dubbo.provider.service.CustomerCommandService.max-concurrent` | `2` | Write-side DB concurrency aligned with Hikari |
| Write methods | `dubbo.provider.service.CustomerCommandService.method.<method>.max-concurrent` | `1` | Predictable local sample writes |

You can also use the fully qualified interface name if simple names collide:

```properties
dubbo.provider.service.com.reactor.rust.dubbo.sample.CustomerQueryService.max-concurrent=2
dubbo.provider.service.com.reactor.rust.dubbo.sample.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=1
```

Override behavior:

| Configured value | Runtime behavior |
|------------------|------------------|
| No interface key | Uses `dubbo.provider.service.default.max-concurrent`. |
| Interface key only | All methods on that interface share the interface gate. |
| Method key exists | That method uses its own method gate instead of the interface default. |

Method override uses the method name. In this sample the interface methods are not overloaded. If you
introduce overloaded Dubbo methods, keep in mind that methods with the same name share the same
method gate.

BEST: set the service limit based on the real bottleneck behind that interface. For DB-backed
services, start at the Hikari max pool size or lower. For CPU-heavy serialization services, start at
available CPU capacity and validate p99 under load. ANTI-PATTERN: set every interface to a large
number and let the DB pool, heap, or Netty threads absorb overload.

## Provider Use Case Cookbook

These examples are called through the REST consumer, but the behavior is implemented here in the
provider. That split is intentional: the REST process stays small, while the provider owns DB access,
mutation rules, and Hikari capacity.

| Use case | Provider interface | Bottleneck | Start |
|----------|--------------------|------------|------:|
| Read static/nested catalog | `NestedCatalogService` | CPU/string generation | `16` |
| Read customers from PostgreSQL | `CustomerQueryService` | Hikari/PostgreSQL | <small><code>dubbo.provider.service.CustomerQueryService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=1</code></small> |
| Create/upsert customer | `CustomerCommandService.createCustomer` | Hikari/PostgreSQL unique key | <small><code>dubbo.provider.service.CustomerCommandService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent=1</code></small> |
| Patch segment/status | `CustomerCommandService.patchCustomer*` | Hikari/PostgreSQL update | <small><code>dubbo.provider.service.CustomerCommandService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerCommandService.method.patchCustomerStatus.max-concurrent=1</code></small> |
| Delete customer | `CustomerCommandService.deleteCustomer` | Hikari/PostgreSQL delete/audit | <small><code>dubbo.provider.service.CustomerCommandService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerCommandService.method.deleteCustomer.max-concurrent=1</code></small> |

### Use Case: Create Customer Command

REST caller:

```powershell
curl -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1001\",\"customerNo\":\"CUST-9001\",\"fullName\":\"Zeynep Şahin\",\"segment\":\"pilot\",\"email\":\"zeynep.sahin@example.com\"}"
```

Provider contract:

```java
public interface CustomerCommandService {
    byte[] createCustomer(byte[] commandJson);
}
```

Provider implementation shape:

```java
public byte[] createCustomer(byte[] commandJson) {
    // Parse only the command fields needed by the provider.
    // Do not materialize a large consumer-side DTO graph.
    SampleCustomer customer = repository.createCustomer(customerNo, fullName, segment, email);
    return readyJsonBytes(customer);
}
```

### Use Case: Patch Customer Segment

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/segment `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1002\",\"segment\":\"enterprise\"}"
```

Use this pattern for targeted changes. It is clearer and usually cheaper than a generic
`PUT /customers/{id}` that accepts every customer field.

### Use Case: Patch Customer Status

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1003\",\"status\":\"passive\"}"
```

Use lifecycle words that your domain understands: `active`, `passive`, `blocked`,
`pending-review`. If status changes trigger side effects, keep idempotency and audit handling in the
provider.

### Use Case: Delete Customer

```powershell
curl -X DELETE http://127.0.0.1:8080/api/v1/customers/3 `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1004\",\"reason\":\"sample cleanup\"}"
```

This sample performs a hard delete so the HTTP verb is visible and testable. In a real business
system, prefer a soft-delete/status change if audit, recovery, or downstream consistency matters.

### Property/Profile Guidance For Provider Users

| User problem | First change | Do not do this |
|--------------|--------------|----------------|
| "Writes are returning 503 under burst" | Increase `CustomerCommandService.max-concurrent` only up to Hikari capacity, then measure. | Do not set provider method limits to 100 while Hikari is `2`. |
| "DB is slow" | Increase `sample.db.connection-timeout-ms` slightly or fix DB latency; keep consumer route bounded. | Do not hide slow DB behind deep provider queues. |
| "Provider RSS is high" | Keep `sample.db.minimum-idle=0`, Netty arenas `1`, and QoS/metrics/tracing disabled. | Do not enable unused Dubbo governance features in this sample. |
| "Need stronger write semantics" | Add idempotency/audit table in provider and keep `requestId` in command JSON. | Do not implement idempotency in the REST consumer if DB mutation is provider-owned. |

## Package Structure

```text
com.reactor.sample.dubbo.provider.app
  Process entry point and provider bootstrap.

com.reactor.sample.dubbo.provider.config
  Properties-only runtime config and Netty/Dubbo tuning keys.

com.reactor.sample.dubbo.provider.db
  HikariCP, ActiveJDBC repository, and sample DB record model.

com.reactor.sample.dubbo.provider.service
  Dubbo service implementation.

com.reactor.sample.dubbo.provider.dubbo
  Minimal Dubbo export and runtime model.

com.reactor.sample.dubbo.provider.registry
  ZooKeeper provider registration.

com.reactor.rust.dubbo.sample
  Shared Dubbo interface examples. In production, move these to a shared API jar.
```

Main class:

```text
com.reactor.sample.dubbo.provider.app.RestSampleDubboProviderApplication
```

## DTO, Runtime Class, and Response Model

The Rust-Java framework standard is:

```text
HTTP JSON request/response DTO = Java record
Runtime behavior/resource owner = Java class
Already serialized JSON/RPC payload = byte[] + RawResponse
```

The classes in this provider are not HTTP JSON DTOs:

| Type | Role | JSON DTO? |
|------|------|-----------|
| `RestSampleDubboProviderApplication` | Process bootstrap and shutdown hook. | No |
| `ProviderProperties` | Reads and validates runtime properties. | No |
| `ProviderRuntimeTuning` | Applies Dubbo/Netty startup tuning. | No |
| `PlainDubboProvider` | Exports Dubbo protocol and owns exporter lifecycle. | No |
| `ZookeeperProviderRegistration` | Owns ZooKeeper session and ephemeral node lifecycle. | No |
| `PostgresCustomerRepository` | Owns DB access behavior and pool usage. | No |
| `NestedCatalogServiceImpl` | Dubbo business service implementation. | No |
| `CustomerQueryServiceImpl` | DB-backed Dubbo business service implementation. | No |
| `SampleCustomer` | Immutable DB row model. | Yes, record is correct. |

### Use Case: DB Row Model

Use records for immutable data rows:

```java
public record CustomerRow(long id, String customerNo, String fullName) {}
```

Use classes for repositories and resources:

```java
public final class CustomerRepository implements AutoCloseable {
    public List<CustomerRow> findCustomers() {
        return List.of();
    }
}
```

### Use Case: Provider Returns Ready JSON

Use `byte[]` when the provider intentionally returns ready-to-forward JSON:

```java
public byte[] getCatalogJson() {
    return jsonBytes;
}
```

This is the path used by this sample.

### Use Case: Object Contract Instead of JSON Bytes

If your Dubbo API should return domain objects, use records in a shared API jar:

```java
public record CatalogItem(String sku, String name) {}

public record CatalogResponse(String source, List<CatalogItem> items) {}

public interface CatalogService {
    CatalogResponse getCatalog();
}
```

That model is more object-oriented, but it adds serialization and object graph cost for the consumer.

### Provider Return Type Choices and Overhead

The provider can return a record, a class DTO, a `String`, or `byte[]`. The right choice depends on
what the consumer will do with the value.

| Provider return type | Best use case | Consumer cost |
|----------------------|---------------|---------------|
| `byte[]` containing UTF-8 JSON | Consumer forwards the response as HTTP JSON. | Lowest. Native handle routes use `RawResponse.nativeResponse(handle.nativeId())`; Java-byte routes use `RawResponse.json(bytes)`. Neither builds another DTO graph. |
| `record` DTO | Consumer needs typed data for filtering, validation, enrichment, or branching. | Hessian2 decode creates a Java object graph; HTTP response may serialize it again. |
| Plain class DTO | Needed for legacy frameworks or serializers that cannot handle records. | Similar to record, usually more mutable and less explicit. |
| `String` JSON | Small/simple payloads where readability is preferred over strict byte control. | String allocation and later UTF-8 encoding. Prefer `byte[]` for hot paths. |
| Large nested object graph | Only when the consumer truly needs the full object model. | Highest heap, GC, p99 latency, and RSS risk. |

For this sample, `byte[]` is intentional because the REST consumer does not need to understand the
catalog structure. It only needs to expose the provider response over HTTP. If the consumer gets a
native response handle, it should return `RawResponse.nativeResponse(handle.nativeId())`. If it gets
Java bytes because it must inspect the payload, it should return `RawResponse.json(bytes)`.

### Can This Provider Return a Record Directly?

Yes, if both sides use the same API contract jar:

```java
public record CatalogItem(String sku, String name) {}

public record CatalogResponse(String source, List<CatalogItem> items) {}

public interface CatalogService {
    CatalogResponse getCatalog();
}
```

Provider implementation:

```java
public final class CatalogServiceImpl implements CatalogService {
    @Override
    public CatalogResponse getCatalog() {
        return new CatalogResponse(
                "rest-sample-dubbo-provider",
                List.of(new CatalogItem("sku-1", "Demo Item")));
    }
}
```

Consumer usage:

```java
NativeDubboMethodInvoker<CatalogResponse> invoker =
        client.method(spec, "getCatalog", CatalogResponse.class);
```

This is a valid object contract, but the wire path is still Hessian2 serialization:

```text
CatalogResponse record
  -> Hessian2 serialize on provider
  -> Dubbo TCP frame
  -> Rust native transport on consumer side
  -> Java Hessian2 decode
  -> new CatalogResponse record instance
```

Hessian Lite 4.0.3 can handle simple Java 21 records in this project environment. Treat that as a
minimum smoke signal, not a substitute for a real compatibility test. Add a contract test for every
record shape that matters: nested records, lists, maps, enums, dates, nulls, and versioned fields.

### Provider-Side Decision Examples

Use ready JSON for read-heavy endpoints:

```java
public byte[] getCatalogJson() {
    return catalogJsonWriter.writeAsUtf8Bytes();
}
```

Use records when the consumer must own business decisions:

```java
public CatalogResponse getCatalog() {
    return catalogRepository.loadCatalogResponse();
}
```

Avoid this for hot paths:

```java
public CatalogResponse getCatalogOnlyToBeConvertedBackToJson() {
    return catalogRepository.loadLargeCatalog();
}
```

If the REST consumer immediately converts that object back to JSON, the system paid for Hessian
object materialization and JSON serialization without gaining business value. In that case, return
UTF-8 JSON bytes from the provider or design a streaming response.

## Dependencies

Provider process is heavier than the consumer because it is a real Dubbo server. The dependency set is
kept explicit:

| Dependency | Purpose |
|------------|---------|
| `dubbo-rpc-dubbo` | Classic `dubbo://` protocol export. |
| `dubbo-remoting-netty4` + `netty-handler` | TCP server transport. |
| `dubbo-serialization-hessian2` | Hessian2 payload compatibility. |
| `zookeeper` | Provider URL registration with ephemeral nodes. |
| `activejdbc` | Simple JDBC access without Spring. |
| `postgresql` | PostgreSQL JDBC driver. |
| `HikariCP` | Bounded JDBC connection pool. |
| `slf4j-nop` | Quiet sample runtime logging. |

Intentionally excluded:

- Spring Boot
- Dubbo Spring Boot starter
- Dubbo consumer/reference/config stack
- Dubbo governance/router features
- Netty proxy/socks/http2/epoll native packages
- Spring JDBC/AOP starters

Note: some Dubbo metrics/API classes remain on the classpath because Dubbo server bytecode references
them. Runtime metrics, tracing, and QoS are disabled by properties.

## Run Order With the v3.2.x Consumer

Use this order for the cleanest local test:

```text
1. Start ZooKeeper.
2. Start PostgreSQL if DB-backed endpoints are enabled.
3. Start this provider.
4. Start rest-sample-dubbo-consumer on rust-java-rest 3.2.x.
5. Call the consumer REST endpoints, not the provider directly.
```

The provider is intentionally a plain Java Dubbo server. The low-RSS HTTP behavior belongs to the
consumer process that runs `rust-java-rest`; this provider preserves that behavior by returning
ready-to-forward JSON bytes and by keeping server-side concurrency bounded.

## Configuration

Main config file:

```text
src/main/resources/rest-sample-dubbo-provider.properties
```

Read order:

```text
system property > environment variable > classpath properties
```

Runtime values live in the properties file. Missing or invalid properties fail fast.

Important properties:

| Property | Purpose |
|----------|---------|
| `dubbo.provider.host` | Host advertised in the Dubbo provider URL. |
| `dubbo.provider.bind-host` | Local bind host. Use `0.0.0.0` in containers if needed. |
| `dubbo.provider.port` | Dubbo provider port. Default sample value is `20880`. |
| `reactor.dubbo.registry-enabled` | Enables ZooKeeper registration. Sample default is `false` for static Service DNS mode; set `true` for ZooKeeper discovery. |
| `reactor.dubbo.registry-address` | ZooKeeper registry address. Used only when `reactor.dubbo.registry-enabled=true`. |
| `reactor.dubbo.registry-root` | ZooKeeper namespace. Used only when `reactor.dubbo.registry-enabled=true`. |
| `dubbo.provider.service.default.max-concurrent` | Default concurrent invocation limit for exported interfaces. |
| `dubbo.provider.service.NestedCatalogService.max-concurrent` | Concurrent invocation limit for catalog provider methods. |
| `dubbo.provider.service.NestedCatalogService.method.*.max-concurrent` | Optional method-level catalog overrides for typed/list methods. |
| `dubbo.provider.service.CustomerQueryService.max-concurrent` | Concurrent invocation limit for DB-backed customer provider methods. |
| `dubbo.provider.service.CustomerQueryService.method.*.max-concurrent` | Method-level overrides for raw JSON, record lookup, list query, and stats methods. Keep DB-backed methods aligned with Hikari. |
| `dubbo.provider.service.CustomerCommandService.max-concurrent` | Concurrent invocation limit for write-side customer commands. |
| `dubbo.provider.service.CustomerCommandService.method.*.max-concurrent` | Method-level write command overrides for byte command and typed command methods. Keep aligned with Hikari. |
| `sample.db.jdbc-url` | PostgreSQL JDBC URL. |
| `sample.db.maximum-pool-size` | Hikari maximum pool size. |
| `sample.db.minimum-idle` | Hikari minimum idle connections. `0` keeps idle RSS lower. |
| `sample.db.connection-timeout-ms` | Maximum time to wait for a DB connection. Default `3000` keeps local cold start usable while still failing fast. |
| `sample.db.schema-init` | Creates demo table and data when true. |
| `sample.db.warmup` | Opens DB connection and seeds data before provider is ready. |
| `io.netty.allocator.numDirectArenas` | Low-RSS Netty allocator tuning. |

## Quick Start

Prerequisites:

- Java 21
- Maven 3.9+
- Docker for PostgreSQL; ZooKeeper is needed only when you set `reactor.dubbo.registry-enabled=true`

Default sample mode is static/no-ZooKeeper. Start ZooKeeper only if you want to test discovery mode:

```powershell
docker run -d --name rust-java-dubbo-zookeeper -p 2181:2181 zookeeper:3.7.2
```

For static mode, you can skip ZooKeeper. The packaged default is already `false`, but setting the
environment variable makes the runtime choice explicit:

```powershell
$env:REACTOR_DUBBO_REGISTRY_ENABLED="false"
mvn -q exec:java
```

Start PostgreSQL:

```powershell
docker run -d --name rest-sample-postgres `
  -e POSTGRES_DB=reactor_sample `
  -e POSTGRES_USER=reactor `
  -e POSTGRES_PASSWORD=reactor `
  -p 15432:5432 `
  postgres:16-alpine
```

Wait until PostgreSQL is ready before starting the provider:

```powershell
docker exec rest-sample-postgres pg_isready -U reactor -d reactor_sample
```

The sample uses a bounded Hikari pool and `sample.db.connection-timeout-ms=3000`. That value is long
enough for a normal local cold start but still short enough to fail fast when the DB is actually down.

Build and run:

```powershell
git clone git@github.com:esasmer-dou/rest-sample-dubbo-provider.git
cd rest-sample-dubbo-provider
mvn -q test
mvn -q exec:java
```

Expected startup output:

```text
[rest-sample-dubbo-provider] database warmup completed
[rest-sample-dubbo-provider] exported dubbo://127.0.0.1:20880/com.reactor.rust.dubbo.sample.NestedCatalogService...
[rest-sample-dubbo-provider] exported dubbo://127.0.0.1:20880/com.reactor.rust.dubbo.sample.CustomerQueryService...
[rest-sample-dubbo-provider] execution limits NestedCatalogService=16, CustomerQueryService=2 methods={getDatabaseCustomersJson=1}
[rest-sample-dubbo-provider] registered at zookeeper://127.0.0.1:2181/dubbo
```

## Test With Consumer

Start the consumer:

```powershell
git clone git@github.com:esasmer-dou/rest-sample-dubbo-consumer.git
cd rest-sample-dubbo-consumer
mvn -q exec:java
```

Call through the REST consumer:

```powershell
curl http://127.0.0.1:8080/api/v1/catalog/nested
curl http://127.0.0.1:8080/api/v1/customers/db
curl http://127.0.0.1:8080/api/v1/catalog/db/customers
```

Expected DB-backed response includes:

```json
{
  "source": "rest-sample-dubbo-provider",
  "storage": "postgresql-activejdbc-hikari",
  "customers": [
    {
      "customerNo": "CUST-1001",
      "fullName": "Mustafa Korkmaz",
      "segment": "pilot"
    }
  ]
}
```

## Container/Kubernetes Notes

For container environments, override bind/advertised host values explicitly:

```powershell
$env:DUBBO_PROVIDER_BIND_HOST="0.0.0.0"
$env:DUBBO_PROVIDER_HOST="catalog-provider"
$env:REACTOR_DUBBO_REGISTRY_ADDRESS="zookeeper://zookeeper:2181"
$env:SAMPLE_DB_JDBC_URL="jdbc:postgresql://postgres:5432/reactor_sample"
mvn -q exec:java
```

Use a small Hikari pool first. Increasing DB pool size without measuring provider CPU, DB capacity,
and consumer concurrency can worsen tail latency.

### OpenJ9 21 jlink Minimum Image

Use `Dockerfile.jlink` when you want the provider sample to run on a custom OpenJ9 runtime instead
of a full JRE image. The image is still a real Dubbo provider with Hikari/PostgreSQL support; jlink
only trims unused Java runtime modules.

Build:

```powershell
docker build -f docker/images/Dockerfile.jlink -t rest-sample-dubbo-provider:jlink .
```

Minimum provider smoke without PostgreSQL warmup:

```powershell
docker network create reactor-jlink-smoke

docker run --rm --name rest-sample-dubbo-provider `
  --network reactor-jlink-smoke `
  -p 20880:20880 `
  -e DUBBO_PROVIDER_HOST=rest-sample-dubbo-provider `
  -e DUBBO_PROVIDER_BIND_HOST=0.0.0.0 `
  -e REACTOR_DUBBO_REGISTRY_ENABLED=false `
  -e SAMPLE_DB_SCHEMA_INIT=false `
  -e SAMPLE_DB_WARMUP=false `
  rest-sample-dubbo-provider:jlink
```

Provider with PostgreSQL/Hikari enabled:

```powershell
docker run --rm --name rest-sample-dubbo-provider `
  --network reactor-jlink-smoke `
  -p 20880:20880 `
  -e DUBBO_PROVIDER_HOST=rest-sample-dubbo-provider `
  -e DUBBO_PROVIDER_BIND_HOST=0.0.0.0 `
  -e REACTOR_DUBBO_REGISTRY_ENABLED=false `
  -e SAMPLE_DB_JDBC_URL=jdbc:postgresql://postgres:5432/reactor_sample `
  -e SAMPLE_DB_USERNAME=reactor `
  -e SAMPLE_DB_PASSWORD=reactor `
  -e SAMPLE_DB_MAXIMUM_POOL_SIZE=2 `
  -e SAMPLE_DB_MINIMUM_IDLE=0 `
  rest-sample-dubbo-provider:jlink
```

Notes:

- Do not remove `java.desktop` from `JAVA_MODULES`. Dubbo uses Java Beans classes even in a headless provider.
- `binutils` is installed only in the build stage because `jlink --strip-debug` needs `objcopy`. It is not copied to the runtime image.
- Local smoke in this workspace observed about `55.8 MiB` provider RSS with DB warmup disabled. DB-backed runs will be higher because Hikari, JDBC, schema init, and active connections add memory.
- `docker/images/Dockerfile.jlink.db-query` builds with `mvn clean package` under the `db-query-provider` profile so old compiled classes cannot leak command/catalog services into the query-only jar.
- If `REACTOR_DUBBO_REGISTRY_ENABLED=false`, ZooKeeper is not required; the consumer can call the provider through Docker/Kubernetes service DNS.

## Troubleshooting

| Symptom | Check |
|---------|-------|
| Provider does not start | Verify ZooKeeper is listening on `2181`. |
| DB endpoint fails | Verify PostgreSQL is listening on `15432` and credentials match properties. |
| Consumer cannot call provider | Verify provider is listening on `20880`. |
| ZooKeeper discovery does not work | Verify provider node exists under `/dubbo/.../providers`. |
| High RSS | Keep Hikari pool small, disable unused Dubbo features, and avoid large Java DTO graphs. |

## Production Notes

- Move `NestedCatalogService` and `CustomerQueryService` to a shared API jar before real use.
- Keep runtime properties explicit.
- Use records for domain DTO contracts.
- Use classes for lifecycle/resource owners.
- Keep DB migration outside the hot provider process in production.
- Protect operational endpoints and secrets outside this sample.
