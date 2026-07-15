package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.DubboProviderApplication;
import com.reactor.rust.dubbo.provider.DubboProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;

public final class DbQueryOnlyProviderApplication {

    private static final String CONFIG = "rest-sample-dubbo-provider.properties";

    private DbQueryOnlyProviderApplication() {}

    public static void main(String[] args) throws Exception {
        DubboApplicationProperties properties = DubboApplicationProperties.load(CONFIG);
        DubboProviderRuntimeTuning.applyLowRssDefaults(properties);

        DubboProviderApplication.builder(properties)
                .name("db-query")
                .registryEnabled(properties.getBoolean("reactor.dubbo.registry-enabled"))
                .shutdownThreadName("sample-db-query-provider-shutdown")
                .module(context -> {
                    PostgresCustomerRepository repository =
                            context.manage(PostgresCustomerRepository.fromProperties(properties));
                    CustomerQueryServiceImpl queries = new CustomerQueryServiceImpl(repository);
                    context.service(CustomerQueryService.class, queries)
                            .onStartIf(properties.getBoolean("sample.db.warmup"), queries::warmupDatabase);
                })
                .run();
    }
}
