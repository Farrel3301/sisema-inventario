# Datos de prueba de Nexo: negocio comercial peruano

La carga es **explícita y exclusiva de desarrollo**. No se ejecuta en el arranque normal, ni como migración Flyway. Requiere simultáneamente el perfil `dev` y `app.seed.enabled=true`; rechaza los perfiles `prod` y `production`, incluso combinados con `dev`. El proceso de carga se cierra al terminar. No hay un endpoint HTTP para activarla.

## Contenido

| Tipo                  | Nuevos registros |
| --------------------- | ---------------: |
| Categorías            |               12 |
| Proveedores ficticios |               20 |
| Productos             |              150 |
| Ventas históricas     |              300 |
| Usuarios ficticios    |                5 |

Categorías: abarrotes, bebidas, lácteos, conservas, limpieza, cuidado personal, papelería, ferretería, mascotas, snacks, menaje y tecnología. Los proveedores representan distribuidores ficticios de Lima, Callao, Arequipa, Trujillo, Cusco y otras ciudades. No se usan RUC, teléfonos operativos, direcciones ni contactos reales; los correos emplean el dominio reservado `.test`. Los nombres y marcas son ficticios.

Los SKU son `NEXO-DEMO-PE-0001` a `NEXO-DEMO-PE-0150`; categorías, proveedores y usuarios llevan la marca `[DEMO PE]`. Cada producto tiene categoría, proveedor, costo y precio decimal en soles (`PEN`), stock inicial y mínimo. El costo representa aproximadamente el 72 % del precio de venta. No se pretende simular contabilidad, IGV ni comprobantes fiscales.

Las ventas se distribuyen uniformemente entre el día siguiente al inicio del intervalo de seis meses y ayer, calculado con el calendario de `America/Lima` en la primera carga. Tienen entre 1 y 4 líneas y de 1 a 3 unidades por línea. Los precios, totales y descuentos de existencias se calculan en el servidor usando `InventoryService`, el mismo servicio que atiende las ventas normales.

Para el producto con índice `i` (SKU menos uno), el stock inicial es `40 + (i % 11) * 10`. Se cumple: **stock final = stock inicial − unidades vendidas**. No se recorta ni se restaura el stock para hacer pasar la carga. Todas las líneas usan productos y vendedores de este conjunto de prueba.

## Seguridad de datos e idempotencia

- Se insertan registros propios; no se reutilizan ni se actualizan registros existentes del negocio.
- Si ya hay un SKU, correo o nombre que ocupa el espacio reservado del seed, la carga falla sin sobrescribirlo.
- Todo el conjunto y el indicador de finalización se guardan en una única transacción. Un error revierte toda la carga.
- `demo_seed_control` coordina la ejecución mediante bloqueo de fila, también para llamadas simultáneas.
- Una segunda ejecución informa `DEMO PE ya completado: no se modificaron datos`. No añade otras 300 ventas, no mueve fechas y no restaura los registros editados después.
- No se debe borrar ni reiniciar el indicador para repetir el seed en una base con datos. Usa otra base de desarrollo para un conjunto nuevo.
- Los usuarios admiten solamente los roles actuales: `demo.pe.1@usuarios.nexo.test` es `ADMIN`; `demo.pe.2@usuarios.nexo.test` a `demo.pe.5@usuarios.nexo.test` son `USER`.
- Define `NEXO_SEED_PASSWORD` al ejecutar. La contraseña elegida se guarda como hash BCrypt para los cinco usuarios; nunca se guarda en Git ni se imprime. Usa una contraseña exclusiva de pruebas, de al menos 12 caracteres y hasta 72 bytes UTF-8. El administrador real existente no cambia.

La migración V2 añade costo nullable y moneda. Los importes antiguos mantienen su significado original `COP` y su valor numérico; el costo antiguo queda sin registrar (`NULL`). El seed crea únicamente importes `PEN`. El selector de moneda de la interfaz separa productos, ventas e indicadores; no suma soles con pesos ni aplica conversiones. No se puede cambiar la moneda de un producto existente. Las ventas rechazan mezclar monedas en una misma operación.

Aunque existen estas protecciones, el destino debe ser una **base de desarrollo**, nunca una base de producción. Los tests automatizados usan bases desechables y sí limpian sus datos: no ejecutes la suite con `TEST_DB_URL` apuntando a una base que quieras conservar.

## Windows: Docker Desktop y PowerShell

Abre PowerShell en la carpeta del repositorio. Necesitas Docker Desktop con contenedores Linux. Para ejecutar Spring Boot directamente en Windows instala además **JDK 21 completo y Maven 3.9+**; comprueba `java -version` y `mvn -version`.

### 1. Preparar MySQL de desarrollo

```powershell
# Solo si aún no tienes configuración local:
Copy-Item .env.example .env
notepad .env
```

