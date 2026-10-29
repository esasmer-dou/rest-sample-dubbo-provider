package com.reactor.sample.dubbo.provider.nativeprovider;

import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.runtime.Hessian2Input;
import com.reactor.rust.dubbo.runtime.Hessian2Output;
import com.reactor.sample.dubbo.provider.service.StreamCatalogServiceImpl;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeProviderSampleTest {

    @Test
    void runtimeDependencyClosureHasNeitherOfficialEngineNorBuildOnlyAnnotationsOrProcessor() {
        var loader = getClass().getClassLoader();
        for (String resource : List.of("org/apache/dubbo/rpc/Invoker.class", "io/netty/channel/Channel.class",
                "com/alibaba/com/caucho/hessian/io/Hessian2Input.class", "org/apache/zookeeper/ZooKeeper.class",
                "org/springframework/context/ApplicationContext.class",
                "com/reactor/rust/dubbo/annotation/DubboStreamed.class",
                "com/reactor/rust/dubbo/nativeprovider/codegen/NativeProviderProcessor.class")) {
            assertNull(loader.getResource(resource), resource + " must not enter the native runtime");
        }
        assertNotNull(loader.getResource("com/reactor/rust/dubbo/runtime/NativeDubboRuntime.class"));
    }

    @Test
    void generatedContractPreservesRealInterfaceMethodAndVersion() {
        var contract = StreamCatalogNativeDefinition__NativeProvider.CONTRACT;
        assertEquals("com.reactor.rust.dubbo.sample.StreamCatalogService", contract.serviceType().getName());
        assertEquals("", contract.group());
        assertEquals("0.0.0", contract.version());
        assertEquals(1, contract.methods().size());
        assertEquals("read", contract.methods().getFirst().name());
        assertEquals("(I)Ljava/util/List;", contract.methods().getFirst().descriptor());
    }

    @Test
    void generatedCodecRoundTrips32And512RowsIncludingSignedZeroWithoutNativeLoading() {
        var service = new StreamCatalogServiceImpl();
        var contract = StreamCatalogNativeDefinition__NativeProvider.CONTRACT;
        for (int count : new int[]{32, 512}) {
            var output = new Hessian2Output();
            var input = new Hessian2Input();
            try {
                output.attach(ByteBuffer.allocateDirect(262144), 0);
                contract.writeResult(1, output, service.read(count));
                assertTrue(output.position() < 262144);
                input.attach(output.buffer(), output.position(), 1024);
                assertEquals(service.read(count), contract.readResult(1, input));
                assertEquals(0, input.remaining());
            } finally {
                input.detach();
                output.detach();
            }
        }
    }

    @Test
    void configuredAdmissionIsBoundedAndOtherEngineRegistryOrLoopbackAreRejected() {
        var values = new Properties();
        values.setProperty("sample.provider.engine", "native");
        var configuration = NativeProviderConfiguration.from(DubboApplicationProperties.from(values));
        assertEquals(4, configuration.options().businessWorkers());
        assertEquals(16, configuration.options().queueCapacity());
        assertEquals(5L * 1024 * 1024, (long) (4 + 16) * configuration.options().maxPayloadBytes());
        for (String[] invalid : new String[][]{{"sample.provider.engine", "official"},
                {"reactor.dubbo.registry-enabled", "true"}, {"dubbo.provider.bind-host", "127.0.0.1"},
                {"sample.native-provider.service-max-concurrent", "5"},
                {"sample.native-provider.queue-capacity", "1025"}}) {
            var overridden = new Properties();
            overridden.putAll(values);
            overridden.setProperty(invalid[0], invalid[1]);
            assertThrows(IllegalArgumentException.class,
                    () -> NativeProviderConfiguration.from(DubboApplicationProperties.from(overridden)), invalid[0]);
        }
    }
}
