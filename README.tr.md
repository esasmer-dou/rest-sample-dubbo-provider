# rest-sample-dubbo-provider

[English](README.md) | Türkçe

Rust-Java REST consumer demosu için hazırlanmış minimal plain Java Dubbo provider örneğidir.
ZooKeeper registration, PostgreSQL/HikariCP data access ve consumer'ın doğrudan forward edebileceği
hazır JSON response üretimini gösterir.

Bu repo, `rest-sample-dubbo-consumer` uygulamasının gerçek bir Dubbo provider'a karşı test
edilebilmesi için hazırlandı. Spring Boot veya Dubbo Spring Boot starter kullanmaz.

## Bu Örnek Ne İçin Hazırlandı?

Bu örnek şu konuları göstermek için hazırlandı:

- Plain Java ile classic `dubbo://` provider nasıl expose edilir.
- Aynı küçük provider process içinden birden fazla Dubbo interface nasıl export edilir.
- Provider URL'i ZooKeeper'a nasıl register edilir.
- Provider dependency seti nasıl açık ve sınırlı tutulur.
- Rust-Java consumer'ın düşük overhead ile forward edebilmesi için provider nasıl JSON bytes döner.
- Spring olmadan PostgreSQL'e HikariCP ve ActiveJDBC ile nasıl bağlanılır.
- Runtime class'lar ile record DTO'lar nasıl ayrılır.

Bu repo genel amaçlı enterprise Dubbo provider template'i değildir. Rust-Java REST Dubbo consumer
yolunu doğrulamak için odaklı bir provider örneğidir.

## Diğer Projelerle İlişkisi

Bu provider şu consumer repo tarafından kullanılır:

```text
git@github.com:esasmer-dou/rest-sample-dubbo-consumer.git
```

Runtime ilişki:

```text
rest-sample-dubbo-consumer
  -> java-rust-dubbo native consumer
  -> bu projedeki dubbo:// provider
  -> opsiyonel PostgreSQL query
  -> JSON byte[]
```

Provider her interface için ZooKeeper altında ayrı path'e register olur:

```text
/dubbo/com.reactor.rust.dubbo.sample.NestedCatalogService/providers
/dubbo/com.reactor.rust.dubbo.sample.CustomerQueryService/providers
```

## Mimari Akış

```text
Dubbo consumer
  -> ZooKeeper provider URL veya static host:port
  -> PlainDubboProvider
  -> seçilen service implementasyonu
  -> JSON byte[]
```

Provider tek büyük RPC yüzeyi yerine iki küçük interface expose eder:

```java
public interface NestedCatalogService {
    byte[] getNestedCatalogJson();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();
}
```

Consumer bu bytes'ı `RawResponse.json(...)` ile tekrar DTO graph kurmadan HTTP response olarak
taşıyabilir.

Interface ayrımı:

| Interface | Implementation | Sorumluluk |
|-----------|----------------|------------|
| `NestedCatalogService` | `NestedCatalogServiceImpl` | Static nested catalog JSON. |
| `CustomerQueryService` | `CustomerQueryServiceImpl` | HikariCP/ActiveJDBC üzerinden PostgreSQL-backed customer JSON. |

BEST: interface'leri küçük ve cohesive tutmak. ACCEPTABLE: sample veya aynı bounded context içinde
bir provider process'in aynı Dubbo portunda birden fazla interface export etmesi. ANTI-PATTERN:
wire etmek kolay diye alakasız read/write operasyonlarını tek god interface içine koymak.

Provider iki interface node'unu tek ZooKeeper session ile register eder. Her export için ayrı
ZooKeeper client açmak çalışırdı, fakat gereksiz thread/session overhead üretirdi.

## Interface Bazlı Execution Limitleri

Her exported Dubbo interface kendi execution bulkhead'ine sahiptir. Bu şu production sorusunu cevaplar:
"Bu interface implementasyonu aynı anda maksimum kaç service method execution çalıştırabilir?"

