# Docker Desktop Runbook

This Docker setup starts:

- `postgres`: PostgreSQL 16 for the sample database.
- `provider`: `rest-sample-dubbo-provider` on `dubbo://localhost:20880`.

ZooKeeper is disabled by default because the consumer sample can use static Service DNS/provider
address mode.

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

ZooKeeper default olarak kapalıdır. Consumer sample static provider adresiyle bağlanabilir.

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
