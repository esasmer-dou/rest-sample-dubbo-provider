package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.dubbo.PlainDubboProvider;
import com.reactor.sample.dubbo.provider.dubbo.ProviderSupport;
import com.reactor.sample.dubbo.provider.registry.ZookeeperProviderRegistration;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;

import java.util.List;

public final class DbQueryOnlyProviderApplication {

    private DbQueryOnlyProviderApplication() {}

    public static void main(String[] args) throws Exception {
        ProviderRuntimeTuning.apply();

        boolean registryEnabled = ProviderProperties.getBoolean("reactor.dubbo.registry-enabled");
        PlainDubboProvider.ProviderConfig config = ProviderSupport.providerConfig(registryEnabled);
        PostgresCustomerRepository customerRepository = PostgresCustomerRepository.fromProperties();
        CustomerQueryServiceImpl customerService = new CustomerQueryServiceImpl(customerRepository);
        if (ProviderProperties.getBoolean("sample.db.warmup")) {
            customerService.warmupDatabase();
            System.out.println("[rest-sample-dubbo-provider] database warmup completed");
        }

        ZookeeperProviderRegistration registration = registryEnabled
                ? ZookeeperProviderRegistration.open(config.registryAddress(), config.registryRoot())
                : null;
        List<ProviderSupport.ServicePlan<?>> services = List.of(
                ProviderSupport.service(CustomerQueryService.class, customerService));
        List<ProviderSupport.ExportedService<?>> exported = List.of();
        try {
            exported = ProviderSupport.exportAll(config, registration, services);
        } catch (Exception e) {
            ProviderSupport.closeAll(exported);
            ProviderSupport.closeAll(registration);
            customerService.close();
            throw e;
        }

        ProviderSupport.logStartup("db-query", config, registryEnabled, exported);
        ProviderSupport.awaitShutdown(
                "sample-db-query-provider-shutdown",
                exported,
                registration,
                customerService);
    }
}