Bu bilinçli olarak interface başına yeni thread pool değildir. Her service için ayrı executor açmak
thread stack RSS getirir ve overload'u queue içinde saklayabilir. Sample, implementasyon önünde düşük
overhead'li semaphore gate kullanır:

```text
Dubbo request
  -> PlainDubboProvider ReflectiveInvoker
  -> interface bazlı concurrency gate
  -> service implementation method
```

Limit doluysa provider çağrıyı hemen reject eder. Bu fail-fast davranış p99 ve memory açısından
unbounded queue büyütmekten daha güvenlidir.

Varsayılan sample limitleri:

| Interface | Property | Default | Neden? |
|-----------|----------|---------|--------|
| Tüm servisler | `dubbo.provider.service.default.max-concurrent` | `16` | Küçük sample servisler için güvenli fallback. |
| `NestedCatalogService` | `dubbo.provider.service.NestedCatalogService.max-concurrent` | `16` | Catalog JSON üretimini CPU/allocation açısından sınırlar. |
| `CustomerQueryService` | `dubbo.provider.service.CustomerQueryService.max-concurrent` | `2` | `sample.db.maximum-pool-size=2` ile hizalıdır; DB pool queue büyümesini engeller. |

Simple name çakışması varsa fully qualified interface adı da kullanılabilir:

```properties
dubbo.provider.service.com.reactor.rust.dubbo.sample.CustomerQueryService.max-concurrent=2
```

BEST: service limitini o interface arkasındaki gerçek darboğaza göre belirlemek. DB-backed servislerde
Hikari max pool size veya daha düşük bir değerle başlamak doğru olur. CPU-heavy serialization
servislerinde CPU kapasitesine göre başlayıp p99 load test ile doğrulayın. ANTI-PATTERN: tüm
interface'lere büyük sayı verip overload'u DB pool, heap veya Netty thread'lerine bırakmak.

## Paket Yapısı

```text
com.reactor.sample.dubbo.provider.app
  Process entry point ve provider bootstrap.

com.reactor.sample.dubbo.provider.config
  Properties-only runtime config ve Netty/Dubbo tuning key'leri.

com.reactor.sample.dubbo.provider.db
  HikariCP, ActiveJDBC repository ve sample DB record modeli.

com.reactor.sample.dubbo.provider.service
  Dubbo service implementasyonu.

com.reactor.sample.dubbo.provider.dubbo
  Minimal Dubbo export ve runtime model.

com.reactor.sample.dubbo.provider.registry
  ZooKeeper provider registration.

com.reactor.rust.dubbo.sample
  Ortak Dubbo interface örnekleri. Production'da shared API jar'a taşınmalı.
```

Main class:

```text
com.reactor.sample.dubbo.provider.app.RestSampleDubboProviderApplication
```

## DTO, Runtime Class ve Response Model Ayrımı

Rust-Java framework standardı şudur:

```text
HTTP JSON request/response DTO = Java record
Runtime davranış/resource sahibi nesne = Java class
Hazır JSON/RPC payload = byte[] + RawResponse
```

Bu provider'daki class'lar HTTP JSON DTO değildir:

| Tip | Rol | JSON DTO mu? |
|-----|-----|--------------|
| `RestSampleDubboProviderApplication` | Process bootstrap ve shutdown hook. | Hayır |
| `ProviderProperties` | Runtime property okur ve validate eder. | Hayır |
| `ProviderRuntimeTuning` | Dubbo/Netty startup tuning uygular. | Hayır |
| `PlainDubboProvider` | Dubbo protocol export ve exporter lifecycle yönetir. | Hayır |
| `ZookeeperProviderRegistration` | ZooKeeper session ve ephemeral node lifecycle yönetir. | Hayır |
| `PostgresCustomerRepository` | DB access davranışı ve pool kullanımını yönetir. | Hayır |
| `NestedCatalogServiceImpl` | Dubbo business service implementasyonu. | Hayır |
| `CustomerQueryServiceImpl` | DB-backed Dubbo business service implementasyonu. | Hayır |
| `SampleCustomer` | Immutable DB row modeli. | Evet, record doğru tercih. |

