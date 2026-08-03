# rest-sample-dubbo-provider 0.5.0

`0.5.0`, provider sample'ını `java-rust-dubbo:0.6.0` ve güncel ortak sample artifact'leriyle
hizalar.

## Neler Değişti?

- `java-rust-dubbo:0.6.0`, `rest-sample-utility:0.3.1` ve `rust-sample-model:0.3.1` kullanılır.
- Workspace Docker build, paketlemeden önce uyumlu REST ve Dubbo artifact'lerini kurar.
- Full, yalnız katalog ve yalnız DB sorgu profile'ları ayrı artifact olarak kalır.

## Uyumluluk

Provider interface'leri, hazır JSON ve typed response'lar, PostgreSQL/HikariCP davranışı, executor
limitleri ve opsiyonel ZooKeeper kaydı değişmedi.

## Çalıştırma

```powershell
mvn -Pcatalog-static-provider clean package
java -Dreactor.dubbo.registry-enabled=false `
  -jar target/rest-sample-dubbo-provider-0.5.0.jar
```
