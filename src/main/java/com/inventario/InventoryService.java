package com.inventario;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class InventoryService {
  final Products products;
  final Sales sales;
  final Users users;

  InventoryService(Products p, Sales s, Users u) {
    products = p;
    sales = s;
    users = u;
  }

  @Transactional
  public Sale sell(List<ApiController.Line> lines, String email) {
    if (lines == null || lines.isEmpty() || lines.size() > 100)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incluye de 1 a 100 productos");
    TreeMap<Long, Integer> quantities = new TreeMap<>();
    for (var line : lines) {
      if (line == null
          || line.productId() == null
          || line.quantity() < 1
          || line.quantity() > 1000000
          || quantities.putIfAbsent(line.productId(), line.quantity()) != null)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Productos duplicados o cantidad inválida");
    }
    Sale sale = new Sale();
    sale.user = users.findByEmail(email).orElseThrow();
    for (var entry : quantities.entrySet()) {
      Product p =
          products
              .locked(entry.getKey())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto inexistente"));
      if (p.stock < entry.getValue())
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente: " + p.name);
      p.stock -= entry.getValue();
      SaleItem item = new SaleItem();
      item.sale = sale;
      item.product = p;
      item.productName = p.name;
      item.quantity = entry.getValue();
      item.unitPrice = p.price;
      sale.items.add(item);
      sale.total = sale.total.add(p.price.multiply(BigDecimal.valueOf(item.quantity)));
    }
    if (sale.total.compareTo(new BigDecimal("999999999999.99")) > 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total demasiado alto");
    return sales.save(sale);
  }
}
