package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.dubbo.PlainDubboProvider;
import com.reactor.sample.dubbo.provider.registry.ZookeeperProviderRegistration;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;
import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;

import java.util.concurrent.CountDownLatch;

public final class RestSampleDubboProviderApplication {

    private RestSampleDubboProviderApplication() {}

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

        NestedCatalogServiceImpl catalogService = new NestedCatalogServiceImpl();
        CustomerQueryServiceImpl customerService =
                new CustomerQueryServiceImpl(PostgresCustomerRepository.fromProperties());
        if (ProviderProperties.getBoolean("sample.db.warmup")) {
            customerService.warmupDatabase();
            System.out.println("[rest-sample-dubbo-provider] database warmup completed");
        }
        ZookeeperProviderRegistration registration = ZookeeperProviderRegistration.open(
                config.registryAddress(),
                config.registryRoot()
        );
        PlainDubboProvider.ServiceExecutionConfig catalogExecution =
                serviceExecutionConfig(NestedCatalogService.class);
        PlainDubboProvider.ServiceExecutionConfig customerExecution =
                serviceExecutionConfig(CustomerQueryService.class);

        PlainDubboProvider<NestedCatalogService> catalogProvider = null;
        PlainDubboProvider<CustomerQueryService> customerProvider = null;
        try {
            catalogProvider = PlainDubboProvider.export(
                    NestedCatalogService.class,
                    catalogService,
                    config,
                    registration,
                    catalogExecution
            );
            customerProvider = PlainDubboProvider.export(
                    CustomerQueryService.class,
                    customerService,
                    config,
                    registration,
                    customerExecution
            );
        } catch (Exception e) {
            closeQuietly(customerProvider);
            closeQuietly(catalogProvider);
            registration.close();
            customerService.close();
            throw e;
        }

        CountDownLatch stop = new CountDownLatch(1);
        PlainDubboProvider<NestedCatalogService> finalCatalogProvider = catalogProvider;
        PlainDubboProvider<CustomerQueryService> finalCustomerProvider = customerProvider;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            finalCustomerProvider.close();
            finalCatalogProvider.close();
            registration.close();
            customerService.close();
            stop.countDown();
        }, "sample-dubbo-provider-shutdown"));

        System.out.println("[rest-sample-dubbo-provider] exported " + catalogProvider.url().toFullString());
        System.out.println("[rest-sample-dubbo-provider] exported " + customerProvider.url().toFullString());
        System.out.println("[rest-sample-dubbo-provider] execution limits "
                + NestedCatalogService.class.getSimpleName() + "=" + catalogExecution.maxConcurrentInvocations()
                + ", " + CustomerQueryService.class.getSimpleName() + "="
                + customerExecution.maxConcurrentInvocations());
        System.out.println("[rest-sample-dubbo-provider] registered at "
                + config.registryAddress() + "/" + config.registryRoot());
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
        return PlainDubboProvider.ServiceExecutionConfig.bounded(max);
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
