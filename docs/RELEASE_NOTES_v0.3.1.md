# rest-sample-dubbo-provider v0.3.1

This sample aligns with `java-rust-dubbo:0.4.1` and the `0.3.1` consumer sample.

- Configures a bounded Dubbo provider executor instead of accepting the large official runtime
  default thread surface.
- Starts with one core thread, eight maximum threads, a queue of sixteen, and one I/O thread.
- Aligns database service concurrency with the two-connection Hikari pool.
- Keeps static and ZooKeeper provider modes, PostgreSQL/Hikari query and command examples, and
  interface/method-level limits available.
- Updates Docker/Jlink images, serialization allowlist coverage, and configuration tests.
