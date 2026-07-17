package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.DubboProviderApplication;
import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;

import static com.reactor.rust.dubbo.provider.DubboServiceBinding.service;

public final class DbQueryProviderModule implements DubboProviderApplication.Module {

    public static final DbQueryProviderModule INSTANCE = new DbQueryProviderModule();

    private DbQueryProviderModule() {}

    @Override
    public void configure(DubboProviderApplication.ModuleContext context) {
        DubboApplicationProperties properties = context.properties();
        PostgresCustomerRepository repository = context.manage(
                PostgresCustomerRepository.fromProperties(properties));
        CustomerQueryServiceImpl queries = new CustomerQueryServiceImpl(repository);
        context.services(service(CustomerQueryService.class, queries))
                .onStartIf(properties.getBoolean("sample.db.warmup"), queries::warmupDatabase);
    }
}
