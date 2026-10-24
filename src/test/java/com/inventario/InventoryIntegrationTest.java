package com.inventario;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1}",
      "spring.datasource.username=${TEST_DB_USER:sa}",
      "spring.datasource.password=${TEST_DB_PASSWORD:}",
      "app.admin.email=",
      "app.admin.password="
    })
@AutoConfigureMockMvc
class InventoryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired Users users;
  @Autowired Products products;
  @Autowired Categories categories;
  @Autowired Suppliers suppliers;
  @Autowired Sales sales;
  @Autowired InventoryService service;

  @BeforeEach
  void clean() {
    sales.deleteAll();
    products.deleteAll();
    categories.deleteAll();
    suppliers.deleteAll();
    users.deleteAll();
  }

  Product fixture() {
    AppUser u = new AppUser();
    u.email = "seller@example.com";
    u.name = "Vendedor";
    u.password = "unused";
    users.save(u);
    Category c = new Category();
    c.name = "Tecnología";
    categories.save(c);
    Supplier s = new Supplier();
    s.name = "Proveedor";
    suppliers.save(s);
    Product p = new Product();
    p.name = "Teclado";
    p.sku = "KEY-1";
    p.price = new BigDecimal("25.50");
    p.stock = 10;
    p.minimumStock = 2;
    p.category = c;
    p.supplier = s;
    return products.save(p);
  }

  @Test
  void unauthenticatedAccessAndCsrfAreBlocked() throws Exception {
    mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/register").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void registrationHashesPasswordAndCannotAssignAdmin() throws Exception {
    mvc.perform(
            post("/api/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"name\":\"Ana\",\"email\":\"ANA@example.com\",\"password\":\"secure-password-123\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.password").doesNotExist());
    var u = users.findByEmail("ana@example.com").orElseThrow();
    assertThat(u.password).startsWith("$2a$").isNotEqualTo("secure-password-123");
  }

  @Test
  void invalidRegistrationRejected() throws Exception {
    mvc.perform(
            post("/api/register")
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  void ordinaryUserCannotManageCatalog() throws Exception {
    mvc.perform(
            post("/api/categories")
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Otra\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/users")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void adminCrudAndRelations() throws Exception {
    var p = fixture();
    mvc.perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].category.name").value("Tecnología"));
    mvc.perform(delete("/api/categories/" + p.category.id).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/products/" + p.id)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"sku\":\"KEY-1\",\"name\":\"Nuevo\",\"price\":30,\"stock\":5,\"minimumStock\":1,\"categoryId\":"
                        + p.category.id
                        + ",\"supplierId\":"
                        + p.supplier.id
                        + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(5));
    mvc.perform(delete("/api/products/" + p.id).with(csrf())).andExpect(status().isOk());
  }

  @Test
  void saleCalculatesPriceAndDecrementsStock() {
    var p = fixture();
    var s = service.sell(List.of(new ApiController.Line(p.id, 3)), "seller@example.com");
    assertThat(s.total).isEqualByComparingTo("76.50");
    assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(7);
    assertThat(s.items.getFirst().unitPrice).isEqualByComparingTo("25.50");
  }

  @Test
  void insufficientStockRollsBackWholeSale() {
    var p = fixture();
    Product other = new Product();
    other.name = "Otro";
    other.sku = "OTHER";
    other.price = BigDecimal.ONE;
    other.stock = 1;
    other.category = p.category;
    other.supplier = p.supplier;
    products.save(other);
    assertThatThrownBy(
            () ->
                service.sell(
                    List.of(new ApiController.Line(p.id, 2), new ApiController.Line(other.id, 2)),
                    "seller@example.com"))
        .hasMessageContaining("Stock insuficiente");
    assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(10);
    assertThat(sales.count()).isZero();
  }

  @Test
  void duplicateAndNegativeQuantitiesRejected() {
    var p = fixture();
    assertThatThrownBy(
            () ->
                service.sell(
                    List.of(new ApiController.Line(p.id, 1), new ApiController.Line(p.id, 1)),
                    "seller@example.com"))
        .hasMessageContaining("duplicados");
    assertThatThrownBy(
            () -> service.sell(List.of(new ApiController.Line(p.id, -1)), "seller@example.com"))
        .hasMessageContaining("inválida");
  }

  @Test
  @WithMockUser(username = "seller@example.com")
  void saleApiAndHistoricalProductProtection() throws Exception {
    var p = fixture();
    mvc.perform(
            post("/api/sales")
                .with(csrf())
                .contentType("application/json")
                .content("{\"items\":[{\"productId\":" + p.id + ",\"quantity\":2}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(51.0))
        .andExpect(jsonPath("$.user.password").doesNotExist());
    mvc.perform(get("/api/sales"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].items[0].productName").value("Teclado"));
  }

  @Test
  void concurrentSalesCannotOversell() throws Exception {
    var p = fixture();
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Boolean> job =
          () -> {
            start.await();
            try {
              service.sell(List.of(new ApiController.Line(p.id, 7)), "seller@example.com");
              return true;
            } catch (org.springframework.web.server.ResponseStatusException e) {
              return false;
            }
          };
      var first = executor.submit(job);
      var second = executor.submit(job);
      start.countDown();
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
      assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(3);
      assertThat(sales.count()).isEqualTo(1);
    }
  }
}
