package com.comp5348.email;
import com.comp5348.email.service.EmailService;
import com.comp5348.email.repo.EmailMessageRepository;
import com.comp5348.email.domain.EmailMessage;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class EmailIdempotencyTest {
    @Test void repeatedNotificationReturnsExistingRecord() {
        var repo=mock(EmailMessageRepository.class);var existing=new EmailMessage();
        when(repo.findByOrderIdAndTypeAndToAddr(1L,"PAID","x@example.com")).thenReturn(Optional.of(existing));
        assertSame(existing,new EmailService(repo).send(1L,"PAID","x@example.com","Paid","body"));
        verify(repo,never()).save(any());
    }
}
