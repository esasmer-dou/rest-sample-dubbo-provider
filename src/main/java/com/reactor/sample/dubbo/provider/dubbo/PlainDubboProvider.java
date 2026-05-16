package com.reactor.sample.dubbo.provider.dubbo;

import com.reactor.sample.dubbo.provider.registry.ZookeeperProviderRegistration;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.model.ServiceMetadata;
import org.apache.dubbo.rpc.protocol.PermittedSerializationKeeper;
import org.apache.dubbo.rpc.proxy.AbstractProxyInvoker;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

public final class PlainDubboProvider<T> implements AutoCloseable {

    private static final String DEFAULT_DUBBO_VERSION = "0.0.0";

    private final Exporter<T> exporter;
    private final AutoCloseable registration;

    private PlainDubboProvider(Exporter<T> exporter, AutoCloseable registration) {
        this.exporter = exporter;
        this.registration = registration;
    }

    public static <T> PlainDubboProvider<T> export(
            Class<T> serviceType,
            T service,
            ProviderConfig config
    ) throws Exception {
        URL exportUrl = providerUrl(serviceType, config);
        registerServiceModel(serviceType, service, exportUrl);
        registerPermittedSerialization(exportUrl);
        Invoker<T> invoker = new ReflectiveInvoker<>(service, serviceType, exportUrl);

        ExtensionLoader<Protocol> loader = DubboProviderRuntimeModel.module().getExtensionLoader(Protocol.class);
        Protocol protocol = loader.getExtension("dubbo", false);
        Exporter<T> exporter = protocol.export(invoker);

        AutoCloseable registration = ZookeeperProviderRegistration.register(
                config.registryAddress(),
                config.registryRoot(),
                serviceType,
                exportUrl
        );
        return new PlainDubboProvider<>(exporter, registration);
    }

    public URL url() {
        return exporter.getInvoker().getUrl();
    }

    @Override
    public void close() {
        try {
            registration.close();
        } catch (Exception ignored) {
            // Session close removes the ephemeral node; explicit delete is best effort.
        } finally {
            exporter.unexport();
        }
    }

    private static <T> URL providerUrl(Class<T> serviceType, ProviderConfig config) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("application", config.applicationName());
        parameters.put("interface", serviceType.getName());
        parameters.put("version", DEFAULT_DUBBO_VERSION);
        parameters.put("side", "provider");
        parameters.put("category", "providers");
        parameters.put("serialization", "hessian2");
        parameters.put("methods", methodNames(serviceType));
        parameters.put("timestamp", Long.toString(System.currentTimeMillis()));
        parameters.put("pid", currentPid());
        parameters.put("anyhost", Boolean.toString("0.0.0.0".equals(config.bindHost())));
        parameters.put("bind.ip", config.bindHost());
        parameters.put("bind.port", Integer.toString(config.port()));
        parameters.put("dubbo", "3.3.5");

        return new URL("dubbo", config.host(), config.port(), serviceType.getName(), parameters)
                .setScopeModel(DubboProviderRuntimeModel.module());
    }

    private static <T> void registerServiceModel(Class<T> serviceType, T service, URL exportUrl) {
        ModuleModel module = DubboProviderRuntimeModel.module();
        ServiceDescriptor descriptor = module.getServiceRepository().registerService(serviceType);
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setServiceType(serviceType);
        metadata.setTarget(service);
        module.getServiceRepository().registerProvider(exportUrl.getServiceKey(), service, descriptor, null, metadata);
    }

    private static void registerPermittedSerialization(URL exportUrl) {
        DubboProviderRuntimeModel.module()
                .getApplicationModel()
                .getFrameworkModel()
                .getBeanFactory()
                .getOrRegisterBean(PermittedSerializationKeeper.class)
                .registerService(exportUrl);
    }

    private static String methodNames(Class<?> serviceType) {
        TreeSet<String> names = new TreeSet<>();
        for (Method method : serviceType.getMethods()) {
            if (method.getDeclaringClass() != Object.class) {
                names.add(method.getName());
            }
        }
        return String.join(",", names);
    }

    private static String currentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        return at > 0 ? name.substring(0, at) : name;
    }

    private static final class ReflectiveInvoker<T> extends AbstractProxyInvoker<T> {

        private ReflectiveInvoker(T proxy, Class<T> type, URL url) {
            super(proxy, type, url);
        }

        @Override
        protected Object doInvoke(T proxy, String methodName, Class<?>[] parameterTypes, Object[] arguments)
                throws Throwable {
            try {
                Method method = proxy.getClass().getMethod(methodName, parameterTypes);
                return method.invoke(proxy, arguments);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    public record ProviderConfig(
            String applicationName,
            String registryAddress,
            String registryRoot,
            String host,
            String bindHost,
            int port) {}
}
