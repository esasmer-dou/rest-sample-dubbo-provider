# rest-sample-dubbo-provider

[English](README.md) | [Türkçe](README.tr.md)

Rust-Java REST consumer sample'ının kullandığı sade bir Java Dubbo provider uygulamasıdır.

- Spring Boot kullanmaz.
- ZooKeeper olmadan çalışabilir.
- İstenirse ZooKeeper'a kayıt olabilir.
- Hazır JSON veya typed record dönebilir.
- Full profile PostgreSQL ve HikariCP kullanır.

Kullanılan sürümler: `java-rust-dubbo:0.5.0`, `rest-sample-utility:0.3.0`, `rust-sample-model:0.3.0`.

## Buradan Başlayın

Önce provider tipini seçin.

| İhtiyaç | Maven profile | İçerik |
|---|---|---|
| Küçük, hazır JSON katalog provider | `catalog-static-provider` | Tek katalog interface'i; DB ve ZooKeeper yok |
| Yalnızca PostgreSQL okuyan provider | `db-query-provider` | Müşteri sorguları ve HikariCP; komut yok |
| Katalog, sorgu ve komut | `full-provider` | Tam sample; varsayılan profil |

Servis yalnızca tek catalog metoduna ihtiyaç duyuyorsa full provider paketlemeyin. Küçük profile daha az sınıf yükler ve daha az memory kullanır.

## Hızlı Başlangıç

### Seçenek A: Küçük katalog provider

Bu repo dizininde çalıştırın:

```powershell
$env:GITHUB_PACKAGES_TOKEN="READ_PACKAGES_YETKILI_TOKEN"

mvn -q -Pcatalog-static-provider clean package

java `
  "-Ddubbo.provider.host=127.0.0.1" `
  "-Ddubbo.provider.bind-host=127.0.0.1" `
  "-Ddubbo.provider.port=20880" `
  "-Dreactor.dubbo.registry-enabled=false" `
  -jar target/rest-sample-dubbo-provider-0.4.0.jar
```

Bu provider'ı consumer'ın `native-static-consumer` profile'ı ile kullanın.

### Seçenek B: PostgreSQL kullanan tam provider

PostgreSQL'i başlatın:

```powershell
docker rm -f rs-provider-postgres-test 2>$null

docker run -d --name rs-provider-postgres-test `
  -e POSTGRES_DB=reactor_sample `
  -e POSTGRES_USER=reactor `
  -e POSTGRES_PASSWORD=reactor `
  -p 15432:5432 postgres:16-alpine
```

Provider'ı build edip başlatın:

```powershell
$env:GITHUB_PACKAGES_TOKEN="READ_PACKAGES_YETKILI_TOKEN"

mvn -q clean package

java `
  "-Dreactor.dubbo.registry-enabled=false" `
  "-Dsample.db.jdbc-url=jdbc:postgresql://127.0.0.1:15432/reactor_sample" `
  "-Dsample.db.username=reactor" `
  "-Dsample.db.password=reactor" `
  "-Dsample.db.schema-init=true" `
  "-Dsample.db.warmup=true" `
  -jar target/rest-sample-dubbo-provider-0.4.0.jar
```

Provider `127.0.0.1:20880` adresinde çalışır.

Consumer'ı şu ayarlarla başlatın:

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=127.0.0.1:20880
```

## Provider Neler Sunar?

Provider HTTP endpoint açmaz. Dubbo interface'lerini yayınlar.

| Interface | Örnek metotlar | Veri tipi |
|---|---|---|
| `CatalogJsonService` | `getNestedCatalogJson()` | Hazır JSON `byte[]` |
| `NestedCatalogService` | başlık, adet, bilgi, ürün listesi, özellikler | String, primitive, record, list, map |
| `CustomerQueryService` | müşteri, segment listesi, istatistik, varlık kontrolü | Hazır JSON ve typed sonuçlar |
| `CustomerCommandService` | create, patch, delete | JSON byte'ları ve typed command record'ları |

Ortak interface package adı `com.reactor.rust.dubbo.sample` olarak kalmalıdır. Dubbo, tam interface adını service kimliği olarak kullanır.

## Static Adres mi, ZooKeeper mı?

### ZooKeeper olmadan

Consumer provider'a bilinen bir adres veya Kubernetes Service DNS üzerinden ulaşıyorsa bunu kullanın.

```properties
reactor.dubbo.registry-enabled=false
dubbo.provider.bind-host=0.0.0.0
dubbo.provider.port=20880
```

Kubernetes örneği:

```text
rest-sample-dubbo-provider:20880
```

Service bir veya birden fazla provider pod'una yönlenebilir.

### ZooKeeper ile

Provider bir Dubbo registry'ye kayıt olacaksa ZooKeeper kullanın.

```properties
reactor.dubbo.registry-enabled=true
reactor.dubbo.registry-address=zookeeper://zookeeper-client.platform.svc.cluster.local:2181
reactor.dubbo.registry-root=dubbo
```

Dubbo kullanmak ZooKeeper'ı zorunlu yapmaz. Gerçek bir registry ihtiyacı yoksa ZooKeeper'ı açmayın.

## Veritabanı ve Eşzamanlı İstek Limitleri

Varsayılan production yapısı bilinçli olarak küçüktür:

```properties
sample.db.maximum-pool-size=2
sample.db.minimum-idle=0

dubbo.provider.executor.core-threads=1
dubbo.provider.executor.max-threads=8
dubbo.provider.executor.queue-capacity=16

