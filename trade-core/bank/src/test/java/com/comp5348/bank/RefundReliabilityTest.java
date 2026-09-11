package com.comp5348.bank;
import com.comp5348.bank.service.*;
import com.comp5348.bank.repo.PaymentTxnRepository;
import com.comp5348.bank.client.StoreGrpcClient;
import com.comp5348.bank.domain.*;
import com.comp5348.bank.dto.RefundRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RefundReliabilityTest {
    @Test void failedRefundDoesNotPoisonIdempotencyKey() {
        var repo=mock(PaymentTxnRepository.class);var accounts=mock(AccountService.class);
        var service=new BankService(repo,accounts);
        assertThrows(IllegalStateException.class,()->service.refund(1L,2L,BigDecimal.TEN,"refund-2"));
        verify(repo,never()).save(any());
    }
    @Test void completedRefundIsReplayedWithoutAnotherTransfer() {
        var repo=mock(PaymentTxnRepository.class);var accounts=mock(AccountService.class);var txn=new PaymentTxn();txn.setStatus(TxStatus.SUCCESS);
        when(repo.findByIdempotencyKey("refund-2")).thenReturn(Optional.of(txn));
        assertSame(txn,new BankService(repo,accounts).refund(1L,2L,BigDecimal.TEN,"refund-2"));verifyNoInteractions(accounts);
    }
    @Test void consumerPropagatesFailureToRetryInterceptor() {
        var bank=mock(BankService.class);var rabbit=mock(RabbitTemplate.class);var store=mock(StoreGrpcClient.class);
        when(bank.refund(anyLong(),anyLong(),any(),anyString())).thenThrow(new IllegalStateException("database offline"));
        assertThrows(IllegalStateException.class,()->new RefundConsumer(bank,rabbit,store)
            .processRefundRequest(new RefundRequest(2L,BigDecimal.TEN,"refund-2",1L)));
    }
}
