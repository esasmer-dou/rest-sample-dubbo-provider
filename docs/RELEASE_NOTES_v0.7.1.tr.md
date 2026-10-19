# rest-sample-dubbo-provider 0.7.1

[English](RELEASE_NOTES_v0.7.1.md) | [Türkçe](RELEASE_NOTES_v0.7.1.tr.md)

İsteğe bağlı `full-provider,glowroot` profilleri, agent 0.7.0 ile provider işlemlerini gözlemler. Spring veya HTTP sunucusu eklenmez.

Platform 4.7.1 olur. Ortak sözleşmeler 0.4.4 olarak kalır. Provider, başlangıçtaki dispatcher gözlemi için Dubbo 0.8.1 kullanır.
Düşük-overhead provider SPI filtrelerini atlar. Yalnız seçilen dispatcher gözlemlenir. Tüm filtre zinciri kapalı kalır.
Docker, PostgreSQL'in TCP bağlantısına hazır olmasını bekler. Kurulum sırasında açılan geçici Unix socket, veritabanını erken hazır göstermez.
Mevcut endpoint, SQL, payload ve hata sözleşmeleri değişmez. Agent varsayılan olarak kapalıdır.
İş mantığı Java'da kalır. Agent açıksa sınırlı gönderim ve kaynak yönetimi Rust'tadır.

[README'deki kurulum](../README.tr.md) ve [agent kullanım rehberi](https://github.com/esasmer-dou/java-rust-glowroot-agent/blob/v0.7.0/docs/functional-operations.tr.md) adımlarını izleyin.
Agent'ın micro HTTP ölçümleri, bu uygulamanın her iş yükünde 3 MiB ek bellek altında kalacağını garanti etmez.
Daha ayrıntılı profilleri kendi iş yükünüzde ölçün.
