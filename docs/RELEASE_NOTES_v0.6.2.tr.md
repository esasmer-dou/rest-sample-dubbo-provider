# rest-sample-dubbo-provider 0.6.2

`0.6.2`, çalışan provider uygulamasını `rust-java-rest:4.4.0`, `java-rust-dubbo:0.7.2`,
`rest-sample-utility:0.4.1` ve `rust-sample-model:0.4.1` ile hizalar.

- Provider interface'leri, service implementasyonları, HikariCP/ActiveJDBC erişimi ve isteğe bağlı
  ZooKeeper kaydı değişmez.
- Sürüm hizalaması, provider kontratlarını `0.6.2` consumer sample ile uyumlu tutar.
- Bu provider resmi Dubbo runtime kullanır. Rust mikro ajan provider Netty runtime'ını değiştirmez ve
  telemetry'yi otomatik açmaz.
