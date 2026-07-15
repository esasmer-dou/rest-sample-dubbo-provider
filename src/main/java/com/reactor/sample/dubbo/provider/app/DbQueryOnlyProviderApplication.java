package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.provider.DubboProviderApplication;

public final class DbQueryOnlyProviderApplication {

    private static final String CONFIG = "rest-sample-dubbo-provider.properties";

    private DbQueryOnlyProviderApplication() {}

    public static void main(String[] args) throws Exception {
        DubboProviderApplication.run(CONFIG, "db-query", DbQueryProviderModule.INSTANCE);
    }
}