### Use Case: DB Row Model

Immutable data row için record kullanın:

```java
public record CustomerRow(long id, String customerNo, String fullName) {}
```

Repository ve resource sahibi nesneler için class kullanın:

```java
public final class CustomerRepository implements AutoCloseable {
    public List<CustomerRow> findCustomers() {
        return List.of();
    }
}
```

### Use Case: Provider Hazır JSON Döndürüyor

Provider hazır JSON üretiyor ve consumer bunu HTTP'ye taşıyacaksa `byte[]` dönebilir:

```java
public byte[] getCatalogJson() {
    return jsonBytes;
}
```

Bu sample'ın kullandığı yol budur.

### Use Case: JSON Bytes Yerine Object Contract

Dubbo API domain object dönecekse ortak API jar içinde record DTO kullanın:

```java
public record CatalogItem(String sku, String name) {}

public record CatalogResponse(String source, List<CatalogItem> items) {}

public interface CatalogService {
    CatalogResponse getCatalog();
}
```

Bu model daha object-oriented olabilir; ancak consumer tarafında serialization ve object graph maliyeti
oluşturur.

### Provider Return Type Seçimleri ve Overhead

Provider `record`, class DTO, `String` veya `byte[]` dönebilir. Doğru seçim consumer'ın bu değerle ne
yapacağına göre verilmelidir.

| Provider dönüş tipi | En uygun use case | Consumer maliyeti |
|---------------------|-------------------|-------------------|
| UTF-8 JSON taşıyan `byte[]` | Consumer cevabı HTTP JSON olarak forward eder. | En düşük. Consumer `RawResponse.json(bytes)` kullanır, ikinci DTO graph kurmaz. |
| `record` DTO | Consumer typed data ile filtreleme, validation, enrichment veya business karar yapar. | Hessian2 decode Java object graph oluşturur; HTTP response tekrar JSON serialize edebilir. |
| Plain class DTO | Legacy framework veya serializer record desteklemiyorsa. | Record'a benzer, genelde daha mutable ve daha az explicit. |
| JSON `String` | Küçük/basit payload ve okunabilirlik byte kontrolünden önemliyse. | String allocation ve sonradan UTF-8 encoding. Hot path için `byte[]` daha doğru. |
| Büyük nested object graph | Consumer gerçekten tüm object model'e ihtiyaç duyuyorsa. | En yüksek heap, GC, p99 latency ve RSS riski. |

Bu sample'da `byte[]` bilinçli tercihtir. REST consumer catalog yapısını anlamak zorunda değildir;
provider cevabını HTTP üzerinden dışarı açması yeterlidir.

### Bu Provider Direkt Record Döndürebilir mi?

Evet, iki taraf da aynı API contract jar'ını kullanıyorsa:

```java
public record CatalogItem(String sku, String name) {}

public record CatalogResponse(String source, List<CatalogItem> items) {}

public interface CatalogService {
    CatalogResponse getCatalog();
}
```

Provider implementasyonu:

```java
public final class CatalogServiceImpl implements CatalogService {
    @Override
    public CatalogResponse getCatalog() {
        return new CatalogResponse(
                "rest-sample-dubbo-provider",
                List.of(new CatalogItem("sku-1", "Demo Item")));
    }
}
```

Consumer kullanımı:

```java
NativeDubboMethodInvoker<CatalogResponse> invoker =
        client.method(spec, "getCatalog", CatalogResponse.class);
```

Bu geçerli bir object contract'tır, fakat wire path yine Hessian2 serialization kullanır:

```text
CatalogResponse record
  -> provider tarafında Hessian2 serialize
  -> Dubbo TCP frame
  -> consumer tarafında Rust native transport
  -> Java Hessian2 decode
  -> yeni CatalogResponse record instance
```

Bu ortamda Hessian Lite 4.0.3 basit Java 21 record'ları işleyebiliyor. Bunu sadece minimum smoke
sinyali olarak kabul edin. Gerçek production contract için nested record, list, map, enum, tarih,
null ve version değişimi içeren contract test yazılmalıdır.

