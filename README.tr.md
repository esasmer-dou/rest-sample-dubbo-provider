# rest-sample-dubbo-provider

[English](https://github.com/esasmer-dou/rest-sample-dubbo-provider/blob/master/README.md) | [Turkish](https://github.com/esasmer-dou/rest-sample-dubbo-provider/blob/master/README.tr.md)

[Kısa Kullanıcı Rehberi](docs/USER_GUIDE.tr.md) | [PDF](docs/rest-sample-dubbo-provider-user-guide.tr.pdf)

Rust-Java REST consumer demosu için hazırlanmış minimal plain Java Dubbo provider örneğidir.

Static provider modu veya ZooKeeper registration ile çalışır. Hazır JSON dönebilir. PostgreSQL ve HikariCP kullanabilir.

Spring Boot kullanmaz.

Ortak Dubbo service interface'leri `com.reactor.sample:rest-sample-utility:0.1.0` paketinden gelir. Ortak
DTO ve row model record'ları `com.reactor.sample:rust-sample-model:0.1.0` paketinden gelir. Dubbo registry
path'leri bozulmasın diye service interface package adı `com.reactor.rust.dubbo.sample` olarak korunur.

## İçindekiler

1. [Bu Örnek Ne İçin Hazırlandı?](#bu-örnek-ne-için-hazırlandı)
2. [Kopyala-Yapıştır: Provider'ı Çalıştır](#kopyala-yapıştır-providerı-çalıştır)
3. [Buradan Başlayın: Provider Şeklinizi Seçin](#buradan-başlayın-provider-şeklinizi-seçin)
4. [Production Reçeteleri](#production-reçeteleri)
5. [Mimari Akış](#mimari-akış)
6. [Interface ve Method Bazlı Execution Limitleri](#interface-ve-method-bazlı-execution-limitleri)
7. [Konfigürasyon](#konfigürasyon)
8. [Hızlı Başlangıç](#hızlı-başlangıç)
9. [Consumer ile Test](#consumer-ile-test)
10. [Sözlük](#sözlük)
11. [Sorun Giderme](#sorun-giderme)

## Bu README Nasıl Okunmalı?

Sadece çalıştırmak istiyorsan kopyala-yapıştır bölümüyle başla.

Image veya Maven profile seçmeden önce provider şekli tablolarına bak.

DB pool, ZooKeeper veya method concurrency ayarlıyorsan konfigürasyon tablosuna bak.

## Property Katmanları

Varsayılan `src/main/resources/rest-sample-dubbo-provider.properties` minimum local dosyadır.
Provider adresini, registry modunu, default execution limit değerini ve DB bağlantı bilgilerini içerir.

Production ayarlarını overlay olarak kullanın:

```powershell
java "-Dreactor.config.file=src/main/resources/config/production.properties" ...
```

Advanced tuning dosyasını provider p99, DB pool wait ve RSS ölçmeden kullanmayın:

```powershell
java "-Dreactor.config.file=src/main/resources/config/production.properties;src/main/resources/config/advanced-tuning.properties" ...
```

- `config/production.properties`: Kubernetes host/bind, registry seçimi ve Hikari pool default'ları içindir.
- `config/advanced-tuning.properties`: interface/method bulkhead ve Netty/Dubbo low-RSS ayarları içindir.
- Environment alternatifi: `REACTOR_CONFIG_FILE=/app/config/production.properties`.

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

## Kopyala-Yapıştır: Provider'ı Çalıştır

Bu bölüm hızlı başlamak içindir. Önce provider'ı açın. Sonra `rest-sample-dubbo-consumer`
uygulamasını çalıştırın.

### Senaryo 1: En Küçük Catalog Provider

Bu senaryoda PostgreSQL yoktur. ZooKeeper yoktur. Provider sadece hazır catalog JSON döner. Static
consumer için en küçük başlangıç budur.

Bu komutları `rest-sample-dubbo-provider` dizininde çalıştırın:

```powershell
mvn -q -Pcatalog-static-provider clean package

java "-Ddubbo.provider.host=127.0.0.1" `
  "-Ddubbo.provider.bind-host=127.0.0.1" `
  "-Ddubbo.provider.port=20880" `
  "-Dreactor.dubbo.registry-enabled=false" `
  -jar target/rest-sample-dubbo-provider-0.1.1.jar
```

Bu provider açıkken consumer şu adresi kullanır:

```properties
reactor.dubbo.providers=127.0.0.1:20880
sample.dubbo.discovery=static
```

### Senaryo 2: PostgreSQL Destekli Full Provider

Bu senaryoda provider catalog, customer query ve customer command interface'lerini açar. PostgreSQL
ve HikariCP devrededir. POST, PATCH ve DELETE örnekleri için bu senaryoyu kullanın.

Bu komutları `rest-sample-dubbo-provider` dizininde çalıştırın:

```powershell
docker rm -f rs-provider-postgres-test 2>$null

docker run -d --name rs-provider-postgres-test `
  -e POSTGRES_DB=reactor_sample `
  -e POSTGRES_USER=reactor `
  -e POSTGRES_PASSWORD=reactor `
  -p 15432:5432 postgres:15.7-alpine

mvn -q clean package

java "-Ddubbo.provider.host=127.0.0.1" `
  "-Ddubbo.provider.bind-host=127.0.0.1" `
  "-Ddubbo.provider.port=20880" `
  "-Dreactor.dubbo.registry-enabled=false" `
  "-Dsample.db.jdbc-url=jdbc:postgresql://127.0.0.1:15432/reactor_sample" `
  "-Dsample.db.username=reactor" `
  "-Dsample.db.password=reactor" `
  "-Dsample.db.schema-init=true" `
  "-Dsample.db.warmup=true" `
  -jar target/rest-sample-dubbo-provider-0.1.1.jar
```

Provider terminalini açık bırakın. Consumer çağrıları bu process'e gider.

## Maven Package Erişimi

Bu provider hâlâ plain Java Dubbo provider'dır. `rust-java-rest` kullanmaz. Ancak küçük ortak sample
paketlerini kullanır:

- `com.reactor.sample:rest-sample-utility`: Dubbo service interface'leri.
- `com.reactor.sample:rust-sample-model`: DTO ve row model record'ları.
- `com.reactor:java-rust-dubbo`: `DubboProviderSupport` ve JDBC helper sınıfları.

Bu paketler private GitHub Packages olarak yayınlanıyorsa Maven tarafında `github-java-rust-dubbo`,
`github-rest-sample-utility` ve `github-rust-sample-model` için `read:packages` credential gerekir.

## Buradan Başlayın: Provider Şeklinizi Seçin

Bu provider bir REST uygulaması olmadığı için `rust-java-rest` runtime profile'ı kullanmaz.
Production davranışı plain provider property'leriyle belirlenir: Dubbo export ayarları, ZooKeeper
registration, HikariCP pool size ve interface/method bazlı concurrency limitleri.

| Senaryo | Provider tasarımı | Ana ayar | Consumer etkisi |
|----------|-------------------|----------|-----------------|
| Read-heavy lookup/catalog | Küçük read interface<br>UTF-8 JSON `byte[]` | <small><code>dubbo.provider.service.NestedCatalogService.max-concurrent=16</code></small> | Native handle: `RawResponse.nativeResponse(...)`<br>Java bytes: `RawResponse.json(bytes)`<br>DTO graph yok |
| Typed lookup/küçük sayfa | `record`, `String`, primitive,<br>`List<record>`, `Map<String,String>` | <small><code>dubbo.provider.service.NestedCatalogService.method.&lt;method&gt;.max-concurrent=4-16</code><br>strict result limit</small> | Typed business karar mümkün<br>Hessian/object allocation var |
| DB-backed query | `CustomerQueryService`<br>küçük DB pool | <small><code>sample.db.maximum-pool-size=2</code><br><code>dubbo.provider.service.CustomerQueryService.max-concurrent=1-2</code></small> | p99 DB kapasitesiyle sınırlanır |
| Write command | Compact JSON command bytes | <small><code>dubbo.provider.service.CustomerCommandService.method.&lt;method&gt;.max-concurrent=1</code><br><code>sample.db.auto-commit=true</code></small> | Retry kapalı, saturation'da fail-fast |
| Typed command | `CreateCustomerCommand -> CustomerMutationResult` | <small><code>dubbo.provider.service.CustomerCommandService.method.createCustomerTyped.max-concurrent=1</code><br>Hikari ile hizalı</small> | Daha temiz contract<br>byte pass-through'dan pahalı |
| Kubernetes discovery | Her interface ZooKeeper'a kaydedilir | <small><code>reactor.dubbo.registry-enabled=true</code><br><code>reactor.dubbo.registry-address=zookeeper://...:2181</code></small> | `zookeeper-discovery` yeniden bağlanabilir |
| Static Service DNS | ZooKeeper kaydı yok; provider sadece `dubbo://` expose eder | <small><code>reactor.dubbo.registry-enabled=false</code><br><code>dubbo.provider.bind-host=0.0.0.0</code></small> | Consumer `reactor.dubbo.providers=service-name:20880` kullanır |
| Lokal/static test | `127.0.0.1:20880` veya container DNS | `dubbo.provider.host`<br>`bind-host`, `port` | Static provider listesi buraya gider |

Önerilen başlangıç: pass-through read API'lerde küçük interface ve hazır JSON bytes dönmek. Consumer
typed business karar verecekse record, list, map, string ve primitive değer dönmek de uygundur.
Consumer'ın hemen tekrar JSON'a çevireceği büyük nested object graph dönmekten kaçının.

## Senaryoya Göre Provider Image Seçimi

Provider tarafında da image'ları senaryoya göre ayırın. Sadece catalog JSON dönecek bir demo için
DB/ZooKeeper destekli full image taşımayın.

| Image | Build komutu | Export eder | Bilinçli dışarıda kalır | Local smoke kanıtı |
|-------|--------------|-------------|-------------------------|--------------------|
| `rest-sample-dubbo-provider:catalog-static-jlink` | `docker build -f docker/images/Dockerfile.jlink.catalog-static -t rest-sample-dubbo-provider:catalog-static-jlink .` | Sadece `CatalogJsonService#getNestedCatalogJson()`. | PostgreSQL, HikariCP, ActiveJDBC, ZooKeeper registration, customer servisleri, typed catalog DTO method'ları. | App jar yaklaşık `9.3M`, JRE `80M`, idle RSS yaklaşık `45 MiB`. |
| `rest-sample-dubbo-provider:db-query-jlink` | `docker build -f docker/images/Dockerfile.jlink.db-query -t rest-sample-dubbo-provider:db-query-jlink .` | Sadece PostgreSQL/HikariCP destekli `CustomerQueryService`. ZooKeeper kaydı açılıp kapatılabilir. | Catalog servisleri ve `CustomerCommandService`; POST/PATCH/DELETE Dubbo command yüzeyi yoktur. | Local smoke: query REST çağrıları `200`, command REST çağrısı beklenen `503`; provider RSS yaklaşık `59 MiB`. |
| `rest-sample-dubbo-provider:jlink` | `docker build -f docker/images/Dockerfile.jlink -t rest-sample-dubbo-provider:jlink .` | Catalog, customer query ve customer command interface'leri. | Sample kapsamındaki özelliklerin tamamını taşır; DB-capable full provider image'dır. | Image yaklaşık `179MB`; DB/customer örnekleri gerekiyorsa kullanın. |

`catalog-static-jlink` neden hâlâ `java.desktop` ve `java.sql` içeriyor: Apache Dubbo'nun resmi
provider runtime'ı raw `byte[]` method için bile Java Beans ve Hessian/SQL-aware serialization
sınıflarını başlatıyor. Bu image DB/ZooKeeper/customer uygulama bağımlılıklarını çıkarır; fakat resmi
Dubbo provider stack ile bu JDK modülleri tamamen kaldırılamaz. Smoke testte bu modüller çıkarılınca
provider runtime hata verdi.

En küçük eşleşme:

```text
rest-sample-dubbo-provider:catalog-static-jlink
rest-sample-dubbo-consumer:native-static-jlink
```

Bu ikili tek static provider adresi ve tek hazır-JSON read API içindir. Typed DTO, DB query, write
command veya ZooKeeper registration gerekiyorsa full provider image ve ona uygun consumer image
kullanılmalıdır.

`db-query-jlink` image'ını, provider PostgreSQL okuyacak ama write command yayınlamayacaksa kullanın.
Bu şekil query-only bounded servisler için uygundur: customer lookup, customer list, segment filter
ve stats. Aynı container `REACTOR_DUBBO_REGISTRY_ENABLED=false` ile Kubernetes Service DNS arkasında
çalışabilir; consumer provider'ları ZooKeeper üzerinden keşfetmek zorundaysa
`REACTOR_DUBBO_REGISTRY_ENABLED=true` yapılabilir.

## Typed DTO ve Hessian Güvenliği

Dubbo/Hessian deserialize tarafı bilinçli olarak strict çalışır. Provider bir method'da
`CreateCustomerCommand` gibi typed DTO kabul ediyorsa bu DTO paketini açıkça allowlist'e eklemek
gerekir:

```text
src/main/resources/security/serialize.allowlist
```

Bu sample'daki mevcut kayıt:

```text
com.reactor.rust.dubbo.sample.dto
```

Bu listeyi dar tutun. Sadece provider'ın Dubbo üzerinden gerçekten kabul ettiği DTO paketlerini
ekleyin. Bir sample request çalışsın diye Dubbo serialization güvenliğini global olarak kapatmayın.
Bu dosya eksikse typed command çağrıları provider status `40` ile dönebilir ve logda "Serialized
class ... is not in allow list" benzeri bir uyarı görürsünüz.

## Production Reçeteleri

### Reçete 1: Kubernetes ZooKeeper Discovery İçin Provider

Consumer `zookeeper-discovery` ile çalışıyorsa ve provider restart/taşınma yaşayabiliyorsa bu yolu
kullanın.

```properties
dubbo.provider.application-name=rest-sample-dubbo-provider
dubbo.provider.host=provider-pod-ip-or-headless-service-dns
dubbo.provider.bind-host=0.0.0.0
dubbo.provider.port=20880
reactor.dubbo.registry-enabled=true
reactor.dubbo.registry-address=zookeeper://zookeeper-client.platform.svc.cluster.local:2181
reactor.dubbo.registry-root=dubbo
```

Etkisi:

| Property | Ne yapar? | Production notu |
|----------|-----------|-----------------|
| `dubbo.provider.host` | ZooKeeper provider URL host'u | Consumer pod erişebilmeli.<br>Gerçek discovery için per-pod IP veya headless DNS kullanın.<br>K8s'te `127.0.0.1` publish etmeyin. |
| `dubbo.provider.bind-host` | Provider'ın dinlediği local interface | Container içinde `0.0.0.0` kullanın. |
| `reactor.dubbo.registry-enabled` | ZooKeeper kaydını açar/kapatır | Discovery modunda `true` kalsın. Consumer static K8s Service DNS kullanıyorsa `false` yapın. |
| `reactor.dubbo.registry-address` | ZooKeeper registry endpoint | Lokal desktop adresi değil Kubernetes DNS kullanın. |
| `reactor.dubbo.registry-root` | Registry namespace | Consumer `reactor.dubbo.registry-root` ile aynı olmalı. |

### Reçete 1B: Static Kubernetes Service DNS İle Provider

ZooKeeper kullanmak istemiyorsanız ve consumer `rest-sample-dubbo-provider:20880` gibi stabil bir
Kubernetes Service adresine bağlanıyorsa bu yolu kullanın. Bu modda endpoint load balancing işini
Kubernetes yapar; provider ZooKeeper session açmaz.

```properties
dubbo.provider.application-name=rest-sample-dubbo-provider
dubbo.provider.host=rest-sample-dubbo-provider
dubbo.provider.bind-host=0.0.0.0
dubbo.provider.port=20880
reactor.dubbo.registry-enabled=false
```

Consumer tarafı:

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=rest-sample-dubbo-provider:20880
```

Etkisi: provider aynı Dubbo interface'leri export eder, sadece ZooKeeper'a register olmaz. Bu,
provider tarafında aktif ZooKeeper runtime yüzeyini kaldırır. Tüm provider replica'ları tek stabil
Service adı arkasından erişilebiliyorsa doğru seçimdir. Interface'ler bağımsız hareket ediyorsa,
farklı provider grupları dinamik keşfedilecekse veya membership bilgisini Kubernetes Service yerine
registry yönetecekse ZooKeeper discovery kullanın.

### Reçete 2: HikariCP İle DB-Backed Query

Provider PostgreSQL okuyor ve consumer'a hazır JSON bytes dönecekse bunu kullanın.

```properties
sample.db.jdbc-url=jdbc:postgresql://postgresql.platform.svc.cluster.local:5432/reactor_sample
sample.db.username=reactor
sample.db.password=${DB_PASSWORD}
sample.db.maximum-pool-size=2
sample.db.minimum-idle=0
sample.db.connection-timeout-ms=3000
sample.db.validation-timeout-ms=1000
dubbo.provider.service.CustomerQueryService.max-concurrent=2
dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=1
```

Etkisi:

| Ayar | Neyi kontrol eder? | Sample neden küçük tutuyor? |
|------|--------------------|-----------------------------|
| `sample.db.maximum-pool-size` | Fiziksel PostgreSQL connection sayısı | Darboğaz çoğu zaman DB'dir; büyük pool memory ve DB contention artırır. |
| `sample.db.minimum-idle=0` | Idle tutulan DB connection sayısı | Low-RSS için iyidir; cold DB acquisition p99 bozarsa artırın. |
| `CustomerQueryService.max-concurrent` | Provider query bulkhead'i | Provider queue değerini DB kapasitesiyle hizalar. |
| Method override | Method bazlı hard cap | Pahalı tek method'u interface'in tamamını düşürmeden korur. |

### Reçete 2B: DB Query-Only Provider Image

Provider sadece read/query API'leri sunacaksa ve command method yayınlamayacaksa bunu kullanın.
POST/PATCH/DELETE akışı başka bir bounded command service, workflow veya queue tarafından yönetiliyor
ama REST consumer tarafı hızlı customer read çağrılarına ihtiyaç duyuyorsa doğru şekil budur.

Build:

```powershell
docker build -f docker/images/Dockerfile.jlink.db-query -t rest-sample-dubbo-provider:db-query-jlink .
```

Static Kubernetes Service DNS modu:

```yaml
env:
  - name: REACTOR_DUBBO_REGISTRY_ENABLED
    value: "false"
  - name: DUBBO_PROVIDER_HOST
    value: "rest-sample-dubbo-provider"
  - name: DUBBO_PROVIDER_BIND_HOST
    value: "0.0.0.0"
  - name: SAMPLE_DB_JDBC_URL
    value: "jdbc:postgresql://postgresql.platform.svc.cluster.local:5432/reactor_sample"
  - name: SAMPLE_DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: customer-db
        key: username
  - name: SAMPLE_DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: customer-db
        key: password
  - name: SAMPLE_DB_MAXIMUM_POOL_SIZE
    value: "2"
  - name: SAMPLE_DB_MINIMUM_IDLE
    value: "0"
  - name: DUBBO_PROVIDER_SERVICE_CUSTOMERQUERYSERVICE_MAX_CONCURRENT
    value: "2"
```

ZooKeeper discovery modu:

```yaml
env:
  - name: REACTOR_DUBBO_REGISTRY_ENABLED
    value: "true"
  - name: REACTOR_DUBBO_REGISTRY_ADDRESS
    value: "zookeeper://zookeeper-client.platform.svc.cluster.local:2181"
  - name: REACTOR_DUBBO_REGISTRY_ROOT
    value: "dubbo"
  - name: DUBBO_PROVIDER_HOST
    valueFrom:
      fieldRef:
        fieldPath: status.podIP
  - name: DUBBO_PROVIDER_BIND_HOST
    value: "0.0.0.0"
```

Beklenen davranış:

| Çağrı tipi | `db-query-jlink` sonucu | Neden |
|------------|--------------------------|-------|
| `CustomerQueryService#getDatabaseCustomersJson` | Çalışır | Query interface export edilir, DB/Hikari aktiftir. |
| `CustomerQueryService#getCustomerStats` | Çalışır | Küçük stats query; method cap default olarak `1` gelir. |
| `CustomerCommandService#createCustomer*` | Fail-fast döner | Command interface bilinçli olarak export edilmez. Consumer bunu unavailable/503 benzeri hata olarak göstermelidir; write path query provider içine gizlenmez. |

Aynı provider POST/PATCH/DELETE command de çalıştıracaksa bu image'ı kullanmayın. Full
`Dockerfile.jlink` image'ını kullanın veya command işlemleri ayrı provider'a ayırıp kendi DB pool ve
concurrency limitleriyle yönetin.

### Reçete 3: Queue Büyütmeden Write Command

POST/PATCH/DELETE istekleri provider command method'larına çevriliyorsa bunu kullanın.

```properties
dubbo.provider.service.CustomerCommandService.max-concurrent=2
dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.createCustomerTyped.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.patchCustomerSegment.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.patchCustomerStatus.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.patchCustomerStatusTyped.max-concurrent=1
dubbo.provider.service.CustomerCommandService.method.deleteCustomer.max-concurrent=1
sample.db.maximum-pool-size=2
```

Etkisi: command method'ları provider saturation anında unbounded queue büyütmek yerine fail-fast
davranır. Memory ve tail latency açısından daha güvenlidir. Caller mutlaka tamamlanma garantisi
istiyorsa provider içine gizli queue koymayın; önüne durable queue/workflow koyun.

### Reçete 4: PostgreSQL İle Docker Desktop

```powershell
docker compose -f docker/docker-compose.yml up --build
```

Bu komut şunları başlatır:

- Host port `15432` üzerinde PostgreSQL 16.
- `dubbo://localhost:20880` üzerinden `rest-sample-dubbo-provider`.
- `REACTOR_DUBBO_REGISTRY_ENABLED=false` ile ZooKeeper kaydı kapalı provider.

Docker Desktop üzerinde `15432` portunu kullanan manuel başlatılmış bir PostgreSQL container varsa
önce o container'ı durdurun veya `docker/docker-compose.yml` içindeki PostgreSQL published port
değerini değiştirin.

Durdurmak için:

```powershell
docker compose -f docker/docker-compose.yml down
```

Etkisi: sample consumer için en kolay Docker Desktop yoludur. Consumer'ı
`sample.dubbo.discovery=static` ve `reactor.dubbo.providers=127.0.0.1:20880` ile başlatın.
ZooKeeper discovery gerekiyorsa Reçete 1'i kullanın ve registry kaydını açıkça enable edin.

### Reçete 5: Read-Heavy Precomputed Catalog

Catalog verisi nadiren değişiyorsa ve consumer sadece REST JSON olarak expose edecekse bunu
kullanın.

```java
public final class NestedCatalogServiceImpl implements NestedCatalogService {
    private final byte[] currentCatalogJson;

    public NestedCatalogServiceImpl() {
        this.currentCatalogJson = buildCatalogJson().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] getNestedCatalogJson() {
        return currentCatalogJson;
    }
}
```

```properties
dubbo.provider.service.NestedCatalogService.max-concurrent=32
```

Etkisi: provider her çağrıda aynı JSON'u yeniden üretmez. Consumer native response handle aldıysa
`RawResponse.nativeResponse(handle.nativeId())` ile forward eder. Java payload'ı incelemek zorunda
kaldığı için bytes zaten elindeyse `RawResponse.json(bytes)` kullanır. Catalog değişiyorsa bu byte
array'i timer, event veya admin operation ile açık bir şekilde yenileyin.

### Reçete 6: Ayrı Kapasiteye Sahip Birden Fazla Interface

Tek provider process hem ucuz read hem DB/write operasyonları expose ediyorsa bunu kullanın.

```properties
dubbo.provider.service.NestedCatalogService.max-concurrent=32
dubbo.provider.service.CustomerQueryService.max-concurrent=2
dubbo.provider.service.CustomerCommandService.max-concurrent=2
dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent=1
```

Etkisi: yoğun bir DB/write method'u ucuz read method'larının bütün provider execution bütçesini
tüketmez. Bu ayrımı interface tasarımında görünür tutun; consumer route admission ayarlarını da daha
kolay tune edersiniz.

### Reçete 7: Provider Rolling Restart

Provider pod'ları deployment rollout veya node hareketiyle restart oluyorsa bunu kullanın.

```properties
dubbo.provider.bind-host=0.0.0.0
dubbo.provider.host=provider-pod-ip-or-headless-service-dns
reactor.dubbo.registry-enabled=true
reactor.dubbo.registry-address=zookeeper://zookeeper-client.platform.svc.cluster.local:2181
reactor.dubbo.registry-root=dubbo
```

Etkisi: her provider ZooKeeper'a ephemeral node register eder. Pod durduğunda ZooKeeper session
expire sonrası node'u kaldırır. Pod tekrar başladığında erişilebilir yeni adresi register eder.
Kaybolan provider'ın uzun request stall üretmemesi için consumer `REACTOR_DUBBO_TIMEOUT_MS` bounded
kalmalıdır.

### Reçete 8: Database'den REST'e Türkçe Karakterler

Database değerlerinde Türkçe karakter veya non-ASCII metin varsa bunu kullanın.

```java
byte[] json = """
        {"city":"İstanbul","district":"Şişli","customer":"Mustafa Korkmaz"}
        """.getBytes(StandardCharsets.UTF_8);
return json;
```

Etkisi: provider encoding'i bir kez doğru yapar, consumer aynı UTF-8 bytes değerini forward eder.
`String#getBytes()` çağrısını charset vermeden kullanmayın.

### Reçete 9: DB Throughput'u Dikkatli Artırmak

Sadece DB pool wait veya provider-side reject görüyorsanız ve database tarafında boş CPU varsa bunu
kullanın.

```properties
sample.db.maximum-pool-size=4
sample.db.minimum-idle=1
dubbo.provider.service.CustomerQueryService.max-concurrent=4
dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=4
```

Etkisi: daha fazla DB-backed çağrı aynı anda çalışabilir. Memory, PostgreSQL connection sayısı ve DB
CPU kullanımı da artar. Consumer DB route admission değerini aynı anda tune edin; aksi halde consumer
provider/database'in bitirebileceğinden fazla iş gönderebilir.

### Reçete 10: Production Schema Management Sınırı

Sample lokal demo için schema initialize edebilir. Production'da migration hot provider process
dışında yürütülmelidir.

```properties
sample.db.schema-init=false
sample.db.warmup=true
sample.db.initialization-fail-timeout-ms=3000
```

Etkisi: her provider pod startup'ta schema DDL çalıştırmaz. Schema migration'ı deployment job,
Flyway/Liquibase adımı veya platform migration pipeline içinde tutun. Provider startup sadece gerekli
tabloların erişilebilir olduğunu doğrulamalıdır.

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

## `rust-java-rest` 3.2.x Bu Provider'ı Nasıl Etkiler?

Bu provider `rust-java-rest` bağımlılığı almaz; bu şekilde kalması doğrudur. Provider'ın görevi,
`rest-sample-dubbo-consumer` uygulamasının v3.2.x low-overhead response yolunu kullanabileceği küçük bir
Dubbo kontratı expose etmektir.

| Provider tercihi | v3.2.x consumer üzerindeki etkisi |
|------------------|--------------------------------|
| UTF-8 JSON'u `byte[]` olarak dönmek | Native handle route'larda consumer `RawResponse.nativeResponse(handle.nativeId())` kullanır; Java bytes'ı incelemek zorundaysa `RawResponse.json(bytes)` kullanır. İki yol da ikinci DTO graph kurmaz. |
| Interface'leri küçük tutmak | Consumer timeout, backpressure ve metrics değerlerini RPC alanına göre tune edebilir. |
| Method concurrency değerlerini bounded tutmak | Provider overload heap, DB pool veya Netty queue büyümesine dönüşmeden görünür olur. |
| DB method limitlerini Hikari ile hizalamak | DB saturation derin queue yerine fail-fast verdiği için consumer p99 daha stabil kalır. |
| Provider limitlerini consumer route admission ile eşlemek | Yavaş provider method'u consumer global JNI queue'yu dolduramaz. |
| Pass-through response için büyük object graph'tan kaçınmak | v3.2.x kazanımları Hessian materialization ve JSON reserialization ile silinmez. |
| Consumer'ı normal framework dependency'de tutmak | Consumer production-like RSS ölçümünde framework sample/demo class'larını taşımaz. |
| Açık consumer property'lerini korumak | Consumer `rust-spring.properties` değerleri runtime profile default'ları tarafından ezilmez. |
| Benchmark-only route'ları production'dan ayırmak | Consumer diagnostics provider-facing route'ların legacy comparison route'lar ile kirlenmediğini gösterebilir. |
| Anon memory'yi minimal app ile ölçmek | Consumer pod sizing heap, class metadata, JIT, direct buffer, Rust-accounted memory ve residual anon olarak ayrılır. |

Provider UTF-8 JSON bytes yazıyor ve consumer JSON content type ile dönüyorsa Türkçe karakterler bu
akışta güvenli taşınır. JSON üretirken platform-default encoding kullanmayın.

BEST: read-heavy pass-through JSON için `byte[]` dönmek ve consumer native handle aldıysa
`RawResponse.nativeResponse(handle.nativeId())` kullanmak. ACCEPTABLE: consumer typed business karar
verecekse record dönmek veya Java bytes'ı bilinçli şekilde incelediyse `RawResponse.json(bytes)`
kullanmak. ANTI-PATTERN: consumer hemen tekrar JSON'a çevirecekse büyük nested object graph dönmek.

### Production Dependency Sınırı

Bu provider bilinçli olarak `rust-java-rest` dependency'si almaz. REST framework consumer process
içinde çalışır; provider process içinde değil.

Memory ve performance analizi yaparken scope'ları ayrı tutun:

| Component | Ne içermeli? | Ne içermemeli? |
|-----------|--------------|----------------|
| Provider | Plain Java Dubbo provider, gerekiyorsa HikariCP/ActiveJDBC | `rust-java-rest` runtime |
| Consumer | Normal `rust-java-rest` dependency ve `java-rust-dubbo` adapter | Framework `rust-java-rest-*-sample.jar` |
| Framework sample jar | Bundled demo/benchmark route'ları | Provider veya consumer pod sizing kanıtı |

`rust-java-rest` `3.2.x` normal jar ve `core-runtime` jar framework sample/benchmark package'larını
içermez. Bu consumer'ın production-like kalmasına yardım eder; fakat provider classpath'ini
değiştirmez çünkü provider zaten framework artifact'ini kullanmaz.

### Provider Limitlerini Consumer İle Nasıl Hizalarsınız?

Consumer sample, Dubbo route'larını `@RouteAdmission` ile korur. Bu değerleri provider limitlerinden
bağımsız tune etmeyin:

| Provider kapasitesi | Consumer ayarı | Pratik kural |
|---------------------|----------------|--------------|
| `dubbo.provider.service.NestedCatalogService.max-concurrent=16` | `reactor.rust.route-admission.get.api.v1.catalog.nested.max-concurrent=16` | Basit catalog için provider kadar in-flight kabul edilebilir. |
| `dubbo.provider.service.CustomerQueryService.max-concurrent=2` | `reactor.rust.route-admission.get.api.v1.customers.db.max-concurrent=8` | Async/kısa çağrıda kabul edilebilir.<br>p99 büyürse önce düşürün. |
| `sample.db.maximum-pool-size=2` | DB method provider limiti | Bilinçli provider-side bekleme istemiyorsanız DB method concurrency Hikari pool'u aşmamalı. |

BEST: küçük provider limitleriyle başlayıp provider CPU, DB pool wait, consumer 503 oranı, p99 latency
ve RSS'i birlikte ölçerek artırmak. ANTI-PATTERN: provider DB pool zaten saturation altındayken
consumer worker sayısını artırmak.

### Provider Hot Path Notları

Provider HikariCP ve ActiveJDBC dependency'lerini korur; çünkü bu sample Spring olmadan mevcut bir
database provider'ın nasıl bağlanacağını da gösterir. Fakat bu, hot query'lerin tamamı ActiveJDBC
`Map` path'i üzerinden geçmeli demek değildir.

Güncel provider benchmark edilen DB-backed route'larda daha hafif yolu kullanır:

| Alan | Güncel implementasyon | Neden önemli? |
|------|-----------------------|---------------|
| `GET /api/v1/customers/db` provider method'u | Hikari `PreparedStatement` + direkt `ResultSet` -> `SampleCustomer` record | Hot read path'te ActiveJDBC `Map` allocation'ı oluşmaz. |
| `customerExists` / `getCustomerDisplayName` | Sadece `1` veya `full_name` okuyan dar SQL | Scalar REST response için full customer row okunmaz. |
| `patchCustomerSegment` / `patchCustomerStatus` SQL | Sabit prepared update statement | Command hot path'te dynamic SQL formatlama yoktur. |
| Command JSON response | `StringBuilder` JSON writer | Her command response'ta `String.formatted(...)` parser/allocation maliyeti oluşmaz. |
| Read-heavy pass-through JSON | UTF-8 `byte[]` JSON | Consumer native response handle veya `RawResponse.json(bytes)` ile DTO graph kurmadan dönebilir. |

Bu hâlâ bir sample provider'dır; genel ORM tercihi dikte etmez. ActiveJDBC basit örnekler ve legacy
provider kodu için faydalı olabilir. Ancak bir provider method'u c64/c256 hot path'teyse explicit SQL,
dar kolon seçimi, bounded result size ve provider/consumer admission hizalaması gerekir. Yavaş
provider, consumer worker artırarak düzelmez.

### Write Contention Kuralı

Hot-row write baskısı distributed write'tan ayrı ele alınmalıdır:

| Write paterni | Provider davranışı | Consumer davranışı |
|---------------|--------------------|--------------------|
| Tek customer id'ye çok sayıda update | PostgreSQL row lock bottleneck olur. | `sample.command.customer-key-admission.max-concurrent-per-key=1` fazla işi erken reddetmelidir. |
| Çok farklı id'ye dağılan update | Ana limit DB pool ve provider command bulkhead olur. | Useful 2xx RPS artıyor, p99/RSS bozulmuyorsa route admission daha geniş olabilir. |
| Retry edilen create/patch/delete | Idempotency yoksa duplicate side-effect oluşabilir. | Command route'larında `reactor.dubbo.retries=0` kalsın. |

BEST: write command'ları `requestId` ile idempotent tasarlamak, provider method concurrency değerini
DB pool'a yakın tutmak ve tek business key overload olduğunda consumer'ın fail-fast davranmasına izin
vermek. ANTI-PATTERN: hot-row lock beklemelerini saniyelik p99 spike'a çevirecek global queue artışı.

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

    String getCatalogTitle();

    int countCatalogItems();

    CatalogInfo getCatalogInfo();

    List<CatalogItem> listFeaturedItems(int limit);

    Map<String, String> getCatalogAttributes();
}

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();

    CustomerSummary getCustomer(long customerId);

    List<CustomerSummary> findCustomersBySegment(String segment, int limit);

    CustomerStats getCustomerStats();

    boolean customerExists(long customerId);

    String getCustomerDisplayName(long customerId);
}

public interface CustomerCommandService {
    byte[] createCustomer(byte[] commandJson);
    byte[] patchCustomerSegment(long customerId, byte[] commandJson);
    byte[] patchCustomerStatus(long customerId, byte[] commandJson);
    byte[] deleteCustomer(long customerId, byte[] commandJson);

    CustomerMutationResult createCustomerTyped(CreateCustomerCommand command);

    CustomerMutationResult patchCustomerStatusTyped(long customerId, String status, String requestId);
}
```

Consumer byte-array method'larını hâlâ `RawResponse.json(...)` ile tekrar DTO graph kurmadan HTTP
response olarak taşıyabilir. Typed method'lar ise küçük business response'lar için normal Dubbo
object contract örneğidir.

### Provider Veri Şekli Kararı

Provider method imzası consumer'ın memory ve p99 davranışını doğrudan etkiler. Bu yüzden method
shape'i sadece API okunabilirliğiyle değil, veri akışı ve sorumluluk sınırıyla seçilmelidir.

| Provider method | Provider sorumluluğu | Consumer etkisi | Ne zaman |
|-----------------|----------------------|-----------------|----------|
| `byte[]` hazır JSON | JSON shape + domain validation | `RawResponse` ile DTO graph yok | Read-heavy pass-through |
| `record` | Domain object üretir | Hessian decode + HTTP JSON serialize | Consumer field okuyacaksa |
| `List<record>` | Küçük bounded page üretir | Liste + item allocation | Limitli liste/sayfa |
| `byte[] command` | JSON parse + command validation | Consumer ince kalır | En düşük allocation command |
| `record command` | Typed command contract | Request encode + response decode | Okunabilir business command |

Sorumluluk çizgisi:

| Konu | Sahip |
|------|------|
| DB constraint, uniqueness, mutation rule | Provider |
| Hazır response JSON formatı | `byte[]` dönen provider method |
| HTTP auth, tenant, basic request reject | Consumer/gateway |
| Büyük response streaming/file kararı | Provider + consumer contract |
| Contract compatibility | Ortak API jar + contract test |

BEST: hot read ve pass-through response için provider `byte[]` UTF-8 JSON döndürsün, consumer native
handle aldıysa `RawResponse.nativeResponse(handle.nativeId())` ile taşısın. ACCEPTABLE: consumer
payload'ı incelemek veya dönüştürmek için Java bytes aldıysa `RawResponse.json(bytes)` kullansın ya
da küçük typed business response için `record` dönülsün. ANTI-PATTERN: büyük nested `List<record>`
üretip consumer JVM'de tekrar JSON'a çevirtmek.

Method veri yapısı kataloğu:

| Method şekli | Sample method | Neden var? | Maliyet |
|--------------|---------------|------------|---------|
| `byte[]` UTF-8 JSON | `getNestedCatalogJson()`<br>`getDatabaseCustomersJson()` | En düşük overhead pass-through JSON | En düşük consumer allocation<br>DTO graph yok |
| `String` | `getCatalogTitle()`<br>`getCustomerDisplayName(id)` | Küçük scalar veri | Küçük allocation + Hessian decode |
| Primitive | `countCatalogItems()`<br>`customerExists(id)` | Count ve yes/no lookup | Çok küçük object graph |
| `record` | `getCatalogInfo()`<br>`getCustomer(id)`, `getCustomerStats()` | Typed business data | Hessian tek record materialize eder |
| `List<record>` | `listFeaturedItems(limit)`<br>`findCustomersBySegment(...)` | Küçük bounded sayfalar | Liste + item record<br>limit strict |
| `Map<String,String>` | `getCatalogAttributes()` | Küçük metadata | Map allocation<br>büyük map hot path'te yok |
| `record -> record` command | `createCustomerTyped(...)` | Okunabilir typed command contract | Request encode + response decode |
| `byte[] -> byte[]` command | `createCustomer(byte[])` | En düşük allocation command pass-through | Validation/JSON shape provider sorumluluğu |

Interface ayrımı:

| Interface | Implementation | Sorumluluk |
|-----------|----------------|------------|
| `NestedCatalogService` | `NestedCatalogServiceImpl` | Static catalog örnekleri: raw JSON bytes, scalar, record, list ve map. |
| `CustomerQueryService` | `CustomerQueryServiceImpl` | PostgreSQL-backed query örnekleri: raw JSON bytes, record lookup, list page, stats, string, boolean. |
| `CustomerCommandService` | `CustomerCommandServiceImpl` | Hem compact JSON bytes hem typed command record ile POST/PATCH/DELETE tarzı DB command'leri. |

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

| Scope | Property | Default | Neden |
|-------|----------|---------|-------|
| Tüm servisler | `dubbo.provider.service.default.max-concurrent` | `16` | Güvenli fallback |
| `NestedCatalogService` | `dubbo.provider.service.NestedCatalogService.max-concurrent` | `16` | Catalog JSON CPU/allocation sınırı |
| `NestedCatalogService` typed method | `dubbo.provider.service.NestedCatalogService.method.<method>.max-concurrent` | `8` | Typed DTO/list bounded kalır |
| `CustomerQueryService` | `dubbo.provider.service.CustomerQueryService.max-concurrent` | `2` | `sample.db.maximum-pool-size=2` ile hizalı |
| `getDatabaseCustomersJson` | `dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent` | `1` | DB-backed method override |
| Typed DB method'ları | `dubbo.provider.service.CustomerQueryService.method.<method>.max-concurrent` | `1-2` | Record/list/stats Hikari ile hizalı |
| `CustomerCommandService` | `dubbo.provider.service.CustomerCommandService.max-concurrent` | `2` | Write-side DB concurrency Hikari ile hizalı |
| Write method'ları | `dubbo.provider.service.CustomerCommandService.method.<method>.max-concurrent` | `1` | Lokal sample write davranışı öngörülebilir |

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

### Consumer Reçeteleri Provider Kapasitesine Nasıl Bağlanır?

`micro-1x1` ve `balanced-stable-4x4` gibi consumer benchmark isimleri provider profile değildir.
Bunlar REST consumer'ın bu provider'a ne kadar iş göndermesine izin verildiğini anlatır. Gerçek DB
concurrency ise provider tarafında Hikari ve interface/method gate değerleriyle kontrol edilir.

| Consumer reçetesi | Provider'a ne gelir? | Provider başlangıç ayarı | Neden |
|-------------------|----------------------|--------------------------|-------|
| `micro-1x1` | Az sayıda concurrent RPC. Low-RSS consumer için uygundur. | <small><code>sample.db.maximum-pool-size=1</code><br><code>dubbo.provider.service.CustomerQueryService.max-concurrent=1</code><br><code>dubbo.provider.service.CustomerCommandService.max-concurrent=1</code></small> | DB işi sıkı sınırda kalır. Spike, derin queue yerine hızlı `503` üretir. |
| `micro-2x2` | Consumer tarafında daha fazla RPC paralelliği. | <small><code>sample.db.maximum-pool-size=2</code><br><code>dubbo.provider.service.CustomerQueryService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerCommandService.max-concurrent=2</code></small> | PostgreSQL ve provider CPU tarafında ölçülmüş boşluk varsa faydalıdır. |
| `balanced-stable-4x4` | Daha çok read/command işi kabul edilir; queue, wide moda göre daha kontrollüdür. | <small><code>sample.db.maximum-pool-size=4</code><br><code>dubbo.provider.service.CustomerQueryService.max-concurrent=4</code><br><code>dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=2-4</code></small> | 2xx RPS artar. DB wait, p99 ve RSS birlikte ölçülmelidir. |
| `balanced-wide-4x4` | Consumer route budget en geniş haldedir. | Sadece PostgreSQL, provider CPU ve row-lock davranışı load test ile kanıtlandıysa. | Aksi halde overload queue içinde saklanır, p99/RSS artar. |

`c64` veya `c256` değerlerini Hikari connection sayısı gibi okumayın. Bunlar load testteki client
concurrency seviyeleridir. Hikari `2` ise aynı anda sadece iki DB çağrısı çalışır. Diğer request'ler
consumer/provider queue içinde kısa süre bekler veya fail-fast olur. Queue büyütmek `503` oranını
azaltabilir, fakat RSS ve p99 değerlerini de artırır.

## Provider Use Case Cookbook

Bu örnekler REST consumer üzerinden çağrılır, fakat davranış provider içinde uygulanır. Bu ayrım
bilinçlidir: REST process küçük kalır, provider DB access, mutation rule ve Hikari kapasitesinin
sahibi olur.

| Use case | Provider interface | Darboğaz | Başlangıç |
|----------|--------------------|----------|-----------:|
| Static/nested catalog read | `NestedCatalogService` | CPU/string generation | `16` |
| PostgreSQL customer read | `CustomerQueryService` | Hikari/PostgreSQL | <small><code>dubbo.provider.service.CustomerQueryService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent=1</code></small> |
| Customer create/upsert | `CustomerCommandService.createCustomer` | Hikari/PostgreSQL unique key | <small><code>dubbo.provider.service.CustomerCommandService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent=1</code></small> |
| Segment/status patch | `CustomerCommandService.patchCustomer*` | Hikari/PostgreSQL update | <small><code>dubbo.provider.service.CustomerCommandService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerCommandService.method.patchCustomerStatus.max-concurrent=1</code></small> |
| Customer delete | `CustomerCommandService.deleteCustomer` | Hikari/PostgreSQL delete/audit | <small><code>dubbo.provider.service.CustomerCommandService.max-concurrent=2</code><br><code>dubbo.provider.service.CustomerCommandService.method.deleteCustomer.max-concurrent=1</code></small> |

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

### Deklaratif Provider Yüzeyi

Provider startup kodu bilinçli olarak küçük tutulur. Her application class sadece açacağı servis
yüzeyini söyler:

```java
DubboProviderSupport support = DubboProviderSupport.fromProperties(ProviderProperties.asProperties());

List<DubboProviderSupport.ServicePlan<?>> services = List.of(
    support.service(NestedCatalogService.class, catalogService),
    support.service(CustomerQueryService.class, customerService),
    support.service(CustomerCommandService.class, customerCommandService));
```

`DubboProviderSupport`, `java-rust-dubbo` içinden gelir. Tekrar eden işleri yapar: servis export
eder, interface/method concurrency limitlerini property'den okur, startup loglarını basar ve kapanışta
kaynakları doğru sırayla kapatır. Daha küçük provider istiyorsanız bu listeden servis çıkarın veya
hazır Maven profile'larından birini kullanın:

| Provider şekli | Servis planı | Ne zaman kullanılır? |
|----------------|--------------|----------------------|
| `catalog-static-provider` | Sadece `CatalogJsonService` | En küçük hazır JSON read provider gerekiyorsa. |
| `db-query-provider` | Sadece `CustomerQueryService` | Provider PostgreSQL okuyacak ama command açmayacaksa. |
| default/full | Catalog + customer query + customer command | POST/PATCH/DELETE dahil bütün sample gerekiyorsa. |

Bu bir reflection scanner değildir. Hangi interface'lerin export edildiği kodda açık kalır. Tekrar
eden Dubbo lifecycle kodu ise tek helper içinde durur. Böylece startup öngörülebilir kalır ve gizli
classpath büyümesi oluşmaz.

DB boilerplate için de aynı kural geçerlidir. `PostgresCustomerRepository`,
`com.reactor.rust.dubbo.provider.jdbc.JdbcRepository` sınıfından miras alır. Hikari kurulumu
`HikariDataSources.create(...)` ile yapılır. Library connection, query ve lifecycle tesisatını
üstlenir. SQL, index, row mapping ve write semantics kararı sample içinde açık kalır.

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

com.reactor.rust.dubbo.sample
  Ortak Dubbo interface örnekleri. Production'da shared API jar'a taşınmalı.

com.reactor.rust.dubbo.provider
  Library içindeki provider desteği: explicit export, ZooKeeper registration, lifecycle ve concurrency gate'leri.
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
| `DubboProviderSupport` | Provider property'lerini okur ve explicit servis listesini export eder. | Hayır |
| `PlainDubboProvider` | Dubbo protocol export ve exporter lifecycle yöneten library sınıfıdır. | Hayır |
| `ZookeeperDubboProviderRegistration` | ZooKeeper session ve ephemeral node lifecycle yöneten library sınıfıdır. | Hayır |
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
| UTF-8 JSON taşıyan `byte[]` | Consumer cevabı HTTP JSON olarak forward eder. | En düşük. Native handle route'larda `RawResponse.nativeResponse(handle.nativeId())`, Java-byte route'larda `RawResponse.json(bytes)` kullanılır. İkinci DTO graph kurulmaz. |
| `record` DTO | Consumer typed data ile filtreleme, validation, enrichment veya business karar yapar. | Hessian2 decode Java object graph oluşturur; HTTP response tekrar JSON serialize edebilir. |
| Plain class DTO | Legacy framework veya serializer record desteklemiyorsa. | Record'a benzer, genelde daha mutable ve daha az explicit. |
| JSON `String` | Küçük/basit payload ve okunabilirlik byte kontrolünden önemliyse. | String allocation ve sonradan UTF-8 encoding. Hot path için `byte[]` daha doğru. |
| Büyük nested object graph | Consumer gerçekten tüm object model'e ihtiyaç duyuyorsa. | En yüksek heap, GC, p99 latency ve RSS riski. |

Bu sample'da `byte[]` bilinçli tercihtir. REST consumer catalog yapısını anlamak zorunda değildir;
provider cevabını HTTP üzerinden dışarı açması yeterlidir. Consumer native response handle aldıysa
`RawResponse.nativeResponse(handle.nativeId())` dönmelidir. Payload'ı incelemek zorunda kaldığı için
Java bytes aldıysa `RawResponse.json(bytes)` kullanmalıdır.

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

## v3.2.x Consumer ile Çalıştırma Sırası

Lokal test için en temiz sıra:

```text
1. ZooKeeper'ı başlatın.
2. DB-backed endpoint'ler açıksa PostgreSQL'i başlatın.
3. Bu provider'ı başlatın.
4. rust-java-rest 3.2.x kullanan rest-sample-dubbo-consumer'ı başlatın.
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

| Property | Ne işe yarar? | Ne zaman değiştirirsin? |
|----------|---------------|-------------------------|
| `dubbo.provider.host` | Dubbo URL içinde provider host bilgisini ilan eder. | Pod IP, node IP veya erişilebilir host ne ise ona göre ayarla. |
| `dubbo.provider.bind-host` | Local bind host seçer. | Container içinde gerekirse `0.0.0.0` yap. |
| `dubbo.provider.port` | Dubbo provider portunu açar. | `20880` uygun değilse değiştir. |
| `reactor.dubbo.registry-enabled` | ZooKeeper registration açar veya kapatır. | Static Service DNS için `false`; ZooKeeper discovery için `true`. |
| `reactor.dubbo.registry-address` | ZooKeeper adresini verir. | Sadece registry açıksa doldur. |
| `reactor.dubbo.registry-root` | ZooKeeper namespace değeridir. | Platform farklı root kullanıyorsa değiştir. |
| `dubbo.provider.service.default.max-concurrent` | Default method concurrency limitidir. | Küçük pod için düşür. Sadece load test sonrası artır. |
| `dubbo.provider.service.NestedCatalogService.max-concurrent` | Catalog interface concurrency limitidir. | Catalog read trafiğine göre ayarla. |
| `dubbo.provider.service.NestedCatalogService.method.*.max-concurrent` | Catalog method bazlı override sağlar. | List/heavy methodları tüm interface'i kısmadan sınırlamak için kullan. |
| `dubbo.provider.service.CustomerQueryService.max-concurrent` | DB query interface concurrency limitidir. | Hikari pool size ile hizala. |
| `dubbo.provider.service.CustomerQueryService.method.*.max-concurrent` | DB query method override sağlar. | List/stats query p99 yükselirse düşür. |
| `dubbo.provider.service.CustomerCommandService.max-concurrent` | Write command concurrency limitidir. | DB ve idempotency korunacak şekilde bounded tut. |
| `dubbo.provider.service.CustomerCommandService.method.*.max-concurrent` | Write method override sağlar. | Aynı row üzerinde yarışan hot command'leri düşür. |
| `sample.db.jdbc-url` | PostgreSQL bağlantısını verir. | Her ortamda kendi DB adresini ver. |
| `sample.db.maximum-pool-size` | Hikari connection üst limitidir. | DB kapasitesi ve provider method limitleriyle hizala. |
| `sample.db.minimum-idle` | Idle DB connection sayısını belirler. | Idle RSS düşük olsun istiyorsan `0` kullan. |
| `sample.db.connection-timeout-ms` | DB connection bekleme süresini sınırlar. | Fail-fast için düşür. Yavaş startup için ölçerek artır. |
| `sample.db.schema-init` | Demo schema ve data oluşturur. | Production'da `false` olmalı. |
| `sample.db.warmup` | Ready olmadan önce DB connection açar. | İlk request latency önemliyse aç. |
| `io.netty.allocator.numDirectArenas` | Netty direct memory arena sayısını ayarlar. | RSS hassas podlarda düşük tut. |

## Hızlı Başlangıç

Gereksinimler:

- Java 21
- Maven 3.9+
- PostgreSQL için Docker; ZooKeeper sadece `reactor.dubbo.registry-enabled=true` yaptığınızda gerekir

Sample'ın default modu static/no-ZooKeeper modudur. ZooKeeper'ı sadece discovery modunu test etmek
istiyorsanız başlatın:

```powershell
docker run -d --name rust-java-dubbo-zookeeper -p 2181:2181 zookeeper:3.7.2
```

Static modda ZooKeeper'ı başlatmanız gerekmez. Packaged default zaten `false`, ama runtime seçimini
net göstermek için environment variable da verebilirsiniz:

```powershell
$env:REACTOR_DUBBO_REGISTRY_ENABLED="false"
mvn -q exec:java
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

### OpenJ9 21 jlink Minimum Image

Provider sample'ı tam JRE image yerine özel ve küçük bir OpenJ9 runtime ile çalıştırmak istiyorsanız
`Dockerfile.jlink` kullanın. Image yine gerçek bir Dubbo provider'dır ve Hikari/PostgreSQL desteği
taşır; jlink sadece kullanılmayan Java runtime modüllerini ayıklar.

Build:

```powershell
docker build -f docker/images/Dockerfile.jlink -t rest-sample-dubbo-provider:jlink .
```

PostgreSQL warmup kapalı minimum provider smoke:

```powershell
docker network create reactor-jlink-smoke

docker run --rm --name rest-sample-dubbo-provider `
  --network reactor-jlink-smoke `
  -p 20880:20880 `
  -e DUBBO_PROVIDER_HOST=rest-sample-dubbo-provider `
  -e DUBBO_PROVIDER_BIND_HOST=0.0.0.0 `
  -e REACTOR_DUBBO_REGISTRY_ENABLED=false `
  -e SAMPLE_DB_SCHEMA_INIT=false `
  -e SAMPLE_DB_WARMUP=false `
  rest-sample-dubbo-provider:jlink
```

PostgreSQL/Hikari aktif provider:

```powershell
docker run --rm --name rest-sample-dubbo-provider `
  --network reactor-jlink-smoke `
  -p 20880:20880 `
  -e DUBBO_PROVIDER_HOST=rest-sample-dubbo-provider `
  -e DUBBO_PROVIDER_BIND_HOST=0.0.0.0 `
  -e REACTOR_DUBBO_REGISTRY_ENABLED=false `
  -e SAMPLE_DB_JDBC_URL=jdbc:postgresql://postgres:5432/reactor_sample `
  -e SAMPLE_DB_USERNAME=reactor `
  -e SAMPLE_DB_PASSWORD=reactor `
  -e SAMPLE_DB_MAXIMUM_POOL_SIZE=2 `
  -e SAMPLE_DB_MINIMUM_IDLE=0 `
  rest-sample-dubbo-provider:jlink
```

Notlar:

- `JAVA_MODULES` içinden `java.desktop` modülünü çıkarmayın. Dubbo headless provider içinde bile Java Beans sınıflarını kullanır.
- `binutils` sadece build stage içinde kurulur; çünkü `jlink --strip-debug` `objcopy` ister. Runtime image'a taşınmaz.
- Bu workspace'teki lokal smoke testte DB warmup kapalı provider RSS yaklaşık `55.8 MiB` görüldü. DB-backed çalışma daha yüksek olur; Hikari, JDBC, schema init ve aktif connection'lar memory ekler.
- `docker/images/Dockerfile.jlink.db-query`, `db-query-provider` profile'ı altında `mvn clean package` çalıştırır. Böylece eski derlenmiş sınıflar query-only jar içine command/catalog servislerini yanlışlıkla taşıyamaz.
- `REACTOR_DUBBO_REGISTRY_ENABLED=false` ise ZooKeeper gerekmez; consumer provider'a Docker/Kubernetes service DNS üzerinden gidebilir.

## Sözlük

| Terim | Anlamı |
|---|---|
| Provider | Business method'ları dışarı açan Dubbo server'dır. |
| Consumer | Bu provider'ı çağıran REST servisidir. |
| Static mod | Provider ZooKeeper'a kayıt olmaz. Consumer Service DNS veya sabit adres kullanır. |
| ZooKeeper registration | Provider'ın Dubbo URL bilgisini ZooKeeper'a yazmasıdır. |
| HikariCP | DB-backed method'lar için kullanılan JDBC connection pool'dur. |
| ActiveJDBC | Sample içinde kullanılan hafif DB erişim katmanıdır. |
| Method limiti | Tek provider method'u için eşzamanlı çağrı üst sınırıdır. |
| Interface limiti | Tek provider interface'i için eşzamanlı çağrı üst sınırıdır. |
| Bulkhead | Yoğun bir method'un diğer işleri bozmasını engelleyen limittir. |
| Hot row | Aynı anda çok sayıda write alan database satırıdır. |
| RSS | Kubernetes memory limitinin gördüğü process memory değeridir. |
| Hazır JSON | Provider tarafından serialize edilmiş JSON byte verisidir. Consumer bunu doğrudan forward edebilir. |

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
