package com.reactor.sample.dubbo.provider.nativeprovider;

import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.NativeProviderFactory;
import com.reactor.rust.dubbo.provider.PlainDubboProvider;
import java.util.List;
import java.util.Map;

/** Cold-start translation only; official executor/registry settings are never silently reused. */
public record NativeProviderConfiguration(
        NativeProviderFactory.Options options, PlainDubboProvider.ServiceExecutionConfig execution) {

    public static NativeProviderConfiguration from(DubboApplicationProperties properties) {
        if (!"native".equals(properties.get("sample.provider.engine"))
                || properties.getBoolean("reactor.dubbo.registry-enabled", false)) {
            throw new IllegalArgumentException("Native profile requires engine=native and registry-enabled=false");
        }
        var options = new NativeProviderFactory.Options(
                properties.getInt("dubbo.provider.port", 20880),
                properties.getInt("sample.native-provider.io-workers", 1),
                properties.getInt("sample.native-provider.business-workers", 4),
                properties.getInt("sample.native-provider.queue-capacity", 16),
                properties.getInt("sample.native-provider.max-payload-bytes", 262144),
                properties.getInt("sample.native-provider.request-timeout-ms", 5000),
                properties.getInt("sample.native-provider.drain-timeout-ms", 5000),
                properties.getInt("sample.native-provider.max-collection-items", 1024));
        int concurrency = properties.getInt("sample.native-provider.service-max-concurrent", options.businessWorkers());
        if (concurrency < 1 || concurrency > options.businessWorkers()) {
            throw new IllegalArgumentException("Service concurrency must be between 1 and business-workers");
        }
        for (String key : List.of("dubbo.provider.bind-host", "dubbo.provider.host")) {
            String host = properties.get(key, "0.0.0.0");
            if (!"0.0.0.0".equals(host)) {
                throw new IllegalArgumentException("Shared ABI-3 provider binds all interfaces; unsupported " + key + "=" + host);
            }
        }
        return new NativeProviderConfiguration(options,
                PlainDubboProvider.ServiceExecutionConfig.bounded(concurrency, Map.of()));
    }
}
