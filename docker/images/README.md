# Provider Docker Images

Keep provider image definitions in this directory. Build commands still use the project root as the
Docker context so `.dockerignore`, `pom.xml`, and `src/` are resolved correctly.

| Dockerfile | Use case |
|------------|----------|
| `Dockerfile` | Full local provider image used by `docker/docker-compose.yml`. |
| `Dockerfile.jlink` | Full provider on a custom OpenJ9 jlink runtime. |
| `Dockerfile.jlink.catalog-static` | Catalog-only provider; no DB, no ZooKeeper registration, no customer services. |
| `Dockerfile.jlink.db-query` | PostgreSQL/Hikari query-only provider; no command or catalog services. |

Example:

```powershell
docker build -f docker/images/Dockerfile.jlink.db-query -t rest-sample-dubbo-provider:db-query-jlink .
```
