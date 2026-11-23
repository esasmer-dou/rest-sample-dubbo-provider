# rest-sample-dubbo-provider 0.8.3

[English](RELEASE_NOTES_v0.8.3.md) | [Turkish](RELEASE_NOTES_v0.8.3.tr.md)

Builds with platform `4.9.0`, core Dubbo `0.9.2` and shared utility/model `0.5.3`. No Spring Dubbo runtime is added to the provider.

Use `-Pstream-catalog-provider` for the generated direct-JSON consumer, or keep `full-provider` for database reads and commands. See the [profile guide](NATIVE_PROVIDER.md).

For migration and setup, see [README](../README.md). JNI contracts and runtime defaults are unchanged. This update does not claim a new performance gain.
