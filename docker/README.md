# Docker Desktop Runbook

This Docker setup starts:

- `postgres`: PostgreSQL 16 for the sample database.
- `provider`: `rest-sample-dubbo-provider` on `dubbo://localhost:20880`.

Image definitions live under `docker/images/`. The compose file keeps the project root as the build
context and points to `docker/images/Dockerfile`.

ZooKeeper is disabled by default because the consumer sample can use static Service DNS/provider
address mode.

The PostgreSQL container keeps durable commits enabled. The compose recipe changes only checkpoint
pacing: `checkpoint_timeout=15min`, `checkpoint_completion_target=0.9`,
`min_wal_size=256MB`, and `max_wal_size=1GB`. This avoids an almost continuous five-minute
checkpoint cycle during sustained sample writes. It does not disable `fsync`,
`synchronous_commit`, or `full_page_writes`.

Treat these values as a Docker sample starting point. For an external PostgreSQL service, let the
database owner size WAL and checkpoint settings from disk latency, recovery-time target, and write
volume. Do not use `synchronous_commit=off` to hide REST p99 variance.

## Start

From the project root:

```powershell
docker compose -f docker/docker-compose.yml up --build
```

If port `15432` is already used by a manually started PostgreSQL container, stop that container first
or change the published port in `docker-compose.yml`.

## Stop

```powershell
docker compose -f docker/docker-compose.yml down
```

To also remove the PostgreSQL sample data volume:

```powershell
docker compose -f docker/docker-compose.yml down -v
```

## Consumer Settings

Start `rest-sample-dubbo-consumer` with:

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=127.0.0.1:20880
```

---

# Docker Desktop Çalıştırma Notu

Bu Docker yapısı iki container başlatır:

- `postgres`: Sample database için PostgreSQL 16.
- `provider`: `dubbo://localhost:20880` üzerinden çalışan `rest-sample-dubbo-provider`.

Image tanımları `docker/images/` altında durur. Compose dosyası build context olarak proje kökünü
kullanır ve `docker/images/Dockerfile` dosyasını işaret eder.

ZooKeeper default olarak kapalıdır. Consumer sample static provider adresiyle bağlanabilir.

PostgreSQL container güvenli commit davranışını korur. Compose reçetesi yalnızca checkpoint akışını
düzenler: `checkpoint_timeout=15min`, `checkpoint_completion_target=0.9`,
`min_wal_size=256MB` ve `max_wal_size=1GB`. Böylece sürekli write yükünde varsayılan beş dakikalık
checkpoint döngüsü neredeyse kesintisiz disk yazısına dönüşmez. `fsync`, `synchronous_commit` ve
`full_page_writes` kapatılmaz.

Bu değerleri Docker sample için başlangıç reçetesi olarak kullanın. Harici PostgreSQL servisinde WAL
ve checkpoint değerlerini disk gecikmesine, recovery süresi hedefine ve write hacmine göre database
ekibi belirlemelidir. REST p99 dalgalanmasını saklamak için `synchronous_commit=off` kullanmayın.

## Başlatma

Proje kök dizininden:

```powershell
docker compose -f docker/docker-compose.yml up --build
```

`15432` portu daha önce manuel başlatılmış bir PostgreSQL container tarafından kullanılıyorsa önce o
container'ı durdurun veya `docker-compose.yml` içindeki published port değerini değiştirin.

## Durdurma

```powershell
docker compose -f docker/docker-compose.yml down
```

PostgreSQL sample data volume'unu da silmek için:

```powershell
docker compose -f docker/docker-compose.yml down -v
```

## Consumer Ayarı

`rest-sample-dubbo-consumer` şu ayarlarla başlatılabilir:

```properties
sample.dubbo.discovery=static
reactor.dubbo.providers=127.0.0.1:20880
```
