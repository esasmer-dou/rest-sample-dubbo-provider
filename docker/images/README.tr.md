# Provider Docker Image'ları

[English](README.md) | [Türkçe](README.tr.md)

Provider image tanımları bu dizinde tutulur. Docker build context olarak proje kökünü kullanır.
Böylece `.dockerignore`, `pom.xml` ve `src/` yolları doğru çözülür.

| Dockerfile | Kullanım |
| --- | --- |
| `Dockerfile` | `docker/docker-compose.yml` tarafından kullanılan full local provider |
| `Dockerfile.jlink` | Özel OpenJ9 jlink runtime kullanan full provider |
| `Dockerfile.jlink.catalog-static` | DB, ZooKeeper ve customer service içermeyen catalog-only provider |
| `Dockerfile.jlink.db-query` | PostgreSQL/Hikari query-only provider; command ve catalog service yok |

```powershell
docker build `
  -f docker/images/Dockerfile.jlink.db-query `
  -t rest-sample-dubbo-provider:db-query-jlink .
```

Oluşan image'ı doğrulayın:

```powershell
docker image inspect rest-sample-dubbo-provider:db-query-jlink `
  --format '{{.Id}} size={{.Size}} bytes'
```

Export edilen service setini içeren en küçük image'ı seçin. Runtime property bir service'i kapatabilir.
Ancak full image içine paketlenmiş sınıfları çıkaramaz.

Database parolasını veya Maven anahtarını Dockerfile içine yazmayın. Runtime Secret ve BuildKit
secret kullanın. PostgreSQL ile provider'ı başlatmak için [Docker rehberine](../README.tr.md) dönün.
