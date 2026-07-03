package com.reactor.sample.dubbo.provider.db;

import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PostgresCustomerRepository implements AutoCloseable {

    private static final String SELECT_CUSTOMERS = """
            select id, customer_no, full_name, segment, email, status, created_at, updated_at
            from sample_customers
            order by id
            limit 100
            """;
    private static final String SELECT_CUSTOMER_BY_ID = """
            select id, customer_no, full_name, segment, email, status, created_at, updated_at
            from sample_customers
            where id = ?
            """;
    private static final String SELECT_CUSTOMER_EXISTS = """
            select 1
            from sample_customers
            where id = ?
            """;
    private static final String SELECT_CUSTOMER_DISPLAY_NAME = """
            select full_name
            from sample_customers
            where id = ?
            """;
    private static final String SELECT_CUSTOMERS_BY_SEGMENT = """
            select id, customer_no, full_name, segment, email, status, created_at, updated_at
            from sample_customers
            where segment = ?
            order by id
            limit ?
            """;
    private static final String SELECT_CUSTOMER_COUNTS = """
            select
              count(*) as total,
              sum(case when status = 'active' then 1 else 0 end) as active,
              sum(case when status = 'passive' then 1 else 0 end) as passive
            from sample_customers
            """;
    private static final String UPDATE_CUSTOMER_SEGMENT = """
            update sample_customers
            set segment = ?, updated_at = now()
            where id = ?
            returning id, customer_no, full_name, segment, email, status, created_at, updated_at
            """;
    private static final String UPDATE_CUSTOMER_STATUS = """
            update sample_customers
            set status = ?, updated_at = now()
            where id = ?
            returning id, customer_no, full_name, segment, email, status, created_at, updated_at
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CUSTOMERS);
             ResultSet resultSet = statement.executeQuery()) {
            List<SampleCustomer> customers = new ArrayList<>(100);
            while (resultSet.next()) {
                customers.add(toCustomer(resultSet));
            }
            return customers;
        } catch (Exception e) {
            throw new IllegalStateException("Find customers failed", e);
        }
    }

    public SampleCustomer findCustomer(long customerId) {
        ensureInitialized();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CUSTOMER_BY_ID)) {
            statement.setLong(1, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? toCustomer(resultSet) : null;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Find customer failed", e);
        }
    }

    public List<SampleCustomer> findCustomersBySegment(String segment, int limit) {
        ensureInitialized();
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CUSTOMERS_BY_SEGMENT)) {
            statement.setString(1, segment == null || segment.isBlank() ? "standard" : segment);
            statement.setInt(2, boundedLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SampleCustomer> customers = new ArrayList<>();
                while (resultSet.next()) {
                    customers.add(toCustomer(resultSet));
                }
                return customers;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Find customers by segment failed", e);
        }
    }

    public CustomerCounts countCustomersByStatus() {
        ensureInitialized();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CUSTOMER_COUNTS);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return new CustomerCounts(0, 0, 0);
            }
            return new CustomerCounts(
                    resultSet.getInt("total"),
                    resultSet.getInt("active"),
                    resultSet.getInt("passive")
            );
        } catch (Exception e) {
            throw new IllegalStateException("Count customers failed", e);
        }
    }

    public boolean customerExists(long customerId) {
        ensureInitialized();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CUSTOMER_EXISTS)) {
            statement.setLong(1, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Check customer existence failed", e);
        }
    }

    public String customerDisplayName(long customerId) {
        ensureInitialized();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CUSTOMER_DISPLAY_NAME)) {
            statement.setLong(1, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("full_name") : "";
            }
        } catch (Exception e) {
            throw new IllegalStateException("Find customer display name failed", e);
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
        return updateSingleField(customerId, UPDATE_CUSTOMER_SEGMENT, segment);
    }

    public SampleCustomer updateStatus(long customerId, String status) {
        return updateSingleField(customerId, UPDATE_CUSTOMER_STATUS, status);
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

    private SampleCustomer updateSingleField(long customerId, String sql, String value) {
        ensureInitialized();
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

    public record CustomerCounts(int total, int active, int passive) {
    }

}
