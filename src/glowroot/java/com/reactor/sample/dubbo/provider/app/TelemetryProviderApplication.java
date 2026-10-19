package com.reactor.sample.dubbo.provider.app;

import com.reactor.glowroot.agent.runtime.StandaloneTelemetry;
import com.reactor.rust.dubbo.provider.DubboProviderApplication;

public final class TelemetryProviderApplication {
    private TelemetryProviderApplication() {}

    public static void main(String[] args) throws Exception {
        DubboProviderApplication.run("rest-sample-dubbo-provider.properties", "full", context -> {
            var telemetry = context.manage(StandaloneTelemetry.startIfEnabled(key -> context.properties().get(key, null)));
            if (telemetry.enabled()) {
                context.decorateDispatchers(new ProviderTelemetry());
            }
            FullProviderModule.INSTANCE.configure(context);
        });
    }
}
