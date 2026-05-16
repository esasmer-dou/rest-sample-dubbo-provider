package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.dubbo.PlainDubboProvider;
import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;

import java.util.concurrent.CountDownLatch;

public final class NestedCatalogProviderApplication {

    private NestedCatalogProviderApplication() {}

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

        NestedCatalogServiceImpl service = new NestedCatalogServiceImpl(PostgresCustomerRepository.fromProperties());
        if (ProviderProperties.getBoolean("sample.db.warmup")) {
            service.warmupDatabase();
            System.out.println("[rest-sample-dubbo-provider] database warmup completed");
        }
        PlainDubboProvider<NestedCatalogService> provider = PlainDubboProvider.export(
                NestedCatalogService.class,
                service,
                config
        );

        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            provider.close();
            service.close();
            stop.countDown();
        }, "sample-dubbo-provider-shutdown"));

        System.out.println("[rest-sample-dubbo-provider] exported " + provider.url().toFullString());
        System.out.println("[rest-sample-dubbo-provider] registered at "
                + config.registryAddress() + "/" + config.registryRoot());
        stop.await();
    }
}
