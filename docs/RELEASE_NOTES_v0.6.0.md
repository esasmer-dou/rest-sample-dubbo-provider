# rest-sample-dubbo-provider 0.6.0

`0.6.0` aligns the runnable provider with `java-rust-dubbo:0.7.0` and the `0.4.0` shared contracts.

## What Changed

- Uses `rust-java-platform-parent:4.2.0`, `java-rust-dubbo:0.7.0`,
  `rest-sample-utility:0.4.0`, and `rust-sample-model:0.4.0`.
- Provider profiles keep catalog-only, query-only, and full database surfaces isolated at build
  time.
- Existing bounded Dubbo executor, Hikari pool, PostgreSQL query/command separation, and optional
  ZooKeeper registry behavior remain explicit.
- Provider business services and database logic remain Java code.

## Run Static Provider

```powershell
mvn -Pcatalog-static-provider clean package
java -Dreactor.dubbo.registry-enabled=false `
  -jar target/rest-sample-dubbo-provider-0.6.0.jar
```

Enable ZooKeeper only when registry-based provider discovery is required.
