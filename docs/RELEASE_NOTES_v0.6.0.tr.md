# rest-sample-dubbo-provider 0.6.0

`0.6.0`, runnable provider uygulamasını `java-rust-dubbo:0.7.0` ve `0.4.0` ortak kontratlarıyla
hizalar.

## Neler Değişti?

- `rust-java-platform-parent:4.2.0`, `java-rust-dubbo:0.7.0`,
  `rest-sample-utility:0.4.0` ve `rust-sample-model:0.4.0` kullanılır.
- Provider profilleri catalog-only, query-only ve full database yüzeylerini build time sırasında
  birbirinden ayırır.
- Bounded Dubbo executor, Hikari pool, PostgreSQL query/command ayrımı ve opsiyonel ZooKeeper registry
  davranışı açık kalır.
- Provider business service ve database logic Java kodunda kalır.

## Static Provider Çalıştırma

```powershell
mvn -Pcatalog-static-provider clean package
java -Dreactor.dubbo.registry-enabled=false `
  -jar target/rest-sample-dubbo-provider-0.6.0.jar
```

ZooKeeper'ı yalnız registry tabanlı provider discovery gerekiyorsa açın.
