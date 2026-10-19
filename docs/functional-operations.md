# Retry-Safe Status Commands

[English](functional-operations.md) | [Turkish](functional-operations.tr.md)

Existing behavior remains the default.

## Enable The Example

```properties
sample.command.idempotency.enabled=true
sample.command.idempotency.timeout-seconds=2
sample.command.idempotency.retention-ms=86400000
sample.command.idempotency.max-result-bytes=65536
```

Only `patchCustomerStatusTyped` uses this opt-in policy.
It requires a nonblank requestId. Retries must preserve both requestId and input.
The same key and status return the original result without a second update.
The same key and different status produce a conflict. Other write methods are unchanged.

The status update and stored response use the same PostgreSQL connection and transaction.
Two replicas share the same table; no process-local deduplication map is used.
The sample scopes the key by operation and customer ID. Add a trusted tenant scope in a multi-tenant service.

Demo schema initialization creates `reactor_command_result` and its expiry index.
It runs on first repository use, not during object construction. A failed DB check keeps readiness
down; it does not silently retry a write. Keep the readiness probe in your deployment.
In production, apply the `PostgresIdempotency.SCHEMA` and `EXPIRY_INDEX` constants through a migration.
Schedule bounded `deleteExpired(batchSize)` cleanup.
TTL is not a database size cap: monitor row count and storage.
After retention expires, the same request ID may execute again.

The timeout applies to each SQL statement, not the whole operation.
The Hikari acquisition timeout and RPC deadline still apply.
There is no hidden write retry and no promise of exactly-once external side effects.

## First Connection And Timeouts

The first JDBC connection can be slower in a CPU-limited container.
Use `sample.db.initialization-fail-timeout-ms=30000` when startup should wait for an initial connection.
This controls Hikari startup validation. It does not increase the request timeout or pool size.
An unavailable database can still fail startup after that budget; keep readiness checks enabled.

With a 2-second SQL timeout and a 1-second consumer RPC timeout, the consumer can give up first.
The provider may still commit. Replay the same `requestId`, not a new key.
Measure RPC budgets against connection wait, SQL work and response preparation together.
A longer timeout does not add capacity. Bound the number of waiting requests separately.

## Smaller Buffers Without More Threads

The sample applies the provider library's low-RSS defaults before Netty starts.
The new buffer settings are explained in the [Dubbo guide](https://github.com/esasmer-dou/java-rust-dubbo/blob/master/docs/functional-operations.md#native-callbacks-and-provider-buffers).
No DB pool, executor, route limit or timeout was increased.
The catalog example still creates a fresh timestamp for every response.
It joins pre-encoded static JSON sections instead of formatting the entire response on every call.
This is not a response cache. Database queries and Java business rules keep running as before.
