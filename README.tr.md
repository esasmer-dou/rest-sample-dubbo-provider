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
- POST/PATCH/DELETE REST use case'leri küçük Dubbo command method'larıyla nasıl desteklenir.
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

## `rust-java-rest` 3.2.1 Bu Provider'ı Nasıl Etkiler?

Bu provider `rust-java-rest` bağımlılığı almaz; bu şekilde kalması doğrudur. Provider'ın görevi,
`rest-sample-dubbo-consumer` uygulamasının v3.2.x low-overhead response yolunu kullanabileceği küçük bir
Dubbo kontratı expose etmektir.

| Provider tercihi | v3.2.1 consumer üzerindeki etkisi |
|------------------|--------------------------------|
| UTF-8 JSON'u `byte[]` olarak dönmek | Consumer `RawResponse.json(bytes)` döner ve ikinci DTO graph kurmaz. |
| Interface'leri küçük tutmak | Consumer timeout, backpressure ve metrics değerlerini RPC alanına göre tune edebilir. |
| Method concurrency değerlerini bounded tutmak | Provider overload heap, DB pool veya Netty queue büyümesine dönüşmeden görünür olur. |
| DB method limitlerini Hikari ile hizalamak | DB saturation derin queue yerine fail-fast verdiği için consumer p99 daha stabil kalır. |
| Provider limitlerini consumer route admission ile eşlemek | Yavaş provider method'u consumer global JNI queue'yu dolduramaz. |
| Pass-through response için büyük object graph'tan kaçınmak | v3.2.x kazanımları Hessian materialization ve JSON reserialization ile silinmez. |
| Consumer'ı normal framework dependency'de tutmak | Consumer production-like RSS ölçümünde framework sample/demo class'larını taşımaz. |
| Açık consumer property'lerini korumak | Consumer `rust-spring.properties` değerleri runtime profile default'ları tarafından ezilmez. |

Provider UTF-8 JSON bytes yazıyor ve consumer JSON content type ile dönüyorsa Türkçe karakterler bu
akışta güvenli taşınır. JSON üretirken platform-default encoding kullanmayın.

BEST: read-heavy pass-through JSON için `byte[]` dönmek. ACCEPTABLE: consumer typed business karar
verecekse record dönmek. ANTI-PATTERN: consumer hemen tekrar JSON'a çevirecekse büyük nested object
graph dönmek.

### Provider Limitlerini Consumer İle Nasıl Hizalarsınız?

Consumer sample, Dubbo route'larını `@RouteAdmission` ile korur. Bu değerleri provider limitlerinden
bağımsız tune etmeyin:

| Provider kapasitesi | Consumer ayarı | Pratik kural |
|---------------------|----------------|--------------|
| `dubbo.provider.service.NestedCatalogService.max-concurrent=16` | `reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16` | Consumer basit catalog çağrılarında provider'ın çalıştırabileceği kadar in-flight çağrıya izin verebilir. |
| `dubbo.provider.service.CustomerQueryService.max-concurrent=2` | DB route admission `max-concurrent=8` | Async ve kısa çağrılarda bir miktar fazla consumer in-flight kabul edilebilir; p99 büyürse önce bunu düşürün. |
| `sample.db.maximum-pool-size=2` | DB method provider limiti | Bilinçli provider-side bekleme istemiyorsanız DB method concurrency Hikari pool'u aşmamalı. |

BEST: küçük provider limitleriyle başlayıp provider CPU, DB pool wait, consumer 503 oranı, p99 latency
ve RSS'i birlikte ölçerek artırmak. ANTI-PATTERN: provider DB pool zaten saturation altındayken
consumer worker sayısını artırmak.

## Mimari Akış

```text
Dubbo consumer
  -> ZooKeeper provider URL veya static host:port
  -> PlainDubboProvider
  -> seçilen service implementasyonu
  -> JSON byte[]
```

Provider tek büyük RPC yüzeyi yerine küçük ve cohesive interface'ler expose eder:

```java
public interface NestedCatalogService {
    byte[] getNestedCatalogJson();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();
}

public interface CustomerCommandService {
    byte[] createCustomer(byte[] commandJson);
    byte[] patchCustomerSegment(long customerId, byte[] commandJson);
    byte[] patchCustomerStatus(long customerId, byte[] commandJson);
    byte[] deleteCustomer(long customerId, byte[] commandJson);
}
```

Consumer bu bytes'ı `RawResponse.json(...)` ile tekrar DTO graph kurmadan HTTP response olarak
taşıyabilir.

Interface ayrımı:

| Interface | Implementation | Sorumluluk |
|-----------|----------------|------------|
| `NestedCatalogService` | `NestedCatalogServiceImpl` | Static nested catalog JSON. |
| `CustomerQueryService` | `CustomerQueryServiceImpl` | HikariCP/ActiveJDBC üzerinden PostgreSQL-backed customer JSON. |
| `CustomerCommandService` | `CustomerCommandServiceImpl` | Compact JSON command bytes ile POST/PATCH/DELETE tarzı DB command'leri. |