dubbo.provider.service.CustomerQueryService.max-concurrent=2
dubbo.provider.service.CustomerCommandService.max-concurrent=2
dubbo.provider.service.CustomerCommandService.method.patchCustomerStatus.max-concurrent=1
```

Bu değerlerin nedeni:

1. HikariCP iki database bağlantısı kullanır.
2. Query ve command concurrency gerçek DB kapasitesine yakın kalır.
3. Aynı satırı değiştiren işlemler tek tek çalışır.
4. Sınırlı queue, memory'nin kontrolsüz büyümesini engeller.

Veritabanı bağlantı havuzu ile servis limitlerini birlikte artırın. Önce PostgreSQL CPU, bağlantı sayısı, kilit bekleme ve sorgu gecikmesi değerlerine bakın.

Yalnızca queue değerini artırmayın. Bu yaklaşım hatayı geciktirir ve en yavaş istekleri daha da yavaşlatır.

## JSON ve DTO Seçimi

| İhtiyaç | Provider dönüş tipi | Sonuç |
|---|---|---|
| Consumer JSON'u alanlarını okumadan iletecek | UTF-8 JSON içeren `byte[]` | En az Java nesnesini oluşturur |
| Consumer tek bir değer kullanacak | Primitive veya `String` | Küçük typed sözleşme |
| Consumer iş kararı verecek | Küçük immutable `record` | Açık sözleşme ve Hessian decode maliyeti |
| Consumer büyük bir sayfayı doğrudan iletecek | Hazır JSON `byte[]` | Büyük record/list grafiği oluşmaz |

Typed DTO package'ları iki tarafta da bulunmalı ve Hessian güvenlik listesine eklenmelidir:

```text
src/main/resources/security/serialize.allowlist
```

## Konfigürasyon

Uygulama ayarları şu sırayla okur:

1. `src/main/resources/rest-sample-dubbo-provider.properties`
2. `reactor.config.file` veya `REACTOR_CONFIG_FILE` ile verilen dosyalar
3. JVM `-D...` değerleri ve desteklenen environment variable'lar

| Dosya | Amacı |
|---|---|
| `rest-sample-dubbo-provider.properties` | Lokal provider, registry, service limiti ve DB ayarları |
| `config/production.properties` | Kubernetes bind adresi ve düşük memory HikariCP ayarları |
| `config/advanced-tuning.properties` | Metot bazlı limitler ve Netty/Dubbo memory ayarları |

Production ortamında database migration işlemini bu process dışında çalıştırın:

```properties
sample.db.schema-init=false
```

Sample bu değeri yalnızca kolay lokal başlangıç için `true` yapar.

## Container Image'ları

| Image tanımı | Kullanım |
|---|---|
| `docker/images/Dockerfile.jlink.catalog-static` | Küçük catalog-only provider |
| `docker/images/Dockerfile.jlink.db-query` | PostgreSQL query-only provider |
| `docker/images/Dockerfile.jlink` | Full provider |
| `docker/images/Dockerfile` | Docker Compose içinde çalışan full provider |

Jlink image oluşturmadan önce [`docker/images/README.md`](docker/images/README.md) dosyasını okuyun. Container build işlemi private ortak package'lara Maven erişimi de ister.

## Kod Haritası

| Dosya | Görevi |
|---|---|
| `RestSampleDubboProviderApplication.java` | Full provider'ı başlatır |
| `CatalogStaticProviderApplication.java` | Catalog-only provider'ı başlatır |
| `DbQueryOnlyProviderApplication.java` | DB query-only provider'ı başlatır |
| `*ProviderModule.java` | Yayınlanan servisleri tanımlar |
| `CustomerQueryServiceImpl.java` | DB okuma örneklerini içerir |
| `CustomerCommandServiceImpl.java` | DB command örneklerini içerir |
| `PostgresCustomerRepository.java` | SQL, transaction ve paging işlemlerini yönetir |
| `rest-sample-dubbo-provider.properties` | Lokal ayarları taşır |

## Maven Package Erişimi

GitHub Packages için `read:packages` yetkili token gerekir. Token'ın private ortak sample repolarına da erişimi olmalıdır.

`~/.m2/settings.xml` içindeki server kimlikleri POM ile aynı olmalıdır:

```xml
<servers>
  <server>
    <id>github-java-rust-dubbo</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github-rest-sample-utility</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
  <server>
    <id>github-rust-sample-model</id>
    <username>GITHUB_KULLANICI_ADI</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
</servers>
```

## Sık Karşılaşılan Sorunlar

| Belirti | Kontrol edin |
|---|---|
| Maven `401` dönüyor | Token, private repo erişimi ve bütün server kimlikleri |
| `20880` portu kullanılamıyor | Başka bir provider veya container bu portu kullanıyor olabilir |
| Consumer bağlanamıyor | Bind host, yayınlanan host, port, Service DNS ve firewall |
| PostgreSQL bağlantısı kurulamıyor | JDBC URL, `15432` portu, kullanıcı adı ve parola |
| Typed DTO reddediliyor | Ortak model sürümü ve `serialize.allowlist` |
| Bazı write istekleri çok yavaş | DB lock bekleme, Hikari pool bekleme, metot limiti ve aynı satır çakışması |

## Ayrıntılı Bilgi

- [Türkçe kullanıcı rehberi](docs/USER_GUIDE.tr.md)
- [Türkçe PDF rehberi](docs/rest-sample-dubbo-provider-user-guide.tr.pdf)
- [Docker çalışma rehberi](docker/README.md)
- [Production ayarları](src/main/resources/config/production.properties)
- [Advanced tuning ayarları](src/main/resources/config/advanced-tuning.properties)
- [v0.4.0 release notları](docs/RELEASE_NOTES_v0.4.0.md)
