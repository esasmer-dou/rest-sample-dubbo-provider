# Güvenli Tekrar Edilen Durum Güncellemesi

[English](functional-operations.md) | [Turkish](functional-operations.tr.md)

Bu isteğe bağlı örnekler, bu `0.8.0` kaynak kodunda vardır. Mevcut varsayılanlar
değişmez; [sürüm notlarındaki](RELEASE_NOTES_v0.8.0.md) eşleşen bağımlılıkları kullanın.
Bu kaynak rehberi yayımlanmış paketin bulunabilirliğini doğrulamaz.

## Örneği Açın

```properties
sample.command.idempotency.enabled=true
sample.command.idempotency.timeout-seconds=2
sample.command.idempotency.retention-ms=86400000
sample.command.idempotency.max-result-bytes=65536
```

Bu ayar yalnız `patchCustomerStatusTyped` metoduna uygulanır.
Boş olmayan requestId gerekir.
İsteği tekrarlarken aynı requestId ve aynı veriyi gönderin.
Aynı anahtar ve durum, kayıtlı yanıtı döner. İkinci güncelleme yapılmaz.
Aynı anahtarla farklı durum gönderirseniz conflict oluşur.
Diğer yazma metotları değişmez.

Güncelleme ve yanıt kaydı aynı PostgreSQL transaction'ında yazılır.
İki replica aynı tabloyu kullanır. Process içinde tekrar listesi tutulmaz.
Sample anahtar kapsamını işlem ve müşteri ID'siyle belirler.
Çok tenant'lı uygulamada doğrulanmış tenant bilgisini de ekleyin.

Demo şema kurulumu `reactor_command_result` tablosunu ve süre indeksini oluşturur.
Bu işlem nesne oluşturulurken değil, repository ilk kez kullanılırken yapılır.
DB kontrolü başarısızsa readiness hazır göstermez. Yazma isteği gizlice tekrarlanmaz.
Deployment içinde readiness kontrolünü koruyun.
Production'da `PostgresIdempotency.SCHEMA` ve `EXPIRY_INDEX` tanımlarını migration ile uygulayın.
`deleteExpired(batchSize)` için sınırlı temizlik görevi tanımlayın.
Saklama süresi tablo boyutunu sınırlamaz. Satır sayısını ve diski izleyin.
Saklama süresi bittikten sonra aynı requestId yeniden çalışabilir.

Timeout her SQL ifadesine uygulanır. Tüm işlemin toplam süresini sınırlamaz.
Hikari bağlantı bekleme süresi ve RPC timeout ayrıca geçerlidir.
Gizli yazma tekrarı yapılmaz. Harici yan etkiler için exactly-once garantisi verilmez.

## İlk Bağlantı Ve Timeout

CPU sınırı olan bir container'da ilk JDBC bağlantısı, ısınmış bağlantıdan daha yavaş olabilir.
Başlangıçta bağlantının kurulmasını beklemek istiyorsanız
`sample.db.initialization-fail-timeout-ms=30000` kullanabilirsiniz.
Bu ayar Hikari'nin başlangıç bağlantı kontrolü içindir. İstek timeout'unu veya pool boyutunu büyütmez.
DB kapalıysa uygulama bu başlangıç bütçesinin sonunda hata verebilir; readiness kontrolünü kaldırmayın.

SQL timeout'u 2 saniyeyken consumer RPC timeout'u 1 saniye ise consumer daha önce vazgeçebilir.
Provider yine de commit etmiş olabilir. Aynı `requestId` ile tekrar sorgulayın; yeni anahtar üretmeyin.
RPC bütçesini bağlantı bekleme, SQL ve yanıt hazırlama süreleriyle birlikte ölçün.
Timeout'u büyütmek kapasiteyi artırmaz. Uzun bekleyen istek sayısını ayrıca sınırlayın.

## Thread Artırmadan Daha Küçük Buffer

Örnek uygulama, Netty başlamadan provider kütüphanesinin düşük RSS ayarlarını uygular.
Yeni buffer ayarları [Dubbo rehberinde](https://github.com/esasmer-dou/java-rust-dubbo/blob/master/docs/functional-operations.tr.md#native-cevaplar-ve-provider-buffer-sınırı) açıklanmıştır.
DB pool, executor, route limiti veya timeout büyütülmedi.
Katalog örneği her cevapta yeni zaman damgası üretmeye devam eder.
Cevabın tamamını her çağrıda biçimlendirmek yerine önceden kodlanmış sabit JSON parçalarını birleştirir.
Bu bir cevap cache'i değildir. DB sorguları ve Java iş kuralları eskisi gibi çalışır.
