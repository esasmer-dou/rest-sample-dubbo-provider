package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.DubboProviderApplication;
import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.NestedCatalogService;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.service.CustomerCommandServiceImpl;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;
import com.reactor.sample.dubbo.provider.service.NestedCatalogServiceImpl;

public final class FullProviderModule implements DubboProviderApplication.Module {

    public static final FullProviderModule INSTANCE = new FullProviderModule();

    private FullProviderModule() {}

    @Override
    public void configure(DubboProviderApplication.ModuleContext context) {
        DubboApplicationProperties properties = context.properties();
        PostgresCustomerRepository repository = context.manage(
                PostgresCustomerRepository.fromProperties(properties));
        CustomerQueryServiceImpl queries = new CustomerQueryServiceImpl(repository);
        context.service(NestedCatalogService.class, new NestedCatalogServiceImpl())
                .service(CustomerQueryService.class, queries)
                .service(CustomerCommandService.class, new CustomerCommandServiceImpl(repository))
                .onStartIf(properties.getBoolean("sample.db.warmup"), queries::warmupDatabase);
    }
}
