# Native Provider And Official Baseline

[English](NATIVE_PROVIDER.md) | [Türkçe](NATIVE_PROVIDER.tr.md)

Use these optional profiles to compare the same Java catalog service with the
official Dubbo engine and the shared native engine. Neither profile changes the
existing default provider. These profiles are present in this `0.8.0` source
checkout; build matching modules and native assets together. This guide does not
establish published package availability. The recorded focused warmed-load comparison
is separate from target-platform distribution and combined product qualification.

## Choose A Profile

| Profile | Entrypoint | Output directory | Configuration resource |
| --- | --- | --- | --- |
| `stream-catalog-provider` | `com.reactor.sample.dubbo.provider.streamcatalog.StreamCatalogProviderApplication` | `target/stream-catalog-provider` | `stream-catalog-provider.properties` |
| `native-provider` | `com.reactor.sample.dubbo.provider.nativeprovider.NativeProviderApplication` | `target/native-provider` | `native-provider.properties` |

Both export `CatalogJsonService` and `StreamCatalogService`, with empty group
and version `0.0.0`, without a database or ZooKeeper. Both instantiate the same
`CatalogJsonServiceImpl` and `StreamCatalogServiceImpl`. Use the official
profile as the control for engine comparisons, not an older, different payload.
Do not activate both build profiles together. Their separate output directories
prevent stale classes from crossing between builds.

The native profile bans official Dubbo/HessianLite, Netty, ZooKeeper/Curator,
Spring, and build-only annotation/processor artifacts from its dependency tree.
The original API JAR still contains unused legacy classes; this is not the same
as loading the official engine.

## Build And Run

Requirements: Java 21, Maven 3.9+, the current source-built dependencies in your
Maven repository, and an explicitly provisioned Linux SO with the current ABI-3
capabilities. A published runtime version or ABI number alone is insufficient.
Build the API, shared model/utility and shared runtime from the coordinated
source revision first. Run the examples from the workspace root in your own
matching Linux build environment. They do not provision or change infrastructure.

Build-only commands, with test compilation/execution disabled:

```bash
mvn -B -ntp -f java-rust-dubbo/native-provider-build/pom.xml -Dmaven.test.skip=true install
mvn -B -ntp -f rest-sample-dubbo-provider/pom.xml -Pstream-catalog-provider -Dmaven.test.skip=true package
mvn -B -ntp -f rest-sample-dubbo-provider/pom.xml -Pnative-provider -Dmaven.test.skip=true package
```

Run each in its own terminal. The different ports permit a connectivity check
on one host; do not treat competing processes on one CPU as an isolated load test.

```bash
java -Xms16m -Xmx48m -XX:ActiveProcessorCount=1 \
  -Dsample.provider.engine=official -Dreactor.dubbo.registry-enabled=false \
  -Ddubbo.provider.port=20880 \
  -jar rest-sample-dubbo-provider/target/stream-catalog-provider/rest-sample-dubbo-provider-0.8.0.jar
```

Set `NATIVE_LIB` to the current verified SO, not an older packaged library:

```bash
NATIVE_LIB=/opt/reactor/native/librust_dubbo.so
java -Xms16m -Xmx48m -XX:ActiveProcessorCount=1 \
  -Dsample.provider.engine=native -Dreactor.dubbo.registry-enabled=false \
  -Ddubbo.provider.port=20881 "-Dreactor.dubbo.native.path=$NATIVE_LIB" \
  -jar rest-sample-dubbo-provider/target/native-provider/rest-sample-dubbo-provider-0.8.0.jar
```

JVM options do not enforce container CPU/RSS limits. Restrict the listener to the
owned test environment: the native engine binds all interfaces. Stop with
`Ctrl+C` or normal termination so its shutdown hook can drain.

## Typed Catalog And HTTP Use

The exact wire contract is
`com.reactor.rust.dubbo.sample.StreamCatalogService.read(int count)`, returning
`List<com.reactor.rust.dubbo.sample.dto.StreamCatalogRow>`.

| Record | Fields |
| --- | --- |
| `StreamCatalogRow` | `long id`, `String name`, `double amount`, `boolean active`, `StreamCatalogDetails details` |
| `StreamCatalogDetails` | `String city`, `List<String> tags`, nullable `String note` |

