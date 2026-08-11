# rest-sample-dubbo-provider 0.6.1

`0.6.1`, çalışan provider uygulamasını `rust-java-rest:4.3.0`, `java-rust-dubbo:0.7.1`,
`rest-sample-utility:0.4.1` ve `rust-sample-model:0.4.1` ile hizalar.

- Provider interface'leri, Java service implementasyonları, veri tabanı profilleri, HikariCP
  ayarları ve opsiyonel ZooKeeper kaydı değişmedi.
- Sürüm aynı minimal profil seçimlerini ve ortak kontratları korur.
- Provider business logic Rust'a taşınmadı.

Varsayılan provider'ı build edin:

```powershell
mvn clean verify
mvn exec:java
```
