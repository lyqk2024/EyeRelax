param([Parameter(Mandatory=$true)][string]$Launcher,[Parameter(Mandatory=$true)][string]$LockPath,[Parameter(Mandatory=$true)][string]$TaskName,[Parameter(Mandatory=$true)][string]$UserSid,[string]$TaskPath='\EyeRelax\')
$ErrorActionPreference='Stop'
function Test-ApplicationLock {
    if(-not (Test-Path -LiteralPath $LockPath)){return $false}
    $stream=$null
    try {
        $stream=[IO.File]::Open($LockPath,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::ReadWrite)
        $stream.Lock(0,1)
        $stream.Unlock(0,1)
        return $false
    } catch {return $true} finally {if($null -ne $stream){$stream.Dispose()}}
}
function Test-UserLoggedIn {
    foreach($process in @(Get-CimInstance Win32_Process -Filter "Name='explorer.exe'" -ErrorAction SilentlyContinue)) {
        $owner=Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid -ErrorAction SilentlyContinue
        if($owner.Sid -eq $UserSid){return $true}
    }
    return $false
}
while($true) {
    # The app and the installer hold the same exclusive lock. While updating,
    # the lock belongs to the installer and the watchdog must not relaunch.
    if((Test-Path -LiteralPath $Launcher) -and -not (Test-ApplicationLock) -and (Test-UserLoggedIn)) {
        try {Start-ScheduledTask -TaskName $TaskName -TaskPath $TaskPath -ErrorAction Stop} catch {}
        Start-Sleep -Seconds 6
    } else {Start-Sleep -Seconds 2}
}