The fixture includes Turkish/supplementary Unicode, escaping, null notes, signed
zero and difficult finite doubles. Counts `0..10000` are business-valid and
never truncated. The 32/512-row cases reuse immutable precomputed lists; counts
above 512 construct additional rows and are not low-allocation workloads.
Business bounds do not override the provider's 4096-item output cap or payload
cap; a larger result can fail rather than be silently shortened.

`CatalogJsonService.getNestedCatalogJson()` retains its existing JSON-byte
response. The typed service carries `@DubboStreamed(directJson=true)`; its
annotation dependency is provided/optional in utility, not a transitive runtime
requirement. Use the real interface and binary DTO names, not an alias contract.

For the existing `direct-json-consumer` profile, select the provider with
`sample.direct-json.providers=127.0.0.1:20880` or `127.0.0.1:20881`.
Its RPC timeout defaults to 3000 ms; set `sample.direct-json.timeout-ms=6000`
when using the native provider's 5000 ms deadline, and allow a longer outer HTTP deadline.
With that consumer running on its configured HTTP port (default 8080):

```bash
curl --fail-with-body 'http://127.0.0.1:8080/api/v1/catalog/rows?count=32'
curl --fail-with-body 'http://127.0.0.1:8080/api/v1/catalog/rows?count=512'
```

Provider argument decoding calls record canonical constructors, including their
validation/normalization. Consumer direct-JSON projection does **not** construct
row/detail DTOs and therefore does not execute those constructors. Do not rely
on constructor side effects or validation on that projection path.

For constructor checks, set `-Dsample.stream-catalog.count-constructors=true`
before class initialization. `StreamCatalogRow.constructionCount()` and
`StreamCatalogDetails.constructionCount()` are cumulative, per-JVM counters;
compare post-warmup deltas, not provider and consumer totals. They are disabled
by default and are not a complete allocation measurement. The sample consumer's
`/api/v1/catalog/diagnostics` endpoint exposes them; keep diagnostics private.

## Configuration

Precedence: JVM system property, environment variable, external
`reactor.config.file` / `REACTOR_CONFIG_FILE`, then the profile resource.
For environment keys, uppercase and replace dots/hyphens with underscores.

| Property | Native sample default | Allowed value |
| --- | ---: | --- |
| `sample.provider.engine` | `native` | `native` only; baseline requires `official` |
| `reactor.dubbo.registry-enabled` | `false` | `false` in both profiles |
| `dubbo.provider.port` | 20880 | 1..65535 |
| `sample.native-provider.io-workers` | 1 | 1..8 |
| `sample.native-provider.business-workers` | 4 | 1..64 |
| `sample.native-provider.queue-capacity` | 16 | 1..1024 |
| `sample.native-provider.max-payload-bytes` | 262144 | 1024..1048576 |
| `sample.native-provider.request-timeout-ms` | 5000 | 1..60000 |
| `sample.native-provider.drain-timeout-ms` | 5000 | 0..60000 |
| `sample.native-provider.max-collection-items` | 1024 | 1..4096 |
| `sample.native-provider.service-max-concurrent` | 4 | 1..business-workers |

`reactor.dubbo.native.path` is an explicit JVM system property. Native host/bind
overrides other than `0.0.0.0` are rejected, not silently ignored. Engine and
registry mismatches fail startup; there is no fallback or transparent RPC replay.
The baseline uses four fixed business workers, queue 16, one IO worker and
service cap 4; its official executor keys are in its configuration resource.

