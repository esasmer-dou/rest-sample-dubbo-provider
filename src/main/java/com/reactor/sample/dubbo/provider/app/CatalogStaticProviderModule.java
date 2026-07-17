package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.provider.DubboProviderApplication;
import com.reactor.rust.dubbo.sample.CatalogJsonService;
import com.reactor.sample.dubbo.provider.service.CatalogJsonServiceImpl;

import static com.reactor.rust.dubbo.provider.DubboServiceBinding.service;

public final class CatalogStaticProviderModule implements DubboProviderApplication.Module {

    public static final CatalogStaticProviderModule INSTANCE = new CatalogStaticProviderModule();

    private CatalogStaticProviderModule() {}

    @Override
    public void configure(DubboProviderApplication.ModuleContext context) {
        context.services(service(CatalogJsonService.class, new CatalogJsonServiceImpl()));
    }
}
