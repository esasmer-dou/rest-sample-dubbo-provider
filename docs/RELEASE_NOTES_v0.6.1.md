# rest-sample-dubbo-provider 0.6.1

`0.6.1` aligns the runnable provider with `rust-java-rest:4.3.0`,
`java-rust-dubbo:0.7.1`, `rest-sample-utility:0.4.1`, and `rust-sample-model:0.4.1`.

- Provider interfaces, Java service implementations, database profiles, HikariCP settings, and
  optional ZooKeeper registration are unchanged.
- The release keeps the same minimal profile choices and shared contracts.
- No provider business logic moved to Rust.

Build the default provider:

```powershell
mvn clean verify
mvn exec:java
```