BEST: interface'leri küçük ve cohesive tutmak. ACCEPTABLE: sample veya aynı bounded context içinde
bir provider process'in aynı Dubbo portunda birden fazla interface export etmesi. ANTI-PATTERN:
wire etmek kolay diye alakasız read/write operasyonlarını tek god interface içine koymak.

Provider tüm interface node'larını tek ZooKeeper session ile register eder. Her export için ayrı
ZooKeeper client açmak çalışırdı, fakat gereksiz thread/session overhead üretirdi.

## Interface ve Method Bazlı Execution Limitleri

Her exported Dubbo interface kendi execution bulkhead'ine sahiptir; ayrıca tekil method'lar bu
interface default limitini override edebilir. Bu iki production sorusunu cevaplar:

- "Bu interface implementasyonu default olarak aynı anda maksimum kaç service method execution çalıştırabilir?"
- "Bu spesifik method interface'in geri kalanından daha düşük veya daha yüksek limit gerektiriyor mu?"

Bu bilinçli olarak interface veya method başına yeni thread pool değildir. Her service/method için
ayrı executor açmak thread stack RSS getirir ve overload'u queue içinde saklayabilir. Sample,
implementasyon önünde düşük overhead'li semaphore gate kullanır:

```text
Dubbo request
  -> PlainDubboProvider ReflectiveInvoker
  -> method override gate varsa onu kullanır
  -> yoksa interface default gate kullanır
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
| `CustomerQueryService.getDatabaseCustomersJson` | `dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent` | `1` | DB-backed method için method-level override örneğidir. |
| `CustomerCommandService` | `dubbo.provider.service.CustomerCommandService.max-concurrent` | `2` | Write-side DB command concurrency Hikari pool ile hizalı kalır. |
| `CustomerCommandService.*` write method'ları | `dubbo.provider.service.CustomerCommandService.method.<method>.max-concurrent` | `1` | Write örnekleri lokal sample davranışını öngörülebilir tutmak için method bazında serialized tutulur. |

Simple name çakışması varsa fully qualified interface adı da kullanılabilir:

```properties
dubbo.provider.service.com.reactor.rust.dubbo.sample.CustomerQueryService.max-concurrent=2
dubbo.provider.service.com.reactor.rust.dubbo.sample.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=1
```

Override davranışı:

| Ayar | Runtime davranış |
|------|------------------|
| Interface key yok | `dubbo.provider.service.default.max-concurrent` kullanılır. |
| Sadece interface key var | O interface'teki tüm method'lar interface gate'i paylaşır. |
| Method key var | O method interface default yerine kendi method gate'ini kullanır. |

Method override method adına göre çalışır. Bu sample'daki interface method'ları overloaded değildir.
Overloaded Dubbo method eklerseniz aynı isimdeki method'ların aynı method gate'i paylaşacağını bilerek
tasarlayın.

BEST: service limitini o interface arkasındaki gerçek darboğaza göre belirlemek. DB-backed servislerde
Hikari max pool size veya daha düşük bir değerle başlamak doğru olur. CPU-heavy serialization
servislerinde CPU kapasitesine göre başlayıp p99 load test ile doğrulayın. ANTI-PATTERN: tüm
interface'lere büyük sayı verip overload'u DB pool, heap veya Netty thread'lerine bırakmak.

## Provider Use Case Cookbook

Bu örnekler REST consumer üzerinden çağrılır, fakat davranış provider içinde uygulanır. Bu ayrım
bilinçlidir: REST process küçük kalır, provider DB access, mutation rule ve Hikari kapasitesinin
sahibi olur.

| Use case | Provider interface | Provider darboğazı | Başlangıç limiti |
|----------|--------------------|--------------------|-----------------:|
| Static/nested catalog read | `NestedCatalogService` | CPU/string generation | `16` |
| PostgreSQL customer read | `CustomerQueryService` | Hikari/PostgreSQL | service `2`, method `1` |
| Customer create/upsert | `CustomerCommandService.createCustomer` | Hikari/PostgreSQL unique key | service `2`, method `1` |
| Segment/status patch | `CustomerCommandService.patchCustomer*` | Hikari/PostgreSQL update | service `2`, method `1` |
| Customer delete | `CustomerCommandService.deleteCustomer` | Hikari/PostgreSQL delete/audit | service `2`, method `1` |

### Use Case: Customer Create Command

REST caller:

```powershell
curl -X POST http://127.0.0.1:8080/api/v1/customers `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1001\",\"customerNo\":\"CUST-9001\",\"fullName\":\"Zeynep Şahin\",\"segment\":\"pilot\",\"email\":\"zeynep.sahin@example.com\"}"
```

Provider contract:

```java
public interface CustomerCommandService {
    byte[] createCustomer(byte[] commandJson);
}
```

Provider implementation şekli:

```java
public byte[] createCustomer(byte[] commandJson) {
    // Sadece provider'ın ihtiyaç duyduğu command field'larını parse edin.
    // Consumer tarafında büyük DTO graph kurmayın.
    SampleCustomer customer = repository.createCustomer(customerNo, fullName, segment, email);
    return readyJsonBytes(customer);
}
```

### Use Case: Customer Segment Patch

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/segment `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1002\",\"segment\":\"enterprise\"}"
```

Targeted değişiklikler için bu pattern'i kullanın. Her customer field'ını alan generic
`PUT /customers/{id}` endpoint'inden genelde daha net ve daha ucuzdur.

### Use Case: Customer Status Patch

```powershell
curl -X PATCH http://127.0.0.1:8080/api/v1/customers/1/status `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1003\",\"status\":\"passive\"}"
```

Domain'inizin anladığı lifecycle kelimeleri kullanın: `active`, `passive`, `blocked`,
`pending-review`. Status değişimi side effect üretiyorsa idempotency ve audit handling provider'da
kalmalıdır.

### Use Case: Customer Delete

```powershell
curl -X DELETE http://127.0.0.1:8080/api/v1/customers/3 `
  -H "Content-Type: application/json" `
  -d "{\"requestId\":\"req-1004\",\"reason\":\"sample cleanup\"}"
```

