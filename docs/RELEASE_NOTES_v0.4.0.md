# rest-sample-dubbo-provider 0.4.0

`0.4.0` updates the provider sample to the declarative Dubbo binding line.

## What's New

- Uses `java-rust-dubbo:0.5.0` and `rest-sample-utility:0.3.0`.
- Uses `DubboServiceBinding` to register service implementations with explicit interface and method
  capacity limits.
- Uses generated JDBC record mappers in database profiles.
- Keeps catalog-static, database-query, and full provider shapes as separate Maven profiles.
- Keeps provider service interfaces and payload contracts unchanged.

## Run

```powershell
mvn clean package
java -jar target/rest-sample-dubbo-provider-0.4.0.jar
```

Provider business implementations, SQL, transactions, and validation remain explicit Java code.
