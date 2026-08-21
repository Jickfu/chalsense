$ErrorActionPreference = 'Stop'

$CoordinateScale = 1000000
$VectorPath = Join-Path $PSScriptRoot '..\..\docs\test-vectors\coordinates-v1.json'
$VectorSet = Get-Content -Raw -LiteralPath $VectorPath | ConvertFrom-Json

function Round-AwayFromZero([double] $Value) {
    return [int64] [Math]::Round($Value, 0, [MidpointRounding]::AwayFromZero)
}

function Clamp-Value([double] $Value, [double] $Minimum, [double] $Maximum) {
    if ($Minimum -gt $Maximum) {
        throw 'minimum must not exceed maximum'
    }
    return [Math]::Min([Math]::Max($Value, $Minimum), $Maximum)
}

function Evaluate-Vector($Vector) {
    $InputValue = $Vector.input
    switch ($Vector.operation) {
        'sourceToNormalizedX' {
            return Round-AwayFromZero ([decimal]$InputValue.sourceX * $CoordinateScale / [decimal]$InputValue.sourceWidth)
        }
        'sourceToNormalizedY' {
            return Round-AwayFromZero ([decimal]$InputValue.sourceY * $CoordinateScale / [decimal]$InputValue.sourceHeight)
        }
        'sourceWidthToNormalized' {
            return Round-AwayFromZero ([decimal]$InputValue.objectWidth * $CoordinateScale / [decimal]$InputValue.sourceWidth)
        }
        'rationalToInteger' {
            return Round-AwayFromZero ([decimal]$InputValue.numerator / [decimal]$InputValue.denominator)
        }
        'pointerDeltaToTrack' {
            return [ordered]@{
                x = Round-AwayFromZero (([double]$InputValue.end.clientX - [double]$InputValue.start.clientX) * $CoordinateScale / [double]$InputValue.rect.width)
                y = Round-AwayFromZero (([double]$InputValue.end.clientY - [double]$InputValue.start.clientY) * $CoordinateScale / [double]$InputValue.rect.height)
            }
        }
        'piecePosition' {
            return [int64](Clamp-Value ([int64]$InputValue.pieceStartX + [int64]$InputValue.trackX) 0 ($CoordinateScale - [int64]$InputValue.pieceWidth))
        }
        'toleranceDraft1' {
            $Raw = Round-AwayFromZero ([decimal]$InputValue.pieceWidth * [decimal]$InputValue.ratioNumerator / [decimal]$InputValue.ratioDenominator)
            return [int64](Clamp-Value $Raw ([int64]$InputValue.min) ([int64]$InputValue.max))
        }
        'positionAccepted' {
            return [Math]::Abs([int64]$InputValue.finalPieceX - [int64]$InputValue.pieceTargetX) -le [int64]$InputValue.tolerance
        }
        'backingStoreSize' {
            $EffectiveDpr = Clamp-Value ([double]$InputValue.devicePixelRatio) 1 ([double]$InputValue.maxDpr)
            return [ordered]@{
                backingWidth = [Math]::Max(1, (Round-AwayFromZero ([double]$InputValue.cssWidth * $EffectiveDpr)))
                backingHeight = [Math]::Max(1, (Round-AwayFromZero ([double]$InputValue.cssHeight * $EffectiveDpr)))
            }
        }
        default {
            throw "Unsupported operation: $($Vector.operation)"
        }
    }
}

$Failures = [System.Collections.Generic.List[object]]::new()
foreach ($Vector in $VectorSet.vectors) {
    $Actual = Evaluate-Vector $Vector
    $ActualJson = ConvertTo-Json $Actual -Compress
    $ExpectedJson = ConvertTo-Json $Vector.expected -Compress
    if ($ActualJson -ne $ExpectedJson) {
        $Failures.Add([ordered]@{id = $Vector.id; expected = $Vector.expected; actual = $Actual})
    }
}

$PieceStartX = 62500
$PieceTargetX = 593750
$PieceWidth = 156250
$TargetDelta = $PieceTargetX - $PieceStartX
$MatrixCases = 0

foreach ($CssWidth in @(240, 320, 333.3, 480)) {
    $CssHeight = $CssWidth * 180 / 320
    foreach ($Dpr in @(1, 1.25, 1.5, 2, 3)) {
        $BackingWidth = Round-AwayFromZero ($CssWidth * $Dpr)
        $BackingHeight = Round-AwayFromZero ($CssHeight * $Dpr)
        if ($BackingWidth -lt 1 -or $BackingHeight -lt 1) {
            $Failures.Add([ordered]@{id = 'matrix-backing-store'; cssWidth = $CssWidth; dpr = $Dpr})
        }
        foreach ($GrabFraction in @(0.1, 0.5, 0.9)) {
            $GrabX = $CssWidth * ($PieceStartX + $PieceWidth * $GrabFraction) / $CoordinateScale
            $TravelCss = $CssWidth * $TargetDelta / $CoordinateScale
            $TrackX = Round-AwayFromZero (($GrabX + $TravelCss - $GrabX) * $CoordinateScale / $CssWidth)
            $FinalX = [int64](Clamp-Value ($PieceStartX + $TrackX) 0 ($CoordinateScale - $PieceWidth))
            if ($FinalX -ne $PieceTargetX) {
                $Failures.Add([ordered]@{id = 'matrix-grab-point'; cssWidth = $CssWidth; dpr = $Dpr; grabFraction = $GrabFraction; finalX = $FinalX})
            }
            $MatrixCases += 1
        }
    }
}

$Report = [ordered]@{
    vectorSet = $VectorSet.vectorSet
    implementation = 'PowerShell/.NET'
    vectorCases = $VectorSet.vectors.Count
    matrixCases = $MatrixCases
    totalCases = $VectorSet.vectors.Count + $MatrixCases
    failures = $Failures
}

$Report | ConvertTo-Json -Depth 8
if ($Failures.Count -gt 0) {
    exit 1
}
