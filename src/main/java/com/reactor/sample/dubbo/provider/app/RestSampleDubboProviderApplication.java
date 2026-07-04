package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.dubbo.PlainDubboProvider;
import com.reactor.sample.dubbo.provider.dubbo.ProviderSupport;
import com.reactor.sample.dubbo.provider.registry.ZookeeperProviderRegistration;
import com.reactor.sample.dubbo.provider.service.CustomerCommandServiceImpl;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;
import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;

import java.util.List;

public final class RestSampleDubboProviderApplication {

    private RestSampleDubboProviderApplication() {}

    public static void main(String[] args) throws Exception {
        ProviderRuntimeTuning.apply();
        boolean registryEnabled = ProviderProperties.getBoolean("reactor.dubbo.registry-enabled");
        PlainDubboProvider.ProviderConfig config = ProviderSupport.providerConfig(registryEnabled);

        NestedCatalogServiceImpl catalogService = new NestedCatalogServiceImpl();
        PostgresCustomerRepository customerRepository = PostgresCustomerRepository.fromProperties();
        CustomerQueryServiceImpl customerService = new CustomerQueryServiceImpl(customerRepository);
        CustomerCommandServiceImpl customerCommandService =
                new CustomerCommandServiceImpl(customerRepository);
        if (ProviderProperties.getBoolean("sample.db.warmup")) {
            customerService.warmupDatabase();
            System.out.println("[rest-sample-dubbo-provider] database warmup completed");
        }

        ZookeeperProviderRegistration registration = registryEnabled
                ? ZookeeperProviderRegistration.open(config.registryAddress(), config.registryRoot())
                : null;
        List<ProviderSupport.ServicePlan<?>> services = List.of(
                ProviderSupport.service(NestedCatalogService.class, catalogService),
                ProviderSupport.service(CustomerQueryService.class, customerService),
                ProviderSupport.service(CustomerCommandService.class, customerCommandService));
        List<ProviderSupport.ExportedService<?>> exported = List.of();
        try {
            exported = ProviderSupport.exportAll(config, registration, services);
        } catch (Exception e) {
            ProviderSupport.closeAll(exported);
            ProviderSupport.closeAll(registration);
            customerService.close();
            throw e;
        }

        ProviderSupport.logStartup("full", config, registryEnabled, exported);
        ProviderSupport.awaitShutdown(
                "sample-dubbo-provider-shutdown",
                exported,
                registration,
                customerService);
    }
}
