package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.SaleItemRequest;
import com.abhedyam.model.Product;
import com.abhedyam.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A "just an amount" sale line reuses its product but keeps it hidden from Stock (inactive), even one made visible before. */
@ExtendWith(MockitoExtension.class)
class AmountOnlySaleTest {

    @Mock ProductRepository productRepository;
    @InjectMocks SaleService saleService;

    UUID owner = UUID.randomUUID();

    @Test
    void existingVisibleUdharProduct_IsReusedAndHidden() {
        Product udhar = new Product();
        udhar.setId(UUID.randomUUID());
        udhar.setName("Udhar");
        udhar.setIsActive(true);
        when(productRepository.findByOwnerIdAndName(owner, "Udhar")).thenReturn(List.of(udhar));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        SaleItemRequest line = new SaleItemRequest();
        line.setProductName(" Udhar ");
        line.setPrice(new BigDecimal("500"));
        line.setAmountOnly(true);

        Product used = saleService.amountOnlyProduct(line, owner);

        assertSame(udhar, used);
        assertFalse(used.getIsActive());
    }

    @Test
    void alreadyHidden_IsNotSavedAgain() {
        Product udhar = new Product();
        udhar.setIsActive(false);
        when(productRepository.findByOwnerIdAndName(owner, "Udhar")).thenReturn(List.of(udhar));

        SaleItemRequest line = new SaleItemRequest();
        line.setProductName("Udhar");
        line.setAmountOnly(true);

        assertSame(udhar, saleService.amountOnlyProduct(line, owner));
        verify(productRepository, never()).save(any());
    }
}
