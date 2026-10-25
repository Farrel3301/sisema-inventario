# Ejecutar con PowerShell desde Windows. No borra ni recrea la base.
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
$previousPassword = $env:NEXO_SEED_PASSWORD
try {
    if (Test-Path '.env') {
        foreach ($line in Get-Content '.env') {
            if ($line -match '^\s*(DB_URL|DB_USER|DB_PASSWORD)=(.*)$') {
                $name = $Matches[1]
                $value = $Matches[2].Trim().Trim('"').Trim("'")
                if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
                    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
                }
            }
        }
    }
    if (-not $env:DB_PASSWORD) { throw 'Define DB_PASSWORD en .env o en el entorno.' }
    if (-not (Test-Path 'target/inventario-1.0.0.jar')) { throw 'Compila antes con mvn clean verify (JDK 21 y Maven).' }
    if (-not $env:NEXO_SEED_PASSWORD) {
        $secure = Read-Host 'Contraseña demo (mínimo 12 caracteres; no se mostrará)' -AsSecureString
        $env:NEXO_SEED_PASSWORD = (New-Object System.Net.NetworkCredential('', $secure)).Password
    }
    & java -jar target/inventario-1.0.0.jar '--spring.profiles.active=dev' '--app.seed.enabled=true' '--server.port=0'
    if ($LASTEXITCODE -ne 0) { throw "La carga falló (código $LASTEXITCODE). No se confirmó el seed." }
} finally {
    $env:NEXO_SEED_PASSWORD = $previousPassword
    Pop-Location
}