Completa `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `ADMIN_EMAIL` y `ADMIN_PASSWORD` con valores propios. Si `.env` ya existe, **consérvalo** y omite `Copy-Item`. No cambies la contraseña de un volumen MySQL existente simplemente editando `.env`: Docker solo la inicializa al crear la base.

```powershell
docker compose up -d --wait
```

El comando conserva el volumen. No uses `docker compose down -v`, `TRUNCATE` ni borrados para cargar el seed.

### 2. Compilar y probar

Con JDK y Maven en Windows:

```powershell
mvn clean verify
```

O compila usando Docker, sin instalar Java/Maven en Windows:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.11-eclipse-temurin-21 mvn clean verify
```

Las pruebas normales usan H2 y no modifican el MySQL de Docker. El JAR generado es `target/inventario-1.0.0.jar`.

### 3A. Ejecutar el seed con Java en Windows

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\seed-dev.ps1
```

El script lee únicamente las variables de conexión de `.env`, solicita una contraseña demo sin mostrarla, activa `dev` y la opción de carga, y termina al completar la transacción. Usa un puerto temporal para evitar conflictos con la aplicación abierta. La contraseña demo se retira del entorno al terminar si no estaba definida previamente. El script no altera la política permanente de PowerShell.

### 3B. Alternativa: ejecutar el seed con Docker

Esta variante no necesita Java local:

```powershell
$secure = Read-Host 'Contraseña exclusiva para los 5 usuarios demo' -AsSecureString
$env:NEXO_SEED_PASSWORD = (New-Object System.Net.NetworkCredential('', $secure)).Password
try {
    docker compose -f compose.yaml -f compose.seed.yaml --profile seed run --rm seed
    if ($LASTEXITCODE -ne 0) { throw 'La carga no se completó' }
} finally {
    Remove-Item Env:NEXO_SEED_PASSWORD -ErrorAction SilentlyContinue
}
```

`compose.seed.yaml` es un archivo optativo y el servicio pertenece al perfil `seed`. **No** se añade al arranque normal ni a producción. El contenedor usa el mismo MySQL de desarrollo y no publica puertos. Espera el mensaje con los cinco recuentos y código de salida 0. Repite el comando para comprobar que no se añaden duplicados.

### 4. Abrir Spring Boot normalmente

Puedes cargar las variables de conexión en PowerShell y ejecutar el JAR, sin activar la opción seed:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*(DB_URL|DB_USER|DB_PASSWORD|ADMIN_EMAIL|ADMIN_PASSWORD)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
    }
}
java -jar target/inventario-1.0.0.jar --spring.profiles.active=dev
```

Usa el puerto 8080 de tu instalación, selecciona **PEN · Soles** e inicia sesión con el administrador existente o un usuario demo y la contraseña que elegiste. Verás únicamente los importes de la moneda seleccionada; COP sigue disponible para los datos anteriores.

## Linux / macOS / entorno cloud

Compila con `./mvnw verify` (en el cloud, `./scripts/cloud-maven.sh verify`). Con la conexión configurada en `.env`:

```bash
read -rs -p 'Contraseña demo: ' NEXO_SEED_PASSWORD; echo
export NEXO_SEED_PASSWORD
./scripts/seed-dev.sh
unset NEXO_SEED_PASSWORD
```

No añadas `app.seed.enabled=true` ni `NEXO_SEED_PASSWORD` a configuraciones permanentes o de producción.

## Comprobaciones

Las pruebas verifican recuentos, claves y relaciones, costos, moneda, totales de cada venta, stock por producto, fechas en los últimos seis meses, hashes de usuarios, conservación de registros previos, colisiones, rollback, idempotencia, concurrencia y rechazo del perfil de producción. También se prueba la migración de importes anteriores sin conversión automática.

Una inspección de solo lectura en MySQL puede comprobar los recuentos del conjunto propio:

```sql
SELECT COUNT(*) AS productos_demo FROM products WHERE sku LIKE 'NEXO-DEMO-PE-%';
SELECT COUNT(*) AS ventas_demo FROM sales s JOIN app_users u ON s.user_id=u.id
WHERE u.email LIKE 'demo.pe.%@usuarios.nexo.test';
SELECT s.id, s.total, SUM(i.quantity*i.unit_price) AS calculado
FROM sales s JOIN sale_items i ON i.sale_id=s.id
JOIN app_users u ON s.user_id=u.id
WHERE u.email LIKE 'demo.pe.%@usuarios.nexo.test'
GROUP BY s.id,s.total HAVING s.total <> SUM(i.quantity*i.unit_price);
```

Los dos primeros resultados esperados son 150 y 300. La consulta de diferencias debe devolver cero filas. Ventas posteriores hechas manualmente por usuarios demo incrementarán el segundo recuento; esto no activa otra carga.
