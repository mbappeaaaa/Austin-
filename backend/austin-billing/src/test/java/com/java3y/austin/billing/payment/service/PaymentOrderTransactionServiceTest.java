package com.java3y.austin.billing.payment.service;

import com.java3y.austin.billing.payment.domain.PaymentOrder;
import com.java3y.austin.billing.payment.domain.PaymentOrderStatus;
import com.java3y.austin.billing.payment.repository.PaymentOrderRepository;
import com.java3y.austin.billing.payment.repository.QuotaPackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOrderTransactionServiceTest {

    @Test
    void shouldListTenantOrdersWithoutStatusFilter() {
        PaymentOrderRepository orderRepository = mock(PaymentOrderRepository.class);
        PaymentOrderTransactionService service = new PaymentOrderTransactionService(
                orderRepository, mock(QuotaPackageRepository.class));
        PageRequest pageRequest = PageRequest.of(1, 20);
        Page<PaymentOrder> expected = new PageImpl<>(Collections.emptyList(), pageRequest, 0);
        when(orderRepository.findAllByTenantIdOrderByCreatedDescIdDesc(7L, pageRequest))
                .thenReturn(expected);

        Page<PaymentOrder> actual = service.listTenantOrders(7L, null, 2, 20);

        assertSame(expected, actual);
        verify(orderRepository).findAllByTenantIdOrderByCreatedDescIdDesc(7L, pageRequest);
    }

    @Test
    void shouldListTenantOrdersByStatus() {
        PaymentOrderRepository orderRepository = mock(PaymentOrderRepository.class);
        PaymentOrderTransactionService service = new PaymentOrderTransactionService(
                orderRepository, mock(QuotaPackageRepository.class));
        PageRequest pageRequest = PageRequest.of(0, 20);
        Page<PaymentOrder> expected = new PageImpl<>(Collections.emptyList(), pageRequest, 0);
        when(orderRepository.findAllByTenantIdAndStatusOrderByCreatedDescIdDesc(
                7L, PaymentOrderStatus.SUCCEEDED, pageRequest)).thenReturn(expected);

        Page<PaymentOrder> actual = service.listTenantOrders(
                7L, PaymentOrderStatus.SUCCEEDED, 1, 20);

        assertSame(expected, actual);
        verify(orderRepository).findAllByTenantIdAndStatusOrderByCreatedDescIdDesc(
                7L, PaymentOrderStatus.SUCCEEDED, pageRequest);
    }

    @Test
    void shouldRejectInvalidPagination() {
        PaymentOrderTransactionService service = new PaymentOrderTransactionService(
                mock(PaymentOrderRepository.class), mock(QuotaPackageRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.listTenantOrders(7L, null, 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> service.listTenantOrders(7L, null, 1, 101));
    }
}
