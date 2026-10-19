package com.reactor.sample.dubbo.provider;

import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.rust.jdbc.PostgresIdempotency;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="REACTOR_TEST_JDBC_URL", matches=".+")
class IdempotentStatusCommandTest {
    @Test void twoRepositoryInstancesReplayTheStoredResultAndRejectChangedInput() {
        var values=DubboApplicationProperties.load("rest-sample-dubbo-provider.properties").asProperties();
        values.setProperty("sample.db.jdbc-url",System.getenv("REACTOR_TEST_JDBC_URL"));
        values.setProperty("sample.db.username",System.getenv("REACTOR_TEST_JDBC_USER"));
        values.setProperty("sample.db.password",System.getenv("REACTOR_TEST_JDBC_PASSWORD"));
        values.setProperty("sample.command.idempotency.enabled","true");
        values.setProperty("sample.db.schema-init","true");
        var properties=DubboApplicationProperties.from(values);
        try(var first=PostgresCustomerRepository.fromProperties(properties);
            var second=PostgresCustomerRepository.fromProperties(properties)) {
            String request=UUID.randomUUID().toString();
            var customer=first.createCustomer("IT-"+request.substring(0,12),"Test Müşteri","standard","test@example.invalid");
            java.util.function.Function<com.reactor.sample.model.customer.SampleCustomer,CustomerMutationResult> response=
                    row -> new CustomerMutationResult("status",request,true,row.id(),row.customerNo(),
                            row.fullName(),row.segment(),row.status(),"updated",row.updatedAt().toString());
            var result=first.updateStatusCommand(customer.id(),"passive",request,response);
            assertEquals(result,second.updateStatusCommand(customer.id(),"passive",request,row->{fail("must not execute twice");return null;}));
            assertThrows(PostgresIdempotency.KeyConflictException.class,
                    ()->second.updateStatusCommand(customer.id(),"active",request,response));
            first.deleteCustomer(customer.id());
        }
    }
}
