# rest-sample-dubbo-provider v0.3.2

[English](RELEASE_NOTES_v0.3.2.md) | [Türkçe](RELEASE_NOTES_v0.3.2.tr.md)

Bu patch sürümü `java-rust-dubbo:0.4.1`, `rest-sample-utility:0.2.0`,
`rust-sample-model:0.2.0` ve `rest-sample-dubbo-consumer:0.3.2` ile uyumludur.

## Neler Değişti?

- Docker PostgreSQL reçetesi `checkpoint_timeout=15min`,
  `checkpoint_completion_target=0.9`, `min_wal_size=256MB` ve `max_wal_size=1GB` kullanır.
- Bu ayarlar sürekli sample write yükünde dönemsel checkpoint I/O baskısının büyümesini azaltır.
- Güvenli commit davranışı korunur. Reçete `fsync`, `synchronous_commit` veya
  `full_page_writes` ayarlarını kapatmaz.
- İngilizce ve Türkçe runbook, harici PostgreSQL kurulumlarında WAL ve checkpoint değerlerinin disk
  gecikmesine ve recovery hedeflerine göre database ekibi tarafından belirlenmesi gerektiğini açıklar.

## Uyumluluk

Provider service interface'leri, typed record'lar, byte-array command'ları, PostgreSQL şeması,
Hikari pool property'leri, static discovery ve ZooKeeper registration davranışı değişmedi.