### Provider Tarafı Karar Örnekleri

Read-heavy endpoint için hazır JSON kullanın:

```java
public byte[] getCatalogJson() {
    return catalogJsonWriter.writeAsUtf8Bytes();
}
```

Consumer business karar verecekse record kullanın:

```java
public CatalogResponse getCatalog() {
    return catalogRepository.loadCatalogResponse();
}
```

Hot path için bundan kaçının:

```java
public CatalogResponse getCatalogOnlyToBeConvertedBackToJson() {
    return catalogRepository.loadLargeCatalog();
}
```

REST consumer bu objeyi hemen tekrar JSON'a çeviriyorsa sistem Hessian object materialization ve JSON
serialization maliyetini boşuna ödemiş olur. Bu durumda provider UTF-8 JSON bytes dönmeli veya
streaming response tasarlanmalıdır.

## Bağımlılıklar

Provider process gerçek Dubbo server olduğu için consumer kadar küçük değildir. Dependency seti açık
tutulmuştur:

| Bağımlılık | Amaç |
|------------|------|
| `dubbo-rpc-dubbo` | Classic `dubbo://` protocol export. |
| `dubbo-remoting-netty4` + `netty-handler` | TCP server transport. |
| `dubbo-serialization-hessian2` | Hessian2 payload uyumluluğu. |
| `zookeeper` | Provider URL registration ve ephemeral node. |
| `activejdbc` | Spring olmadan basit JDBC access. |
| `postgresql` | PostgreSQL JDBC driver. |
| `HikariCP` | Bounded JDBC connection pool. |
| `slf4j-nop` | Sample runtime'da sessiz logging. |

Bilinçli olarak dışarıda bırakılanlar:

- Spring Boot
- Dubbo Spring Boot starter
- Dubbo consumer/reference/config stack
- Dubbo governance/router özellikleri
- Netty proxy/socks/http2/epoll native paketleri
- Spring JDBC/AOP starter'ları

Not: Bazı Dubbo metrics/API sınıfları classpath'te kalır çünkü Dubbo server bytecode'u bunlara
referans verir. Runtime'da metrics, tracing ve QoS properties ile kapalıdır.

## Konfigürasyon

Ana config dosyası:

```text
src/main/resources/rest-sample-dubbo-provider.properties
```

Okuma sırası:

```text
system property > environment variable > classpath properties
```

Runtime değerleri properties dosyasındadır. Eksik veya hatalı property startup'ta fail-fast olur.

Önemli property'ler:

| Property | Açıklama |
|----------|----------|
| `dubbo.provider.host` | Dubbo provider URL içinde ilan edilen host. |
| `dubbo.provider.bind-host` | Local bind host. Container içinde gerekirse `0.0.0.0`. |
| `dubbo.provider.port` | Dubbo provider portu. Sample default değeri `20880`. |
| `reactor.dubbo.registry-address` | ZooKeeper registry adresi. |
| `dubbo.provider.service.default.max-concurrent` | Export edilen interface'ler için default concurrent invocation limiti. |
| `dubbo.provider.service.NestedCatalogService.max-concurrent` | Catalog provider method'ları için concurrent invocation limiti. |
| `dubbo.provider.service.CustomerQueryService.max-concurrent` | DB-backed customer provider method'ları için concurrent invocation limiti. |
| `sample.db.jdbc-url` | PostgreSQL JDBC URL. |
| `sample.db.maximum-pool-size` | Hikari maximum pool size. |
| `sample.db.minimum-idle` | Hikari minimum idle connection sayısı. `0` idle RSS'i düşük tutar. |
| `sample.db.schema-init` | True ise demo tablo/data oluşturur. |
| `sample.db.warmup` | Provider hazır olmadan önce DB connection ve seed işlemini yapar. |
| `io.netty.allocator.numDirectArenas` | Low-RSS Netty allocator tuning. |

## Hızlı Başlangıç

Gereksinimler:

- Java 21
- Maven 3.9+
- ZooKeeper ve PostgreSQL için Docker

