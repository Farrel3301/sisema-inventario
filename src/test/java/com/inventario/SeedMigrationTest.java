package com.inventario;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class SeedMigrationTest {
  @Test
  void migrationPreservesLegacyAmountsAndDoesNotSeedBusinessData() throws Exception {
    String url = "jdbc:h2:mem:legacy_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
    try (Connection c = DriverManager.getConnection(url, "sa", "");
        Statement s = c.createStatement()) {
      s.execute("INSERT INTO categories(id,name) VALUES(1,'Existente')");
      s.execute("INSERT INTO suppliers(id,name) VALUES(1,'Existente')");
      s.execute(
          "INSERT INTO app_users(id,email,password,name,role)"
              + " VALUES(1,'real@example.test','unchanged-hash','Existente','USER')");
      s.execute(
          "INSERT INTO products(id,sku,name,price,stock,minimum_stock,category_id,supplier_id)"
              + " VALUES(1,'REAL','Existente',1234.56,9,1,1,1)");
      s.execute(
          "INSERT INTO sales(id,created_at,user_id,total) VALUES(1,CURRENT_TIMESTAMP,1,1234.56)");
      s.execute(
          "INSERT INTO sale_items(sale_id,product_id,product_name,quantity,unit_price)"
              + " VALUES(1,1,'Existente',1,1234.56)");
    }
    Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (Connection c = DriverManager.getConnection(url, "sa", "");
        Statement s = c.createStatement()) {
      try (ResultSet r =
          s.executeQuery("SELECT price, stock, cost, currency FROM products WHERE id=1")) {
        assertThat(r.next()).isTrue();
        assertThat(r.getBigDecimal("price")).isEqualByComparingTo("1234.56");
        assertThat(r.getInt("stock")).isEqualTo(9);
        assertThat(r.getBigDecimal("cost")).isNull();
        assertThat(r.getString("currency")).isEqualTo("COP");
      }
      try (ResultSet r = s.executeQuery("SELECT total,currency FROM sales WHERE id=1")) {
        assertThat(r.next()).isTrue();
        assertThat(r.getBigDecimal("total")).isEqualByComparingTo("1234.56");
        assertThat(r.getString("currency")).isEqualTo("COP");
      }
      try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM products")) {
        r.next();
        assertThat(r.getInt(1)).isEqualTo(1);
      }
      try (ResultSet r = s.executeQuery("SELECT completed FROM demo_seed_control WHERE id=1")) {
        r.next();
        assertThat(r.getBoolean(1)).isFalse();
      }
    }
  }
}
