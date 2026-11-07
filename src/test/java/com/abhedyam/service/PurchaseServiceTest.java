package com.abhedyam.service;

import com.abhedyam.dto.ItemRequest;
import com.abhedyam.dto.ItemResponse;
import com.abhedyam.dto.PurchaseRequest;
import com.abhedyam.dto.PurchaseResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.model.Product;
import com.abhedyam.model.enums.StockUnit;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.security.UserPrincipal;
import com.abhedyam.service.interfaces.IAuditService;
import com.abhedyam.service.interfaces.IItemService;
import com.abhedyam.service.interfaces.IProductService;
import com.abhedyam.service.interfaces.IPurchaseService;
import com.abhedyam.service.interfaces.IStockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({PurchaseService.class, StockService.class, ItemService.class, InventoryService.class, ObjectMapper.class, com.abhedyam.config.JpaConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
class PurchaseServiceTest {

    @Autowired IPurchaseService purchases;
    @Autowired IItemService items;
    @Autowired IStockService stock;
    @Autowired ProductRepository products;
    @MockBean RedisTemplate<String, String> redis;
    @MockBean IAuditService audit;
    @MockBean IProductService productService;

    UUID owner;

    @BeforeEach
    void signIn() {
        owner = UUID.randomUUID();
        as(owner);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void as(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(id, null), null, List.of()));
    }

    private UUID item(String name, String price, String opening) {
        ItemRequest r = new ItemRequest();
        r.setName(name);
        r.setPrice(new BigDecimal(price));
        if (opening != null) r.setOpeningStock(new BigDecimal(opening));
        return items.create(owner, r).getId();
    }

    private static PurchaseRequest.Line line(UUID itemId, String qty, String cost) {
        PurchaseRequest.Line l = new PurchaseRequest.Line();
        l.setItemId(itemId);
        l.setQuantity(new BigDecimal(qty));
        if (cost != null) l.setUnitCost(new BigDecimal(cost));
        return l;
    }

    private PurchaseResponse buy(String total, String supplier, PurchaseRequest.Line... lines) {
        PurchaseRequest r = new PurchaseRequest();
        r.setTotalPaid(total == null ? null : new BigDecimal(total));
        r.setSupplier(supplier);
        r.setItems(new ArrayList<>(List.of(lines)));
        return purchases.create(owner, r);
    }

    private ItemResponse itemView(UUID id) {
        return items.list(owner, IItemService.Filter.ALL, null, 0, 100).getItems().getContent().stream()
                .filter(i -> i.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void purchaseAddsStockAndFillsBuyingPrices() {
        UUID rice = item("Rice", "60", null), oil = item("Oil", "120", "2");
        PurchaseResponse p = buy("1000", " Ramesh  traders ", line(rice, "10", null), line(oil, "5", null));

        assertEquals("Ramesh traders", p.getSupplier());
        assertEquals(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")), p.getBoughtOn());
        assertEquals(100, p.getLeftPercent());
        assertTrue(p.isCanDelete());
        assertFalse(p.isPaidBack());
        ItemResponse r = itemView(rice);
        assertTrue(r.isTrackStock());
        assertEquals(0, new BigDecimal("10").compareTo(r.getStock()));
        assertEquals(0, new BigDecimal("50").compareTo(r.getCostPrice()));
        assertEquals(0, new BigDecimal("7").compareTo(itemView(oil).getStock()));
        assertEquals(0, new BigDecimal("100").compareTo(itemView(oil).getCostPrice()));
    }

    @Test
    void salesUseOldStockFirstThenThePurchaseUntilItPaysBack() {
        UUID rice = item("Rice", "60", "4");
        UUID id = buy("500", null, line(rice, "10", null)).getId();
        stock.saleOut(owner, rice, new BigDecimal("6"), "T1");

        PurchaseResponse p = purchases.get(owner, id);
        assertEquals(0, new BigDecimal("8").compareTo(p.getItems().get(0).getLeft()), "4 old used first, then 2 of the purchase");
        assertEquals(80, p.getLeftPercent());
        assertFalse(p.isCanDelete());

        stock.saleOut(owner, rice, new BigDecimal("8"), "T2");
        p = purchases.get(owner, id);
        assertTrue(p.isFinished());
        assertTrue(p.isPaidBack(), "10 sold × ₹60 = ₹600 ≥ ₹500 paid");
        assertEquals(0, new BigDecimal("100").compareTo(p.getProfit()));
        assertTrue(purchases.list(owner, IPurchaseService.Status.OPEN, null, 0, 10).getContent().isEmpty());
        assertEquals(1, purchases.list(owner, IPurchaseService.Status.ALL, rice, 0, 10).getContent().size());
    }

    @Test
    void newItemsCanBeAddedInsideThePurchase() {
        PurchaseRequest.Line l = new PurchaseRequest.Line();
        ItemRequest n = new ItemRequest();
        n.setName("Sugar");
        n.setPrice(new BigDecimal("45"));
        n.setUnit(StockUnit.KG);
        l.setNewItem(n);
        l.setQuantity(new BigDecimal("25"));
        PurchaseResponse p = buy("1000", "Gupta", l);
        assertEquals("Sugar", p.getItems().get(0).getName());
        assertEquals(StockUnit.KG, p.getItems().get(0).getUnit());
        assertEquals(0, new BigDecimal("40").compareTo(p.getItems().get(0).getUnitCost()));
    }

    @Test
    void exactPricesAloneGiveTheTotal() {
        UUID a = item("A", "10", null);
        PurchaseResponse p = buy(null, null, line(a, "3", "7"));
        assertEquals(0, new BigDecimal("21").compareTo(p.getTotalPaid()));
    }

    @Test
    void invalidPurchasesAreRefused() {
        UUID a = item("A", "10", null);
        assertThrows(BusinessException.class, () -> buy(null, null, line(a, "3", null)), "total needed");
        assertThrows(BusinessException.class, () -> buy("10", null, line(a, "3", "7")), "given > total");
        assertThrows(BusinessException.class, () -> buy("10", null, line(a, "0", null)), "quantity");
        assertThrows(BusinessException.class, () -> buy("10", null, line(a, "1", null), line(a, "2", null)), "twice");
        assertThrows(BusinessException.class, () -> buy("10", null), "no items");
        PurchaseRequest future = new PurchaseRequest();
        future.setTotalPaid(BigDecimal.TEN);
        future.setBoughtOn(LocalDate.now().plusDays(3));
        future.setItems(List.of(line(a, "1", null)));
        assertThrows(BusinessException.class, () -> purchases.create(owner, future));
    }

    @Test
    void deleteOnlyWhileUntouchedAndItTakesTheStockBack() {
        UUID a = item("A", "10", "1");
        UUID id = buy("50", null, line(a, "5", null)).getId();
        assertEquals(0, new BigDecimal("6").compareTo(itemView(a).getStock()));
        purchases.delete(owner, id);
        assertEquals(0, BigDecimal.ONE.compareTo(itemView(a).getStock()));
        assertTrue(purchases.list(owner, IPurchaseService.Status.ALL, null, 0, 10).getContent().isEmpty());

        UUID sold = buy("50", null, line(a, "5", null)).getId();
        stock.saleOut(owner, a, new BigDecimal("3"), "T");
        BusinessException e = assertThrows(BusinessException.class, () -> purchases.delete(owner, sold));
        assertTrue(e.getMessage().contains("already sold"));
    }

    @Test
    void ownersSeeOnlyTheirOwnPurchases() {
        UUID a = item("A", "10", null);
        UUID id = buy("50", null, line(a, "5", null)).getId();
        UUID other = UUID.randomUUID();
        as(other);
        assertThrows(BusinessException.class, () -> purchases.get(owner, id));
        assertThrows(Exception.class, () -> purchases.get(other, id));
        PurchaseRequest steal = new PurchaseRequest();
        steal.setTotalPaid(BigDecimal.ONE);
        steal.setItems(List.of(line(a, "1", null)));
        assertThrows(Exception.class, () -> purchases.create(other, steal));
        Product p = products.findById(a).orElseThrow();
        assertEquals(owner, p.getOwnerId());
    }
}
