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
- How to add PostgreSQL access through HikariCP and ActiveJDBC without Spring.
- How to separate runtime classes from record DTOs.

This is not a generic enterprise Dubbo provider template. It is a focused provider used to validate
the Rust-Java REST Dubbo consumer path.

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

## How `rust-java-rest` 3.1.0 Affects This Provider

This provider does not depend on `rust-java-rest`, and it should stay that way. Its job is to expose
a small Dubbo contract that lets the `rest-sample-dubbo-consumer` use the v3.1 low-overhead response
path.

| Provider choice | Effect on the v3.1 consumer |
|-----------------|----------------------------|
| Return UTF-8 JSON as `byte[]` | Consumer can return `RawResponse.json(bytes)` and avoid a second DTO graph. |
| Keep interfaces small | Consumer can tune timeouts, backpressure, and metrics per RPC area. |
| Keep method concurrency bounded | Provider overload becomes explicit instead of turning into heap, DB pool, or Netty queue growth. |
| Align DB method limits with Hikari | Consumer p99 is more stable because DB saturation fails fast instead of queueing deeply. |
| Avoid huge object graphs for pass-through responses | v3.1 improvements are preserved instead of being erased by Hessian materialization and JSON reserialization. |

Turkish characters are safe in this flow when the provider writes UTF-8 JSON bytes and the consumer
returns them with JSON content type. Do not build JSON through platform-default encodings.

BEST: return `byte[]` for read-heavy pass-through JSON. ACCEPTABLE: return records when the consumer
must make typed business decisions. ANTI-PATTERN: return a large nested object graph only for the
consumer to convert it back to JSON.

## Architecture

```text
Dubbo consumer
  -> ZooKeeper provider URL or static host:port
  -> PlainDubboProvider
  -> selected service implementation
  -> JSON byte[]
```

The provider intentionally exposes two small interfaces instead of one large RPC surface:

```java
public interface NestedCatalogService {
    byte[] getNestedCatalogJson();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();
}
```

The consumer can forward those bytes through `RawResponse.json(...)` without building another DTO
graph.

Interface split:

| Interface | Implementation | Responsibility |
|-----------|----------------|----------------|
| `NestedCatalogService` | `NestedCatalogServiceImpl` | Static nested catalog JSON. |
| `CustomerQueryService` | `CustomerQueryServiceImpl` | PostgreSQL-backed customer JSON through HikariCP/ActiveJDBC. |

BEST: keep interfaces cohesive and small. ACCEPTABLE: one provider process can export multiple
interfaces on the same Dubbo port for a sample or a tightly related bounded context. ANTI-PATTERN:
put unrelated read/write operations into one god interface because it is easier to wire.

The provider uses one ZooKeeper session to register both interface nodes. Opening one ZooKeeper
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

| Interface | Property | Default | Reason |
|-----------|----------|---------|--------|
| All services | `dubbo.provider.service.default.max-concurrent` | `16` | Safe fallback for small sample services. |
| `NestedCatalogService` | `dubbo.provider.service.NestedCatalogService.max-concurrent` | `16` | CPU/allocation bounded catalog JSON generation. |
| `CustomerQueryService` | `dubbo.provider.service.CustomerQueryService.max-concurrent` | `2` | Aligned with `sample.db.maximum-pool-size=2` to avoid DB-pool queue buildup. |
| `CustomerQueryService.getDatabaseCustomersJson` | `dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent` | `1` | Demonstrates method-level override for the DB-backed method. |

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
| `byte[]` containing UTF-8 JSON | Consumer forwards the response as HTTP JSON. | Lowest. Consumer uses `RawResponse.json(bytes)` and does not build another DTO graph. |
| `record` DTO | Consumer needs typed data for filtering, validation, enrichment, or branching. | Hessian2 decode creates a Java object graph; HTTP response may serialize it again. |
| Plain class DTO | Needed for legacy frameworks or serializers that cannot handle records. | Similar to record, usually more mutable and less explicit. |
| `String` JSON | Small/simple payloads where readability is preferred over strict byte control. | String allocation and later UTF-8 encoding. Prefer `byte[]` for hot paths. |
| Large nested object graph | Only when the consumer truly needs the full object model. | Highest heap, GC, p99 latency, and RSS risk. |

For this sample, `byte[]` is intentional because the REST consumer does not need to understand the
catalog structure. It only needs to expose the provider response over HTTP.

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

## Run Order With the v3.1 Consumer

Use this order for the cleanest local test:

```text
1. Start ZooKeeper.
2. Start PostgreSQL if DB-backed endpoints are enabled.
3. Start this provider.
4. Start rest-sample-dubbo-consumer on rust-java-rest 3.1.0.
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
| `reactor.dubbo.registry-address` | ZooKeeper registry address. |
| `dubbo.provider.service.default.max-concurrent` | Default concurrent invocation limit for exported interfaces. |
| `dubbo.provider.service.NestedCatalogService.max-concurrent` | Concurrent invocation limit for catalog provider methods. |
| `dubbo.provider.service.CustomerQueryService.max-concurrent` | Concurrent invocation limit for DB-backed customer provider methods. |
| `dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent` | Method-level override for the customer DB method. |
| `sample.db.jdbc-url` | PostgreSQL JDBC URL. |
| `sample.db.maximum-pool-size` | Hikari maximum pool size. |
| `sample.db.minimum-idle` | Hikari minimum idle connections. `0` keeps idle RSS lower. |
| `sample.db.schema-init` | Creates demo table and data when true. |
| `sample.db.warmup` | Opens DB connection and seeds data before provider is ready. |
| `io.netty.allocator.numDirectArenas` | Low-RSS Netty allocator tuning. |

## Quick Start

Prerequisites:

- Java 21
- Maven 3.9+
- Docker for ZooKeeper and PostgreSQL

Start ZooKeeper:

```powershell
docker run -d --name rust-java-dubbo-zookeeper -p 2181:2181 zookeeper:3.7.2
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
