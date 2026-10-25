package com.inventario;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:seedtest;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000}",
      "spring.datasource.username=${TEST_DB_USER:sa}",
      "spring.datasource.password=${TEST_DB_PASSWORD:}",
      "app.admin.email=",
      "app.admin.password=",
      "app.seed.enabled=false"
    })
@ActiveProfiles("dev")
class DemoSeedIntegrationTest {
  @Autowired DemoSeedService seed;
  @Autowired JdbcTemplate jdbc;
  @Autowired ConfigurableEnvironment environment;
  @Autowired Users users;
  @Autowired Categories categories;
  @Autowired Suppliers suppliers;
  @Autowired Products products;
  @Autowired Sales sales;
  @Autowired PasswordEncoder encoder;
  @MockitoSpyBean InventoryService inventory;
  Map<String, Object> options;
  String password;

  @BeforeEach
  void clean() {
    for (String table :
        List.of("sale_items", "sales", "products", "categories", "suppliers", "app_users"))
      jdbc.update("DELETE FROM " + table);
    jdbc.update("UPDATE demo_seed_control SET completed=FALSE,completed_at=NULL WHERE id=1");
    environment.setActiveProfiles("dev");
    password = UUID.randomUUID().toString();
    options = new HashMap<>(Map.of("app.seed.enabled", true, "NEXO_SEED_PASSWORD", password));
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("demo-test-overrides", options));
  }

  @AfterEach
  void resetEnvironment() {
    environment.getPropertySources().remove("demo-test-overrides");
    environment.setActiveProfiles("dev");
  }

  List<String> fingerprint() {
    var snapshot = new ArrayList<String>();
    for (String table :
        List.of("app_users", "categories", "suppliers", "products", "sales", "sale_items"))
      snapshot.add(jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id").toString());
    return snapshot;
  }

  @Test
  void coherentDatasetPreservesExistingBusinessData() {
    Category c = new Category();
    c.name = "Categoría real";
    categories.save(c);
    Supplier s = new Supplier();
    s.name = "Proveedor real";
    suppliers.save(s);
    AppUser u = new AppUser();
    u.email = "real@example.test";
    u.name = "Usuario real";
    u.password = encoder.encode(UUID.randomUUID().toString());
    users.save(u);
    Product p = new Product();
    p.sku = "REAL-1";
    p.name = "Producto real";
    p.price = new BigDecimal("1200.50");
    p.stock = 20;
    p.category = c;
    p.supplier = s;
    p.currency = "COP";
    products.save(p);
    Sale existing = inventory.sell(List.of(new ApiController.Line(p.id, 2)), u.email);
    var originalProduct = jdbc.queryForMap("SELECT * FROM products WHERE id=?", p.id);
    var originalSale = jdbc.queryForMap("SELECT * FROM sales WHERE id=?", existing.id);
    assertThat(seed.load()).isTrue();
    assertThat(categories.count()).isEqualTo(13);
    assertThat(suppliers.count()).isEqualTo(21);
    assertThat(products.count()).isEqualTo(151);
    assertThat(users.count()).isEqualTo(6);
    assertThat(sales.count()).isEqualTo(301);
    assertThat(jdbc.queryForMap("SELECT * FROM products WHERE id=?", p.id))
        .isEqualTo(originalProduct);
    assertThat(jdbc.queryForMap("SELECT * FROM sales WHERE id=?", existing.id))
        .isEqualTo(originalSale);
    var demo =
        products.findAll().stream()
            .filter(x -> x.sku.startsWith(DemoSeedService.SKU_PREFIX))
            .toList();
    assertThat(demo).hasSize(150);
    assertThat(demo.stream().map(x -> x.sku).distinct().count()).isEqualTo(150);
    for (Product product : demo) {
      assertThat(product.currency).isEqualTo("PEN");
      assertThat(product.cost).isPositive().isLessThan(product.price);
      assertThat(product.category).isNotNull();
      assertThat(product.supplier).isNotNull();
      int index = Integer.parseInt(product.sku.substring(DemoSeedService.SKU_PREFIX.length())) - 1;
      int sold =
          jdbc.queryForObject(
              "SELECT COALESCE(SUM(quantity),0) FROM sale_items WHERE product_id=?",
              Integer.class,
              product.id);
      assertThat(product.stock)
          .isEqualTo(DemoSeedService.openingStock(index) - sold)
          .isNotNegative();
    }
    var historical = sales.findAll().stream().filter(x -> x.currency.equals("PEN")).toList();
    Instant now = Instant.now();
    Instant from = ZonedDateTime.now(DemoSeedService.ZONE).minusMonths(6).toInstant();
    Set<java.time.YearMonth> months = new HashSet<>();
    for (Sale sale : historical) {
      assertThat(sale.createdAt).isAfter(from).isBefore(now);
      months.add(YearMonth.from(sale.createdAt.atZone(DemoSeedService.ZONE)));
      BigDecimal sum =
          sale.items.stream()
              .map(i -> i.unitPrice.multiply(BigDecimal.valueOf(i.quantity)))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      assertThat(sale.total).isEqualByComparingTo(sum);
      assertThat(sale.items).isNotEmpty();
      for (SaleItem item : sale.items) {
        assertThat(item.quantity).isPositive();
        assertThat(item.unitPrice).isEqualByComparingTo(item.product.price);
        assertThat(item.product.currency).isEqualTo(sale.currency);
      }
    }
    assertThat(months.size()).isGreaterThanOrEqualTo(6);
    var demoUsers =
        users.findAll().stream().filter(x -> x.email.endsWith("@usuarios.nexo.test")).toList();
    assertThat(demoUsers.stream().filter(x -> x.role.equals("ADMIN")).count()).isEqualTo(1);
    assertThat(demoUsers.stream().filter(x -> x.role.equals("USER")).count()).isEqualTo(4);
    for (AppUser user : demoUsers) {
      assertThat(user.password).isNotEqualTo(password);
      assertThat(encoder.matches(password, user.password)).isTrue();
    }
    var after = fingerprint();
    assertThat(seed.load()).isFalse();
    assertThat(fingerprint()).isEqualTo(after);
  }

  @Test
  void completedSeedNeverRepairsOrOverwritesLaterEdits() {
    seed.load();
    jdbc.update("UPDATE products SET stock=7 WHERE sku=?", DemoSeedService.SKU_PREFIX + "0001");
    var before = fingerprint();
    assertThat(seed.load()).isFalse();
    assertThat(fingerprint()).isEqualTo(before);
  }

  @Test
  void conflictingReservedSkuAbortsWithoutTouchingExistingRows() {
    Category c = new Category();
    c.name = "Existente";
    categories.save(c);
    Supplier s = new Supplier();
    s.name = "Existente";
    suppliers.save(s);
    Product p = new Product();
    p.sku = DemoSeedService.SKU_PREFIX + "0001";
    p.name = "Existente";
    p.price = BigDecimal.TEN;
    p.stock = 5;
    p.category = c;
    p.supplier = s;
    products.save(p);
    var before = fingerprint();
    assertThatThrownBy(seed::load).hasMessageContaining("colisión");
    assertThat(fingerprint()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT completed FROM demo_seed_control WHERE id=1", Boolean.class))
        .isFalse();
  }

  @Test
  void midwayFailureRollsBackAllBusinessRowsAndMarker() {
    var count = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (count.incrementAndGet() == 4)
                throw new IllegalStateException("Simulated failure");
              return invocation.callRealMethod();
            })
        .when(inventory)
        .sell(anyList(), anyString());
    var before = fingerprint();
    assertThatThrownBy(seed::load).hasMessageContaining("Simulated failure");
    assertThat(fingerprint()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT completed FROM demo_seed_control WHERE id=1", Boolean.class))
        .isFalse();
  }

  @Test
  void forbiddenProfilesAndDisabledFlagWriteNothing() {
    var before = fingerprint();
    for (String[] profiles :
        new String[][] {{"prod"}, {"dev", "prod"}, {"dev", "production"}, {}}) {
      environment.setActiveProfiles(profiles);
      assertThatThrownBy(seed::load).hasMessageContaining("bloqueado");
    }
    environment.setActiveProfiles("dev");
    options.put("app.seed.enabled", false);
    assertThatThrownBy(seed::load).hasMessageContaining("bloqueado");
    assertThat(fingerprint()).isEqualTo(before);
  }

  @Test
  void missingPasswordCannotCreateAnyData() {
    options.put("NEXO_SEED_PASSWORD", "");
    assertThatThrownBy(seed::load).hasMessageContaining("NEXO_SEED_PASSWORD");
    assertThat(products.count()).isZero();
    assertThat(users.count()).isZero();
  }

  @Test
  void concurrentSeedInvocationsCreateExactlyOneDataset() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      CountDownLatch start = new CountDownLatch(1);
      Callable<Boolean> job =
          () -> {
            start.await();
            return seed.load();
          };
      var a = executor.submit(job);
      var b = executor.submit(job);
      start.countDown();
      assertThat(List.of(a.get(90, TimeUnit.SECONDS), b.get(90, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(products.count()).isEqualTo(150);
    assertThat(sales.count()).isEqualTo(300);
    assertThat(categories.count()).isEqualTo(12);
    assertThat(suppliers.count()).isEqualTo(20);
    assertThat(users.count()).isEqualTo(5);
  }

  @Test
  void normalSaleRejectsMixedCurrenciesWithoutChangingStock() {
    seed.load();
    var demo = products.findAll();
    Product a = demo.get(0), b = demo.get(1);
    jdbc.update("UPDATE products SET currency='COP' WHERE id=?", b.id);
    int stock = a.stock;
    assertThatThrownBy(
            () ->
                inventory.sell(
                    List.of(new ApiController.Line(a.id, 1), new ApiController.Line(b.id, 1)),
                    "demo.pe.1@usuarios.nexo.test"))
        .hasMessageContaining("mezclar monedas");
    assertThat(products.findById(a.id).orElseThrow().stock).isEqualTo(stock);
    assertThat(sales.count()).isEqualTo(300);
  }
}
