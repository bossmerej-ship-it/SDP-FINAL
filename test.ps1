$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    New-Item -ItemType Directory -Path out -Force | Out-Null
    $sources = @(Get-ChildItem -Path src/main/java,src/test/java -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    & javac --release 17 -encoding UTF-8 -Xlint:all -d out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    & java -cp out restaurant.RestaurantTests
    if ($LASTEXITCODE -ne 0) { throw 'Restaurant tests failed.' }
    & java -cp out restaurant.WebTests
    if ($LASTEXITCODE -ne 0) { throw 'HTTP tests failed.' }
} finally {
    Pop-Location
}
