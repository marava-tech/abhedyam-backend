package com.abhedyam.service;

import com.abhedyam.config.StockTrackingMigration;
import com.abhedyam.dto.ItemListResponse;
import com.abhedyam.dto.ItemRequest;
import com.abhedyam.dto.ItemResponse;
import com.abhedyam.dto.StockChangeRequest;
import com.abhedyam.dto.StockMovementResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Inventory;
import com.abhedyam.model.Product;
import com.abhedyam.model.enums.StockMovementType;
import com.abhedyam.model.enums.StockRemovalReason;
import com.abhedyam.model.enums.StockUnit;
import com.abhedyam.repository.InventoryRepository;
import com.abhedyam.repository.ProductRepository;
import com.abhedyam.repository.StockMovementRepository;
import com.abhedyam.security.UserPrincipal;
import com.abhedyam.service.interfaces.IAuditService;
import com.abhedyam.service.interfaces.IItemService;
import com.abhedyam.service.interfaces.IProductService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Items + stock movements (specs/inventory-items.md), against real JPA and real transaction proxies. */
@DataJpaTest
@AutoConfigureTestDatabase
@Import({StockService.class, ItemService.class, InventoryService.class, ObjectMapper.class, com.abhedyam.config.JpaConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
class InventoryItemsTest {

    @Autowired IItemService items;
    @Autowired IStockService stock;
    @Autowired ProductRepository products;
    @Autowired InventoryRepository inventories;
    @Autowired StockMovementRepository movements;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;
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

    private ItemResponse item(String name, String opening, String low, String cost) {
        ItemRequest r = new ItemRequest();
        r.setName(name);
        r.setPrice(new BigDecimal("100"));
        if (opening != null) r.setOpeningStock(new BigDecimal(opening));
        if (low != null) r.setLowStockLevel(new BigDecimal(low));
        if (cost != null) r.setCostPrice(new BigDecimal(cost));
        return items.create(owner, r);
    }

    private ItemResponse change(UUID id, StockChangeRequest.Type type, String qty, StockRemovalReason reason) {
        StockChangeRequest r = new StockChangeRequest();
        r.setType(type);
        r.setQuantity(new BigDecimal(qty));
        r.setReason(reason);
        return items.changeStock(owner, id, r);
    }

    private List<StockMovementResponse> history(UUID id) {
        return items.movements(owner, id, 0, 50).getContent();
    }

    @Test
    void openingStockTracksAndWritesOpeningMovement() {
        ItemResponse rice = item("Rice", "25.5", "5", null);
        assertTrue(rice.isTrackStock());
        assertEquals(0, new BigDecimal("25.5").compareTo(rice.getStock()));
        assertEquals(ItemResponse.Status.OK, rice.getStatus());
        assertEquals(StockUnit.PCS, rice.getUnit());
        assertTrue(products.findById(rice.getId()).get().getCode().startsWith("ITEM-"));
        List<StockMovementResponse> h = history(rice.getId());
        assertEquals(1, h.size());
        assertEquals(StockMovementType.OPENING, h.get(0).getType());
    }

    @Test
    void itemWithoutStockIsUntracked() {
        ItemResponse pen = item("Pen", null, null, null);
        assertFalse(pen.isTrackStock());
        assertNull(pen.getStock());
        assertEquals(ItemResponse.Status.UNTRACKED, pen.getStatus());
    }

    @Test
    void receivedRemovedAndCountAreMovementsNotOverwrites() {
        UUID id = item("Oil", "10", null, null).getId();
        assertEquals(0, new BigDecimal("14").compareTo(change(id, StockChangeRequest.Type.RECEIVED, "4", null).getStock()));
        assertEquals(0, new BigDecimal("11.75").compareTo(change(id, StockChangeRequest.Type.REMOVED, "2.25", StockRemovalReason.DAMAGED).getStock()));
        assertEquals(0, new BigDecimal("9").compareTo(change(id, StockChangeRequest.Type.COUNT, "9", null).getStock()));
        change(id, StockChangeRequest.Type.COUNT, "9", null);

        List<StockMovementResponse> h = history(id);
        assertEquals(4, h.size(), "an unchanged count writes nothing");
        StockMovementResponse removed = h.stream().filter(m -> m.getType() == StockMovementType.REMOVED).findFirst().orElseThrow();
        assertEquals(StockRemovalReason.DAMAGED, removed.getReason());
        assertEquals(0, new BigDecimal("-2.25").compareTo(removed.getQuantity()));
        StockMovementResponse count = h.stream().filter(m -> m.getType() == StockMovementType.COUNT).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("-2.75").compareTo(count.getQuantity()));
        assertEquals(0, new BigDecimal("9").compareTo(inventories.findByOwnerIdAndProductId(owner, id).get().getStock()));
    }

    @Test
    void changingStockOnUntrackedItemTurnsTrackingOn() {
        UUID id = item("Soap", null, null, null).getId();
        ItemResponse after = change(id, StockChangeRequest.Type.RECEIVED, "12", null);
        assertTrue(after.isTrackStock());
        assertEquals(0, new BigDecimal("12").compareTo(after.getStock()));
    }

    @Test
    void salesOfUntrackedItemsWriteNothing() {
        UUID id = item("Pen", null, null, null).getId();
        stock.saleOut(owner, id, new BigDecimal("3"), "T1");
        assertFalse(movements.existsByOwnerIdAndProductId(owner, id));
    }

    @Test
    void trackedSaleCanGoNegativeAndCancelRestores() {
        UUID id = item("Sugar", "2", null, null).getId();
        stock.saleOut(owner, id, new BigDecimal("5"), "T1");
        ItemResponse sold = items.list(owner, IItemService.Filter.ALL, null, 0, 10).getItems().getContent().get(0);
        assertEquals(0, new BigDecimal("-3").compareTo(sold.getStock()), "no silent bump to 0 any more");
        assertEquals(ItemResponse.Status.OUT, sold.getStatus());

        stock.saleCancelled(owner, id, new BigDecimal("5"), "T1");
        assertEquals(0, new BigDecimal("2").compareTo(stock.currentStock(owner, id)));
        List<StockMovementResponse> h = history(id);
        assertTrue(h.stream().anyMatch(m -> m.getType() == StockMovementType.SALE && "T1".equals(m.getSaleTransactionId())));
        assertTrue(h.stream().anyMatch(m -> m.getType() == StockMovementType.SALE_CANCELLED));
    }

    @Test
    void aStockProblemNeverRollsBackTheSale() {
        UUID mine = item("Tea", "5", null, null).getId();
        UUID other = UUID.randomUUID();
        as(other);
        ItemRequest r = new ItemRequest();
        r.setName("Theirs");
        r.setPrice(BigDecimal.ONE);
        r.setOpeningStock(BigDecimal.TEN);
        UUID theirs = items.create(other, r).getId();
        as(owner);

        TransactionTemplate sale = new TransactionTemplate(txManager);
        assertDoesNotThrow(() -> sale.executeWithoutResult(s -> {
            Product marker = new Product();
            marker.setOwnerId(owner); marker.setCode("SALE-MARKER"); marker.setName("marker"); marker.setPrice(BigDecimal.ONE);
            products.save(marker);
            stock.saleOut(owner, UUID.randomUUID(), BigDecimal.ONE, "T9");
            stock.saleOut(owner, theirs, BigDecimal.ONE, "T9");
            stock.saleOut(owner, mine, BigDecimal.ONE, "T9");
        }));
        assertTrue(products.findByOwnerIdAndCode(owner, "SALE-MARKER").isPresent(), "the sale committed");
        assertEquals(0, new BigDecimal("4").compareTo(stock.currentStock(owner, mine)));
        assertEquals(0, BigDecimal.TEN.compareTo(stock.currentStock(other, theirs)), "another owner's stock untouched");
    }

    @Test
    void listOrdersOutThenLowAndSummarisesValue() {
        item("Zinc", "10", "2", "3");
        UUID low = item("Bread", "2", "5", "20").getId();
        UUID out = item("Milk", "0", null, "25").getId();
        item("Apple", "4", null, null);
        item("Pen", null, null, null);

        ItemListResponse all = items.list(owner, IItemService.Filter.ALL, null, 0, 10);
        assertEquals(List.of("Milk", "Bread", "Apple", "Pen", "Zinc"),
                all.getItems().getContent().stream().map(ItemResponse::getName).toList());
        ItemListResponse.Summary s = all.getSummary();
        assertEquals(5, s.getTotal());
        assertEquals(4, s.getTracked());
        assertEquals(1, s.getLow());
        assertEquals(1, s.getOut());
        assertEquals(0, new BigDecimal("70.00").compareTo(s.getStockValue()), "10×3 + 2×20; out-of-stock and no-cost items excluded");
        assertEquals(1, s.getMissingCostPrice());

        assertEquals(List.of(low), items.list(owner, IItemService.Filter.LOW, null, 0, 10).getItems().getContent().stream().map(ItemResponse::getId).toList());
        assertEquals(List.of(out), items.list(owner, IItemService.Filter.OUT, null, 0, 10).getItems().getContent().stream().map(ItemResponse::getId).toList());
        assertEquals(1, items.list(owner, IItemService.Filter.ALL, "brE", 0, 10).getItems().getTotalElements());
        var page = items.list(owner, IItemService.Filter.ALL, null, 1, 2).getItems();
        assertEquals(2, page.getContent().size());
        assertTrue(page.isHasNext());
    }

    @Test
    void namesAreUniqueAmongActiveItemsAndDeleteHidesTheItem() {
        UUID id = item("Rice", null, null, null).getId();
        BusinessException dup = assertThrows(BusinessException.class, () -> item(" rice ", null, null, null));
        assertEquals("ITEM_EXISTS", dup.getErrorCode());
        items.archive(owner, id);
        assertEquals(0, items.list(owner, IItemService.Filter.ALL, null, 0, 10).getSummary().getTotal());
        assertDoesNotThrow(() -> item("Rice", null, null, null));
        assertThrows(ResourceNotFoundException.class, () -> items.movements(owner, id, 0, 10));
    }

    @Test
    void editChangesOnlyFieldsSentAndZeroClears() {
        UUID id = item("Dal", "5", "2", "80").getId();
        ItemRequest r = new ItemRequest();
        r.setUnit(StockUnit.KG);
        r.setLowStockLevel(BigDecimal.ZERO);
        ItemResponse after = items.update(owner, id, r);
        assertEquals("Dal", after.getName());
        assertEquals(StockUnit.KG, after.getUnit());
        assertNull(after.getLowStockLevel());
        assertEquals(0, new BigDecimal("80").compareTo(after.getCostPrice()));
    }

    @Test
    void ownersCannotTouchEachOthersItems() {
        UUID id = item("Salt", "3", null, null).getId();
        UUID stranger = UUID.randomUUID();
        as(stranger);
        assertThrows(BusinessException.class, () -> items.list(owner, IItemService.Filter.ALL, null, 0, 10));
        assertThrows(ResourceNotFoundException.class, () -> items.movements(stranger, id, 0, 10));
    }

    @Test
    void migrationTracksOnlyProductsThatHaveStock() {
        Product withStock = legacy("Old with stock");
        Product empty = legacy("Old empty");
        Inventory inv = new Inventory();
        inv.setOwnerId(owner); inv.setProductId(withStock.getId()); inv.setStock(new BigDecimal("7"));
        inventories.save(inv);

        jdbc.update(StockTrackingMigration.SQL);
        assertEquals(Boolean.TRUE, products.findById(withStock.getId()).get().getTrackStock());
        assertEquals(Boolean.FALSE, products.findById(empty.getId()).get().getTrackStock());
    }

    private Product legacy(String name) {
        Product p = new Product();
        p.setOwnerId(owner); p.setCode(name); p.setName(name); p.setPrice(BigDecimal.ONE);
        return products.save(p);
    }
}
