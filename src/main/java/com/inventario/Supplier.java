package com.inventario;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "suppliers")
public class Supplier {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public String name;

  public String email;
  public String phone;
}
