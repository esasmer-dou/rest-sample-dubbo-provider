# rest-sample-dubbo-provider 0.5.0

`0.5.0` aligns the provider sample with `java-rust-dubbo:0.6.0` and the shared sample artifacts.

## What Changed

- Uses `java-rust-dubbo:0.6.0`, `rest-sample-utility:0.3.1`, and
  `rust-sample-model:0.3.1`.
- Workspace Docker builds install the aligned REST and Dubbo artifacts before packaging.
- Full, catalog-only, and DB-query-only profiles remain separate artifacts.

## Compatibility

Provider interfaces, ready-JSON and typed responses, PostgreSQL/HikariCP behavior, executor limits,
and optional ZooKeeper registration are unchanged.

## Run

```powershell
mvn -Pcatalog-static-provider clean package
java -Dreactor.dubbo.registry-enabled=false `
  -jar target/rest-sample-dubbo-provider-0.5.0.jar
```
