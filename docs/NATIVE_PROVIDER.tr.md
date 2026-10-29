# Native Provider Ve Resmî Motor Karşılaştırması

[English](NATIVE_PROVIDER.md) | [Türkçe](NATIVE_PROVIDER.tr.md)

Bu isteğe bağlı profiller, aynı Java katalog servisini resmî Dubbo motoru ve ortak
native motorla karşılaştırır. Mevcut varsayılan provider değişmez. Bu profiller,
bu `0.8.0` kaynak kodunda vardır; eşleşen modülleri ve native dosyaları birlikte
derleyin. Bu rehber yayımlanmış paketin bulunabilirliğini doğrulamaz. Kaydedilmiş
odak sıcak yük karşılaştırması, hedef platform dağıtımı ve bütün ürünün birleşik
kabulünden ayrıdır.

## Profil Seçimi

| Profil | Başlangıç sınıfı | Çıktı dizini | Ayar kaynağı |
| --- | --- | --- | --- |
| `stream-catalog-provider` | `com.reactor.sample.dubbo.provider.streamcatalog.StreamCatalogProviderApplication` | `target/stream-catalog-provider` | `stream-catalog-provider.properties` |
| `native-provider` | `com.reactor.sample.dubbo.provider.nativeprovider.NativeProviderApplication` | `target/native-provider` | `native-provider.properties` |

İkisi de veritabanı ve ZooKeeper olmadan, boş group ve `0.0.0` version ile
`CatalogJsonService` ve `StreamCatalogService` sunar. Kullanılan Java sınıfları
aynıdır: `CatalogJsonServiceImpl` ve `StreamCatalogServiceImpl`. Motor
karşılaştırmasında farklı/eski payload yerine resmî profili kontrol olarak kullanın.
İki derleme profilini birlikte açmayın. Ayrı çıktı dizinleri, önceki derlemenin
sınıflarının diğer profile karışmasını önler.

Native profil; resmî Dubbo/HessianLite, Netty, ZooKeeper/Curator, Spring ve yalnız
derlemede gereken annotation/processor artifact'lerini bağımlılık ağacında reddeder.
Eski API JAR'ında kullanılmayan legacy sınıfların bulunması, resmî motorun
yüklendiği anlamına gelmez.

## Derleme Ve Çalıştırma

Java 21, Maven 3.9+, Maven deposunda güncel kaynaktan hazırlanmış bağımlılıklar ve
güncel ABI-3 yeteneklerini içeren açıkça seçilmiş Linux SO gerekir. Yayımlanmış
runtime sürümü veya ABI numarası tek başına yeterli değildir. Önce API, ortak
model/utility ve ortak runtime aynı kaynak revizyonundan hazırlanmalıdır.
Örnekleri kendi Linux derleme ortamınızda, workspace kökünden çalıştırın.
Komutlar altyapı kurmaz veya mevcut altyapıyı değiştirmez.

Aşağıdaki komutlar test derlemesini ve çalıştırmasını kapatarak paket üretir:

```bash
mvn -B -ntp -f java-rust-dubbo/native-provider-build/pom.xml -Dmaven.test.skip=true install
mvn -B -ntp -f rest-sample-dubbo-provider/pom.xml -Pstream-catalog-provider -Dmaven.test.skip=true package
mvn -B -ntp -f rest-sample-dubbo-provider/pom.xml -Pnative-provider -Dmaven.test.skip=true package
```

Her uygulamayı ayrı terminalde başlatın. Farklı portlar aynı host üzerinde bağlantı
kontrolü sağlar; tek CPU'da yarışan iki süreç izole yük testi değildir.

```bash
java -Xms16m -Xmx48m -XX:ActiveProcessorCount=1 \
  -Dsample.provider.engine=official -Dreactor.dubbo.registry-enabled=false \
  -Ddubbo.provider.port=20880 \
  -jar rest-sample-dubbo-provider/target/stream-catalog-provider/rest-sample-dubbo-provider-0.8.0.jar
```

`NATIVE_LIB` değişkenini eski paketli dosya yerine güncel doğrulanmış SO'ya ayarlayın:

```bash
NATIVE_LIB=/opt/reactor/native/librust_dubbo.so
java -Xms16m -Xmx48m -XX:ActiveProcessorCount=1 \
  -Dsample.provider.engine=native -Dreactor.dubbo.registry-enabled=false \
  -Ddubbo.provider.port=20881 "-Dreactor.dubbo.native.path=$NATIVE_LIB" \
  -jar rest-sample-dubbo-provider/target/native-provider/rest-sample-dubbo-provider-0.8.0.jar
```

