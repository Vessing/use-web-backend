[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$referenceRoot = Join-Path $RepositoryRoot 'src/test/resources/reference'
$shellRoot = Join-Path $referenceRoot 'original-use/shell'
$inventoryPath = Join-Path $referenceRoot 'original-use/inventory.json'
$metadataRoot = Join-Path $referenceRoot 'converted/metadata'
$jsonPath = Join-Path $metadataRoot 'shell-ocl-reference-cases.json'
$markdownPath = Join-Path $metadataRoot 'shell-ocl-reference-cases.md'

if (-not (Test-Path -LiteralPath $shellRoot -PathType Container)) {
    throw "Shell reference corpus not found: $shellRoot"
}
if (-not (Test-Path -LiteralPath $inventoryPath -PathType Leaf)) {
    throw 'File inventory not found. Run generate-original-use-inventory.ps1 first.'
}

New-Item -ItemType Directory -Force -Path $metadataRoot | Out-Null

$inventory = Get-Content -LiteralPath $inventoryPath -Raw | ConvertFrom-Json
$inventoryByPath = @{}
foreach ($file in $inventory.files) { $inventoryByPath[$file.path] = $file }

function Test-Pattern {
    param([string]$Text, [string]$Pattern)
    return [regex]::IsMatch($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Multiline)
}

function Get-PathHash {
    param([string]$SourceFile)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($SourceFile.ToLowerInvariant().Replace('\', '/'))
    return ([Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes))).Substring(0, 12)
}

function Get-CaseId {
    param([string]$SourceFile, [int]$SourceLine)
    return 'USE-SHELL-{0}-L{1:D6}' -f (Get-PathHash $SourceFile), $SourceLine
}

function Get-FeatureTags {
    param([string]$Expression)

    $patterns = [ordered]@{
        OCL_UNDEFINED           = '\b(?:oclUndefined|undefined|null|OclVoid)\b'
        OCL_LET                 = '\blet\b'
        OCL_IF                  = '\bif\b[\s\S]*\bthen\b[\s\S]*\belse\b'
        OCL_ALL_INSTANCES       = '\ballInstances\b'
        OCL_IMPLIES             = '\bimplies\b'
        OCL_TYPE_OPERATION      = '\boclIs(?:KindOf|TypeOf|Undefined|Invalid|New)\b|\boclAsType\b'
        OCL_PRE_STATE           = '@pre\b'
        OCL_RESULT              = '\bresult\b'
        ITERATOR_FOR_ALL        = '\bforAll\b'
        ITERATOR_EXISTS         = '\bexists\b'
        ITERATOR_SELECT         = '\bselect\b'
        ITERATOR_REJECT         = '\breject\b'
        ITERATOR_COLLECT        = '\bcollect(?:Nested)?\b'
        ITERATOR_ITERATE        = '\biterate\b'
        ITERATOR_ANY            = '\bany\b'
        ITERATOR_ONE            = '\bone\b'
        ITERATOR_IS_UNIQUE      = '\bisUnique\b'
        ITERATOR_SORTED_BY      = '\bsortedBy\b'
        ITERATOR_CLOSURE        = '\bclosure\b'
        COLLECTION_SET          = '\bSet\s*[({]'
        COLLECTION_BAG          = '\bBag\s*[({]'
        COLLECTION_SEQUENCE     = '\bSequence\s*[({]'
        COLLECTION_ORDERED_SET  = '\bOrderedSet\s*[({]'
        COLLECTION_UNION        = '\bunion\b'
        COLLECTION_INTERSECTION = '\bintersection\b'
        COLLECTION_FLATTEN      = '\bflatten\b'
        COLLECTION_INCLUDING    = '\bincluding\b'
        COLLECTION_EXCLUDING    = '\bexcluding\b'
        COLLECTION_INCLUDES     = '\bincludes(?:All)?\b'
        COLLECTION_EXCLUDES     = '\bexcludes(?:All)?\b'
        STRING_OPERATION        = "'(?:[^']|'')*'\s*\.\s*(?:size|concat|substring|toUpper|toLower|matches)\b"
        ARITHMETIC              = '(?:^|\s)(?:\+|-|\*|/|div\b|mod\b)'
    }

    $tags = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $patterns.GetEnumerator()) {
        if (Test-Pattern $Expression $entry.Value) { $tags.Add($entry.Key) }
    }
    if ($Expression -match '(?:->|\.)\s*\w+\s*(?!\()\b') { $tags.Add('PARAMETERLESS_CALL_SYNTAX') }
    if ($Expression -match '\b\w+(?:\.\w+){2,}') { $tags.Add('NAVIGATION_CHAIN') }
    return @($tags | Sort-Object -Unique)
}

function Get-DependencySteps {
    param([string[]]$FeatureTags)

    $steps = [System.Collections.Generic.List[int]]::new()
    foreach ($tag in $FeatureTags) {
        switch -Regex ($tag) {
            '^OCL_UNDEFINED$' { $steps.Add(10); break }
            '^ARITHMETIC$|^STRING_OPERATION$|^OCL_IMPLIES$|^PARAMETERLESS_CALL_SYNTAX$|^NAVIGATION_CHAIN$' { $steps.Add(11); break }
            '^COLLECTION_' { $steps.Add(12); break }
            '^ITERATOR_(FOR_ALL|EXISTS)$' { $steps.Add(18); break }
            '^ITERATOR_(SELECT|REJECT)$' { $steps.Add(19); break }
            '^ITERATOR_COLLECT$' { $steps.Add(20); break }
            '^ITERATOR_(ANY|ONE|IS_UNIQUE|SORTED_BY)$' { $steps.Add(21); break }
            '^ITERATOR_(ITERATE|CLOSURE)$' { $steps.Add(22); break }
            '^OCL_IF$' { $steps.Add(23); break }
            '^OCL_LET$' { $steps.Add(24); break }
            '^OCL_ALL_INSTANCES$' { $steps.Add(25); break }
            '^OCL_TYPE_OPERATION$' { $steps.Add(27); break }
            '^OCL_(PRE_STATE|RESULT)$' { $steps.Add(29); break }
        }
    }
    return @($steps | Sort-Object -Unique)
}

function Get-GapIds {
    param([string[]]$FeatureTags, [bool]$RequiresSetup, [bool]$OperationTrace)

    $gaps = [System.Collections.Generic.List[string]]::new()
    $gaps.Add('OCL-GAP-017D')
    $gaps.Add('OCL-GAP-017E')
    if ($RequiresSetup) {
        $gaps.Add('OCL-GAP-017F')
        $gaps.Add('OCL-GAP-018')
    }
    if ($OperationTrace) { $gaps.Add('OCL-GAP-012') }

    foreach ($tag in $FeatureTags) {
        switch -Regex ($tag) {
            '^OCL_UNDEFINED$' { $gaps.Add('OCL-GAP-014'); break }
            '^OCL_IMPLIES$' { $gaps.Add('OCL-GAP-002'); break }
            '^ARITHMETIC$|^STRING_OPERATION$|^PARAMETERLESS_CALL_SYNTAX$' { $gaps.Add('OCL-GAP-003'); break }
            '^NAVIGATION_CHAIN$' { $gaps.Add('OCL-GAP-008'); break }
            '^COLLECTION_' { $gaps.Add('OCL-GAP-004'); break }
            '^ITERATOR_' { $gaps.Add('OCL-GAP-007'); break }
            '^OCL_(LET|IF)$' { $gaps.Add('OCL-GAP-009'); break }
            '^OCL_ALL_INSTANCES$' { $gaps.Add('OCL-GAP-010'); break }
            '^OCL_TYPE_OPERATION$' { $gaps.Add('OCL-GAP-013'); break }
        }
    }
    return @($gaps | Sort-Object -Unique)
}

function Get-ExpectedResult {
    param([string[]]$ExpectedLines)

    if ($ExpectedLines.Count -eq 0) {
        return [pscustomobject][ordered]@{
            kind = 'INFRASTRUCTURE'
            type = $null
            summary = $null
            isStructured = $false
        }
    }

    $first = $ExpectedLines[0]
    if ($first -match '^\*->\s*(.*)\s:\s(.+)\s*$') {
        $rawValue = $Matches[1].Trim()
        $type = $Matches[2].Trim()
        $collectionKind = $null
        if ($type -match '^(Set|Bag|Sequence|OrderedSet|Collection)\s*\(') { $collectionKind = $Matches[1].ToUpperInvariant() }
        $valueKind = if ($collectionKind) { 'COLLECTION' } elseif ($type -eq 'Boolean') { 'BOOLEAN' } elseif ($type -eq 'Integer') { 'INTEGER' } elseif ($type -eq 'Real') { 'REAL' } elseif ($type -eq 'String') { 'STRING' } elseif ($type -eq 'OclVoid' -or $rawValue -eq 'null') { 'UNDEFINED' } else { 'OBJECT_OR_OTHER' }
        return [pscustomobject][ordered]@{
            kind = 'VALUE'
            type = $type
            summary = [pscustomobject][ordered]@{
                valueKind = $valueKind
                rawValue = $rawValue
                collectionKind = $collectionKind
                orderRelevant = $collectionKind -in @('SEQUENCE', 'ORDEREDSET')
            }
            isStructured = $true
        }
    }

    return [pscustomobject][ordered]@{
        kind = 'DIAGNOSTIC'
        type = $null
        summary = [pscustomobject][ordered]@{
            rawLines = @($ExpectedLines)
        }
        isStructured = $false
    }
}

function Get-CommandType {
    param([string]$Line)
    $trimmed = $Line.Trim()
    if ($trimmed -match '^!\s*(create|new)\b') { return 'OBJECT_CREATE' }
    if ($trimmed -match '^!\s*(set|\w+\.\w+\s*:=)') { return 'ATTRIBUTE_SET' }
    if ($trimmed -match '^!\s*(insert|delete)\b') { return 'LINK_CHANGE' }
    if ($trimmed -match '^!\s*(openter|opexit)\b') { return 'OPERATION_TRACE' }
    if ($trimmed -match '^!') { return 'SOIL_COMMAND' }
    if ($trimmed -match '^(open|read|readq)\b') { return 'MODEL_OR_COMMAND_LOAD' }
    if ($trimmed -match '^(check|constraints)\b') { return 'VALIDATION_COMMAND' }
    if ($trimmed -match '^(gen|generate|coverage)\b') { return 'GENERATOR_COMMAND' }
    return 'SHELL_COMMAND'
}

function Test-CommandLine {
    param([string]$Line)
    $trimmed = $Line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('--')) { return $false }
    if ($trimmed.StartsWith('?') -or $trimmed.StartsWith('*') -or $trimmed -eq '.' -or $trimmed -eq '\' -or $trimmed -match '^exit\b') { return $false }
    return $true
}

function Get-RelatedResources {
    param([string]$InputBaseName)
    return @(
        Get-ChildItem -LiteralPath $shellRoot -File |
            Where-Object { $_.BaseName -eq $InputBaseName -and $_.Extension -ne '.in' } |
            ForEach-Object { 'shell/' + $_.Name }
    ) | Sort-Object -Unique
}

function Get-ModelImports {
    param([string]$ModelPath)
    if (-not $ModelPath) { return @() }
    $absolute = Join-Path $referenceRoot ('original-use/' + $ModelPath.Replace('/', '\'))
    if (-not (Test-Path -LiteralPath $absolute -PathType Leaf)) { return @() }
    $text = Get-Content -LiteralPath $absolute -Raw
    return @(
        foreach ($match in [regex]::Matches($text, '\bimport\b[^\r\n]*?\bfrom\s+"([^"]+)"', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
            $match.Groups[1].Value.Replace('\', '/')
        }
    ) | Sort-Object -Unique
}

$cases = [System.Collections.Generic.List[object]]::new()
$fileAnalyses = [System.Collections.Generic.List[object]]::new()
$inputFiles = @(Get-ChildItem -LiteralPath $shellRoot -File -Filter '*.in' | Sort-Object Name)

foreach ($inputFile in $inputFiles) {
    $sourceFile = 'shell/' + $inputFile.Name
    if (-not $inventoryByPath.ContainsKey($sourceFile)) { throw "Missing inventory record: $sourceFile" }

    $lines = @(Get-Content -LiteralPath $inputFile.FullName)
    $baseName = $inputFile.BaseName
    $modelCandidate = Join-Path $shellRoot ($baseName + '.use')
    $modelFile = if (Test-Path -LiteralPath $modelCandidate -PathType Leaf) { 'shell/' + $baseName + '.use' } else { $null }
    $relatedResources = @(Get-RelatedResources $baseName)
    $imports = @(Get-ModelImports $modelFile)
    $commandLineNumbers = [System.Collections.Generic.List[int]]::new()
    $commandTypes = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $commandsSinceQuery = [System.Collections.Generic.List[int]]::new()
    $queryCount = 0
    $expectedLineCount = 0
    $lineIndex = 0

    while ($lineIndex -lt $lines.Count) {
        $line = $lines[$lineIndex]
        if (-not $line.TrimStart().StartsWith('?')) {
            if (Test-CommandLine $line) {
                $lineNumber = $lineIndex + 1
                $commandLineNumbers.Add($lineNumber)
                $commandsSinceQuery.Add($lineNumber)
                [void]$commandTypes.Add((Get-CommandType $line))
            }
            if ($line.TrimStart().StartsWith('*')) { $expectedLineCount++ }
            $lineIndex++
            continue
        }

        $queryStartIndex = $lineIndex
        $queryLines = [System.Collections.Generic.List[string]]::new()
        $queryLines.Add($line.Substring($line.IndexOf('?') + 1))
        $lineIndex++

        if ($lineIndex -lt $lines.Count -and -not $lines[$lineIndex].TrimStart().StartsWith('*')) {
            $scan = $lineIndex
            $terminator = -1
            while ($scan -lt $lines.Count) {
                $candidate = $lines[$scan].Trim()
                if ($candidate -eq '.') { $terminator = $scan; break }
                if ($candidate.StartsWith('?') -or $candidate.StartsWith('*') -or $candidate.StartsWith('!') -or $candidate -match '^(exit|open|read|check|gen|constraints|coverage)\b') { break }
                if ([string]::IsNullOrWhiteSpace($candidate) -or $candidate.StartsWith('#')) { break }
                $scan++
            }
            if ($terminator -ge 0) {
                while ($lineIndex -lt $terminator) {
                    $queryLines.Add($lines[$lineIndex])
                    $lineIndex++
                }
                $lineIndex++
            }
        }

        $queryEndIndex = $lineIndex - 1
        $expectedLines = [System.Collections.Generic.List[string]]::new()
        $expectedStartLine = 0
        while ($lineIndex -lt $lines.Count -and $lines[$lineIndex].TrimStart().StartsWith('*')) {
            if ($expectedStartLine -eq 0) { $expectedStartLine = $lineIndex + 1 }
            $expectedLines.Add($lines[$lineIndex])
            $expectedLineCount++
            $lineIndex++
        }
        $blockEndLine = if ($expectedLines.Count -gt 0) { $lineIndex } else { $queryEndIndex + 1 }

        $queryCount++
        $originalInput = $queryLines -join [Environment]::NewLine
        $normalizedExpression = (($queryLines | ForEach-Object { $_.Trim() }) -join ' ').Trim()
        $features = @(Get-FeatureTags $normalizedExpression)
        $requiresSetup = $modelFile -ne $null -or $commandLineNumbers.Count -gt 0
        $operationTrace = $commandTypes.Contains('OPERATION_TRACE') -or $features -contains 'OCL_PRE_STATE' -or $features -contains 'OCL_RESULT'
        $expected = Get-ExpectedResult @($expectedLines)
        $category = if ($operationTrace) { 'OCL_CONTRACT_EVALUATION' } elseif ($expected.kind -eq 'DIAGNOSTIC') { 'OCL_DIAGNOSTIC' } elseif ($expected.kind -eq 'VALUE') { 'OCL_EVALUATION' } else { 'OCL_QUERY_UNASSERTED' }

        if ($requiresSetup) {
            $status = 'FAILING_INFRASTRUCTURE'
            $failureCategory = if ($operationTrace) { 'OPERATION_TRACE_HARNESS_NOT_AVAILABLE' } else { 'MODEL_OR_SNAPSHOT_SETUP_NOT_AVAILABLE' }
        }
        elseif ($expectedLines.Count -gt 0) {
            $status = 'FAILING_FORMAT'
            $failureCategory = if ($expected.kind -eq 'DIAGNOSTIC') { 'SHELL_DIAGNOSTIC_NOT_NORMALIZED' } else { 'SHELL_VALUE_FORMAT_NOT_REVIEWED' }
        }
        else {
            $status = 'UNCLEAR'
            $failureCategory = 'MISSING_EXPECTED_OUTPUT'
        }

        $deltaStart = if ($commandsSinceQuery.Count -gt 0) { $commandsSinceQuery[0] } else { $null }
        $deltaEnd = if ($commandsSinceQuery.Count -gt 0) { $commandsSinceQuery[$commandsSinceQuery.Count - 1] } else { $null }
        $setup = [pscustomobject][ordered]@{
            modelFile = $modelFile
            imports = $imports
            relatedResources = $relatedResources
            replaySourceFile = $sourceFile
            replayThroughLine = $queryStartIndex
            setupCommandCountBeforeQuery = $commandLineNumbers.Count
            setupCommandTypes = @($commandTypes | Sort-Object)
            commandDelta = [pscustomobject][ordered]@{
                count = $commandsSinceQuery.Count
                startLine = $deltaStart
                endLine = $deltaEnd
            }
            requiresSnapshot = $commandLineNumbers.Count -gt 0
            requiresOperationTrace = $operationTrace
        }
        $commandsSinceQuery.Clear()

        $cases.Add([pscustomobject][ordered]@{
            id = Get-CaseId $sourceFile ($queryStartIndex + 1)
            sourceFile = $sourceFile
            sourceLine = $queryStartIndex + 1
            sourceEndLine = $blockEndLine
            originalInput = '?' + $originalInput
            originalExpectedOutput = @($expectedLines)
            expectedOutputSourceFile = if ($expectedLines.Count -gt 0) { $sourceFile } else { $null }
            expectedOutputSourceLine = if ($expectedStartLine -gt 0) { $expectedStartLine } else { $null }
            normalizedExpression = $normalizedExpression
            category = $category
            standardStatus = 'UNCLEAR'
            featureTags = $features
            setup = $setup
            expectedResultKind = $expected.kind
            expectedType = $expected.type
            expectedValueSummary = $expected.summary
            currentStatus = $status
            failureCategory = $failureCategory
            gapIds = @(Get-GapIds $features $requiresSetup $operationTrace)
            targetTestType = if ($operationTrace) { 'VALIDATION' } else { 'EVALUATOR' }
            roadMapStep = 6
            dependencySteps = @(Get-DependencySteps $features)
            notes = 'Extracted without executing the USE shell. Setup metadata describes how a future harness can replay source commands.'
        })
    }

    $fileAnalyses.Add([pscustomobject][ordered]@{
        sourceFile = $sourceFile
        lineCount = $lines.Count
        queryCount = $queryCount
        expectedOutputLineCount = $expectedLineCount
        commandCount = $commandLineNumbers.Count
        commandTypes = @($commandTypes | Sort-Object)
        modelFile = $modelFile
        imports = $imports
        relatedResources = $relatedResources
        containsOclQueries = $queryCount -gt 0
        notes = if ($queryCount -gt 0) { 'All detected ? query blocks are represented as reference cases.' } else { 'No ? query block detected; retained as analyzed shell-only/setup input.' }
    })
}

$sortedCases = @($cases | Sort-Object sourceFile, sourceLine)
$sortedFiles = @($fileAnalyses | Sort-Object sourceFile)
$ids = @($sortedCases.id)
if (@($ids | Sort-Object -Unique).Count -ne $ids.Count) { throw 'Duplicate shell reference case IDs detected.' }
if ($sortedFiles.Count -ne $inputFiles.Count) { throw 'Not every shell .in file has a file analysis.' }

$statusCounts = [ordered]@{}
foreach ($status in @('FAILING_FORMAT', 'FAILING_INFRASTRUCTURE', 'UNCLEAR')) {
    $statusCounts[$status] = @($sortedCases | Where-Object currentStatus -eq $status).Count
}
$categoryCounts = [ordered]@{}
foreach ($group in $sortedCases | Group-Object category | Sort-Object Name) { $categoryCounts[$group.Name] = $group.Count }

$document = [pscustomobject][ordered]@{
    schemaVersion = '1.0'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    sourceArea = 'shell'
    sourceRoot = 'src/test/resources/reference/original-use/shell'
    summary = [pscustomobject][ordered]@{
        inputFiles = $inputFiles.Count
        filesWithQueries = @($sortedFiles | Where-Object containsOclQueries).Count
        filesWithoutQueries = @($sortedFiles | Where-Object { -not $_.containsOclQueries }).Count
        totalCases = $sortedCases.Count
        casesWithExpectedOutput = @($sortedCases | Where-Object { @($_.originalExpectedOutput).Count -gt 0 }).Count
        casesWithoutExpectedOutput = @($sortedCases | Where-Object { @($_.originalExpectedOutput).Count -eq 0 }).Count
        multilineQueries = @($sortedCases | Where-Object { $_.originalInput -match "`r?`n" }).Count
        categoryCounts = [pscustomobject]$categoryCounts
        statusCounts = [pscustomobject]$statusCounts
    }
    assumptions = @(
        'A shell OCL query starts with ?; a multiline query ends at a line containing only a period.',
        'Only immediately following lines prefixed with * belong to the query expectation block.',
        'Commands are not executed. A future harness can replay the original .in file through replayThroughLine.',
        'Expected value/type summaries are conservative; raw output is retained for review.',
        'No case is PASSING before execution by the future separate reference harness.'
    )
    files = $sortedFiles
    cases = $sortedCases
}

$document | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM

$markdown = [System.Collections.Generic.List[string]]::new()
$markdown.Add('# Original USE Shell OCL Reference Cases')
$markdown.Add('')
$markdown.Add('This report analyzes all shell `.in` files and extracts OCL `?` blocks without executing the USE shell.')
$markdown.Add('')
$markdown.Add('## Summary')
$markdown.Add('')
$markdown.Add('| Metric | Count |')
$markdown.Add('|---|---:|')
$markdown.Add("| Shell `.in` files | $($document.summary.inputFiles) |")
$markdown.Add("| Files with OCL queries | $($document.summary.filesWithQueries) |")
$markdown.Add("| Files without OCL queries | $($document.summary.filesWithoutQueries) |")
$markdown.Add("| Extracted OCL query cases | $($document.summary.totalCases) |")
$markdown.Add("| Cases with expected output | $($document.summary.casesWithExpectedOutput) |")
$markdown.Add("| Cases without expected output | $($document.summary.casesWithoutExpectedOutput) |")
$markdown.Add("| Multiline queries | $($document.summary.multilineQueries) |")
$markdown.Add('')
$markdown.Add('## Categories')
$markdown.Add('')
$markdown.Add('| Category | Count |')
$markdown.Add('|---|---:|')
foreach ($category in $categoryCounts.Keys) { $markdown.Add("| ``$category`` | $($categoryCounts[$category]) |") }
$markdown.Add('')
$markdown.Add('## Initial Statuses')
$markdown.Add('')
$markdown.Add('| Status | Count | Reason |')
$markdown.Add('|---|---:|---|')
$markdown.Add("| `FAILING_FORMAT` | $($statusCounts.FAILING_FORMAT) | Shell values or diagnostics require review and structured normalization. |")
$markdown.Add("| `FAILING_INFRASTRUCTURE` | $($statusCounts.FAILING_INFRASTRUCTURE) | Model, snapshot, imports, or operation-trace setup is required. |")
$markdown.Add("| `UNCLEAR` | $($statusCounts.UNCLEAR) | Query has no directly associated expected `*` output. |")
$markdown.Add('')
$markdown.Add('## Boundaries')
$markdown.Add('')
$markdown.Add('- Shell commands are recorded as setup metadata but are not executed or converted.')
$markdown.Add('- Only `?` query blocks become reference cases; shell-only files remain visible in the file analysis.')
$markdown.Add('- `*` output remains available verbatim even when a conservative value/type summary is present.')
$markdown.Add('- No case is marked `PASSING` or `FAILING_GAP` without the separate reference harness.')
$markdown.Add('- `shell-ocl-reference-cases.json` is the complete machine-readable result.')

$markdown | Set-Content -LiteralPath $markdownPath -Encoding utf8NoBOM

Write-Output "Shell OCL metadata generated: $($sortedCases.Count) query cases from $($inputFiles.Count) .in files."
