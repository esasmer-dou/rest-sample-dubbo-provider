# Docker Desktop Çalıştırma Rehberi

[English](README.md) | [Türkçe](README.tr.md)

Bu Docker Compose yapısı iki container başlatır:

- `postgres`: Sample database için PostgreSQL 16.
- `provider`: `dubbo://localhost:20880` adresinde çalışan `rest-sample-dubbo-provider`.

ZooKeeper varsayılan olarak kapalıdır. Consumer static provider adresi veya Kubernetes Service DNS
kullanabilir.

## Başlatma

Proje kök dizininde çalıştırın:

```powershell
docker compose -f docker/docker-compose.yml up --build
```

`15432` portu kullanılıyorsa ilgili container'ı durdurun veya PostgreSQL published portunu değiştirin.
Consumer'ı başlatmadan önce iki container'ı kontrol edin:

```powershell
docker compose -f docker/docker-compose.yml ps
docker logs rest-sample-dubbo-provider
```

## Consumer Ayarı

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=127.0.0.1:20880
```

Consumer aynı Compose network içinde çalışıyorsa `127.0.0.1` yerine provider service adını kullanın.

## Durdurma

Database volume'u koruyun:

```powershell
docker compose -f docker/docker-compose.yml down
```

Sample database volume'unu da silin:

```powershell
docker compose -f docker/docker-compose.yml down -v
```

## PostgreSQL Güvenliği

Sample kalıcı commit davranışını korur. Yalnız checkpoint aralığını düzenler:

```text
checkpoint_timeout=15min
checkpoint_completion_target=0.9
min_wal_size=256MB
max_wal_size=1GB
```

`fsync`, `synchronous_commit` ve `full_page_writes` kapatılmaz. Harici database kullanırken WAL ve
checkpoint değerlerini database ekibi disk gecikmesi, recovery hedefi ve write hacmine göre
belirlemelidir. API p99 dalgalanmasını gizlemek için `synchronous_commit=off` kullanmayın.

Image tanımları [`images/README.tr.md`](images/README.tr.md) dosyasında açıklanır.
