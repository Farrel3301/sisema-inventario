# Validación realizada

- JDK completo Eclipse Temurin 21; Maven 3.9.11 verificado con SHA-512.
- `./scripts/cloud-maven.sh clean verify`: 10 pruebas ejecutadas con H2, 0 fallos, 0 errores, 0 omitidas; JAR ejecutable generado.
- Suite `InventoryIntegrationTest` repetida con MySQL 8.4: 10 pruebas, 0 fallos, 0 errores, 0 omitidas. Flyway aplicó el esquema y Hibernate lo validó.
- `./scripts/cloud-setup.sh`: ejecución completa y repetible con BUILD SUCCESS, conservando la configuración local.
- `CHROMIUM_PATH=/usr/bin/chromium npm run test:ui`: flujo real de navegador con autenticación, catálogo relacionado, venta, descuento de existencias, registro de colaborador, permisos y cierre de sesión. Escritorio 1440×1000 y móvil 390×844; sin errores JavaScript ni desbordamiento horizontal del panel móvil.
- Las pruebas incluyen rechazo de accesos no autorizados, CSRF, hash de contraseñas, validación de formularios, restricciones relacionales, cálculo de totales, rollback y competencia de ventas por el mismo stock.
- Se conservaron los registros de navegador con prefijo E2E como datos locales de prueba. No son datos de producción. Las credenciales locales están en `.env`, ignorado por Git; nunca se imprimen en este informe.

Las pruebas Java usan H2 por defecto. Para ejecutarlas contra MySQL se requiere una base exclusiva de pruebas: se eliminan registros entre casos. La configuración reutilizable de instalación y arranque se guardó como borrador del entorno; publicar corresponde al usuario. No se ha verificado restauración en una tarea nueva ni despliegue público.