`(business-workers + queue-capacity) * max-payload-bytes` must not exceed 32 MiB.
The sample defaults imply 5 MiB logical admitted-request capacity, not a 5 MiB
slab or total RSS reservation. The adapter API's `Options.defaults` instead uses
queue 64, implying 17 MiB. Full service/schema/parser and global admission bounds
are in the [adapter guide](https://github.com/esasmer-dou/java-rust-dubbo/blob/master/native-provider/README.md).

## Ownership, Monitoring And Troubleshooting

The adapter deliberately reuses the shared ABI-3 JNI/Tokio engine, rather than
adding a second Dubbo network stack to `rust-spring`. Java business logic and
generated Java dispatch/codecs remain unchanged by that placement choice.

The native engine learns a connection-local header plan of at most 1 KiB from
the first eligible primitive call. It checks scalar wire arguments, not just the
method signature. Matching service, method, version and
attachment bytes no longer need fresh objects on each call. Arguments are still
validated on every request, and the business method still executes every time.
This is not a response cache. Changed headers or object/collection arguments use
the existing bounded parser. The learned plan is immutable, so variable headers
do not allocate replacement cache entries on every call. No additional property
or annotation is needed.

The output registry now uses 256 fixed generation-tagged atomic slots; stale
handles cannot act on a newer generation. Timeout/drain retain borrowed bytes
and admission until the actual Java callback finishes. Outbound bodies retain
their credit through socket write/drop. A callback has one writer per handle
and must stop using old buffer views after growth.

Pending completions now share the atomic output slots; there is no separate
pending-map mutex. The engine is **not wholly lock-free**: the provider-registry
mutex remains for startup, stop and metrics, not per RPC. Per-connection writes
use a Tokio async mutex: waiting does not block an OS thread, but complete frames
are serialized, so a slow write can delay later responses on that connection.
The separate consumer-response registry is not on this provider path.
See the [adapter guide](https://github.com/esasmer-dou/java-rust-dubbo/blob/master/native-provider/README.md) for metrics.

| Symptom | Action |
| --- | --- |
| Missing native capability/parser-policy gauges | Use the matching verified SO; do not disable the startup checks. |
| Wrong engine, registry enabled, or unsupported bind host | Correct the profile/overlay; do not mix engine configurations. |
| Payload, collection or capacity rejection | Reduce result size or tune within the declared budgets; never replay an uncertain business call automatically. |
| Outstanding handles after timeout | Check whether a callback/socket still owns them; timeout is not permission to free borrowed memory. |
| Restart in the same JVM rejected | Start a new JVM; native registration has no unregister. |

Set downstream deadlines below the provider deadline, and the consumer/outer
HTTP deadlines above it. Allow termination grace beyond drain plus a margin;
already-running Java code is not forcibly cancelled.

## Verification And Remaining Work

On 27 October 2026, the current frozen source passed EmaTech functional checks:
74 Rust tests (57 native + 17 core) and 171 Java tests (164 + 7 sample).
These focused results include response ownership, bounded consumer handles,
callback admission, partial vectored writes and pending completions.
Two unsupported OpenJ9 allocation checks were aborted, not counted as
successes; eight separate calibrated HotSpot allocation checks passed.
Linux build, fmt and Clippy checks passed; maximum GLIBC symbol remains 2.14,
within the 2.17 ceiling. Functional success is not performance or production acceptance.

All nine warmed-load cells passed across four new independent process pairs.
Each application kept its 1 CPU/128 MiB limit. Useful RPS stayed at least 0.98x,
p99 at most 1.10x; every target cell reduced provider RSS, and final heap decreased.
There were no HTTP, body or socket errors. Each arm was warmed immediately at
its measured concurrency. The previous plan warmed only at c16 before treating
the first c64/c256 windows as steady. Limits, measurement duration, workers and
queues were not increased. These recorded Linux results do not verify the current
Windows DLL/parser policy, platform packaging or a completed product release.
Check matching assets in the target environment rather than inferring availability
or qualification from this comparison.

Provider dispatch now binds the exact JNI method at startup. Per-call signature
parsing, method lookup and the temporary native argument array are removed.
The Java method and lifetime rules are unchanged.

Latest comparison using the same Java service on both engines:

| Response / concurrent requests | Useful RPS change | p99 change | Provider RSS change |
| --- | ---: | ---: | ---: |
| 32 rows / 16 | +21.43% | -4.27% | -19.90 MiB |
| 32 rows / 64 | +15.87% | -11.71% | -20.17 MiB |
| 32 rows / 256 | +15.70% | -17.63% | -20.35 MiB |
| 512 rows / 16 | +21.34% | -13.85% | -24.22 MiB |
| 512 rows / 64 | +17.81% | -17.44% | -25.72 MiB |
| 512 rows / 256 | +20.89% | -15.47% | -27.10 MiB |

Changes are medians of four paired ratios, not the gain from header planning alone.
Every request still executed an RPC and the Java business method; no response
cache was used. All three unchanged health-control cells also passed. The paired
median final-heap change was -7.04 MiB.

This qualifies warmed 32/512-row reads, not cold-start, the first request after a
long idle period, complex-argument load or combined all-feature acceptance. This
source profile remains optional; existing provider profiles are unchanged.
The measurements above were executed in EmaTech. Repeat the relevant checks in
your own bounded test environment before selecting production settings.
