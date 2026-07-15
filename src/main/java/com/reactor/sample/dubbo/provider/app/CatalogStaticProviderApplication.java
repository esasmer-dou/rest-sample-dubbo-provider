package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.provider.DubboProviderApplication;

public final class CatalogStaticProviderApplication {

    private static final String CONFIG = "rest-sample-dubbo-provider.properties";

    private CatalogStaticProviderApplication() {}

    public static void main(String[] args) throws Exception {
        DubboProviderApplication.run(CONFIG, "catalog-static", CatalogStaticProviderModule.INSTANCE);
    }
}
