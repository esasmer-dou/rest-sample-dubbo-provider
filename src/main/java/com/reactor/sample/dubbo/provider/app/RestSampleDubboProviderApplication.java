package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.rust.dubbo.provider.DubboProviderSupport;
import com.reactor.rust.dubbo.provider.PlainDubboProvider;
import com.reactor.rust.dubbo.provider.ZookeeperDubboProviderRegistration;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.service.CustomerCommandServiceImpl;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;
import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;

import java.util.List;

public final class RestSampleDubboProviderApplication {

    private RestSampleDubboProviderApplication() {}

    public static void main(String[] args) throws Exception {
        ProviderRuntimeTuning.apply();
        boolean registryEnabled = ProviderProperties.getBoolean("reactor.dubbo.registry-enabled");
        DubboProviderSupport support = DubboProviderSupport.fromProperties(ProviderProperties.asProperties());
        PlainDubboProvider.ProviderConfig config = support.providerConfig(registryEnabled);

        NestedCatalogServiceImpl catalogService = new NestedCatalogServiceImpl();
        PostgresCustomerRepository customerRepository = PostgresCustomerRepository.fromProperties();
        CustomerQueryServiceImpl customerService = new CustomerQueryServiceImpl(customerRepository);
        CustomerCommandServiceImpl customerCommandService =
                new CustomerCommandServiceImpl(customerRepository);
        if (ProviderProperties.getBoolean("sample.db.warmup")) {
            customerService.warmupDatabase();
            System.out.println("[rest-sample-dubbo-provider] database warmup completed");
        }

        ZookeeperDubboProviderRegistration registration = registryEnabled
                ? ZookeeperDubboProviderRegistration.open(
                        config.registryAddress(),
                        config.registryRoot(),
                        config.applicationName())
                : null;
        List<DubboProviderSupport.ServicePlan<?>> services = List.of(
                support.service(NestedCatalogService.class, catalogService),
                support.service(CustomerQueryService.class, customerService),
                support.service(CustomerCommandService.class, customerCommandService));
        List<DubboProviderSupport.ExportedService<?>> exported = List.of();
        try {
            exported = support.exportAll(config, registration, services);
        } catch (Exception e) {
            support.closeAll(exported);
            support.closeAll(registration);
            customerService.close();
            throw e;
        }

        support.logStartup("full", config, registryEnabled, exported);
        support.awaitShutdown(
                "sample-dubbo-provider-shutdown",
                exported,
                registration,
                customerService);
    }
}
