package com.reactor.sample.dubbo.provider.db;

import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.javalite.activejdbc.Base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PostgresCustomerRepository implements AutoCloseable {

    private static final String SELECT_CUSTOMERS = """
            select id, customer_no, full_name, segment, email, status, created_at, updated_at
            from sample_customers
            order by id
            limit 100
            """;

    private final HikariDataSource dataSource;
    private final boolean schemaInit;
    private volatile boolean initialized;

    private PostgresCustomerRepository(HikariDataSource dataSource, boolean schemaInit) {
        this.dataSource = dataSource;
        this.schemaInit = schemaInit;
    }

    public static PostgresCustomerRepository fromProperties() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(ProviderProperties.get("sample.db.pool-name"));
        config.setDriverClassName(ProviderProperties.get("sample.db.driver-class-name"));
        config.setJdbcUrl(ProviderProperties.get("sample.db.jdbc-url"));
        config.setUsername(ProviderProperties.get("sample.db.username"));
        config.setPassword(ProviderProperties.get("sample.db.password"));
        config.setMaximumPoolSize(ProviderProperties.getInt("sample.db.maximum-pool-size"));
        config.setMinimumIdle(ProviderProperties.getInt("sample.db.minimum-idle"));
        config.setConnectionTimeout(ProviderProperties.getLong("sample.db.connection-timeout-ms"));
        config.setValidationTimeout(ProviderProperties.getLong("sample.db.validation-timeout-ms"));
        config.setIdleTimeout(ProviderProperties.getLong("sample.db.idle-timeout-ms"));
        config.setMaxLifetime(ProviderProperties.getLong("sample.db.max-lifetime-ms"));
        config.setLeakDetectionThreshold(ProviderProperties.getLong("sample.db.leak-detection-threshold-ms"));
        config.setInitializationFailTimeout(ProviderProperties.getLong("sample.db.initialization-fail-timeout-ms"));
        config.setAutoCommit(ProviderProperties.getBoolean("sample.db.auto-commit"));
        config.setReadOnly(ProviderProperties.getBoolean("sample.db.read-only"));
        config.setRegisterMbeans(ProviderProperties.getBoolean("sample.db.register-mbeans"));
        config.addDataSourceProperty("ApplicationName", ProviderProperties.get("sample.db.postgresql.application-name"));

        return new PostgresCustomerRepository(
                new HikariDataSource(config),
                ProviderProperties.getBoolean("sample.db.schema-init")
        );
    }

    public List<SampleCustomer> findCustomers() {
        ensureInitialized();
        Base.open(dataSource);
        try {
            List<Map> rows = Base.findAll(SELECT_CUSTOMERS);
            List<SampleCustomer> customers = new ArrayList<>(rows.size());
            for (Map row : rows) {
                customers.add(toCustomer(row));
            }
            return customers;
        } finally {
            Base.close();
        }
    }

    public SampleCustomer createCustomer(String customerNo, String fullName, String segment, String email) {
        ensureInitialized();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into sample_customers (customer_no, full_name, segment, email, status)
                     values (?, ?, ?, ?, 'active')
                     on conflict (customer_no) do update set
                       full_name = excluded.full_name,
                       segment = excluded.segment,
                       email = excluded.email,
                       status = 'active',
                       updated_at = now()
                     returning id, customer_no, full_name, segment, email, status, created_at, updated_at
                     """)) {
            statement.setString(1, customerNo);
            statement.setString(2, fullName);
            statement.setString(3, segment);
            statement.setString(4, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return toCustomer(resultSet);
                }
                throw new IllegalStateException("Create customer returned no row");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Create customer failed", e);
        }
    }

    public SampleCustomer updateSegment(long customerId, String segment) {
        return updateSingleField(customerId, "segment", segment);
    }

    public SampleCustomer updateStatus(long customerId, String status) {
        return updateSingleField(customerId, "status", status);
    }

    public boolean deleteCustomer(long customerId) {
        ensureInitialized();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     delete from sample_customers
                     where id = ?
                     """)) {
            statement.setLong(1, customerId);
            return statement.executeUpdate() > 0;
        } catch (Exception e) {
            throw new IllegalStateException("Delete customer failed", e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private void ensureInitialized() {
        if (!schemaInit || initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        create table if not exists sample_customers (
                          id bigserial primary key,
                          customer_no varchar(32) not null unique,
                          full_name varchar(128) not null,
                          segment varchar(32) not null,
                          email varchar(160) not null default '',
                          status varchar(32) not null default 'active',
                          created_at timestamptz not null default now()
                        )
                        """);
                statement.executeUpdate("alter table sample_customers add column if not exists email varchar(160) not null default ''");
                statement.executeUpdate("alter table sample_customers add column if not exists status varchar(32) not null default 'active'");
                statement.executeUpdate("alter table sample_customers add column if not exists updated_at timestamptz not null default now()");
                statement.executeUpdate("""
                        insert into sample_customers (customer_no, full_name, segment, email, status)
                        values
                          ('CUST-1001', 'Mustafa Korkmaz', 'pilot', 'mustafa.korkmaz@example.com', 'active'),
                          ('CUST-1002', 'Ayse Demir', 'enterprise', 'ayse.demir@example.com', 'active'),
                          ('CUST-1003', 'Mehmet Celik', 'standard', 'mehmet.celik@example.com', 'passive')
                        on conflict (customer_no) do nothing
                        """);
                initialized = true;
            } catch (Exception e) {
                throw new IllegalStateException("PostgreSQL sample schema init failed", e);
            }
        }
    }

    private static SampleCustomer toCustomer(Map row) {
        return new SampleCustomer(
                number(row.get("id")).longValue(),
                string(row.get("customer_no")),
                string(row.get("full_name")),
                string(row.get("segment")),
                string(row.get("email")),
                string(row.get("status")),
                instant(row.get("created_at")),
                instant(row.get("updated_at"))
        );
    }

    private SampleCustomer updateSingleField(long customerId, String fieldName, String value) {
        ensureInitialized();
        String sql = """
                update sample_customers
                set %s = ?, updated_at = now()
                where id = ?
                returning id, customer_no, full_name, segment, email, status, created_at, updated_at
                """.formatted(fieldName);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setLong(2, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return toCustomer(resultSet);
                }
                return null;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Update customer failed", e);
        }
    }

    private static SampleCustomer toCustomer(ResultSet row) throws Exception {
        return new SampleCustomer(
                row.getLong("id"),
                row.getString("customer_no"),
                row.getString("full_name"),
                row.getString("segment"),
                row.getString("email"),
                row.getString("status"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant()
        );
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return Instant.parse(String.valueOf(value));
    }

}
