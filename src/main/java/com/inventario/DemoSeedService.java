package com.inventario;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, atomic, development-only demo dataset. Never reuses existing business records. */
@Service
public class DemoSeedService {
  static final String SKU_PREFIX = "NEXO-DEMO-PE-";

  static int openingStock(int index) {
    return 40 + (index % 11) * 10;
  }

  static final ZoneId ZONE = ZoneId.of("America/Lima");
  private final Environment environment;
  private final JdbcTemplate jdbc;
  private final Users users;
  private final Categories categories;
  private final Suppliers suppliers;
  private final Products products;
  private final Sales sales;
  private final InventoryService inventory;
  private final PasswordEncoder encoder;

  DemoSeedService(
      Environment environment,
      JdbcTemplate jdbc,
      Users users,
      Categories categories,
      Suppliers suppliers,
      Products products,
      Sales sales,
      InventoryService inventory,
      PasswordEncoder encoder) {
    this.environment = environment;
    this.jdbc = jdbc;
    this.users = users;
    this.categories = categories;
    this.suppliers = suppliers;
    this.products = products;
    this.sales = sales;
    this.inventory = inventory;
    this.encoder = encoder;
  }

  @Transactional
  public boolean load() {
    Set<String> profiles = Set.of(environment.getActiveProfiles());
    if (!profiles.contains("dev")
        || profiles.contains("prod")
        || profiles.contains("production")
        || !environment.getProperty("app.seed.enabled", Boolean.class, false))
      throw new IllegalStateException(
          "Seed bloqueado: requiere perfil dev, sin prod/production, y app.seed.enabled=true");
    // The row lock serializes simultaneous invocations; completion is committed with ALL demo data.
    Boolean completed =
        jdbc.queryForObject(
            "SELECT completed FROM demo_seed_control WHERE id=1 FOR UPDATE", Boolean.class);
    if (Boolean.TRUE.equals(completed)) return false;
    String password = environment.getProperty("NEXO_SEED_PASSWORD", "");
    if (password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException(
          "Define NEXO_SEED_PASSWORD (12 caracteres mínimo, 72 bytes UTF-8 máximo); nunca se"
              + " registra en logs");
    ensureNamespaceAvailable();
    var demoCategories = new ArrayList<Category>();
    for (String name : CATEGORY_NAMES) {
      Category c = new Category();
      c.name = name + " [DEMO PE]";
      c.description = "Catálogo ficticio para comercio peruano. Importes en soles.";
      demoCategories.add(categories.save(c));
    }
    var demoSuppliers = new ArrayList<Supplier>();
    for (int n = 0; n < SUPPLIER_NAMES.length; n++) {
      Supplier s = new Supplier();
      s.name = SUPPLIER_NAMES[n] + " [DEMO PE]";
      s.email = String.format(Locale.ROOT, "proveedor%02d@proveedores.nexo.test", n + 1);
      s.phone = "DEMO - sin contacto real";
      demoSuppliers.add(suppliers.save(s));
    }
    String[] people = {
      "Valeria Quispe Huamán",
      "Diego Flores Paredes",
      "Camila Rojas Salazar",
      "Mateo Torres Vargas",
      "Lucía Mendoza Ramos"
    };
    var demoUsers = new ArrayList<AppUser>();
    for (int n = 0; n < people.length; n++) {
      AppUser u = new AppUser();
      u.name = people[n] + " [DEMO PE]";
      u.email = "demo.pe." + (n + 1) + "@usuarios.nexo.test";
      u.password = encoder.encode(password);
      u.role = n == 0 ? "ADMIN" : "USER";
      demoUsers.add(users.save(u));
    }
    var demoProducts = new ArrayList<Product>();
    for (int n = 0; n < 150; n++) {
      int group = n % 12, variant = n / 12;
      Product p = new Product();
      p.sku = SKU_PREFIX + String.format(Locale.ROOT, "%04d", n + 1);
      p.name =
          PRODUCT_NAMES[group][variant % PRODUCT_NAMES[group].length]
              + " · "
              + BRANDS[variant / PRODUCT_NAMES[group].length];
      p.price =
          new BigDecimal(PRODUCT_PRICES[group][variant % 5])
              .add(BigDecimal.valueOf((variant / 5) * 35L, 2));
      p.cost = p.price.multiply(new BigDecimal("0.72")).setScale(2, java.math.RoundingMode.HALF_UP);
      p.currency = "PEN";
      p.stock = openingStock(n);
      p.minimumStock = 35;
      p.category = demoCategories.get(group);
      p.supplier = demoSuppliers.get(n % 20);
      demoProducts.add(products.save(p));
    }
    // Offset dates across exactly six calendar months, in local trading hours, strictly before now.
    ZonedDateTime now = ZonedDateTime.now(ZONE);
    LocalDate start = now.toLocalDate().minusMonths(6).plusDays(1);
    long days = java.time.temporal.ChronoUnit.DAYS.between(start, now.toLocalDate());
    int[] sold = new int[150];
    for (int n = 0; n < 300; n++) {
      var lines = new ArrayList<ApiController.Line>();
      for (int j = 0; j < 1 + n % 4; j++) {
        int index = (n * 17 + j * 31) % 150;
        int quantity = 1 + (n + j) % 3;
        lines.add(new ApiController.Line(demoProducts.get(index).id, quantity));
        sold[index] += quantity;
      }
      Sale sale = inventory.sell(lines, demoUsers.get(n % 5).email);
      sale.createdAt =
          start.plusDays(n * days / 300).atTime(9 + n % 10, (n * 7) % 60).atZone(ZONE).toInstant();
      sales.save(sale);
    }
    products.flush();
    sales.flush();
    for (int n = 0; n < 150; n++) {
      Product p = products.findById(demoProducts.get(n).id).orElseThrow();
      if (p.stock != openingStock(n) - sold[n] || p.stock < 0)
        throw new IllegalStateException("Stock demo inconsistente");
    }
    jdbc.update(
        "UPDATE demo_seed_control SET completed=TRUE, completed_at=? WHERE id=1",
        java.sql.Timestamp.from(Instant.now()));
    return true;
  }

  private void ensureNamespaceAvailable() {
    // Fail rather than modifying/reusing records with reserved names; a failure rolls back
    // everything.
    if (jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE sku LIKE 'NEXO-DEMO-PE-%'", Long.class)
            > 0
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE email LIKE '%@usuarios.nexo.test'",
                Long.class)
            > 0
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM suppliers WHERE email LIKE '%@proveedores.nexo.test' OR name"
                    + " LIKE '%[DEMO PE]%'",
                Long.class)
            > 0
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE name LIKE '%[DEMO PE]%'", Long.class)
            > 0)
      throw new IllegalStateException(
          "Existe una colisión con el espacio reservado DEMO PE; no se modificó ningún registro");
  }

  static final String[] CATEGORY_NAMES = {
    "Abarrotes",
    "Bebidas",
    "Lácteos",
    "Conservas",
    "Limpieza",
    "Cuidado personal",
    "Papelería",
    "Ferretería",
    "Mascotas",
    "Snacks",
    "Menaje",
    "Tecnología"
  };
  static final String[] SUPPLIER_NAMES = {
    "Distribuidora Costa Limeña",
    "Comercial Andina del Sur",
    "Abastos Valle del Mantaro",
    "Mayorista Norte Trujillano",
    "Comercial Sol de Arequipa",
    "Distribuciones Misti",
    "Almacenes Puerto Chalaco",
    "Comercial Santa Rosa del Cusco",
    "Proveedora Selva Central",
    "Distribuidora La Huerta",
    "Importadora Pacífico Azul",
    "Suministros Wari",
    "Comercial Valle Sagrado",
    "Distribuidora Chiclayo Norte",
    "Abastos Huancayo Centro",
    "Comercial Bahía de Pisco",
    "Proveedores Sierra Dorada",
    "Distribuciones Tacna Sur",
    "Comercial Piura del Sol",
    "Almacenes Cajamarca Verde"
  };
  static final String[] BRANDS = {
    "Marca Demo Valle", "Marca Demo Andina", "Marca Demo Pacífico", "Marca Demo Sierra"
  };
  static final String[][] PRODUCT_PRICES = {
    {"4.90", "4.20", "9.90", "3.20", "5.80"},
    {"1.80", "6.50", "5.50", "3.00", "2.50"},
    {"4.60", "5.80", "8.90", "9.50", "5.20"},
    {"6.90", "5.50", "12.90", "4.20", "3.50"},
    {"9.90", "6.50", "4.00", "7.90", "3.50"},
    {"14.90", "6.90", "7.50", "4.50", "6.90"},
    {"7.50", "3.50", "8.90", "5.90", "2.50"},
    {"3.90", "8.50", "9.90", "14.90", "6.50"},
    {"12.90", "10.90", "11.50", "5.90", "8.90"},
    {"4.90", "6.50", "5.90", "4.50", "3.90"},
    {"8.90", "19.90", "6.50", "7.90", "12.90"},
    {"12.90", "24.90", "19.90", "39.90", "29.90"}
  };
  static final String[][] PRODUCT_NAMES = {
    {
      "Arroz extra bolsa 1 kg",
      "Azúcar rubia bolsa 1 kg",
      "Aceite vegetal botella 1 L",
      "Fideos tallarín 500 g",
      "Lenteja bolsa 500 g"
    },
    {
      "Agua sin gas 625 ml",
      "Gaseosa sabor cola 1.5 L",
      "Néctar de durazno 1 L",
      "Bebida de cebada 500 ml",
      "Agua con gas 500 ml"
    },
    {
      "Leche evaporada lata 400 g",
      "Leche UHT caja 1 L",
      "Yogur de fresa 1 L",
      "Queso fresco 250 g",
      "Mantequilla 100 g"
    },
    {
      "Atún en trozos lata 170 g",
      "Sardinas lata 155 g",
      "Duraznos en almíbar 820 g",
      "Arvejas en conserva 300 g",
      "Pasta de tomate 160 g"
    },
    {
      "Detergente en polvo 800 g",
      "Lavavajillas líquido 500 ml",
      "Lejía botella 1 L",
      "Limpiador multiuso 900 ml",
      "Esponja de cocina pack 3"
    },
    {
      "Champú familiar 400 ml",
      "Jabón de tocador pack 3",
      "Pasta dental 90 g",
      "Cepillo dental mediano",
      "Papel higiénico pack 4"
    },
    {
      "Cuaderno A4 cuadriculado 100 hojas",
      "Lapiceros azules pack 3",
      "Resaltadores pack 4",
      "Papel bond A4 100 hojas",
      "Cinta adhesiva 18 mm"
    },
    {
      "Cinta aislante 18 m",
      "Foco LED 9 W",
      "Destornillador plano 6 pulgadas",
      "Candado de latón 30 mm",
      "Brocha de pintura 2 pulgadas"
    },
    {
      "Alimento perro adulto 1 kg",
      "Alimento gato adulto 500 g",
      "Arena para gato 2 kg",
      "Snack canino 100 g",
      "Plato para mascota mediano"
    },
    {
      "Galletas de vainilla pack 6",
      "Papas fritas bolsa 150 g",
      "Maní tostado 200 g",
      "Chocolate de leche 80 g",
      "Maíz cancha tostado 150 g"
    },
    {
      "Táper rectangular 1 L",
      "Vasos de vidrio pack 6",
      "Espátula de cocina",
      "Paños de cocina pack 3",
      "Botella reutilizable 750 ml"
    },
    {
      "Cable USB-C 1 m",
      "Mouse óptico USB",
      "Audífonos alámbricos",
      "Cargador USB 20 W",
      "Memoria USB 32 GB"
    }
  };
}
