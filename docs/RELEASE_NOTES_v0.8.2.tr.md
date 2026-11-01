# rest-sample-dubbo-provider 0.8.2

[English](RELEASE_NOTES_v0.8.2.md) | [Türkçe](RELEASE_NOTES_v0.8.2.tr.md)

Koordineli güncelleme: 1 Kasım 2026.

## Bağımlılıklar

`com.reactor:rust-java-platform-parent:4.8.2` kullanın. Mevcut POM için tanımlar;
seçilen provider profili, PostgreSQL/Hikari ve eklenti ayarlarını koruyun:

```xml
<dependency>
  <groupId>com.reactor</groupId>
  <artifactId>java-rust-dubbo</artifactId>
  <version>0.9.2</version>
</dependency>
<dependency>
  <groupId>com.reactor.sample</groupId>
  <artifactId>rest-sample-utility</artifactId>
  <version>0.5.2</version>
</dependency>
```

Utility, model `0.5.2` getirir; consumer ve provider'ı aynı kontratlarla yeniden derleyin.
Bağımsız Spring Dubbo adapter/native paketleri `0.8.1` (ABI `3`) olarak kalır.
[Maven erişimine](../README.tr.md#maven-package-erişimi) bakın. Normal DB provider:
`mvn -B -ntp -DskipTests -Pfull-provider clean package`. Testleri atlamak kabul kanıtı değildir.

## Mevcut DB Ve Kabul Kontrolü Örnekleri

[DB/eşzamanlılık tarifi](../README.tr.md#veritabanı-ve-eşzamanlı-istek-limitleri), küçük
Hikari havuzunu, sınırlı executor/kuyruğu ve query/command/metot sınırlarını birlikte korur.
Consumer `@KeyAdmission` kontrolleri süreç içidir, dağıtık DB kilidi değildir;
tutarlılığın sahibi transaction, satır kilidi ve mevcut typed-status idempotency
örneğidir. SQL veya iş sözleşmesi geçişi eklenmez.

Tam provider, [Compose](../README.tr.md#compose) customer komut/sorgu akışını sunar.
`native-provider` overlay yalnız katalog sunar; SQL ile yürütülen writer/reader smoke,
customer RPC kanıtı değildir. Ayrı `native-db-query-provider` kaynak profili yalnız
okuma/sorgu içindir, tam provider değildir. Readiness seçimi ilk kez consumer `0.8.1`
ile sunuldu.

## Geçiş Ve Kanıt

JNI ABI veya ayar anahtarı geçişi gerekmez. Seçilen profilin `0.8.2` JAR'ını ve
eşleşen native dosyasını kullanın; eski SO/DLL dosyasını kopyalamayın.
Bu notlar yayın veya tam kabul kanıtı değildir. İlgili adayın kesin doğrulama kaydını
esas alın; hosted uyumluluk, EmaTech performans/soak kanıtından ayrıdır ve yeni
RPS/p99/bellek PASS anlamına gelmez.
