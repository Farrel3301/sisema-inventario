package com.inventario;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "products")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true)
  public String sku;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal price;

  public int stock;
  public int minimumStock;

  @ManyToOne(optional = false)
  @JoinColumn(name = "category_id")
  public Category category;

  @ManyToOne(optional = false)
  @JoinColumn(name = "supplier_id")
  public Supplier supplier;
}
