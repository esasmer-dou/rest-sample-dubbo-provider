package com.reactor.sample.dubbo.provider.dubbo;

import com.reactor.sample.dubbo.provider.config.ProviderProperties;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class ProviderSupport {

    private ProviderSupport() {}

    public static PlainDubboProvider.ProviderConfig providerConfig(boolean registryEnabled) {
        return new PlainDubboProvider.ProviderConfig(
                ProviderProperties.get("dubbo.provider.application-name"),
                registryEnabled ? ProviderProperties.get("reactor.dubbo.registry-address") : "",
                registryEnabled ? ProviderProperties.get("reactor.dubbo.registry-root") : "",
                ProviderProperties.get("dubbo.provider.host"),
                ProviderProperties.get("dubbo.provider.bind-host"),
                ProviderProperties.getInt("dubbo.provider.port")
        );
    }

    public static <T> ServicePlan<T> service(Class<T> serviceType, T implementation) {
        return new ServicePlan<>(serviceType, implementation, serviceExecutionConfig(serviceType));
    }

    public static List<ExportedService<?>> exportAll(
            PlainDubboProvider.ProviderConfig config,
            ProviderRegistration registration,
            List<ServicePlan<?>> services) throws Exception {
        List<ExportedService<?>> exported = new ArrayList<>(services.size());
        try {
            for (ServicePlan<?> service : services) {
                exported.add(exportOne(service, config, registration));
            }
            return Collections.unmodifiableList(exported);
        } catch (Exception e) {
            closeAll(exported);
            throw e;
        }
    }

    public static void awaitShutdown(
            String shutdownThreadName,
            List<ExportedService<?>> exported,
            AutoCloseable... closeables) throws InterruptedException {
        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            closeAll(exported);
            closeAll(closeables);
            stop.countDown();
        }, shutdownThreadName));
        stop.await();
    }

    public static void logStartup(
            String label,
            PlainDubboProvider.ProviderConfig config,
            boolean registryEnabled,
            List<ExportedService<?>> exported) {
        for (ExportedService<?> service : exported) {
            System.out.println("[rest-sample-dubbo-provider] " + label + " exported "
                    + service.provider().url().toFullString());
        }
        System.out.println("[rest-sample-dubbo-provider] execution limits " + executionSummary(exported));
        if (registryEnabled) {
            System.out.println("[rest-sample-dubbo-provider] registered at "
                    + config.registryAddress() + "/" + config.registryRoot());
        } else {
            System.out.println("[rest-sample-dubbo-provider] registry disabled; static consumers can use "
                    + config.host() + ":" + config.port());
        }
    }

    public static void closeAll(List<? extends AutoCloseable> closeables) {
        for (int i = closeables.size() - 1; i >= 0; i--) {
            closeQuietly(closeables.get(i));
        }
    }

    public static void closeAll(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            closeQuietly(closeable);
        }
    }

    private static <T> ExportedService<T> exportOne(
            ServicePlan<T> service,
            PlainDubboProvider.ProviderConfig config,
            ProviderRegistration registration) throws Exception {
        PlainDubboProvider<T> provider = PlainDubboProvider.export(
                service.serviceType(),
                service.implementation(),
                config,
                registration,
                service.executionConfig()
        );
        return new ExportedService<>(service.serviceType(), provider, service.executionConfig());
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
        return value.isBlank() ? null : parsePositiveInt(fqcnKey + " / " + simpleKey, value);
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

    private static String executionSummary(List<ExportedService<?>> exported) {
        List<String> values = new ArrayList<>(exported.size());
        for (ExportedService<?> service : exported) {
            values.add(formatExecution(service.serviceType(), service.executionConfig()));
        }
        return String.join(", ", values);
    }

    private static String formatExecution(
            Class<?> serviceType,
            PlainDubboProvider.ServiceExecutionConfig config) {
        String value = serviceType.getSimpleName() + "=" + config.maxConcurrentInvocations();
        if (config.hasMethodOverrides()) {
            value += " methods=" + config.methodMaxConcurrentInvocations();
        }
        return value;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Startup failure and shutdown cleanup are best effort.
        }
    }

    public record ServicePlan<T>(
            Class<T> serviceType,
            T implementation,
            PlainDubboProvider.ServiceExecutionConfig executionConfig) {}

    public record ExportedService<T>(
            Class<T> serviceType,
            PlainDubboProvider<T> provider,
            PlainDubboProvider.ServiceExecutionConfig executionConfig) implements AutoCloseable {

        @Override
        public void close() {
            provider.close();
        }
    }
}
