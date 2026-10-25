package com.inventario;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
class DemoSeedConfiguration {
  @Bean
  CommandLineRunner explicitDemoSeed(DemoSeedService seed, ConfigurableApplicationContext context) {
    return args -> {
      boolean created = seed.load();
      System.out.println(
          created
              ? "DEMO PE: creadas 12 categorías, 20 proveedores, 150 productos, 300 ventas y 5"
                  + " usuarios. Importes PEN."
              : "DEMO PE ya completado: no se modificaron datos.");
      SpringApplication.exit(context, () -> 0);
    };
  }
}
