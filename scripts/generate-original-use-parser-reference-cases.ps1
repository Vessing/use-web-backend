[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$referenceRoot = Join-Path $RepositoryRoot 'src/test/resources/reference'
$parserRoot = Join-Path $referenceRoot 'original-use/parser'
$inventoryPath = Join-Path $referenceRoot 'original-use/inventory.json'
$metadataRoot = Join-Path $referenceRoot 'converted/metadata'
$jsonPath = Join-Path $metadataRoot 'parser-reference-cases.json'
$markdownPath = Join-Path $metadataRoot 'parser-reference-cases.md'

if (-not (Test-Path -LiteralPath $parserRoot -PathType Container)) {
    throw "Parser reference corpus not found: $parserRoot"
}
if (-not (Test-Path -LiteralPath $inventoryPath -PathType Leaf)) {
    throw "File inventory not found. Run generate-original-use-inventory.ps1 first."
}

New-Item -ItemType Directory -Force -Path $metadataRoot | Out-Null

$inventory = Get-Content -LiteralPath $inventoryPath -Raw | ConvertFrom-Json
$inventoryByPath = @{}
foreach ($file in $inventory.files) {
    $inventoryByPath[$file.path] = $file
}

function Test-Pattern {
    param([string]$Text, [string]$Pattern)
    return [regex]::IsMatch($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Multiline)
}

function Get-PathHash {
    param([string]$SourceFile)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($SourceFile.ToLowerInvariant().Replace('\', '/'))
    $hash = [System.Security.Cryptography.SHA256]::HashData($bytes)
    return ([Convert]::ToHexString($hash)).Substring(0, 12)
}

function Get-CaseId {
    param([string]$SourceFile, [int]$BlockIndex)
    return 'USE-PARSER-{0}-{1:D3}' -f (Get-PathHash $SourceFile), $BlockIndex
}

function Get-FeatureTags {
    param([string]$Text, [string[]]$BaseTags = @())

    $patterns = [ordered]@{
        OCL_INVARIANT          = '\binv\b'
        OCL_PRECONDITION       = '\bpre\b'
        OCL_POSTCONDITION      = '\bpost\b'
        OCL_DERIVED            = '\bderive\b|\bderived\b'
        OCL_INIT               = '\binit\b'
        OCL_LET                = '\blet\b'
        OCL_IF                 = '\bif\b[\s\S]*\bthen\b[\s\S]*\belse\b'
        OCL_ALL_INSTANCES      = '\ballInstances\b'
        OCL_IMPLIES            = '\bimplies\b'
        ITERATOR_FOR_ALL       = '\bforAll\b'
        ITERATOR_EXISTS        = '\bexists\b'
        ITERATOR_SELECT        = '\bselect\b'
        ITERATOR_REJECT        = '\breject\b'
        ITERATOR_COLLECT       = '\bcollect(?:Nested)?\b'
        ITERATOR_ITERATE       = '\biterate\b'
        ITERATOR_IS_UNIQUE     = '\bisUnique\b'
        ITERATOR_SORTED_BY     = '\bsortedBy\b'
        COLLECTION_SET         = '\bSet\s*[({]'
        COLLECTION_BAG         = '\bBag\s*[({]'
        COLLECTION_SEQUENCE    = '\bSequence\s*[({]'
        COLLECTION_ORDERED_SET = '\bOrderedSet\s*[({]'
        COLLECTION_UNION       = '\bunion\b'
        COLLECTION_INTERSECTION = '\bintersection\b'
        COLLECTION_FLATTEN     = '\bflatten\b'
        UML_GENERALIZATION     = '\babstract\s+class\b|\bclass\s+\w+\s*<\s*\w+'
        UML_ENUM               = '\benum\b'
        UML_ASSOCIATION_CLASS  = '\bassociationclass\b'
        USE_IMPORT             = '\bimport\b'
    }

    $tags = [System.Collections.Generic.List[string]]::new()
    foreach ($tag in $BaseTags) { if ($tag) { $tags.Add($tag) } }
    foreach ($entry in $patterns.GetEnumerator()) {
        if (Test-Pattern $Text $entry.Value) { $tags.Add($entry.Key) }
    }
    return @($tags | Sort-Object -Unique)
}

function Get-DependencySteps {
    param([string[]]$FeatureTags)

    $steps = [System.Collections.Generic.List[int]]::new()
    foreach ($tag in $FeatureTags) {
        switch -Regex ($tag) {
            '^COLLECTION_' { $steps.Add(12); break }
            '^ITERATOR_(FOR_ALL|EXISTS)$' { $steps.Add(18); break }
            '^ITERATOR_(SELECT|REJECT)$' { $steps.Add(19); break }
            '^ITERATOR_COLLECT$' { $steps.Add(20); break }
            '^ITERATOR_(IS_UNIQUE|SORTED_BY)$' { $steps.Add(21); break }
            '^ITERATOR_ITERATE$' { $steps.Add(22); break }
            '^OCL_IF$' { $steps.Add(23); break }
            '^OCL_LET$' { $steps.Add(24); break }
            '^OCL_ALL_INSTANCES$' { $steps.Add(25); break }
            '^UML_(GENERALIZATION|ENUM)$' { $steps.Add(26); break }
            '^OCL_(PRECONDITION|POSTCONDITION)$' { $steps.Add(28); break }
            '^OCL_(DERIVED|INIT)$' { $steps.Add(30); break }
            '^USE_IMPORT$' { $steps.Add(31); break }
        }
    }
    return @($steps | Sort-Object -Unique)
}

function Get-Imports {
    param([string]$Text)
    $imports = [System.Collections.Generic.List[string]]::new()
    foreach ($match in [regex]::Matches($Text, '\bimport\b[^\r\n]*?\bfrom\s+"([^"]+)"', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
        $imports.Add($match.Groups[1].Value.Replace('\', '/'))
    }
    return @($imports | Sort-Object -Unique)
}

function Get-ExpectedType {
    param([string]$ExpectedOutput)
    if ($ExpectedOutput -match '^.*\s:\s(.+)$') { return $Matches[1].Trim() }
    return $null
}

function New-ReferenceCase {
    param(
        [string]$Id,
        [string]$SourceFile,
        [int]$SourceLine,
        [int]$SourceEndLine,
        [string]$OriginalInput,
        [object]$OriginalExpectedOutput,
        [string]$ExpectedOutputSourceFile,
        [int]$ExpectedOutputSourceLine,
        [string]$NormalizedExpression,
        [string]$Category,
        [string]$StandardStatus,
        [string[]]$FeatureTags,
        [object]$Setup,
        [string]$ExpectedResultKind,
        [string]$ExpectedType,
        [object]$ExpectedValueSummary,
        [string]$CurrentStatus,
        [string]$FailureCategory,
        [string[]]$GapIds,
        [string]$TargetTestType,
        [int]$RoadMapStep,
        [int[]]$DependencySteps,
        [string]$Notes
    )

    return [pscustomobject][ordered]@{
        id = $Id
        sourceFile = $SourceFile
        sourceLine = $SourceLine
        sourceEndLine = $SourceEndLine
        originalInput = $OriginalInput
        originalExpectedOutput = $OriginalExpectedOutput
        expectedOutputSourceFile = $ExpectedOutputSourceFile
        expectedOutputSourceLine = if ($ExpectedOutputSourceLine -gt 0) { $ExpectedOutputSourceLine } else { $null }
        normalizedExpression = $NormalizedExpression
        category = $Category
        standardStatus = $StandardStatus
        featureTags = @($FeatureTags)
        setup = $Setup
        expectedResultKind = $ExpectedResultKind
        expectedType = $ExpectedType
        expectedValueSummary = $ExpectedValueSummary
        currentStatus = $CurrentStatus
        failureCategory = $FailureCategory
        gapIds = @($GapIds)
        targetTestType = $TargetTestType
        roadMapStep = $RoadMapStep
        dependencySteps = @($DependencySteps)
        notes = $Notes
    }
}

$cases = [System.Collections.Generic.List[object]]::new()
$useFiles = @(Get-ChildItem -LiteralPath $parserRoot -Recurse -File -Filter '*.use' | Sort-Object FullName)
$failFiles = @(Get-ChildItem -LiteralPath $parserRoot -File -Filter '*.fail' | Sort-Object Name)
$pairedFailPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

foreach ($useFile in $useFiles) {
    $relativeWithinParser = $useFile.FullName.Substring($parserRoot.Length).TrimStart('\').Replace('\', '/')
    $sourceFile = "parser/$relativeWithinParser"
    $lines = @(Get-Content -LiteralPath $useFile.FullName)
    $text = Get-Content -LiteralPath $useFile.FullName -Raw
    $inventoryRecord = $inventoryByPath[$sourceFile]
    if ($null -eq $inventoryRecord) { throw "Missing inventory record: $sourceFile" }

    $isImportFixture = $relativeWithinParser.StartsWith('imports/', [System.StringComparison]::OrdinalIgnoreCase)
    $failPath = [System.IO.Path]::ChangeExtension($useFile.FullName, '.fail')
    $hasExpectedFailure = -not $isImportFixture -and (Test-Path -LiteralPath $failPath -PathType Leaf)
    $imports = @(Get-Imports $text)
    $baseTags = @($inventoryRecord.possibleFeatureTags)
    if ($isImportFixture) { $baseTags += 'IMPORT_FIXTURE' }
    if ($hasExpectedFailure) { $baseTags += 'NEGATIVE_MODEL_CASE' }
    $featureTags = @(Get-FeatureTags -Text $text -BaseTags $baseTags)
    $dependencySteps = @(Get-DependencySteps $featureTags)
    $blockIndex = 1

    if ($hasExpectedFailure) {
        $expectedLines = @(Get-Content -LiteralPath $failPath)
        $expectedOutputSourceFile = 'parser/' + ([System.IO.Path]::GetFileName($failPath))
        $expectedOutputSourceLine = 1
        [void]$pairedFailPaths.Add((Resolve-Path -LiteralPath $failPath).Path)
        $status = 'FAILING_FORMAT'
        $failureCategory = 'EXPECTED_DIAGNOSTIC_NOT_NORMALIZED'
        $gapIds = @('OCL-GAP-017A', 'OCL-GAP-017B', 'OCL-GAP-018')
        $expectedResultKind = 'DIAGNOSTIC'
        $notes = 'Negative model case. Expected USE compiler text must later be mapped to structured phase, code, and source range.'
    }
    elseif ($isImportFixture -or $imports.Count -gt 0) {
        $expectedLines = $null
        $expectedOutputSourceFile = $null
        $expectedOutputSourceLine = 0
        $status = 'FAILING_INFRASTRUCTURE'
        $failureCategory = 'IMPORT_RESOLVER_NOT_AVAILABLE'
        $gapIds = @('OCL-GAP-017A', 'OCL-GAP-017C', 'OCL-GAP-018')
        $expectedResultKind = if ($isImportFixture) { 'IMPORT_FIXTURE' } else { 'MODEL_ACCEPTED' }
        $notes = if ($isImportFixture) { 'Import dependency resource; retained as a classified fixture rather than an independently asserted model.' } else { 'Positive model case requiring a controlled import resolver.' }
    }
    else {
        $expectedLines = $null
        $expectedOutputSourceFile = $null
        $expectedOutputSourceLine = 0
        $status = 'FAILING_INFRASTRUCTURE'
        $failureCategory = 'MODEL_PARSER_REFERENCE_HARNESS_NOT_AVAILABLE'
        $gapIds = @('OCL-GAP-017A', 'OCL-GAP-018')
        $expectedResultKind = 'MODEL_ACCEPTED'
        $notes = 'Positive model case. Execution is deferred until the separate parser reference harness exists.'
    }

    $modelCategory = if ($isImportFixture) { 'IMPORT_FIXTURE' } else { 'MODEL_PARSER' }
    $modelTarget = if ($isImportFixture -or $imports.Count -gt 0) { 'INFRASTRUCTURE' } else { 'PARSER' }
    $modelSetup = [pscustomobject][ordered]@{
        modelFile = $sourceFile
        imports = $imports
        snapshotCommands = @()
    }
    $cases.Add((New-ReferenceCase -Id (Get-CaseId $sourceFile $blockIndex) -SourceFile $sourceFile -SourceLine 1 -SourceEndLine $lines.Count -OriginalInput $text -OriginalExpectedOutput $expectedLines -ExpectedOutputSourceFile $expectedOutputSourceFile -ExpectedOutputSourceLine $expectedOutputSourceLine -NormalizedExpression $null -Category $modelCategory -StandardStatus 'UNCLEAR' -FeatureTags $featureTags -Setup $modelSetup -ExpectedResultKind $expectedResultKind -ExpectedType $null -ExpectedValueSummary $null -CurrentStatus $status -FailureCategory $failureCategory -GapIds $gapIds -TargetTestType $modelTarget -RoadMapStep 5 -DependencySteps $dependencySteps -Notes $notes))

    $contextStarts = [System.Collections.Generic.List[int]]::new()
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^\s*context\b') { $contextStarts.Add($index) }
    }

    for ($contextIndex = 0; $contextIndex -lt $contextStarts.Count; $contextIndex++) {
        $startIndex = $contextStarts[$contextIndex]
        $endIndex = if ($contextIndex + 1 -lt $contextStarts.Count) { $contextStarts[$contextIndex + 1] - 1 } else { $lines.Count - 1 }
        while ($endIndex -gt $startIndex -and [string]::IsNullOrWhiteSpace($lines[$endIndex])) { $endIndex-- }
        $blockLines = @($lines[$startIndex..$endIndex])
        $blockText = $blockLines -join [Environment]::NewLine
        $blockTags = @(Get-FeatureTags -Text $blockText)
        $blockIndex++
        $cases.Add((New-ReferenceCase -Id (Get-CaseId $sourceFile $blockIndex) -SourceFile $sourceFile -SourceLine ($startIndex + 1) -SourceEndLine ($endIndex + 1) -OriginalInput $blockText -OriginalExpectedOutput $null -ExpectedOutputSourceFile $null -ExpectedOutputSourceLine 0 -NormalizedExpression $null -Category 'OCL_DECLARATION' -StandardStatus 'UNCLEAR' -FeatureTags $blockTags -Setup $modelSetup -ExpectedResultKind 'VALIDATION' -ExpectedType $null -ExpectedValueSummary $null -CurrentStatus 'FAILING_INFRASTRUCTURE' -FailureCategory 'MODEL_CONTEXT_NOT_AVAILABLE' -GapIds @('OCL-GAP-017A', 'OCL-GAP-018') -TargetTestType 'VALIDATION' -RoadMapStep 5 -DependencySteps (Get-DependencySteps $blockTags) -Notes 'Context block extracted at source level. Expression and individual invariant/contract splitting require later review and harness support.'))
    }
}

$orphanFailFiles = @($failFiles | Where-Object { -not $pairedFailPaths.Contains((Resolve-Path -LiteralPath $_.FullName).Path) })
if ($orphanFailFiles.Count -gt 0) {
    throw "Unpaired .fail files: $($orphanFailFiles.Name -join ', ')"
}

$expressionFile = Join-Path $parserRoot 'test_expr.in'
if (-not (Test-Path -LiteralPath $expressionFile -PathType Leaf)) {
    throw "Expression reference file not found: $expressionFile"
}

$expressionSourceFile = 'parser/test_expr.in'
$expressionLines = @(Get-Content -LiteralPath $expressionFile)
$lineIndex = 0
$expressionBlockIndex = 0
$section = $null

while ($lineIndex -lt $expressionLines.Count) {
    $line = $expressionLines[$lineIndex]
    if ($line.StartsWith('##')) {
        $section = $line.Substring(2).Trim()
        $lineIndex++
        continue
    }
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
        $lineIndex++
        continue
    }

    $startIndex = $lineIndex
    $inputLines = [System.Collections.Generic.List[string]]::new()
    while ($lineIndex -lt $expressionLines.Count -and -not $expressionLines[$lineIndex].StartsWith('-> ')) {
        $inputLines.Add($expressionLines[$lineIndex])
        $lineIndex++
    }
    if ($lineIndex -ge $expressionLines.Count) {
        throw "Missing expected result for expression starting at line $($startIndex + 1)."
    }

    $expectedLineIndex = $lineIndex
    $expectedOutput = $expressionLines[$lineIndex].Substring(3)
    $lineIndex++
    $expressionBlockIndex++
    $inputText = $inputLines -join [Environment]::NewLine
    $normalizedExpression = (($inputLines | ForEach-Object { $_.Trim() }) -join ' ').Trim()
    $featureTags = @(Get-FeatureTags -Text $inputText -BaseTags @('EXPRESSION_REFERENCE'))
    $dependencySteps = @(Get-DependencySteps $featureTags)
    $notes = if ($section) { "Original section: $section. Execution is deferred until the separate reference harness exists." } else { 'Execution is deferred until the separate reference harness exists.' }
    $setup = [pscustomobject][ordered]@{
        modelFile = $null
        imports = @()
        snapshotCommands = @()
    }

    $cases.Add((New-ReferenceCase -Id (Get-CaseId $expressionSourceFile $expressionBlockIndex) -SourceFile $expressionSourceFile -SourceLine ($startIndex + 1) -SourceEndLine ($expectedLineIndex + 1) -OriginalInput $inputText -OriginalExpectedOutput $expectedOutput -ExpectedOutputSourceFile $expressionSourceFile -ExpectedOutputSourceLine ($expectedLineIndex + 1) -NormalizedExpression $normalizedExpression -Category 'OCL_EXPRESSION_EVALUATION' -StandardStatus 'UNCLEAR' -FeatureTags $featureTags -Setup $setup -ExpectedResultKind 'VALUE' -ExpectedType (Get-ExpectedType $expectedOutput) -ExpectedValueSummary $expectedOutput -CurrentStatus 'FAILING_INFRASTRUCTURE' -FailureCategory 'REFERENCE_EXPRESSION_RUNNER_NOT_AVAILABLE' -GapIds @('OCL-GAP-017A') -TargetTestType 'EVALUATOR' -RoadMapStep 5 -DependencySteps $dependencySteps -Notes $notes))
}

$sortedCases = @($cases | Sort-Object sourceFile, sourceLine, id)
$ids = @($sortedCases.id)
if (@($ids | Sort-Object -Unique).Count -ne $ids.Count) { throw 'Duplicate parser reference case IDs detected.' }

$classifiedUseFiles = @($sortedCases | Where-Object { $_.category -in @('MODEL_PARSER', 'IMPORT_FIXTURE') } | Select-Object -ExpandProperty sourceFile -Unique)
if ($classifiedUseFiles.Count -ne $useFiles.Count) { throw 'Not every parser .use file has exactly one file-level classification.' }

$document = [pscustomobject][ordered]@{
    schemaVersion = '1.0'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    sourceArea = 'parser'
    sourceRoot = 'src/test/resources/reference/original-use/parser'
    summary = [pscustomobject][ordered]@{
        sourceFiles = 90
        useFiles = $useFiles.Count
        failFiles = $failFiles.Count
        expressionInputFiles = 1
        totalCases = $sortedCases.Count
        modelCases = @($sortedCases | Where-Object category -eq 'MODEL_PARSER').Count
        importFixtureCases = @($sortedCases | Where-Object category -eq 'IMPORT_FIXTURE').Count
        oclDeclarationCases = @($sortedCases | Where-Object category -eq 'OCL_DECLARATION').Count
        expressionCases = @($sortedCases | Where-Object category -eq 'OCL_EXPRESSION_EVALUATION').Count
        expectedDiagnosticCases = @($sortedCases | Where-Object expectedResultKind -eq 'DIAGNOSTIC').Count
        statusCounts = [pscustomobject][ordered]@{
            FAILING_FORMAT = @($sortedCases | Where-Object currentStatus -eq 'FAILING_FORMAT').Count
            FAILING_INFRASTRUCTURE = @($sortedCases | Where-Object currentStatus -eq 'FAILING_INFRASTRUCTURE').Count
            UNCLEAR = @($sortedCases | Where-Object currentStatus -eq 'UNCLEAR').Count
        }
    }
    assumptions = @(
        'A .fail file is paired with the same-named top-level .use model and stores expected compiler output.',
        'Each .use file receives one file-level case; each context block receives an additional source-level OCL declaration case.',
        'test_expr.in is split using the original harness rule: expression lines continue until a line prefixed with ->.',
        'Feature tags and standard status are preliminary and require technical and normative review.',
        'No case is PASSING before execution by the future separate reference harness.'
    )
    cases = $sortedCases
}

$document | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM

$markdown = [System.Collections.Generic.List[string]]::new()
$markdown.Add('# Original USE Parser Reference Cases')
$markdown.Add('')
$markdown.Add('This report classifies parser-corpus files and source blocks. It does not execute them.')
$markdown.Add('')
$markdown.Add('## Summary')
$markdown.Add('')
$markdown.Add('| Metric | Count |')
$markdown.Add('|---|---:|')
$markdown.Add("| Parser source files | 90 |")
$markdown.Add("| `.use` files | $($useFiles.Count) |")
$markdown.Add("| `.fail` files paired as expectations | $($failFiles.Count) |")
$markdown.Add("| Model parser cases | $($document.summary.modelCases) |")
$markdown.Add("| Import fixture cases | $($document.summary.importFixtureCases) |")
$markdown.Add("| OCL declaration blocks | $($document.summary.oclDeclarationCases) |")
$markdown.Add("| ``test_expr.in`` expression cases | $($document.summary.expressionCases) |")
$markdown.Add("| Total reference cases | $($document.summary.totalCases) |")
$markdown.Add('')
$markdown.Add('## Initial Statuses')
$markdown.Add('')
$markdown.Add('| Status | Count | Reason |')
$markdown.Add('|---|---:|---|')
$markdown.Add("| `FAILING_FORMAT` | $($document.summary.statusCounts.FAILING_FORMAT) | Original compiler diagnostics require structured normalization. |")
$markdown.Add("| `FAILING_INFRASTRUCTURE` | $($document.summary.statusCounts.FAILING_INFRASTRUCTURE) | Parser/model/import/evaluation reference harness is not part of this step. |")
$markdown.Add("| `UNCLEAR` | $($document.summary.statusCounts.UNCLEAR) | Reserved for cases requiring review. |")
$markdown.Add('')
$markdown.Add('## Boundaries')
$markdown.Add('')
$markdown.Add('- No shell corpus file is included.')
$markdown.Add('- No original USE reference case is executed or marked `PASSING`.')
$markdown.Add('- `context` blocks are source-level units; splitting individual invariants/contracts is later review work.')
$markdown.Add('- Feature tags and OCL 2.4 compatibility are preliminary classifications.')
$markdown.Add('- `parser-reference-cases.json` is the complete machine-readable case list.')

$markdown | Set-Content -LiteralPath $markdownPath -Encoding utf8NoBOM

Write-Output "Parser reference metadata generated: $($sortedCases.Count) cases from $($useFiles.Count) .use, $($failFiles.Count) .fail, and test_expr.in."
