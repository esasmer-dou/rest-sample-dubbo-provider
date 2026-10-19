# rest-sample-dubbo-provider 0.7.1

[English](RELEASE_NOTES_v0.7.1.md) | [Türkçe](RELEASE_NOTES_v0.7.1.tr.md)

The optional `full-provider,glowroot` profiles observe provider methods through agent 0.7.0. No Spring or HTTP server is added.

Platform moves to 4.7.1; shared contracts stay at 0.4.4. This provider explicitly uses Dubbo 0.8.1 for its startup dispatcher hook.
The low-level provider bypasses SPI filters. Telemetry decorates only selected dispatchers; the full filter chain stays disabled.
Docker waits for PostgreSQL TCP readiness. The temporary initialization server's Unix socket no longer marks the database ready too early.
Existing endpoints, SQL, payloads and error contracts do not change. Telemetry remains disabled by default.
Java keeps business logic. Enabled telemetry uses bounded Rust export and resource management.

Follow the [README setup](../README.md) and [agent guide](https://github.com/esasmer-dou/java-rust-glowroot-agent/blob/v0.7.0/docs/functional-operations.md).
The agent's micro HTTP measurements are not a guarantee of under 3 MiB incremental memory for every workload in this application.
Measure richer profiles with your own workload.
