# rest-sample-dubbo-provider 0.8.2

[English](RELEASE_NOTES_v0.8.2.md) | [Türkçe](RELEASE_NOTES_v0.8.2.tr.md)

Coordinated update: November 1, 2026.

## Dependencies

Use platform parent `com.reactor:rust-java-platform-parent:4.8.2`. Entries for the
existing POM; retain the selected provider profile, PostgreSQL/Hikari and plugins:

```xml
<dependency>
  <groupId>com.reactor</groupId>
  <artifactId>java-rust-dubbo</artifactId>
  <version>0.9.2</version>
</dependency>
<dependency>
  <groupId>com.reactor.sample</groupId>
  <artifactId>rest-sample-utility</artifactId>
  <version>0.5.2</version>
</dependency>
```

Utility brings model `0.5.2`; rebuild consumer and provider against the same contracts.
Independent Spring Dubbo adapter/native artifacts stay at `0.8.1` (ABI `3`).
See [Maven access](../README.md#maven-package-access). For the normal DB provider:
`mvn -B -ntp -DskipTests -Pfull-provider clean package`. Skipping tests is not acceptance.

## Existing DB And Admission Examples

The [DB/concurrency recipe](../README.md#database-and-concurrency-limits) keeps the
small Hikari pool, bounded executor/queue and query/command/method caps together.
Consumer `@KeyAdmission` guards are process-local, not distributed DB locks;
transactions, row locks and the existing typed-status idempotency example still
own consistency. No SQL/business-contract migration is introduced.

The full provider supports the customer command/query flows used by
[Compose](../README.md#compose). The `native-provider` overlay is catalog-only;
its SQL-driven writer/reader smoke does not prove customer RPC. The separate
`native-db-query-provider` source profile remains read-only/query-only, not the
full provider. Readiness selection was first introduced in consumer `0.8.1`.

## Upgrade And Evidence

No JNI ABI or configuration-key migration is required. Run the `0.8.2` JAR for the
selected profile and use its matching packaged native files, not an older SO/DLL.
These notes do not establish publication or full qualification. Use the exact
candidate's receipt; hosted compatibility is separate from EmaTech performance/soak
evidence and does not establish a new RPS/p99/memory PASS.
