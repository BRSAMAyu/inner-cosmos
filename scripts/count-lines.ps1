$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$groups = @(
  @{ Name = "Java"; Pattern = "*.java"; Path = "src\main\java" },
  @{ Name = "HTML"; Pattern = "*.html"; Path = "src\main\resources\static\pages" },
  @{ Name = "CSS"; Pattern = "*.css"; Path = "src\main\resources\static\css" },
  @{ Name = "JS"; Pattern = "*.js"; Path = "src\main\resources\static\js" },
  @{ Name = "SQL"; Pattern = "*.sql"; Path = "src\main\resources" },
  @{ Name = "Docs"; Pattern = "*.md"; Path = "docs" }
)

$total = 0
foreach ($group in $groups) {
  $path = Join-Path $root $group.Path
  if (Test-Path $path) {
    $lines = (Get-ChildItem -Path $path -Recurse -Filter $group.Pattern -File | Get-Content | Measure-Object -Line).Lines
    $total += $lines
    "{0}: {1}" -f $group.Name, $lines
  }
}
"Total: {0}" -f $total
