package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.dubbo.PlainDubboProvider;
import com.reactor.sample.dubbo.provider.registry.ZookeeperProviderRegistration;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class DbQueryOnlyProviderApplication {

    private DbQueryOnlyProviderApplication() {}

    public static void main(String[] args) throws Exception {
        ProviderRuntimeTuning.apply();

        PlainDubboProvider.ProviderConfig config = new PlainDubboProvider.ProviderConfig(
                ProviderProperties.get("dubbo.provider.application-name"),
                ProviderProperties.get("reactor.dubbo.registry-address"),
                ProviderProperties.get("reactor.dubbo.registry-root"),
                ProviderProperties.get("dubbo.provider.host"),
                ProviderProperties.get("dubbo.provider.bind-host"),
                ProviderProperties.getInt("dubbo.provider.port")
        );

        PostgresCustomerRepository customerRepository = PostgresCustomerRepository.fromProperties();
        CustomerQueryServiceImpl customerService = new CustomerQueryServiceImpl(customerRepository);
        if (ProviderProperties.getBoolean("sample.db.warmup")) {
            customerService.warmupDatabase();
            System.out.println("[rest-sample-dubbo-provider] database warmup completed");
        }

        boolean registryEnabled = ProviderProperties.getBoolean("reactor.dubbo.registry-enabled");
        ZookeeperProviderRegistration registration = registryEnabled
                ? ZookeeperProviderRegistration.open(config.registryAddress(), config.registryRoot())
                : null;
        PlainDubboProvider.ServiceExecutionConfig queryExecution =
                serviceExecutionConfig(CustomerQueryService.class);

        PlainDubboProvider<CustomerQueryService> queryProvider = null;
        try {
            queryProvider = PlainDubboProvider.export(
                    CustomerQueryService.class,
                    customerService,
                    config,
                    registration,
                    queryExecution
            );
        } catch (Exception e) {
            closeQuietly(queryProvider);
            closeQuietly(registration);
            customerService.close();
            throw e;
        }

        CountDownLatch stop = new CountDownLatch(1);
        PlainDubboProvider<CustomerQueryService> finalQueryProvider = queryProvider;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            finalQueryProvider.close();
            closeQuietly(registration);
            customerService.close();
            stop.countDown();
        }, "sample-db-query-provider-shutdown"));

        System.out.println("[rest-sample-dubbo-provider] db-query exported "
                + queryProvider.url().toFullString());
        System.out.println("[rest-sample-dubbo-provider] query execution limit "
                + formatExecution(CustomerQueryService.class, queryExecution));
        if (registryEnabled) {
            System.out.println("[rest-sample-dubbo-provider] registered at "
                    + config.registryAddress() + "/" + config.registryRoot());
        } else {
            System.out.println("[rest-sample-dubbo-provider] registry disabled; static consumers can use "
                    + config.host() + ":" + config.port());
        }
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

    private static String formatExecution(Class<?> serviceType, PlainDubboProvider.ServiceExecutionConfig config) {
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
            // Startup failure cleanup is best effort.
        }
    }
}
