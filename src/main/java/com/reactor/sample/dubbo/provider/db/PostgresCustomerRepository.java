package com.reactor.sample.dubbo.provider.db;

import com.reactor.rust.dubbo.provider.jdbc.HikariDataSources;
import com.reactor.rust.dubbo.provider.jdbc.JdbcRepository;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.model.customer.CustomerCounts;
import com.reactor.sample.model.customer.SampleCustomer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public final class PostgresCustomerRepository extends JdbcRepository {

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

    private PostgresCustomerRepository() {
        super(
                HikariDataSources.create(ProviderProperties.asProperties(), "sample.db"),
                ProviderProperties.getBoolean("sample.db.schema-init"));
    }

    public static PostgresCustomerRepository fromProperties() {
        return new PostgresCustomerRepository();
    }

    public List<SampleCustomer> findCustomers() {
        return query("Find customers", SELECT_CUSTOMERS, SqlBinder.none(), PostgresCustomerRepository::toCustomer);
    }

    public SampleCustomer findCustomer(long customerId) {
        return queryOne("Find customer", SELECT_CUSTOMER_BY_ID, statement -> {
            statement.setLong(1, customerId);
        }, PostgresCustomerRepository::toCustomer, null);
    }

    public List<SampleCustomer> findCustomersBySegment(String segment, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return query("Find customers by segment", SELECT_CUSTOMERS_BY_SEGMENT, statement -> {
            statement.setString(1, segment == null || segment.isBlank() ? "standard" : segment);
            statement.setInt(2, boundedLimit);
        }, PostgresCustomerRepository::toCustomer);
    }

    public CustomerCounts countCustomersByStatus() {
        return queryOne(
                "Count customers",
                SELECT_CUSTOMER_COUNTS,
                SqlBinder.none(),
                row -> new CustomerCounts(row.getInt("total"), row.getInt("active"), row.getInt("passive")),
                new CustomerCounts(0, 0, 0));
    }

    public boolean customerExists(long customerId) {
        return queryOne("Check customer existence", SELECT_CUSTOMER_EXISTS, statement -> {
            statement.setLong(1, customerId);
        }, row -> true, false);
    }

    public String customerDisplayName(long customerId) {
        return queryOne("Find customer display name", SELECT_CUSTOMER_DISPLAY_NAME, statement -> {
            statement.setLong(1, customerId);
        }, row -> row.getString("full_name"), "");
    }

    public SampleCustomer createCustomer(String customerNo, String fullName, String segment, String email) {
        SampleCustomer customer = queryOne("Create customer", """
                     insert into sample_customers (customer_no, full_name, segment, email, status)
                     values (?, ?, ?, ?, 'active')
                     on conflict (customer_no) do update set
                       full_name = excluded.full_name,
                       segment = excluded.segment,
                       email = excluded.email,
                       status = 'active',
                       updated_at = now()
                     returning id, customer_no, full_name, segment, email, status, created_at, updated_at
                     """, statement -> {
            statement.setString(1, customerNo);
            statement.setString(2, fullName);
            statement.setString(3, segment);
            statement.setString(4, email);
        }, PostgresCustomerRepository::toCustomer, null);
        if (customer == null) {
            throw new IllegalStateException("Create customer returned no row");
        }
        return customer;
    }

    public SampleCustomer updateSegment(long customerId, String segment) {
        return updateSingleField(customerId, UPDATE_CUSTOMER_SEGMENT, segment);
    }

    public SampleCustomer updateStatus(long customerId, String status) {
        return updateSingleField(customerId, UPDATE_CUSTOMER_STATUS, status);
    }

    public boolean deleteCustomer(long customerId) {
        return update("Delete customer", """
                     delete from sample_customers
                     where id = ?
                     """, statement -> {
            statement.setLong(1, customerId);
        }) > 0;
    }

    @Override
    protected void initializeSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
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
        }
    }

    private SampleCustomer updateSingleField(long customerId, String sql, String value) {
        return queryOne("Update customer", sql, statement -> {
            statement.setString(1, value);
            statement.setLong(2, customerId);
        }, PostgresCustomerRepository::toCustomer, null);
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

}
