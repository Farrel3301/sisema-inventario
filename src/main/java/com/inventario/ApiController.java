package com.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@EnableMethodSecurity
class ApiController {
  final Users users;
  final Categories categories;
  final Suppliers suppliers;
  final Products products;
  final Sales sales;
  final PasswordEncoder encoder;
  final InventoryService service;

  ApiController(
      Users u,
      Categories c,
      Suppliers s,
      Products p,
      Sales sales,
      PasswordEncoder e,
      InventoryService service) {
    users = u;
    categories = c;
    suppliers = s;
    products = p;
    this.sales = sales;
    encoder = e;
    this.service = service;
  }

  record Registration(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Email @Size(max = 150) String email,
      @Size(min = 12, max = 72) @NotNull String password) {}

  record CategoryInput(
      @NotBlank @Size(max = 100) String name, @Size(max = 255) String description) {}

  record SupplierInput(
      @NotBlank @Size(max = 100) String name,
      @Email @Size(max = 150) String email,
      @Size(max = 40) String phone) {}

  record ProductInput(
      @NotBlank @Size(max = 60) String sku,
      @NotBlank @Size(max = 150) String name,
      @NotNull @DecimalMin("0.01") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2)
          BigDecimal price,
      @Min(0) @Max(1000000) int stock,
      @Min(0) @Max(1000000) int minimumStock,
      @NotNull Long categoryId,
      @NotNull Long supplierId,
      @DecimalMin("0.00") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2)
          BigDecimal cost,
      @Pattern(regexp = "PEN|COP") String currency) {}

  record Line(@NotNull Long productId, @Min(1) @Max(1000000) int quantity) {}

  record SaleInput(@NotEmpty @Size(max = 100) List<@NotNull @Valid Line> items) {}

  record RoleInput(@Pattern(regexp = "ADMIN|USER") @NotNull String role) {}

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  AppUser register(@Valid @RequestBody Registration input) {
    if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "La contraseña admite hasta 72 bytes UTF-8");
    }
    AppUser u = new AppUser();
    u.email = input.email().trim().toLowerCase(Locale.ROOT);
    u.name = input.name().trim();
    u.password = encoder.encode(input.password());
    return users.save(u);
  }

  @GetMapping("/me")
  AppUser me(Principal principal) {
    return users.findByEmail(principal.getName()).orElseThrow();
  }

  @GetMapping("/categories")
  List<Category> categories() {
    return categories.findAll();
  }

  @PostMapping("/categories")
  @PreAuthorize("hasRole('ADMIN')")
  Category category(@Valid @RequestBody CategoryInput i) {
    return saveCategory(new Category(), i);
  }

  @PutMapping("/categories/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  Category category(@PathVariable Long id, @Valid @RequestBody CategoryInput i) {
    return saveCategory(categories.findById(id).orElseThrow(ApiController::missing), i);
  }

  Category saveCategory(Category c, CategoryInput i) {
    c.name = i.name().trim();
    c.description = i.description();
    return categories.save(c);
  }

  @DeleteMapping("/categories/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  void deleteCategory(@PathVariable Long id) {
    categories.delete(categories.findById(id).orElseThrow(ApiController::missing));
  }

  @GetMapping("/suppliers")
  List<Supplier> suppliers() {
    return suppliers.findAll();
  }

  @PostMapping("/suppliers")
  @PreAuthorize("hasRole('ADMIN')")
  Supplier supplier(@Valid @RequestBody SupplierInput i) {
    return saveSupplier(new Supplier(), i);
  }

  @PutMapping("/suppliers/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  Supplier supplier(@PathVariable Long id, @Valid @RequestBody SupplierInput i) {
    return saveSupplier(suppliers.findById(id).orElseThrow(ApiController::missing), i);
  }

  Supplier saveSupplier(Supplier s, SupplierInput i) {
    s.name = i.name().trim();
    s.email = i.email();
    s.phone = i.phone();
    return suppliers.save(s);
  }

  @DeleteMapping("/suppliers/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  void deleteSupplier(@PathVariable Long id) {
    suppliers.delete(suppliers.findById(id).orElseThrow(ApiController::missing));
  }

  @GetMapping("/products")
  List<Product> products() {
    return products.findAll();
  }

  @PostMapping("/products")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  Product product(@Valid @RequestBody ProductInput i) {
    return saveProduct(new Product(), i);
  }

  @PutMapping("/products/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  Product product(@PathVariable Long id, @Valid @RequestBody ProductInput i) {
    return saveProduct(products.locked(id).orElseThrow(ApiController::missing), i);
  }

  Product saveProduct(Product p, ProductInput i) {
    p.sku = i.sku().trim();
    p.name = i.name().trim();
    if (p.id != null && i.currency() != null && !p.currency.equals(i.currency()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "La moneda de un producto existente no se puede cambiar");
    if (i.currency() != null) p.currency = i.currency();
    if (i.cost() != null) p.cost = i.cost();
    p.price = i.price();
    p.stock = i.stock();
    p.minimumStock = i.minimumStock();
    p.category = categories.findById(i.categoryId()).orElseThrow(ApiController::missing);
    p.supplier = suppliers.findById(i.supplierId()).orElseThrow(ApiController::missing);
    return products.save(p);
  }

  @DeleteMapping("/products/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  void deleteProduct(@PathVariable Long id) {
    products.delete(products.findById(id).orElseThrow(ApiController::missing));
  }

  @GetMapping("/sales")
  List<Sale> sales() {
    return sales.findAll();
  }

  @PostMapping("/sales")
  Sale sale(@Valid @RequestBody SaleInput input, Principal principal) {
    return service.sell(input.items(), principal.getName());
  }

  @GetMapping("/users")
  List<AppUser> users() {
    return users.findAll();
  }

  @PutMapping("/users/{id}/role")
  @Transactional
  public AppUser role(@PathVariable Long id, @Valid @RequestBody RoleInput i, Principal principal) {
    AppUser u = users.findById(id).orElseThrow(ApiController::missing);
    if (u.email.equals(principal.getName()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar tu propio rol");
    u.role = i.role();
    return users.save(u);
  }

  static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro inexistente");
  }
}

@RestControllerAdvice
class Errors {
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(Map.of("message", "Registro duplicado o relacionado con otros registros"));
  }

  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  ResponseEntity<?> validation() {
    return ResponseEntity.badRequest().body(Map.of("message", "Revisa los campos y sus límites"));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", Objects.toString(e.getReason(), "Solicitud inválida")));
  }
}