ZooKeeper başlatın:

```powershell
docker run -d --name rust-java-dubbo-zookeeper -p 2181:2181 zookeeper:3.7.2
```

PostgreSQL başlatın:

```powershell
docker run -d --name rest-sample-postgres `
  -e POSTGRES_DB=reactor_sample `
  -e POSTGRES_USER=reactor `
  -e POSTGRES_PASSWORD=reactor `
  -p 15432:5432 `
  postgres:16-alpine
```

Build ve run:

```powershell
git clone git@github.com:esasmer-dou/rest-sample-dubbo-provider.git
cd rest-sample-dubbo-provider
mvn -q test
mvn -q exec:java
```

Beklenen startup çıktısı:

```text
[rest-sample-dubbo-provider] database warmup completed
[rest-sample-dubbo-provider] exported dubbo://127.0.0.1:20880/com.reactor.rust.dubbo.sample.NestedCatalogService...
[rest-sample-dubbo-provider] exported dubbo://127.0.0.1:20880/com.reactor.rust.dubbo.sample.CustomerQueryService...
[rest-sample-dubbo-provider] execution limits NestedCatalogService=16, CustomerQueryService=2
[rest-sample-dubbo-provider] registered at zookeeper://127.0.0.1:2181/dubbo
```

## Consumer ile Test

Consumer'ı başlatın:

```powershell
git clone git@github.com:esasmer-dou/rest-sample-dubbo-consumer.git
cd rest-sample-dubbo-consumer
mvn -q exec:java
```

REST consumer üzerinden çağırın:

```powershell
curl http://127.0.0.1:8080/api/v1/catalog/nested
curl http://127.0.0.1:8080/api/v1/customers/db
curl http://127.0.0.1:8080/api/v1/catalog/db/customers
```

Beklenen DB-backed response içinde şunlar bulunur:

```json
{
  "source": "rest-sample-dubbo-provider",
  "storage": "postgresql-activejdbc-hikari",
  "customers": [
    {
      "customerNo": "CUST-1001",
      "fullName": "Mustafa Korkmaz",
      "segment": "pilot"
    }
  ]
}
```

## Container/Kubernetes Notları

Container ortamında bind/advertised host değerlerini açık verin:

```powershell
$env:DUBBO_PROVIDER_BIND_HOST="0.0.0.0"
$env:DUBBO_PROVIDER_HOST="catalog-provider"
$env:REACTOR_DUBBO_REGISTRY_ADDRESS="zookeeper://zookeeper:2181"
$env:SAMPLE_DB_JDBC_URL="jdbc:postgresql://postgres:5432/reactor_sample"
mvn -q exec:java
```

Önce küçük Hikari pool ile başlayın. DB pool size'ı ölçmeden artırmak provider CPU, DB kapasitesi ve
consumer concurrency altında tail latency'yi kötüleştirebilir.

## Sorun Giderme

| Belirti | Kontrol |
|---------|---------|
| Provider başlamıyor | ZooKeeper `2181` üzerinde dinliyor mu kontrol edin. |
| DB endpoint hata veriyor | PostgreSQL `15432` üzerinde mi, credential'lar property ile uyumlu mu kontrol edin. |
| Consumer provider'a erişemiyor | Provider `20880` üzerinde dinliyor mu kontrol edin. |
| ZooKeeper discovery çalışmıyor | Provider node'u `/dubbo/.../providers` altında oluşmuş mu kontrol edin. |
| RSS yüksek | Hikari pool'u küçük tutun, kullanılmayan Dubbo özelliklerini kapatın, büyük Java DTO graph kurmayın. |

## Production Notları

- `NestedCatalogService` ve `CustomerQueryService` gerçek kullanımda shared API jar'a taşınmalıdır.
- Runtime property'leri açık tutulmalıdır.
- Domain DTO contract'ları record olmalıdır.
- Lifecycle/resource sahibi nesneler class olmalıdır.
- DB migration production'da hot provider process dışında yapılmalıdır.
- Operational endpoint ve secret yönetimi bu sample dışındadır.
