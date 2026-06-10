package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CatalogJsonService;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.dubbo.PlainDubboProvider;
import com.reactor.sample.dubbo.provider.service.CatalogJsonServiceImpl;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class CatalogStaticProviderApplication {

    private CatalogStaticProviderApplication() {}

    public static void main(String[] args) throws Exception {
        ProviderRuntimeTuning.apply();

        PlainDubboProvider.ProviderConfig config = new PlainDubboProvider.ProviderConfig(
                ProviderProperties.get("dubbo.provider.application-name"),
                "",
                "",
                ProviderProperties.get("dubbo.provider.host"),
                ProviderProperties.get("dubbo.provider.bind-host"),
                ProviderProperties.getInt("dubbo.provider.port")
        );

        PlainDubboProvider.ServiceExecutionConfig executionConfig =
                serviceExecutionConfig(CatalogJsonService.class);
        PlainDubboProvider<CatalogJsonService> provider = PlainDubboProvider.export(
                CatalogJsonService.class,
                new CatalogJsonServiceImpl(),
                config,
                null,
                executionConfig
        );

        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            provider.close();
            stop.countDown();
        }, "sample-catalog-static-provider-shutdown"));

        System.out.println("[rest-sample-dubbo-provider] catalog-static exported "
                + provider.url().toFullString());
        System.out.println("[rest-sample-dubbo-provider] registry disabled; static consumers can use "
                + config.host() + ":" + config.port());
        System.out.println("[rest-sample-dubbo-provider] catalog execution limit "
                + executionConfig.maxConcurrentInvocations());
        stop.await();
    }

    private static PlainDubboProvider.ServiceExecutionConfig serviceExecutionConfig(Class<?> serviceType) {
        int defaultMax = ProviderProperties.getInt("dubbo.provider.service.default.max-concurrent");
        int max = ProviderProperties.getIntOrDefault(
                "dubbo.provider.service." + serviceType.getName() + ".max-concurrent",
                ProviderProperties.getIntOrDefault(
                        "dubbo.provider.service." + serviceType.getSimpleName() + ".max-concurrent",
                        defaultMax
                )
        );
        return PlainDubboProvider.ServiceExecutionConfig.bounded(max, methodExecutionOverrides(serviceType));
    }

    private static Map<String, Integer> methodExecutionOverrides(Class<?> serviceType) {
        Map<String, Integer> overrides = new LinkedHashMap<>();
        for (Method method : serviceType.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            Integer max = methodMaxConcurrent(serviceType, method.getName());
            if (max != null) {
                overrides.put(method.getName(), max);
            }
        }
        return overrides;
    }

    private static Integer methodMaxConcurrent(Class<?> serviceType, String methodName) {
        String fqcnKey = "dubbo.provider.service." + serviceType.getName()
                + ".method." + methodName + ".max-concurrent";
        String simpleKey = "dubbo.provider.service." + serviceType.getSimpleName()
                + ".method." + methodName + ".max-concurrent";
        String value = ProviderProperties.getOrDefault(
                fqcnKey,
                ProviderProperties.getOrDefault(simpleKey, "")
        );
        if (value.isBlank()) {
            return null;
        }
        return parsePositiveInt(fqcnKey + " / " + simpleKey, value);
    }

    private static int parsePositiveInt(String key, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("Provider property must be >= 1: " + key + "=" + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Provider property must be an integer: " + key + "=" + value, e);
        }
    }
}
