package com.reactor.sample.dubbo.provider.app;

import com.reactor.glowroot.agent.runtime.NativeTelemetry;
import com.reactor.glowroot.agent.runtime.OperationRegistry;
import com.reactor.rust.dubbo.provider.ProviderDispatcherDecorator;
import com.reactor.rust.dubbo.provider.ProviderMethodDispatcher;
import java.util.concurrent.CompletionStage;

/** Only the explicitly selected plain-provider dispatcher is observed; no Dubbo filter chain. */
final class ProviderTelemetry implements ProviderDispatcherDecorator {
    private final OperationRegistry operations;

    ProviderTelemetry() {
        var telemetry = NativeTelemetry.current();
        operations = new OperationRegistry(telemetry, "Dubbo Provider", telemetry.maxRoutes());
    }

    @Override
    public <T> ProviderMethodDispatcher<T> decorate(Class<T> serviceType, ProviderMethodDispatcher<T> dispatcher) {
        String service = serviceType.getName();
        return (target, method, types, arguments) -> {
            var operation = operations.operation(service, method);
            long started = operation.start();
            try {
                Object result = dispatcher.invoke(target, method, types, arguments);
                if (result instanceof CompletionStage<?> stage) return operation.observe(started, stage);
                operation.success(started);
                return result;
            } catch (Throwable error) {
                operation.failure(started, error);
                throw error;
            }
        };
    }
}
