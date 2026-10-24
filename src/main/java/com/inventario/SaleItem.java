package com.inventario;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "sale_items")
public class SaleItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  @JoinColumn(name = "sale_id")
  public Sale sale;

  @ManyToOne(optional = false)
  @JoinColumn(name = "product_id")
  public Product product;

  public String productName;
  public int quantity;

  @Column(precision = 14, scale = 2)
  public BigDecimal unitPrice;
}
