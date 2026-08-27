[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$referenceRoot = Join-Path $RepositoryRoot 'src/test/resources/reference/original-use'
$areas = [ordered]@{
    parser = Join-Path $referenceRoot 'parser'
    shell  = Join-Path $referenceRoot 'shell'
}

foreach ($entry in $areas.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $entry.Value -PathType Container)) {
        throw "Reference area '$($entry.Key)' not found: $($entry.Value)"
    }
}

function Test-Pattern {
    param([string]$Text, [string]$Pattern)
    return [regex]::IsMatch($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Multiline)
}

function Get-DetectedContentTypes {
    param([string]$Extension, [string]$Text)

    $types = [System.Collections.Generic.List[string]]::new()
    switch ($Extension) {
        '.use'  { $types.Add('USE_MODEL') }
        '.fail' { $types.Add('NEGATIVE_PARSER_INPUT') }
        '.in'   { $types.Add('SHELL_INPUT') }
        '.cmd'  { $types.Add('COMMAND_SCRIPT') }
        '.assl' { $types.Add('ASSL_SCRIPT') }
        '.clt'  { $types.Add('CLASS_LAYOUT') }
        '.olt'  { $types.Add('OBJECT_LAYOUT') }
        default { $types.Add('OTHER_RESOURCE') }
    }

    if (Test-Pattern $Text '^\s*\?') { $types.Add('OCL_QUERY') }
    if (Test-Pattern $Text '^\s*\*') { $types.Add('EXPECTED_SHELL_OUTPUT') }
    if (Test-Pattern $Text '\b(import|include)\b') { $types.Add('IMPORT_REFERENCE') }
    if (Test-Pattern $Text '\b(context|inv|pre|post|derive|init|body|def)\b') { $types.Add('OCL_DECLARATION') }

    return @($types | Sort-Object -Unique)
}

function Get-PossibleFeatureTags {
    param([string]$Extension, [string]$Text, [string]$RelativePath)

    $patterns = [ordered]@{
        OCL_INVARIANT          = '\binv\b'
        OCL_PRECONDITION       = '\bpre\b'
        OCL_POSTCONDITION      = '\bpost\b'
        OCL_DERIVED            = '\bderive\b|\bderived\b'
        OCL_INIT               = '\binit\b'
        OCL_LET                = '\blet\b'
        OCL_IF                 = '\bif\b[\s\S]*\bthen\b[\s\S]*\belse\b'
        OCL_ALL_INSTANCES      = '\ballInstances\b'
        ITERATOR_FOR_ALL       = '\bforAll\b'
        ITERATOR_EXISTS        = '\bexists\b'
        ITERATOR_SELECT        = '\bselect\b'
        ITERATOR_REJECT        = '\breject\b'
        ITERATOR_COLLECT       = '\bcollect(?:Nested)?\b'
        ITERATOR_ITERATE       = '\biterate\b'
        COLLECTION_SET         = '\bSet\s*[({]'
        COLLECTION_BAG         = '\bBag\s*[({]'
        COLLECTION_SEQUENCE    = '\bSequence\s*[({]'
        COLLECTION_ORDERED_SET = '\bOrderedSet\s*[({]'
        UML_GENERALIZATION     = '\babstract\s+class\b|\b(?:subsets|redefines)\b|\bclass\s+\w+\s*<\s*\w+'
        UML_ENUM               = '\benum\b'
        UML_STATE_MACHINE      = '\bstatemachine\b'
        USE_IMPORT             = '\bimport\b|\binclude\b'
    }

    $tags = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $patterns.GetEnumerator()) {
        if (Test-Pattern $Text $entry.Value) { $tags.Add($entry.Key) }
    }

    if ($Extension -eq '.in' -and (Test-Pattern $Text '^\s*\?')) { $tags.Add('SHELL_OCL_QUERY') }
    if ($Extension -eq '.in' -and (Test-Pattern $Text '^\s*\*')) { $tags.Add('SHELL_EXPECTED_OUTPUT') }
    if ($Extension -eq '.fail') { $tags.Add('NEGATIVE_PARSER_CASE') }
    if ($Extension -eq '.cmd') { $tags.Add('SOIL_COMMAND') }
    if ($Extension -eq '.assl') { $tags.Add('ASSL') }
    if ($Extension -in @('.clt', '.olt')) { $tags.Add('LAYOUT') }
    if ($RelativePath -match '(^|/)imports?(/|$)|_imports?\.') { $tags.Add('IMPORT_FIXTURE') }

    return @($tags | Sort-Object -Unique)
}

$records = [System.Collections.Generic.List[object]]::new()