Bu sample HTTP verb net görülsün diye hard delete yapar. Gerçek business sistemde audit, recovery
ve downstream consistency önemliyse soft-delete/status change daha doğru olur.

### Provider Kullanıcısı İçin Property/Profile Rehberi

| Kullanıcı problemi | İlk değişiklik | Bunu yapmayın |
|--------------------|----------------|---------------|
| "Burst altında write 503 dönüyor" | `CustomerCommandService.max-concurrent` değerini sadece Hikari kapasitesine kadar artırın, sonra ölçün. | Hikari `2` iken provider method limitini `100` yapmayın. |
| "DB yavaş" | `sample.db.connection-timeout-ms` biraz artırılabilir veya DB latency düzeltilir; consumer bounded kalır. | Yavaş DB'yi derin provider queue arkasına saklamayın. |
| "Provider RSS yüksek" | `sample.db.minimum-idle=0`, Netty arena `1`, QoS/metrics/tracing kapalı kalsın. | Bu sample'da kullanılmayan Dubbo governance özelliklerini açmayın. |
| "Daha güçlü write semantics lazım" | Provider'da idempotency/audit table ekleyin ve command JSON içindeki `requestId` değerini kullanın. | DB mutation provider'daysa idempotency'yi REST consumer'da çözmeyin. |

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

## v3.2.1 Consumer ile Çalıştırma Sırası

Lokal test için en temiz sıra:

```text
1. ZooKeeper'ı başlatın.
2. DB-backed endpoint'ler açıksa PostgreSQL'i başlatın.
3. Bu provider'ı başlatın.
4. rust-java-rest 3.2.1 kullanan rest-sample-dubbo-consumer'ı başlatın.
5. Provider'ı doğrudan değil, consumer REST endpoint'lerini çağırarak test edin.
```

Provider bilinçli olarak plain Java Dubbo server'dır. Low-RSS HTTP davranışı `rust-java-rest` çalışan
consumer process'e aittir; provider hazır JSON bytes dönerek ve server-side concurrency'yi bounded
tutarak bu davranışı korur.

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
| `dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent` | Customer DB method'u için method-level override limiti. |
| `dubbo.provider.service.CustomerCommandService.max-concurrent` | Write-side customer command'leri için concurrent invocation limiti. |
| `dubbo.provider.service.CustomerCommandService.method.*.max-concurrent` | Method-level write command override'ları. Hikari ile hizalı tutun. |
| `sample.db.jdbc-url` | PostgreSQL JDBC URL. |
| `sample.db.maximum-pool-size` | Hikari maximum pool size. |
| `sample.db.minimum-idle` | Hikari minimum idle connection sayısı. `0` idle RSS'i düşük tutar. |
| `sample.db.connection-timeout-ms` | DB connection bekleme üst limiti. Default `3000`; lokal cold start'ı kullanılabilir tutar ama DB down ise fail-fast kalır. |
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

Provider'ı başlatmadan önce PostgreSQL'in hazır olduğunu kontrol edin:

```powershell
docker exec rest-sample-postgres pg_isready -U reactor -d reactor_sample
```

Sample bounded Hikari pool ve `sample.db.connection-timeout-ms=3000` kullanır. Bu değer normal lokal
cold start için yeterince toleranslıdır; DB gerçekten down ise yine hızlı fail eder.

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
[rest-sample-dubbo-provider] execution limits NestedCatalogService=16, CustomerQueryService=2 methods={getDatabaseCustomersJson=1}
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
