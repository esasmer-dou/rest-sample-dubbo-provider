package com.reactor.sample.dubbo.provider.streamcatalog;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Selected only by stream-catalog-provider; no native library or network access required. */
class OfficialStreamCatalogProfileTest {

    @Test
    void baselineUsesOfficialEngineWithoutNativeAdapterRuntimeAnnotationsOrZookeeper() {
        var loader = getClass().getClassLoader();
        assertNotNull(loader.getResource("org/apache/dubbo/rpc/Invoker.class"));
        assertNotNull(loader.getResource("io/netty/channel/Channel.class"));
        assertNotNull(loader.getResource("com/alibaba/com/caucho/hessian/io/Hessian2Input.class"));
        for (String resource : List.of("com/reactor/rust/dubbo/runtime/NativeDubboRuntime.class",
                "com/reactor/rust/dubbo/provider/NativeProviderFactory.class",
                "com/reactor/rust/dubbo/annotation/DubboStreamed.class",
                "org/apache/zookeeper/ZooKeeper.class")) {
            assertNull(loader.getResource(resource), resource + " must not enter the official static baseline");
        }
    }
}
