# Validación realizada

- JDK completo Eclipse Temurin 21; Maven 3.9.11 verificado con SHA-512.
- `./scripts/cloud-maven.sh clean verify`: 10 pruebas ejecutadas con H2, 0 fallos, 0 errores, 0 omitidas; JAR ejecutable generado.
- Suite `InventoryIntegrationTest` repetida con MySQL 8.4: 10 pruebas, 0 fallos, 0 errores, 0 omitidas. Flyway aplicó el esquema y Hibernate lo validó.
- `./scripts/cloud-setup.sh`: ejecución completa y repetible con BUILD SUCCESS, conservando la configuración local.
- `CHROMIUM_PATH=/usr/bin/chromium npm run test:ui`: flujo real de navegador con autenticación, catálogo relacionado, venta, descuento de existencias, registro de colaborador, permisos y cierre de sesión. Escritorio 1440×1000 y móvil 390×844; sin errores JavaScript ni desbordamiento horizontal del panel móvil.
- Las pruebas incluyen rechazo de accesos no autorizados, CSRF, hash de contraseñas, validación de formularios, restricciones relacionales, cálculo de totales, rollback y competencia de ventas por el mismo stock.
- Se conservaron los registros de navegador con prefijo E2E como datos locales de prueba. No son datos de producción. Las credenciales locales están en `.env`, ignorado por Git; nunca se imprimen en este informe.

Las pruebas Java usan H2 por defecto. Para ejecutarlas contra MySQL se requiere una base exclusiva de pruebas: se eliminan registros entre casos. La configuración reutilizable de instalación y arranque se guardó como borrador del entorno; publicar corresponde al usuario. No se ha verificado restauración en una tarea nueva ni despliegue público.

## Carga peruana de desarrollo

- Suite actual: **19 pruebas aprobadas**, sin fallos, errores ni omitidas, con H2. Se ajustó el tiempo de espera de bloqueo de H2 a 60 segundos para que la prueba de concurrencia espere a una carga completa, en lugar del límite predeterminado de 2 segundos.
- Suite repetida con una base MySQL 8.4 exclusiva: **18 pruebas de integración aprobadas**; la prueba adicional de migración V1→V2 usa H2 y también pasó. Nunca se apuntaron las pruebas destructivas a la base del negocio.
- JAR real: primera ejecución creó 12 categorías, 20 proveedores, 150 productos, 300 ventas y 5 usuarios; segunda ejecución no insertó ni actualizó datos; invocación con perfil `prod` y opción seed terminó con código 1 y fue rechazada.
- PowerShell: análisis sintáctico y ejecución real de `scripts/seed-dev.ps1` con PowerShell 7.4 en Linux. No se contó con un equipo Windows nativo; las instrucciones Windows usan PowerShell, Docker Desktop y los mismos comandos de Java.
- Docker: ejecución del servicio optativo `compose.seed.yaml` contra bases MySQL aisladas, sin publicar puertos; el comando termina al finalizar la carga.
- En la base de desarrollo de esta sesión se añadieron exactamente los cinco conjuntos solicitados y 750 líneas de venta. Se compararon todas las columnas originales de **4 usuarios, 3 categorías, 3 proveedores, 3 productos, 3 ventas y 3 líneas preexistentes**: ninguno cambió. La segunda ejecución mantuvo idénticos todos los registros.
- La contraseña aleatoria de los cinco usuarios demo está únicamente en `.env.seed.local` (archivo local ignorado, permisos 600); no se imprimió ni se incorpora al commit. El administrador anterior conserva su contraseña.

El mecanismo no añade opciones seed a la configuración de arranque permanente ni a producción. La migración solo añade estructura, moneda COP para conservar la interpretación de importes antiguos y metadatos de coordinación; no inserta datos demo del negocio.
- Chromium: inicio de sesión con administrador demo, visualización de 150 productos PEN con costos y 300 ventas PEN, cambio a COP conservando los 3 productos anteriores, regreso a PEN y comprobación móvil sin errores JavaScript ni desbordamiento. Capturas locales en `test-results/demo-peru-*.png`, ignoradas por Git.
