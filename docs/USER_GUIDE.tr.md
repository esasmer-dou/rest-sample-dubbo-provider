# rest-sample-dubbo-provider Kullanıcı Rehberi

Bu rehber ilk kullanım içindir.

Amaç kısa ve nettir: REST consumer'ın çağıracağı plain Java Dubbo provider'ı çalıştırmak.

## İçindekiler

1. [Bu Proje Ne İşe Yarar?](#bu-proje-ne-işe-yarar)
2. [Akış Nasıl Çalışır?](#akış-nasıl-çalışır)
3. [Ne Zaman Kullanılır?](#ne-zaman-kullanılır)
4. [Hızlı Başlangıç](#hızlı-başlangıç)
5. [Provider Şekilleri](#provider-şekilleri)
6. [DB Ve Hikari Ayarları](#db-ve-hikari-ayarları)
7. [Concurrency Kuralı](#concurrency-kuralı)
8. [Sık Hatalar](#sık-hatalar)

## Bu Proje Ne İşe Yarar?

`rest-sample-dubbo-provider`, Dubbo service implementasyonlarını barındırır.

Spring Boot kullanmaz. REST API açmaz.

İster hazır JSON döner, ister PostgreSQL/Hikari üzerinden veri okuyup command işler.

## Akış Nasıl Çalışır?

```mermaid
flowchart LR
    A["Dubbo Consumer"] --> B["Dubbo Provider"]
    B --> C["Service Implementation"]
    C --> D["HikariCP"]
    D --> E["PostgreSQL"]
```

Consumer sadece çağırır. DB bağlantısı provider içindedir.

## Ne Zaman Kullanılır?

| Senaryo | Bu proje uygun mu? | Neden |
|---------|--------------------|-------|
| Consumer Dubbo üzerinden veri alacak | Evet | Ana kullanım budur. |
| Hazır JSON provider lazım | Evet | Catalog static profile uygundur. |
| PostgreSQL query provider lazım | Evet | DB query profile uygundur. |
| REST endpoint açılacak | Hayır | REST consumer tarafında açılır. |
| DB write command işlenecek | Evet | Customer command service örneği vardır. |

## Hızlı Başlangıç

PostgreSQL hazırsa provider'ı çalıştırın:

```powershell
mvn -q package
java -jar target/rest-sample-dubbo-provider-0.1.1.jar
```

Default local ayar ZooKeeper istemez:

```properties
reactor.dubbo.registry-enabled=false
dubbo.provider.host=127.0.0.1
dubbo.provider.port=20880
```

## Provider Şekilleri

| Şekil | Ne açar? | Ne zaman kullanılır? |
|-------|----------|----------------------|
| `catalog-static-provider` | Sadece hazır catalog JSON | En küçük read-only provider |
| `db-query-provider` | Sadece DB query service | Command yoksa |
| Default/full | Catalog + customer query + command | Tüm sample endpoint'leri gerekiyorsa |

## DB Ve Hikari Ayarları

| Property | Ne işe yarar? | Başlangıç |
|----------|---------------|-----------|
| `sample.db.jdbc-url` | PostgreSQL bağlantı adresi | Ortama göre değişir. |
| `sample.db.maximum-pool-size` | Fiziksel DB connection üst limiti | `2` iyi başlangıçtır. |
| `sample.db.minimum-idle` | Boşta tutulacak connection sayısı | Low-RSS için `0`. |
| `sample.db.connection-timeout-ms` | Pool'dan connection bekleme süresi | Kısa tutun, DB yavaşsa ölçün. |
| `sample.db.schema-init` | Demo schema oluşturur | Production'da kapalı olmalıdır. |

## Concurrency Kuralı

Provider limitleri Hikari kapasitesiyle uyumlu olmalıdır.

| Interface | Property | Öneri |
|-----------|----------|-------|
| Query service | `dubbo.provider.service.CustomerQueryService.max-concurrent` | Hikari pool size veya daha düşük. |
| DB list method | `dubbo.provider.service.CustomerQueryService.method.getDatabaseCustomersJson.max-concurrent` | DB yavaşsa `1-2`. |
| Command service | `dubbo.provider.service.CustomerCommandService.max-concurrent` | Hikari pool size veya daha düşük. |
| Command method | `dubbo.provider.service.CustomerCommandService.method.createCustomer.max-concurrent` | Aynı key/write contention varsa `1`. |

Client tarafında `c64` yük gelmesi Hikari'nin 64 connection açacağı anlamına gelmez.

Hikari `2` ise aynı anda iki DB işi çalışır. Geri kalan iş kısa süre bekler veya fail-fast olur.

## Sık Hatalar

| Belirti | Muhtemel neden | Çözüm |
|---------|----------------|-------|
| Consumer `provider unavailable` alıyor | Provider kapalı veya adres yanlış. | Host, port ve static/ZooKeeper ayarlarını kontrol edin. |
| DB wait artıyor | Hikari pool dolu. | Önce SQL ve index kontrol edin, sonra pool artırın. |
| p99 saniyelere çıkıyor | Queue fazla büyüdü. | Provider method limitlerini DB kapasitesine indirin. |
| RSS yüksek | Full provider yüzeyi gereksiz olabilir. | `catalog-static-provider` veya `db-query-provider` kullanın. |

