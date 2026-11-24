# JTrac NG Portable Distribution Packaging Script (PowerShell)
param(
    [string]$JettyZipPath = "",
    [string]$WarFilePath = "$PSScriptRoot\..\target\jtrac.war",
    [string]$OutputDirectory = "$PSScriptRoot\..\target",
    [string]$OutputZipName = "jtrac-ng-3.0.0-beta-portable.zip"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$WarFile = (Resolve-Path $WarFilePath -ErrorAction SilentlyContinue)
if (-not $WarFile) {
    $WarFile = [System.IO.Path]::GetFullPath("$ProjectRoot\target\jtrac.war")
} else {
    $WarFile = $WarFile.Path
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  JTrac NG (v3.0.0-beta) Portable Package Builder" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Project Root: $ProjectRoot"

# Helper: Find highest semantic version among jetty-home-*.zip in a directory
function Get-LatestLocalJettyZip {
    param([string]$SearchDir, [switch]$Recurse)
    if (-not (Test-Path $SearchDir)) { return $null }
    $files = if ($Recurse) {
        Get-ChildItem -Path $SearchDir -Filter "jetty-home-*.zip" -File -Recurse -ErrorAction SilentlyContinue
    } else {
        Get-ChildItem -Path $SearchDir -Filter "jetty-home-*.zip" -File -ErrorAction SilentlyContinue
    }
    $candidates = @()
    foreach ($f in $files) {
        if ($f.Length -gt 1048576 -and $f.Name -match '^jetty-home-(\d+\.\d+\.\d+)\.zip$') {
            $candidates += [PSCustomObject]@{
                Version = [version]$Matches[1]
                VersionText = $Matches[1]
                File = $f
            }
        }
    }
    if ($candidates.Count -eq 0) { return $null }
    return ($candidates | Sort-Object Version -Descending | Select-Object -First 1)
}

# 1. Resolve Base Jetty ZIP (Explicit Override -> Online Latest 12.1.x -> Local Latest Fallback)
$JettyDir = [System.IO.Path]::GetFullPath("$ProjectRoot\tools\jetty")
if (-not (Test-Path $JettyDir)) {
    New-Item -ItemType Directory -Path $JettyDir -Force | Out-Null
}

$JettyZip = $null
if (-not [string]::IsNullOrWhiteSpace($JettyZipPath)) {
    $ResolvedCustom = Resolve-Path $JettyZipPath -ErrorAction SilentlyContinue
    if (-not $ResolvedCustom) {
        throw "Specified -JettyZipPath does not exist: $JettyZipPath"
    }
    $JettyZip = $ResolvedCustom.Path
    Write-Host "[1/5] Using explicitly specified Jetty ZIP: $JettyZip" -ForegroundColor Green
} else {
    # Ensure TLS 1.2+ for Maven Central HTTPS connections on Windows PowerShell 5.1
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    } catch {}

    $LocalBest = Get-LatestLocalJettyZip -SearchDir $JettyDir
    $MetadataUrl = "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/maven-metadata.xml"
    $RemoteLatestVerText = $null

    Write-Host "[1/5] Checking Maven Central for latest stable Jetty 12.1.x release..." -ForegroundColor Cyan
    $prevProgress = $ProgressPreference
    $ProgressPreference = 'SilentlyContinue'
    try {
        $resp = Invoke-WebRequest -Uri $MetadataUrl -UseBasicParsing -TimeoutSec 6 -ErrorAction Stop
        $xmlContent = $resp.Content
        if ($xmlContent -is [byte[]]) {
            $xmlContent = [System.Text.Encoding]::UTF8.GetString($xmlContent)
        }
        $verMatches = [regex]::Matches($xmlContent, '<version>(12\.1\.\d+)</version>')
        $remoteCandidates = @()
        foreach ($m in $verMatches) {
            $remoteCandidates += [PSCustomObject]@{
                Version = [version]$m.Groups[1].Value
                Text = $m.Groups[1].Value
            }
        }
        if ($remoteCandidates.Count -gt 0) {
            $RemoteLatestVerText = ($remoteCandidates | Sort-Object Version -Descending | Select-Object -First 1).Text
        }
    } catch {
        Write-Host "       [Offline/Timeout] Could not reach Maven Central ($($_.Exception.Message))." -ForegroundColor Yellow
    } finally {
        $ProgressPreference = $prevProgress
    }

    if ($RemoteLatestVerText) {
        $TargetZip = Join-Path $JettyDir "jetty-home-$RemoteLatestVerText.zip"
        if ((Test-Path $TargetZip) -and ((Get-Item $TargetZip).Length -gt 1048576)) {
            $JettyZip = $TargetZip
            Write-Host "       Local Jetty is already up to date (v$RemoteLatestVerText): $JettyZip" -ForegroundColor Green
        } else {
            # Check local .m2 cache before downloading over network
            $M2Candidate = "$env:USERPROFILE\.m2\repository\org\eclipse\jetty\jetty-home\$RemoteLatestVerText\jetty-home-$RemoteLatestVerText.zip"
            if ((Test-Path $M2Candidate) -and ((Get-Item $M2Candidate).Length -gt 1048576)) {
                Copy-Item -Path $M2Candidate -Destination $TargetZip -Force
                $JettyZip = $TargetZip
                Write-Host "       Copied latest Jetty v$RemoteLatestVerText from local Maven repository to $JettyZip" -ForegroundColor Green
            } else {
                $LocalLabel = if ($LocalBest) { "v$($LocalBest.VersionText)" } else { "none" }
                Write-Host "       Downloading latest Jetty v$RemoteLatestVerText (local: $LocalLabel)..." -ForegroundColor Yellow
                $BaseArtifactUrl = "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/$RemoteLatestVerText/jetty-home-$RemoteLatestVerText.zip"
                $TempDownload = "$TargetZip.tmp"
                $prevProgress = $ProgressPreference
                $ProgressPreference = 'SilentlyContinue'
                try {
                    if (Test-Path $TempDownload) { Remove-Item -Path $TempDownload -Force }
                    Invoke-WebRequest -Uri $BaseArtifactUrl -OutFile $TempDownload -UseBasicParsing -TimeoutSec 120 -ErrorAction Stop

                    # Optional SHA-1 verification against Maven Central checksum
                    $Sha1Resp = $null
                    try {
                        $Sha1Resp = Invoke-WebRequest -Uri "$BaseArtifactUrl.sha1" -UseBasicParsing -TimeoutSec 6 -ErrorAction Stop
                    } catch {}
                    if ($Sha1Resp -and $Sha1Resp.Content) {
                        $rawSha = if ($Sha1Resp.Content -is [byte[]]) { [System.Text.Encoding]::ASCII.GetString($Sha1Resp.Content) } else { [string]$Sha1Resp.Content }
                        if ($rawSha -match '([0-9a-fA-F]{40})') {
                            $expectedSha1 = $Matches[1].ToLowerInvariant()
                            $actualSha1 = (Get-FileHash -Path $TempDownload -Algorithm SHA1).Hash.ToLowerInvariant()
                            if ($actualSha1 -ne $expectedSha1) {
                                throw "SHA-1 checksum mismatch (expected $expectedSha1, got $actualSha1)"
                            }
                        }
                    }

                    Move-Item -Path $TempDownload -Destination $TargetZip -Force
                    $JettyZip = $TargetZip
                    Write-Host "       Successfully downloaded and verified Jetty v$RemoteLatestVerText -> $JettyZip" -ForegroundColor Green
                } catch {
                    if (Test-Path $TempDownload) { Remove-Item -Path $TempDownload -Force -ErrorAction SilentlyContinue }
                    Write-Host "       [WARN] Download failed ($($_.Exception.Message))." -ForegroundColor Yellow
                } finally {
                    $ProgressPreference = $prevProgress
                }
            }
        }
    }

    # Fallback to newest local ZIP in tools/jetty (or .m2 cache) if offline or download failed
    if (-not $JettyZip) {
        $LocalBest = Get-LatestLocalJettyZip -SearchDir $JettyDir
        if ($LocalBest) {
            $JettyZip = $LocalBest.File.FullName
            Write-Host "       Using newest local Jetty (v$($LocalBest.VersionText)): $JettyZip" -ForegroundColor Green
        } else {
            $M2Best = Get-LatestLocalJettyZip -SearchDir "$env:USERPROFILE\.m2\repository\org\eclipse\jetty\jetty-home" -Recurse
            if ($M2Best) {
                $CachedDest = Join-Path $JettyDir $M2Best.File.Name
                Copy-Item -Path $M2Best.File.FullName -Destination $CachedDest -Force
                $JettyZip = $CachedDest
                Write-Host "       Using newest Jetty from local .m2 cache (v$($M2Best.VersionText)): $JettyZip" -ForegroundColor Green
            } else {
                throw "No network access to Maven Central and no local jetty-home-*.zip found in $JettyDir."
            }
        }
    }
}

# 2. Check or Build jtrac WAR
if (-not (Test-Path $WarFile)) {
    Write-Host "[2/5] jtrac.war not found. Building project with Maven..." -ForegroundColor Yellow
    Push-Location $ProjectRoot
    try {
        & mvn clean package -DskipTests
    } finally {
        Pop-Location
    }
    if (-not (Test-Path $WarFile)) {
        throw "Maven build finished but $WarFile was not generated."
    }
} else {
    Write-Host "[2/5] Using existing WAR: $WarFile" -ForegroundColor Green
}

# 3. Assemble Portable Staging Directory
$StagingRoot = "$OutputDirectory\portable-staging"
$BundleDir = "$StagingRoot\jtrac-ng-3.0.0-beta-portable"

if (Test-Path $StagingRoot) {
    Remove-Item -Path $StagingRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $BundleDir -Force | Out-Null

Write-Host "[3/5] Extracting base Jetty 12 server..." -ForegroundColor Yellow
$TempExtract = "$StagingRoot\temp-jetty"
New-Item -ItemType Directory -Path $TempExtract -Force | Out-Null
Expand-Archive -Path $JettyZip -DestinationPath $TempExtract -Force

$ExtractedHome = Get-ChildItem -Path $TempExtract -Directory | Select-Object -First 1
if (-not $ExtractedHome) {
    throw "Invalid Jetty ZIP: no root directory found."
}
Move-Item -Path $ExtractedHome.FullName -Destination "$BundleDir\jetty-home"
Remove-Item -Path $TempExtract -Recurse -Force

Write-Host "[4/5] Initializing Jetty base modules & deploying application..." -ForegroundColor Yellow
Push-Location $BundleDir
try {
    # Find Java 21+ for start.d initialization
    $CandidateJdks = @()
    if ($env:JAVA_HOME) { $CandidateJdks += "$env:JAVA_HOME\bin\java.exe" }
    $CandidateJdks += @(
        "W:\developer\jdk-21.0.9\bin\java.exe",
        "W:\developer\jdk-21\bin\java.exe",
        "$ProjectRoot\jdk-21.0.9\bin\java.exe",
        "$ProjectRoot\jdk-21\bin\java.exe",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-21*\bin\java.exe",
        "$env:ProgramFiles\Java\jdk-21*\bin\java.exe"
    )
    $JavaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($JavaCmd) { $CandidateJdks += $JavaCmd.Source }

    $JavaBin = $null
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        foreach ($cand in $CandidateJdks) {
            $resolvedList = Resolve-Path $cand -ErrorAction SilentlyContinue
            foreach ($r in $resolvedList) {
                if (Test-Path $r.Path) {
                    $verOutput = cmd /c "`"$($r.Path)`" -version 2>&1"
                    if ($verOutput -match 'version "(2[1-9]|[3-9][0-9])\.') {
                        $JavaBin = $r.Path
                        break
                    }
                }
            }
            if ($JavaBin) { break }
        }
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    if (-not $JavaBin) {
        throw "Could not find a valid Java 21+ runtime for Jetty 12 initialization. Please set JAVA_HOME to JDK 21+."
    }
    Write-Host "       Using Java runtime for initialization: $JavaBin" -ForegroundColor Gray
    & $JavaBin -jar "jetty-home\start.jar" --create-startd --add-modules=ee10-deploy,http
    if (-not (Test-Path "$BundleDir\start.d")) {
        throw "Failed to initialize start.d directory in $BundleDir"
    }
} finally {
    Pop-Location
}

# Copy ROOT.war
if (-not (Test-Path "$BundleDir\webapps")) {
    New-Item -ItemType Directory -Path "$BundleDir\webapps" -Force | Out-Null
}
Copy-Item -Path $WarFile -Destination "$BundleDir\webapps\ROOT.war" -Force

# Create data directory
New-Item -ItemType Directory -Path "$BundleDir\data" -Force | Out-Null

# Copy runtime scripts and README from templates
$TemplatesDir = "$ProjectRoot\scripts\templates"
if (Test-Path $TemplatesDir) {
    Copy-Item -Path "$TemplatesDir\*" -Destination $BundleDir -Force
}

# 5. Compress to Final Output ZIP
Write-Host "[5/5] Compressing portable distribution archive..." -ForegroundColor Yellow
$FinalZip = "$OutputDirectory\$OutputZipName"
if (Test-Path $FinalZip) {
    Remove-Item -Path $FinalZip -Force
}

Compress-Archive -Path "$BundleDir\*" -DestinationPath $FinalZip -Force

# Also sync to unzipped directory in output directory for immediate local use
$UnzippedTarget = "$OutputDirectory\jtrac-ng-3.0.0-beta-portable"
Write-Host "       Updating extracted folder: $UnzippedTarget..." -ForegroundColor Gray
if (Test-Path $UnzippedTarget) {
    Remove-Item -Path $UnzippedTarget -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $UnzippedTarget -Force | Out-Null
Copy-Item -Path "$BundleDir\*" -Destination $UnzippedTarget -Recurse -Force

# Clean up staging directory
Remove-Item -Path $StagingRoot -Recurse -Force

$ZipSizeMB = [math]::Round((Get-Item $FinalZip).Length / 1MB, 2)
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  打包成功 (SUCCESS) !" -ForegroundColor Green
Write-Host "  輸出檔案: $FinalZip" -ForegroundColor Green
Write-Host "  檔案大小: $ZipSizeMB MB" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
