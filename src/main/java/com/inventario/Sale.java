package com.inventario;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "sales")
public class Sale {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 3)
  public String currency = "PEN";

  public Instant createdAt = Instant.now();

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id")
  public AppUser user;

  @Column(precision = 14, scale = 2)
  public BigDecimal total = BigDecimal.ZERO;

  @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  public List<SaleItem> items = new ArrayList<>();
}