JVM seçenekleri container CPU/RSS sınırı koymaz. Native motor bütün ağ
arayüzlerine bind eder; erişimi yalnız bu işe ayrılmış test ortamıyla sınırlayın.
Shutdown hook'un drain yapabilmesi için `Ctrl+C` veya normal sonlandırma kullanın.

## Tipli Katalog Ve HTTP Kullanımı

Wire contract tam olarak
`com.reactor.rust.dubbo.sample.StreamCatalogService.read(int count)` metodudur;
`List<com.reactor.rust.dubbo.sample.dto.StreamCatalogRow>` döndürür.

| Record | Alanlar |
| --- | --- |
| `StreamCatalogRow` | `long id`, `String name`, `double amount`, `boolean active`, `StreamCatalogDetails details` |
| `StreamCatalogDetails` | `String city`, `List<String> tags`, null olabilen `String note` |

Örnek veri Türkçe/ek Unicode karakterleri, escaping, null note, işaretli sıfır ve
zor finite double değerlerini kapsar. İş kuralı `0..10000` count kabul eder ve
sonucu kesmez. 32/512 satırlık sonuçlar önceden hazırlanmış immutable listelerdir;
512 üzeri çağrılar ek satırlar oluşturur ve düşük tahsisat senaryosu değildir.
İş kuralı sınırı, provider'ın 4096 öğelik çıkış veya payload sınırını kaldırmaz;
büyük sonuç sessizce kısaltılmak yerine hata verebilir.

`CatalogJsonService.getNestedCatalogJson()` mevcut JSON-byte yanıtını korur.
Tipli serviste `@DubboStreamed(directJson=true)` vardır; utility içindeki annotation
bağımlılığı provided/optional'dır, uygulamaya transitif runtime gereksinimi taşımaz.
Alias interface yerine gerçek interface ve DTO binary adlarını kullanın.

Mevcut `direct-json-consumer` profilinde provider adresini
`sample.direct-json.providers=127.0.0.1:20880` veya `127.0.0.1:20881` seçin.
RPC timeout varsayılanı 3000 ms'dir; native provider'ın 5000 ms deadline'ı için
`sample.direct-json.timeout-ms=6000` seçin, dış HTTP deadline'ını daha uzun tutun.
Consumer kendi HTTP portunda çalışırken (varsayılan 8080):

```bash
curl --fail-with-body 'http://127.0.0.1:8080/api/v1/catalog/rows?count=32'
curl --fail-with-body 'http://127.0.0.1:8080/api/v1/catalog/rows?count=512'
```

Provider argüman çözümü record canonical constructor'larını gerçekten çağırır;
validation/normalization da çalışır. Consumer direct-JSON yolu satır/detail DTO'su
oluşturmaz ve bu constructor'ları **çalıştırmaz**. O yolda constructor yan etkisine
veya constructor validation'ına güvenmeyin.

Constructor kontrolü için sınıf başlatılmadan önce
`-Dsample.stream-catalog.count-constructors=true` verin.
`StreamCatalogRow.constructionCount()` ve
`StreamCatalogDetails.constructionCount()` süreç başına biriken sayaçlardır;
ısınma sonrası farkları karşılaştırın, provider ve consumer toplamlarını değil.
Varsayılan kapalıdır; bütün tahsisatların ölçümü değildir. Consumer örneğinin
`/api/v1/catalog/diagnostics` endpoint'i sayaçları gösterir; dışarıya açmayın.

## Ayarlar

Öncelik: JVM system property, ortam değişkeni, dış
`reactor.config.file` / `REACTOR_CONFIG_FILE`, ardından profil kaynağı.
Ortam anahtarlarında harfleri büyütün, nokta/tire yerine alt çizgi kullanın.

