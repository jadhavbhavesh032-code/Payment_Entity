param(
    [string]$DatabaseUrl = 'jdbc:postgresql://localhost:5432/payment_entity_db',
    [string]$DatabaseUser = 'postgres'
)
$ErrorActionPreference = 'Stop'
$previousUrl = $env:DB_URL
$previousUser = $env:DB_USERNAME
$previousPassword = $env:DB_PASSWORD
$previousProfile = $env:SPRING_PROFILES_ACTIVE
try {
    $env:DB_URL = $DatabaseUrl
    $env:DB_USERNAME = $DatabaseUser
    $env:SPRING_PROFILES_ACTIVE = 'postgres'
    if (-not $env:DB_PASSWORD) {
        $secret = Read-Host 'PostgreSQL password' -AsSecureString
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
    }
    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    $env:DB_URL = $previousUrl
    $env:DB_USERNAME = $previousUser
    $env:DB_PASSWORD = $previousPassword
    $env:SPRING_PROFILES_ACTIVE = $previousProfile
}
