# rest-sample-dubbo-provider 0.6.2

`0.6.2` aligns the runnable provider with `rust-java-rest:4.4.0`, `java-rust-dubbo:0.7.2`,
`rest-sample-utility:0.4.1`, and `rust-sample-model:0.4.1`.

- Provider interfaces, service implementations, HikariCP/ActiveJDBC access, and optional ZooKeeper
  registration are unchanged.
- The version alignment keeps provider contracts compatible with the `0.6.2` consumer sample.
- This provider uses the official Dubbo runtime. The Rust micro agent does not replace provider
  Netty or add telemetry automatically.