| Ayar | Native sample varsayılanı | İzin verilen |
| --- | ---: | --- |
| `sample.provider.engine` | `native` | Yalnız `native`; kontrol profili `official` ister |
| `reactor.dubbo.registry-enabled` | `false` | İki profilde de `false` |
| `dubbo.provider.port` | 20880 | 1..65535 |
| `sample.native-provider.io-workers` | 1 | 1..8 |
| `sample.native-provider.business-workers` | 4 | 1..64 |
| `sample.native-provider.queue-capacity` | 16 | 1..1024 |
| `sample.native-provider.max-payload-bytes` | 262144 | 1024..1048576 |
| `sample.native-provider.request-timeout-ms` | 5000 | 1..60000 |
| `sample.native-provider.drain-timeout-ms` | 5000 | 0..60000 |
| `sample.native-provider.max-collection-items` | 1024 | 1..4096 |
| `sample.native-provider.service-max-concurrent` | 4 | 1..business-workers |

`reactor.dubbo.native.path` açık bir JVM system property'dir. Native host/bind
değeri `0.0.0.0` dışında olursa sessizce yok sayılmaz, reddedilir. Yanlış motor
ve registry ayarı başlangıcı durdurur; fallback veya gizli RPC replay yoktur.
Resmî kontrol profili dört fixed iş worker'ı, queue 16, bir IO worker ve servis
limiti 4 kullanır; resmî executor anahtarları kendi ayar kaynağındadır.

