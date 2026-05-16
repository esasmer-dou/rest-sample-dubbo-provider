package com.reactor.sample.dubbo.provider.config;

public final class ProviderRuntimeTuning {

    private ProviderRuntimeTuning() {}

    public static void apply() {
        setIfAbsent("io.netty.allocator.numDirectArenas");
        setIfAbsent("io.netty.allocator.numHeapArenas");
        setIfAbsent("io.netty.recycler.maxCapacityPerThread");
        setIfAbsent("io.netty.noPreferDirect");
        setIfAbsent("dubbo.application.logger");
        setIfAbsent("dubbo.application.qos.enable");
        setIfAbsent("dubbo.metrics.enable");
        setIfAbsent("dubbo.tracing.enabled");
    }

    private static void setIfAbsent(String key) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, ProviderProperties.get(key));
        }
    }
}
