package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CatalogJsonService;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.dubbo.PlainDubboProvider;
import com.reactor.sample.dubbo.provider.dubbo.ProviderSupport;
import com.reactor.sample.dubbo.provider.service.CatalogJsonServiceImpl;

import java.util.List;

public final class CatalogStaticProviderApplication {

    private CatalogStaticProviderApplication() {}

    public static void main(String[] args) throws Exception {
        ProviderRuntimeTuning.apply();

        PlainDubboProvider.ProviderConfig config = ProviderSupport.providerConfig(false);
        List<ProviderSupport.ServicePlan<?>> services = List.of(
                ProviderSupport.service(CatalogJsonService.class, new CatalogJsonServiceImpl()));
        List<ProviderSupport.ExportedService<?>> exported = ProviderSupport.exportAll(config, null, services);

        ProviderSupport.logStartup("catalog-static", config, false, exported);
        ProviderSupport.awaitShutdown("sample-catalog-static-provider-shutdown", exported);
    }
}
