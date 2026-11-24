$ErrorActionPreference='Stop'
$sid='__SID__'
$launcher=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__LAUNCHER__'))
$lock=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__LOCK__'))
$enabled=__ENABLED__
$service=New-Object -ComObject Schedule.Service
$service.Connect()
$folder=$null
try {$folder=$service.GetFolder('\EyeRelax')} catch {if($enabled){$folder=$service.GetFolder('\').CreateFolder('EyeRelax','D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;GRGX;;;BU)')}}
$guardName='Guard-'+$sid
$appName='App-'+$sid
if(-not $enabled) {
    foreach($name in @($guardName,$appName)) {
        Stop-ScheduledTask -TaskName $name -TaskPath '\EyeRelax\' -ErrorAction SilentlyContinue
        Unregister-ScheduledTask -TaskName $name -TaskPath '\EyeRelax\' -Confirm:$false -ErrorAction SilentlyContinue
    }
    exit 0
}
if(-not (Test-Path -LiteralPath $launcher -PathType Leaf)){throw 'The installed application launcher does not exist.'}
$directory=Join-Path $env:ProgramFiles ('EyeRelaxGuard\'+$sid)
New-Item -ItemType Directory -Path $directory -Force | Out-Null
& "$env:SystemRoot\System32\icacls.exe" $directory '/inheritance:r' '/grant:r' '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-32-545:(OI)(CI)RX' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Cannot protect the watchdog directory.'}
& "$env:SystemRoot\System32\icacls.exe" $directory '/setowner' '*S-1-5-32-544' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Cannot protect the watchdog owner.'}
$script=Join-Path $directory 'guard-watch.ps1'
[IO.File]::WriteAllText($script,[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__WATCH_SCRIPT__')),(New-Object Text.UTF8Encoding($false)))
$powershell=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$userPrincipal=New-ScheduledTaskPrincipal -UserId $sid -LogonType Interactive -RunLevel Limited
$appAction=New-ScheduledTaskAction -Execute $launcher -Argument '--guard-recovery' -WorkingDirectory (Split-Path -Parent $launcher)
$settings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$userTask=New-ScheduledTask -Action $appAction -Principal $userPrincipal -Settings $settings -Description 'EyeRelax interactive recovery launcher; managed through EyeRelax settings.'
Register-ScheduledTask -TaskName $appName -TaskPath '\EyeRelax\' -InputObject $userTask -Force | Out-Null
$folder.GetTask($appName).SetSecurityDescriptor('D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;GRGX;;;'+$sid+')',0)
function Quote-Argument([string]$text){return '"'+$text.Replace('"','')+'"'}
$arguments='-NoProfile -NonInteractive -WindowStyle Hidden -File '+(Quote-Argument $script)+' -Launcher '+(Quote-Argument $launcher)+' -LockPath '+(Quote-Argument $lock)+' -TaskName '+(Quote-Argument $appName)+' -UserSid '+(Quote-Argument $sid)
$action=New-ScheduledTaskAction -Execute $powershell -Argument $arguments
$systemPrincipal=New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$triggers=@((New-ScheduledTaskTrigger -AtStartup),(New-ScheduledTaskTrigger -AtLogOn -User $sid))
$guardSettings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
$task=New-ScheduledTask -Action $action -Trigger $triggers -Principal $systemPrincipal -Settings $guardSettings -Description 'EyeRelax usage-limit watchdog. Restarts unexpected exits; waits while the installer holds its update lock. Administrators can disable or remove this task.'
Stop-ScheduledTask -TaskName $guardName -TaskPath '\EyeRelax\' -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName $guardName -TaskPath '\EyeRelax\' -InputObject $task -Force | Out-Null
$folder.GetTask($guardName).SetSecurityDescriptor('D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;GR;;;'+$sid+')',0)
Start-ScheduledTask -TaskName $guardName -TaskPath '\EyeRelax\'
