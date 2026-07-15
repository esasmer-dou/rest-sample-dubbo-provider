package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CatalogJsonService;
import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.DubboProviderApplication;
import com.reactor.rust.dubbo.provider.DubboProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.service.CatalogJsonServiceImpl;

public final class CatalogStaticProviderApplication {

    private static final String CONFIG = "rest-sample-dubbo-provider.properties";

    private CatalogStaticProviderApplication() {}

    public static void main(String[] args) throws Exception {
        DubboApplicationProperties properties = DubboApplicationProperties.load(CONFIG);
        DubboProviderRuntimeTuning.applyLowRssDefaults(properties);

        DubboProviderApplication.builder(properties)
                .name("catalog-static")
                .service(CatalogJsonService.class, new CatalogJsonServiceImpl())
                .shutdownThreadName("sample-catalog-static-provider-shutdown")
                .run();
    }
}
