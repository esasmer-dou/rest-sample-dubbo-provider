# rest-sample-dubbo-provider 0.4.0

`0.4.0`, provider örneğini deklaratif Dubbo service binding sürüm çizgisine taşır.

## Yenilikler

- `java-rust-dubbo:0.5.0` ve `rest-sample-utility:0.3.0` kullanılır.
- Service implementasyonları, interface ve metot kapasite limitleriyle birlikte
  `DubboServiceBinding` üzerinden tanımlanır.
- Database profillerinde generated JDBC record mapper sınıfları kullanılır.
- Catalog-static, database-query ve full provider biçimleri ayrı Maven profilleri olarak korunur.
- Provider service interface ve payload sözleşmeleri değişmez.

## Çalıştırma

```powershell
mvn clean package
java -jar target/rest-sample-dubbo-provider-0.4.0.jar
```

Provider business implementasyonları, SQL, transaction ve validation kodu açık şekilde Java'da
kalır.
