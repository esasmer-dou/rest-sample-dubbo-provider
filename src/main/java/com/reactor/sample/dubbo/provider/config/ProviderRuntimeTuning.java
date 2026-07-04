package com.reactor.sample.dubbo.provider.config;

public final class ProviderRuntimeTuning {

    private ProviderRuntimeTuning() {}

    public static void apply() {
        setIfAbsent("io.netty.allocator.numDirectArenas", "1");
        setIfAbsent("io.netty.allocator.numHeapArenas", "1");
        setIfAbsent("io.netty.recycler.maxCapacityPerThread", "0");
        setIfAbsent("io.netty.noPreferDirect", "true");
        setIfAbsent("dubbo.application.logger", "slf4j");
        setIfAbsent("dubbo.application.qos.enable", "false");
        setIfAbsent("dubbo.metrics.enable", "false");
        setIfAbsent("dubbo.tracing.enabled", "false");
    }

    private static void setIfAbsent(String key, String defaultValue) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, ProviderProperties.getOrDefault(key, defaultValue));
        }
    }
}
