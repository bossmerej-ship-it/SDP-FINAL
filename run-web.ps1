param([ValidateRange(0, 65535)][int]$Port = 8080)

$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    New-Item -ItemType Directory -Path out -Force | Out-Null
    $sources = @(Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    & javac --release 17 -encoding UTF-8 -Xlint:all -d out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    & java -cp out restaurant.web.WebServer $Port
    if ($LASTEXITCODE -ne 0) { throw 'The web server stopped with an error.' }
} finally {
    Pop-Location
}
