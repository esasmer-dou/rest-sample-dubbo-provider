# rest-sample-dubbo-provider v0.3.2

[English](RELEASE_NOTES_v0.3.2.md) | [Turkish](RELEASE_NOTES_v0.3.2.tr.md)

This patch release aligns with `java-rust-dubbo:0.4.1`, `rest-sample-utility:0.2.0`,
`rust-sample-model:0.2.0`, and `rest-sample-dubbo-consumer:0.3.2`.

## What Changed

- The Docker PostgreSQL recipe uses `checkpoint_timeout=15min`,
  `checkpoint_completion_target=0.9`, `min_wal_size=256MB`, and `max_wal_size=1GB`.
- The settings reduce periodic checkpoint I/O amplification during sustained sample writes.
- Commit durability remains enabled. The recipe does not disable `fsync`, `synchronous_commit`, or
  `full_page_writes`.
- English and Turkish runbooks explain that external PostgreSQL installations need DBA-reviewed
  WAL and checkpoint sizing based on disk latency and recovery objectives.

## Compatibility

Provider service interfaces, typed records, byte-array commands, PostgreSQL schema, Hikari pool
properties, static discovery, and ZooKeeper registration behavior are unchanged.
