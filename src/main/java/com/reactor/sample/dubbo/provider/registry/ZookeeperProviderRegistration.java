package com.reactor.sample.dubbo.provider.registry;

import org.apache.dubbo.common.URL;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ZookeeperProviderRegistration implements AutoCloseable {

    private final ZooKeeper zookeeper;
    private final String nodePath;

    private ZookeeperProviderRegistration(ZooKeeper zookeeper, String nodePath) {
        this.zookeeper = zookeeper;
        this.nodePath = nodePath;
    }

    public static ZookeeperProviderRegistration register(
            String registryAddress,
            String registryRoot,
            Class<?> serviceType,
            URL providerUrl
    ) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        ZooKeeper zk = new ZooKeeper(
                zookeeperConnectString(registryAddress),
                30_000,
                event -> onEvent(event, connected)
        );
        if (!connected.await(5, TimeUnit.SECONDS)) {
            zk.close();
            throw new IllegalStateException("Timed out connecting to Zookeeper: " + registryAddress);
        }

        String root = normalizeRoot(registryRoot);
        String servicePath = "/" + root + "/" + URL.encode(serviceType.getName());
        String providersPath = servicePath + "/providers";
        ensurePersistent(zk, "/" + root);
        ensurePersistent(zk, servicePath);
        ensurePersistent(zk, providersPath);

        String nodePath = providersPath + "/" + URL.encode(providerUrl.toFullString());
        recreateEphemeral(zk, nodePath);
        return new ZookeeperProviderRegistration(zk, nodePath);
    }

    @Override
    public void close() {
        try {
            if (zookeeper.exists(nodePath, false) != null) {
                zookeeper.delete(nodePath, -1);
            }
        } catch (Exception ignored) {
            // Best effort cleanup. Session close also removes the ephemeral node.
        } finally {
            try {
                zookeeper.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void onEvent(WatchedEvent event, CountDownLatch connected) {
        if (event.getState() == Watcher.Event.KeeperState.SyncConnected
                || event.getState() == Watcher.Event.KeeperState.ConnectedReadOnly) {
            connected.countDown();
        }
    }

    private static void ensurePersistent(ZooKeeper zk, String path) throws Exception {
        if (zk.exists(path, false) != null) {
            return;
        }
        try {
            zk.create(path, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException ignored) {
            // Concurrent local provider startup can create the same parent path.
        }
    }

    private static void recreateEphemeral(ZooKeeper zk, String path) throws Exception {
        try {
            if (zk.exists(path, false) != null) {
                zk.delete(path, -1);
            }
        } catch (KeeperException.NoNodeException ignored) {
            // No existing node.
        }
        zk.create(
                path,
                "rest-sample-dubbo-provider".getBytes(StandardCharsets.UTF_8),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL
        );
    }

    private static String zookeeperConnectString(String address) {
        String trimmed = address == null ? "" : address.trim();
        int scheme = trimmed.indexOf("://");
        if (scheme >= 0) {
            trimmed = trimmed.substring(scheme + 3);
        }
        int query = trimmed.indexOf('?');
        if (query >= 0) {
            trimmed = trimmed.substring(0, query);
        }
        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(0, slash);
        }
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("registry address must not be blank");
        }
        return trimmed;
    }

    private static String normalizeRoot(String root) {
        String value = root == null || root.isBlank() ? "dubbo" : root.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? "dubbo" : value;
    }
}