foreach ($entry in $areas.GetEnumerator()) {
    $area = $entry.Key
    $areaRoot = (Resolve-Path -LiteralPath $entry.Value).Path

    foreach ($file in Get-ChildItem -LiteralPath $areaRoot -Recurse -File | Sort-Object FullName) {
        $relativeWithinArea = $file.FullName.Substring($areaRoot.Length).TrimStart('\').Replace('\', '/')
        $path = "$area/$relativeWithinArea"
        $extension = $file.Extension.ToLowerInvariant()
        $notes = [System.Collections.Generic.List[string]]::new()

        try {
            $text = Get-Content -LiteralPath $file.FullName -Raw
        }
        catch {
            $text = ''
            $notes.Add('Content detection unavailable; file could not be read as text.')
        }

        $contentTypes = @(Get-DetectedContentTypes -Extension $extension -Text $text)
        $featureTags = @(Get-PossibleFeatureTags -Extension $extension -Text $text -RelativePath $relativeWithinArea)
        $candidate = $extension -in @('.use', '.fail', '.in') -or $contentTypes -contains 'OCL_DECLARATION' -or $contentTypes -contains 'OCL_QUERY'

        if ($candidate) {
            $notes.Add('Candidate is heuristic and requires block-level review in later roadmap steps.')
        }
        else {
            $notes.Add('Supporting or non-OCL resource; relevance requires later review.')
        }

        $records.Add([pscustomobject][ordered]@{
            path                    = $path
            originalArea            = $area
            fileExtension           = $extension
            size                    = [long]$file.Length
            sha256                  = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            detectedContentTypes    = $contentTypes
            possibleFeatureTags     = $featureTags
            referenceSuiteCandidate = [bool]$candidate
            notes                   = @($notes)
        })
    }
}

$sortedRecords = @($records | Sort-Object path)
$extensionCounts = [ordered]@{}
foreach ($group in $sortedRecords | Group-Object fileExtension | Sort-Object Name) {
    $key = if ([string]::IsNullOrEmpty($group.Name)) { '<none>' } else { $group.Name }
    $extensionCounts[$key] = $group.Count
}

$areaCounts = [ordered]@{}
foreach ($area in $areas.Keys) {
    $areaCounts[$area] = @($sortedRecords | Where-Object originalArea -eq $area).Count
}

$subdirectories = @(
    foreach ($entry in $areas.GetEnumerator()) {
        $areaRoot = (Resolve-Path -LiteralPath $entry.Value).Path
        foreach ($directory in Get-ChildItem -LiteralPath $areaRoot -Recurse -Directory) {
            $relative = $directory.FullName.Substring($areaRoot.Length).TrimStart('\').Replace('\', '/')
            if ($relative) { "$($entry.Key)/$relative" }
        }
    }
) | Sort-Object -Unique

$inventory = [ordered]@{
    schemaVersion = '1.0'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    corpusRoot = 'src/test/resources/reference/original-use'
    summary = [ordered]@{
        totalFiles = $sortedRecords.Count
        filesByArea = $areaCounts
        filesByExtension = $extensionCounts
        referenceSuiteCandidates = @($sortedRecords | Where-Object referenceSuiteCandidate).Count
        supportingOrUnclearFiles = @($sortedRecords | Where-Object { -not $_.referenceSuiteCandidate }).Count
        subdirectories = $subdirectories
    }
    assumptions = @(
        'Content types, feature tags, and candidate flags are heuristic file-level hints.',
        'No Reference Case, execution status, expected result, or roadmap status is assigned in this inventory.',
        'Original resources are not modified by this generator.'
    )
    files = $sortedRecords
}

$jsonPath = Join-Path $referenceRoot 'inventory.json'
$markdownPath = Join-Path $referenceRoot 'inventory.md'
$inventory | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM

$markdown = [System.Collections.Generic.List[string]]::new()
$markdown.Add('# Original USE Test Resource Inventory')
$markdown.Add('')
$markdown.Add('Generated from the unchanged resources below `parser/` and `shell/`.')
$markdown.Add('This is a file inventory, not a classification of executable reference cases.')
$markdown.Add('')
$markdown.Add('## Summary')
$markdown.Add('')
$markdown.Add('| Metric | Count |')
$markdown.Add('|---|---:|')
$markdown.Add("| Total files | $($sortedRecords.Count) |")
$markdown.Add("| Parser files | $($areaCounts.parser) |")
$markdown.Add("| Shell files | $($areaCounts.shell) |")
$markdown.Add("| Heuristic reference-suite candidates | $(@($sortedRecords | Where-Object referenceSuiteCandidate).Count) |")
$markdown.Add("| Supporting or unclear resources | $(@($sortedRecords | Where-Object { -not $_.referenceSuiteCandidate }).Count) |")
$markdown.Add('')
$markdown.Add('## File Extensions')
$markdown.Add('')
$markdown.Add('| Extension | Count |')
$markdown.Add('|---|---:|')
foreach ($extension in $extensionCounts.Keys) {
    $markdown.Add("| ``$extension`` | $($extensionCounts[$extension]) |")
}
$markdown.Add('')
$markdown.Add('## Subdirectories')
$markdown.Add('')
if ($subdirectories.Count -eq 0) {
    $markdown.Add('No subdirectories detected.')
}
else {
    foreach ($directory in $subdirectories) { $markdown.Add("- ``$directory``") }
}
$markdown.Add('')
$markdown.Add('## Heuristic Categories')
$markdown.Add('')
$markdown.Add('| Content type | Files |')
$markdown.Add('|---|---:|')
$contentTypeNames = @($sortedRecords.detectedContentTypes | Sort-Object -Unique)
foreach ($contentType in $contentTypeNames) {
    $count = @($sortedRecords | Where-Object { $_.detectedContentTypes -contains $contentType }).Count
    $markdown.Add("| ``$contentType`` | $count |")
}
$markdown.Add('')
$markdown.Add('## Important Limitations')
$markdown.Add('')
$markdown.Add('- Tags and candidate flags are heuristic and may contain false positives or false negatives.')
$markdown.Add('- No `.use`, `.fail`, or `.in` blocks are extracted in this roadmap step.')
$markdown.Add('- No `PASSING` or `FAILING_*` status is assigned without an executable reference test.')
$markdown.Add('- `inventory.json` is the complete machine-readable per-file inventory.')

$markdown | Set-Content -LiteralPath $markdownPath -Encoding utf8NoBOM

Write-Output "Inventory generated: $($sortedRecords.Count) files ($($areaCounts.parser) parser, $($areaCounts.shell) shell)."
