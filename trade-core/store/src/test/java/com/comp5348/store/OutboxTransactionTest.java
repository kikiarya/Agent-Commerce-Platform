package com.comp5348.store;
import com.comp5348.store.service.MessagePublisher;
import com.comp5348.store.dto.DeliveryRequest;
import com.comp5348.store.model.OutboxMessage;
import com.comp5348.store.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.default_schema=PUBLIC",
    "spring.datasource.password=", "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"})
@ContextConfiguration(classes=OutboxTransactionTest.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OutboxTransactionTest {
    @Configuration @EnableAutoConfiguration @EntityScan(basePackageClasses=OutboxMessage.class)
    @EnableJpaRepositories(basePackageClasses=OutboxRepository.class) @Import(MessagePublisher.class)
    static class Config { @Bean ObjectMapper json(){return new ObjectMapper();} }
    @Autowired OutboxRepository repo; @Autowired MessagePublisher publisher; @Autowired PlatformTransactionManager manager;
    @BeforeEach void clear(){repo.deleteAll();}
    @Test void rollbackRemovesOutboxEvent() {
        var tx=new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            publisher.publishDeliveryRequest(new DeliveryRequest(1L,List.of()));
            throw new IllegalStateException("Business transaction failed");
        }));
        assertEquals(0,repo.count());
    }
    @Test void committedEventIsAvailableForLaterDelivery() {
        new TransactionTemplate(manager).executeWithoutResult(status->publisher.publishDeliveryRequest(new DeliveryRequest(1L,List.of())));
        assertEquals(1,repo.findTop50BySentAtIsNullOrderByIdAsc().size());
    }
}
