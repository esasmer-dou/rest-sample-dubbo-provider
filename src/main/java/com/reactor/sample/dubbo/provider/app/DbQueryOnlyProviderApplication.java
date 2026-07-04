package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.provider.DubboProviderSupport;
import com.reactor.rust.dubbo.provider.PlainDubboProvider;
import com.reactor.rust.dubbo.provider.ZookeeperDubboProviderRegistration;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;

import java.util.List;

public final class DbQueryOnlyProviderApplication {

    private DbQueryOnlyProviderApplication() {}

    public static void main(String[] args) throws Exception {
        ProviderRuntimeTuning.apply();

        boolean registryEnabled = ProviderProperties.getBoolean("reactor.dubbo.registry-enabled");
        DubboProviderSupport support = DubboProviderSupport.fromProperties(ProviderProperties.asProperties());
        PlainDubboProvider.ProviderConfig config = support.providerConfig(registryEnabled);
        PostgresCustomerRepository customerRepository = PostgresCustomerRepository.fromProperties();
        CustomerQueryServiceImpl customerService = new CustomerQueryServiceImpl(customerRepository);
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
                support.service(CustomerQueryService.class, customerService));
        List<DubboProviderSupport.ExportedService<?>> exported = List.of();
        try {
            exported = support.exportAll(config, registration, services);
        } catch (Exception e) {
            support.closeAll(exported);
            support.closeAll(registration);
            customerService.close();
            throw e;
        }

        support.logStartup("db-query", config, registryEnabled, exported);
        support.awaitShutdown(
                "sample-db-query-provider-shutdown",
                exported,
                registration,
                customerService);
    }
}
