package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.DubboProviderApplication;
import com.reactor.rust.dubbo.provider.DubboProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.service.CustomerCommandServiceImpl;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;
import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;

public final class RestSampleDubboProviderApplication {

    private static final String CONFIG = "rest-sample-dubbo-provider.properties";

    private RestSampleDubboProviderApplication() {}

    public static void main(String[] args) throws Exception {
        DubboApplicationProperties properties = DubboApplicationProperties.load(CONFIG);
        DubboProviderRuntimeTuning.applyLowRssDefaults(properties);

        DubboProviderApplication.builder(properties)
                .name("full")
                .registryEnabled(properties.getBoolean("reactor.dubbo.registry-enabled"))
                .shutdownThreadName("sample-dubbo-provider-shutdown")
                .module(context -> {
                    PostgresCustomerRepository repository =
                            context.manage(PostgresCustomerRepository.fromProperties(properties));
                    CustomerQueryServiceImpl queries = new CustomerQueryServiceImpl(repository);
                    context.service(NestedCatalogService.class, new NestedCatalogServiceImpl())
                            .service(CustomerQueryService.class, queries)
                            .service(CustomerCommandService.class, new CustomerCommandServiceImpl(repository))
                            .onStartIf(properties.getBoolean("sample.db.warmup"), queries::warmupDatabase);
                })
                .run();
    }
}
