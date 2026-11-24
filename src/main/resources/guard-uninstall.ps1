$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)

function Test-SamePath([string]$first,[string]$second) {
    if([string]::IsNullOrWhiteSpace($first) -or [string]::IsNullOrWhiteSpace($second)){return $false}
    return [IO.Path]::GetFullPath($first).TrimEnd('\').Equals([IO.Path]::GetFullPath($second).TrimEnd('\'),[StringComparison]::OrdinalIgnoreCase)
}
function Get-QuotedArgument([string]$arguments,[string]$name) {
    $match=[regex]::Match($arguments,'(?i)(?:^|\s)'+[regex]::Escape($name)+'\s+"([^"]+)"(?=\s|$)')
    if(-not $match.Success){throw '运行保护任务参数无法确认，请在任务计划程序中检查 EyeRelax 任务后重试。'}
    return $match.Groups[1].Value
}
function Assert-OwnedTasks($app,$guard,[string]$sid,[string]$launcher) {
    if($null -ne $app) {
        $definition=$app.Definition
        if($definition.Actions.Count -ne 1 -or $definition.Actions.Item(1).Type -ne 0 -or
           -not (Test-SamePath $definition.Actions.Item(1).Path $launcher) -or $definition.Principal.UserId -ne $sid) {
            throw '运行保护指向其他安装位置或账户，未删除任何任务。请先检查原安装的运行保护。'
        }
    }
    if($null -ne $guard) {
        $definition=$guard.Definition
        if($definition.Actions.Count -ne 1 -or $definition.Actions.Item(1).Type -ne 0) {
            throw '运行保护任务定义无法确认，未删除任何任务。'
        }
        $arguments=$definition.Actions.Item(1).Arguments
        $expectedScript=Join-Path ([Environment]::GetFolderPath('ProgramFiles')) ('EyeRelaxGuard\'+$sid+'\guard-watch.ps1')
        $expectedHost=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
        if(-not (Test-SamePath (Get-QuotedArgument $arguments '-Launcher') $launcher) -or
           -not (Test-SamePath $definition.Actions.Item(1).Path $expectedHost) -or
           -not (Test-SamePath (Get-QuotedArgument $arguments '-File') $expectedScript) -or
           (Get-QuotedArgument $arguments '-UserSid') -ne $sid -or
           (Get-QuotedArgument $arguments '-TaskName') -ne ('App-'+$sid)) {
            throw '运行保护指向其他安装位置或账户，未删除任何任务。请先检查原安装的运行保护。'
        }
    }
}
function Test-MissingTask($exception) {
    for($current=$exception;$null -ne $current;$current=$current.InnerException) {
        if($current.HResult -eq -2147024894 -or $current.HResult -eq -2147024893){return $true}
    }
    return $false
}
function Read-GuardTask($folder,[string]$name) {
    try{return $folder.GetTask($name)}catch{if(Test-MissingTask $_.Exception){return $null};throw}
}
function Stop-OwnedGuard($guard,[int]$waitMilliseconds=3000) {
    $guard.Stop(0)
    $wait=[Diagnostics.Stopwatch]::StartNew()
    while($guard.GetInstances(0).Count -gt 0) {
        if($wait.ElapsedMilliseconds -ge $waitMilliseconds){throw '运行保护仍在运行，请以管理员身份重新运行卸载。'}
        Start-Sleep -Milliseconds 50
    }
}

try {
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
    $sid=$identity.User.Value
    $configuredSid='__SID__'
    if($configuredSid -ne '' -and $configuredSid -ne $sid){throw '运行保护属于其他账户，请在原安装账户中运行卸载。'}
    $launcher=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__LAUNCHER__'))
    $service=New-Object -ComObject Schedule.Service
    $service.Connect()
    try{$folder=$service.GetFolder('\EyeRelax')}catch{if(Test-MissingTask $_.Exception){Write-Output '当前账户没有需要移除的运行保护。';exit 0};throw}
    $appName='App-'+$sid
    $guardName='Guard-'+$sid
    $app=Read-GuardTask $folder $appName
    $guard=Read-GuardTask $folder $guardName
    if($null -eq $app -and $null -eq $guard){Write-Output '当前账户没有需要移除的运行保护。';exit 0}
    Assert-OwnedTasks $app $guard $sid $launcher
    $ownedScript=''
    if($null -ne $guard){$ownedScript=Get-QuotedArgument $guard.Definition.Actions.Item(1).Arguments '-File'}
    $principal=New-Object Security.Principal.WindowsPrincipal($identity)
    if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw '移除运行保护需要管理员权限，请以管理员身份重新运行卸载。'
    }
    # Validate everything before stopping either task. Never enumerate other accounts or installations.
    if($null -ne $guard){Stop-OwnedGuard $guard;$folder.DeleteTask($guardName,0)}
    # App shutdown belongs to close / force-close; removing its registration never force-stops the user's app.
    if($null -ne $app){$folder.DeleteTask($appName,0)}
    Write-Output '本安装、当前账户的运行保护任务已移除。'
    if($null -ne $guard) {
        # Only the exact helper file validated above is owned. Preserve any other files in this folder.
        try {
            [IO.File]::Delete($ownedScript)
            $directory=[IO.Path]::GetDirectoryName($ownedScript)
            if([IO.Directory]::Exists($directory) -and @([IO.Directory]::EnumerateFileSystemEntries($directory)).Count -eq 0){[IO.Directory]::Delete($directory,$false)}
        }catch{Write-Output ('运行保护已解除；辅助文件未能清理，可稍后手动删除：'+$ownedScript)}
    }
}catch{
    [Console]::Error.WriteLine('运行保护尚未完整移除：'+$_.Exception.Message)
    exit 2
}