`(business-workers + queue-capacity) * max-payload-bytes` en fazla 32 MiB olabilir.
Sample varsayılanı 5 MiB mantıksal istek kabul kapasitesidir; 5 MiB slab veya toplam
RSS rezervasyonu değildir. Adaptör API'sindeki `Options.defaults` ise queue 64
kullanır ve 17 MiB eder. Servis/şema/parser ve global kabul sınırlarının tamamı
[adaptör rehberindedir](https://github.com/esasmer-dou/java-rust-dubbo/blob/master/native-provider/README.tr.md).

## Sahiplik, İzleme Ve Sorun Giderme

Adaptör, `rust-spring` içine ikinci Dubbo ağ katmanı eklemek yerine ortak ABI-3
JNI/Tokio motorunu bilinçli olarak kullanır. Java iş mantığı ile generated Java
dispatch/codec'ler bu yerleşim kararı nedeniyle değiştirilmez.

Native motor, bağlantıdaki ilk uygun primitive çağrıdan en fazla 1 KiB'lık bir
protokol başlık planı öğrenir. Yalnız imzaya güvenmez; wire argümanlarının da
sayı, boolean veya null olmasını doğrular. Servis, metot, sürüm ve attachment
byte'ları eşleşirse bu sabit bilgiler yeniden nesnelere dönüştürülmez. Argümanlar
her istekte yeniden doğrulanır; iş metodu her istekte çalışır. Bu bir yanıt cache'i
değildir. Başlık değişirse veya argüman bir nesne/koleksiyon ise mevcut sınırlı
ayrıştırıcı kullanılır. Plan değiştirilmez; değişken başlıklar her istekte yeni
bir cache kaydı oluşturmaz. Ek bir property veya annotation gerekmez.

Output registry artık generation etiketli, sabit 256 atomik slot kullanır; eski
handle yeni generation üzerinde işlem yapamaz. Timeout/drain, ödünç belleği ve
kabul kredisini gerçek Java callback tamamlanana kadar korur. Outbound body kredi
sahipliğini socket write/drop sonuna kadar taşır. Handle başına tek writer olmalı,
buffer büyüdüğünde eski view kullanılmamalıdır.

Bekleyen completion'lar artık atomik output slotlarını paylaşır; ayrı pending-map
mutex'i yoktur. Motor **bütünüyle lock-free değildir**: provider-registry mutex'i
başlangıç, durdurma ve metrik okumasında kalır, her RPC'de kullanılmaz. Bağlantı
başına yazımlar Tokio async mutex kullanır: bekleme OS thread'ini bloklamaz,
ancak tam frame'ler sırayla yazıldığı için yavaş yazım aynı bağlantıdaki sonraki
yanıtları geciktirebilir. Ayrı consumer-response registry bu provider yolunda
değildir. Metrikler için [adaptör rehberine](https://github.com/esasmer-dou/java-rust-dubbo/blob/master/native-provider/README.tr.md) bakın.

| Belirti | Yapılacak işlem |
| --- | --- |
| Native yetenek/parser-policy metrikleri eksik | Uyumlu doğrulanmış SO kullanın; başlangıç kontrollerini kapatmayın. |
| Yanlış motor, açık registry veya desteklenmeyen bind host | Profil/overlay'i düzeltin; motor ayarlarını karıştırmayın. |
| Payload, koleksiyon veya kapasite reddi | Sonucu küçültün veya ilan edilen bütçeler içinde ayarlayın; belirsiz iş çağrısını otomatik tekrar göndermeyin. |
| Timeout sonrası açık handle | Callback/socket sahipliği sürüyor mu kontrol edin; timeout ödünç belleği serbest bırakma izni değildir. |
| Aynı JVM'de restart reddi | Yeni JVM başlatın; native kayıtta unregister yoktur. |

Alt sistem deadline'larını provider'dan kısa; consumer/dış HTTP deadline'larını
provider'dan uzun tutun. Termination grace, drain ve güvenlik payını aşmalıdır;
çalışan Java kodu zorla iptal edilmez.

## Doğrulama Ve Açık İşler

27 Ekim 2026'da güncel dondurulmuş kaynak EmaTech işlev kontrollerini geçti:
74 Rust testi (57 native + 17 core) ve 171 Java testi (164 + 7 sample).
Bu odak kontrolleri yanıt sahipliğini, sınırlı consumer handle'larını, callback
kabulünü, kısmi vectored write ve bekleyen completion değişikliklerini kapsar.
Desteklenmeyen iki OpenJ9 allocation kontrolü iptal edildi, başarılı sayılmaz;
ayrı sekiz kalibre edilmiş HotSpot allocation kontrolü başarılıdır.
Linux derleme, fmt ve Clippy kontrolleri başarılıdır; en yüksek GLIBC sembolü,
2.17 tavanının altında 2.14 olarak kaldı. İşlev başarısı performans veya production
kabulü değildir.

Sıcak yük kabulünde dört yeni süreç çifti ve dokuz hücrenin tamamı geçti.
Her uygulama 1 CPU/128 MiB ile sınırlandı. Başarılı RPS en az 0.98x, p99 en fazla
1.10x sınırında kaldı; bütün hedef hücrelerde provider RSS ve final heap azaldı.
HTTP, body ve socket hatası olmadı. Her kol, ölçülen eşzamanlılık düzeyinde hemen
önce ısıtıldı. Yalnız c16 ile ısınıp c64/c256 ilk penceresini de sıcak sayan eski
ölçüm planı kullanılmadı. Sınırlar, ölçüm süresi, işçi sayısı ve kuyruk büyütülmedi.
Bu kaydedilmiş Linux sonuçları güncel Windows DLL/parser politikasını, platform
paketlemesini veya tamamlanmış ürün release'ini doğrulamaz. Paket bulunabilirliğini
ya da kabulü bu karşılaştırmadan çıkarmak yerine eşleşen dosyaları hedef ortamda kontrol edin.
Production ayarlarını seçmeden önce ilgili kontrolleri kendi sınırlı kaynaklı
test ortamınızda da tekrarlayın.

Provider artık JNI metodunu başlangıçta bağlar. Her çağrıda imza çözümleme,
metot arama ve geçici native argüman dizisi oluşturma kaldırıldı. Java metodu
ve sahiplik kuralları değişmedi.

Resmî motorla aynı Java servisini kullanan son karşılaştırma:

| Yanıt / eşzamanlı istek | Başarılı RPS farkı | p99 farkı | Provider RSS farkı |
| --- | ---: | ---: | ---: |
| 32 satır / 16 | +%21,43 | -%4,27 | -19,90 MiB |
| 32 satır / 64 | +%15,87 | -%11,71 | -20,17 MiB |
| 32 satır / 256 | +%15,70 | -%17,63 | -20,35 MiB |
| 512 satır / 16 | +%21,34 | -%13,85 | -24,22 MiB |
| 512 satır / 64 | +%17,81 | -%17,44 | -25,72 MiB |
| 512 satır / 256 | +%20,89 | -%15,47 | -27,10 MiB |

Farklar dört eşlenmiş çiftin medyanıdır; yalnız başlık planının kazancı değildir.
Her istekte RPC ve Java iş metodu çalıştı; yanıt cache edilmedi. Dubbo çağırmayan
üç health kontrol hücresi de kabul sınırlarında kaldı. Final heap farkı -7,04 MiB'dir.

Bu sonuç sıcak 32/512 satırlı okuma yüküne aittir. Cold-start, uzun idle sonrası
ilk istek, karmaşık argümanların yükü veya tüm özelliklerin birleşik kabulü yerine
geçmez. Bu kaynak profili isteğe bağlıdır; mevcut provider profilleri değişmedi.
