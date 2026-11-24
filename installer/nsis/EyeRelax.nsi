; STANDARD INSTALLER SOURCE — compiled with official NSIS 3.12; Windows acceptance still required.
; Build using build-installer.py after installing official NSIS 3.
Unicode true
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "x64.nsh"
!include "nsDialogs.nsh"
!include "WinVer.nsh"
!include "TextFunc.nsh"

!ifndef PAYLOAD
  !error "Use build-installer.py: PAYLOAD is required."
!endif
!ifndef GENERATED
  !error "Generated per-file install/uninstall manifest is required."
!endif
!ifndef OUTPUT
  !define OUTPUT "EyeRelax-Setup-${VERSION}.exe"
!endif
!define PRODUCT "EyeRelax"
!define APP_DIR_NAME "EyeRelax"
!define STARTMENU_DIR_NAME "EyeRelax"
!define DATA_DIR_NAME ".eye-relax"
!define APP_SUBDIR "app"
!define APP_JAR_NAME "rest-1.0.0.jar"
!define APP_EXE_NAME "EyeRelax.exe"
!define APP_ID "EyeRelax.NSIS.v1"
!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\EyeRelax"
!define LEGACY_RUN_KEY "Software\Microsoft\Windows\CurrentVersion\Run"
!define VCRUNTIME_KEY "Software\Microsoft\VisualStudio\14.0\VC\Runtimes\x64"
!define VCRUNTIME_MAJOR 14
!define VCRUNTIME_MINOR 40
!define VCRUNTIME_BUILD 33810
!define VCREDIST_URL "https://download.visualstudio.microsoft.com/download/pr/ebdab8e5-1d7b-4d9f-a11b-cbb1720c3b12/843068991DAAA1F73AD9F6239BCE4D0F6A07A51F18C37EA2A867E9BECA71295C/VC_redist.x64.exe"
!define MULTIUSER_EXECUTIONLEVEL Highest
!define MULTIUSER_MUI
!define MULTIUSER_INSTALLMODE_DEFAULT_CURRENTUSER
!define MULTIUSER_INSTALLMODE_DEFAULT_REGISTRY_KEY "${UNINSTALL_KEY}"
!define MULTIUSER_INSTALLMODE_DEFAULT_REGISTRY_VALUENAME "InstallLocation"
!define MULTIUSER_USE_PROGRAMFILES64
!define MULTIUSER_INSTALLMODE_INSTDIR "${APP_DIR_NAME}"
!define MULTIUSER_INSTALLMODE_INSTDIR_REGISTRY_KEY "${UNINSTALL_KEY}"
!define MULTIUSER_INSTALLMODE_INSTDIR_REGISTRY_VALUENAME "InstallLocation"
!define MULTIUSER_INSTALLMODE_FUNCTION OnInstallMode
!define MULTIUSER_INSTALLMODEPAGE_TEXT_TOP "选择哪些账户可以使用 EyeRelax。所有用户安装需要管理员授权。"
!define MULTIUSER_INSTALLMODEPAGE_TEXT_CURRENTUSER "仅为当前用户安装（默认）"
!define MULTIUSER_INSTALLMODEPAGE_TEXT_ALLUSERS "为这台电脑的所有用户安装"
!include "MultiUser.nsh"
!include "MUI2.nsh"
; Per-user setup opens without UAC. Elevate only an explicitly selected machine install.
RequestExecutionLevel user
!include "${GENERATED}"

Name "EyeRelax"
Caption "EyeRelax · 安装向导"
OutFile "${OUTPUT}"
InstallDir "$LOCALAPPDATA\Programs\${APP_DIR_NAME}"
SetCompressor /SOLID zlib
BrandingText " "
ManifestDPIAware true
SetFont "Microsoft YaHei UI" 9
VIProductVersion "${PE_VERSION}"
VIAddVersionKey "ProductName" "EyeRelax"
VIAddVersionKey "FileDescription" "EyeRelax setup"
VIAddVersionKey "FileVersion" "${PE_VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "EyeRelax contributors"
ShowInstDetails hide
ShowUninstDetails hide

!define MUI_ICON "assets\EyeRelax.ico"
!define MUI_UNICON "assets\EyeRelax.ico"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_HEADERIMAGE_BITMAP "assets\header.bmp"
!define MUI_WELCOMEFINISHPAGE_BITMAP "assets\welcome.bmp"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "assets\welcome.bmp"
!define MUI_BGCOLOR "FFFFFF"
!define MUI_TEXTCOLOR "203D34"
!define MUI_INSTFILESPAGE_COLORS "244A3C F5F7F5"
!define MUI_CUSTOMFUNCTION_GUIINIT SetupGuiInit
!define MUI_CUSTOMFUNCTION_UNGUIINIT un.SetupGuiInit
Page custom SetupOverview SetupOverviewLeave
Page custom SetupScope SetupScopeLeave
!define MUI_PAGE_CUSTOMFUNCTION_PRE DirectoryPre
!define MUI_PAGE_CUSTOMFUNCTION_SHOW SetupDirectoryShow
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE ValidateDirectory
!define MUI_DIRECTORYPAGE_TEXT_TOP "选择 EyeRelax 的安装文件夹。"
!insertmacro MUI_PAGE_DIRECTORY
Page custom DataPage DataLeave
Page custom SetupReady SetupReadyLeave
!define MUI_PAGE_CUSTOMFUNCTION_PRE BeginInstall
!define MUI_PAGE_CUSTOMFUNCTION_SHOW SetupInstallShow
!define MUI_INSTFILESPAGE_HEADER_TEXT "正在准备 EyeRelax"
!define MUI_INSTFILESPAGE_HEADER_SUBTEXT ""
!insertmacro MUI_PAGE_INSTFILES
Page custom SetupFinish SetupFinishLeave
UninstPage custom un.SetupConfirm
!define MUI_PAGE_CUSTOMFUNCTION_PRE un.BeginUninstall
!define MUI_PAGE_CUSTOMFUNCTION_SHOW un.SetupInstallShow
!define MUI_UNINSTFILESPAGE_HEADER_TEXT "正在移除 EyeRelax"
!define MUI_UNINSTFILESPAGE_HEADER_SUBTEXT ""
!insertmacro MUI_UNPAGE_INSTFILES
UninstPage custom un.SetupFinish
!insertmacro MUI_LANGUAGE "SimpChinese"

Var DesktopChoice
Var StartupChoice
Var SharedChoice
Var DesktopControl
Var StartupControl
Var SharedControl
Var DataDir
Var DataManaged
Var DataChangeRequested
Var DataDirControl
Var DataBrowseControl
Var DialogHandle
Var OwnedFile
Var RunCommand
Var UninstallSummary
Var VCRuntimeReady
Var UserBootstrapDir
Var ShortcutWarnings
Var StartMenuShortcutPath
Var DesktopShortcutPath
Var StartMenuDirectory
Var StartupShortcutPath
Var StartupTemplatePath
Var SelectedInstallDir
Var IsUpgrade
Var ExistingVersion
Var ExistingInstallDir
Var ExistingHasNativeLauncher
Var DetectedInstallScope
Var DetectedInstallDir
Var ForceAllUsersUpdate
Var UserHomeDir
Var ExistingDataDir
Var HelperJava
Var HelperCommand
Var HelperExit
Var HelperOutput
Var InstallArguments
Var UpdateLockHandle
Var VersionNeedsConsent
Var VersionConsent
Var VersionCheckbox
Var VersionComparison
Var SetupCommitted
Var SetupSucceeded
Var SetupRequestedHome
Var SetupRequestedScope
Var SetupLog
Var SetupLogLine
Var SetupElevationArguments
Var SetupGuardNeedsAdmin
Var SetupAccount
Var SetupCallerAccount
Var InstallUserStartupDir
!include "SetupUI.nsh"
!include "ModernBridge.nsh"

!macro SetupReadChoice FLAG DEST
  ClearErrors
  ${GetOptions} "$InstallArguments" "${FLAG}" $0
  ${IfNot} ${Errors}
    ${If} $0 == "1"
      StrCpy ${DEST} ${BST_CHECKED}
    ${ElseIf} $0 == "0"
      StrCpy ${DEST} ${BST_UNCHECKED}
    ${EndIf}
  ${EndIf}
!macroend

Function OnInstallMode
  StrCpy $IsUpgrade 0
  StrCpy $ExistingVersion ""
  StrCpy $ExistingInstallDir ""
  StrCpy $ExistingHasNativeLauncher 0
  ReadRegStr $0 SHCTX "${UNINSTALL_KEY}" "InstallLocation"
  ReadRegStr $1 SHCTX "${UNINSTALL_KEY}" "DisplayVersion"
  ${If} $0 != ""
    ; Current marker is authoritative. For older/broken preview installers, recover only
    ; when the EyeRelax app JAR plus one of our launch/uninstall artifacts are present.
    ReadINIStr $2 "$0\install.ini" "Install" "AppId"
    ${If} $2 == "${APP_ID}"
      Goto upgrade_found
    ${EndIf}
    IfFileExists "$0\${APP_SUBDIR}\${APP_JAR_NAME}" 0 stale_registration
    IfFileExists "$0\${APP_EXE_NAME}" upgrade_found 0
    IfFileExists "$0\jre\bin\javaw.exe" upgrade_found 0
    IfFileExists "$0\Uninstall.exe" upgrade_found stale_registration

    upgrade_found:
      StrCpy $IsUpgrade 1
      StrCpy $ExistingVersion "$1"
      ${If} $ExistingVersion == ""
        ReadINIStr $ExistingVersion "$0\install.ini" "Install" "Version"
      ${EndIf}
      StrCpy $ExistingInstallDir "$0"
      IfFileExists "$0\${APP_EXE_NAME}" 0 +2
        StrCpy $ExistingHasNativeLauncher 1
      StrCpy $INSTDIR "$0"
      StrCpy $SelectedInstallDir "$0"
      Return

    stale_registration:
      ; Stale/foreign registration: preserve preview.6 behavior and reuse its location as a fresh install.
      StrCpy $INSTDIR "$0"
      Return
  ${ElseIf} $MultiUser.InstallMode == "AllUsers"
    StrCpy $INSTDIR "$PROGRAMFILES64\${APP_DIR_NAME}"
  ${Else}
    StrCpy $INSTDIR "$LOCALAPPDATA\Programs\${APP_DIR_NAME}"
  ${EndIf}

  ; Registry-loss recovery for the two standard locations only. This does not search arbitrary
  ; folders and therefore cannot turn an unrelated directory into an upgrade target.
  IfFileExists "$INSTDIR\${APP_SUBDIR}\${APP_JAR_NAME}" 0 mode_done
  IfFileExists "$INSTDIR\${APP_EXE_NAME}" standard_upgrade_found 0
  IfFileExists "$INSTDIR\jre\bin\javaw.exe" standard_upgrade_found 0
  IfFileExists "$INSTDIR\Uninstall.exe" standard_upgrade_found mode_done

  standard_upgrade_found:
    StrCpy $IsUpgrade 1
    ReadINIStr $ExistingVersion "$INSTDIR\install.ini" "Install" "Version"
    StrCpy $ExistingInstallDir "$INSTDIR"
    IfFileExists "$INSTDIR\${APP_EXE_NAME}" 0 +2
      StrCpy $ExistingHasNativeLauncher 1
    StrCpy $SelectedInstallDir "$INSTDIR"
  mode_done:
FunctionEnd

Function CheckWindows10OrLater
  StrCpy $0 0
  System::Alloc 284
  Pop $1
  ${If} $1 == 0
    Return
  ${EndIf}
  System::Call '*$1(i 284)'
  System::Call 'ntdll::RtlGetVersion(p r1) i.r2'
  ${If} $2 == 0
    System::Call '*$1(i, i.r3, i.r4)'
    ${If} $3 >= 10
      StrCpy $0 1
    ${EndIf}
  ${EndIf}
  System::Free $1
FunctionEnd

!macro DEFINE_UPDATE_LOCK PREFIX
Function ${PREFIX}TryUpdateLock
  ${If} $UpdateLockHandle != ""
    Return
  ${EndIf}
  System::Call 'kernel32::CreateFileW(w "$UserBootstrapDir\instance.lock", i 0xC0000000, i 0x00000007, p 0, i 4, i 0x80, p 0) p.r0 ?e'
  Pop $2
  ${If} $0 != -1
    System::Call 'kernel32::LockFile(p r0, i 0, i 0, i 1, i 0) i.r1 ?e'
    Pop $2
    ${If} $1 != 0
      StrCpy $UpdateLockHandle $0
      Return
    ${EndIf}
    System::Call 'kernel32::CloseHandle(p r0)'
    ${If} $2 == 33 ; ERROR_LOCK_VIOLATION
      Return
    ${EndIf}
  ${EndIf}
  StrCpy $SetupLogLine "Cannot acquire instance.lock (Windows error $2)."
  Call ${PREFIX}SetupWriteLog
  IfSilent +2
    MessageBox MB_OK|MB_ICONSTOP "无法检查 EyeRelax 运行状态。请确认可以访问当前账户的数据文件夹后重试。"
  SetErrorLevel 2
  Abort
FunctionEnd

Function ${PREFIX}WaitForUpdateLock
  ; A helper can finish before Windows releases the instance lock.
  Push $3
  StrCpy $3 20
  wait_for_exit:
    Call ${PREFIX}TryUpdateLock
    ${If} $UpdateLockHandle == ""
    ${AndIf} $3 > 0
      IntOp $3 $3 - 1
      Sleep 100
      Goto wait_for_exit
    ${EndIf}
  Pop $3
FunctionEnd

Function ${PREFIX}AcquireUpdateLock
  CreateDirectory "$UserBootstrapDir"
  Call ${PREFIX}TryUpdateLock
  ${If} $UpdateLockHandle != ""
    Return
  ${EndIf}
  IfSilent lock_cancel
  MessageBox MB_OKCANCEL|MB_ICONINFORMATION "继续前需退出 EyeRelax。请先保存正在编辑的内容，点击“确定”后将自动关闭 EyeRelax。" IDOK request_close
  Goto lock_cancel

  request_close:
  !if "${PREFIX}" == ""
    ${If} $HelperJava == ""
      Call LoadFallbackHelperRuntime
    ${EndIf}
  !endif
  StrCpy $HelperCommand 'close'
  ${If} $ExistingInstallDir != ""
    StrCpy $HelperCommand 'close $\"$ExistingInstallDir$\"'
  ${EndIf}
  Call ${PREFIX}RunInstallerHelper
  Call ${PREFIX}WaitForUpdateLock
  ${If} $UpdateLockHandle != ""
    Return
  ${EndIf}
  StrCpy $SetupLogLine "Cooperative exit did not release instance.lock ($HelperExit): $HelperOutput"
  Call ${PREFIX}SetupWriteLog
  ${If} $ExistingInstallDir == ""
    MessageBox MB_OK|MB_ICONEXCLAMATION "无法确认正在运行的 EyeRelax 安装位置。请从托盘退出 EyeRelax 并关闭其它安装窗口，再重试。"
    Goto lock_cancel
  ${EndIf}
  MessageBox MB_YESNO|MB_DEFBUTTON2|MB_ICONEXCLAMATION "EyeRelax 未能退出。是否强制关闭？未保存的内容可能丢失。" IDYES force_close
  Goto lock_cancel

  force_close:
  ; The user may have closed the app while the confirmation was on screen.
  Call ${PREFIX}TryUpdateLock
  ${If} $UpdateLockHandle != ""
    Return
  ${EndIf}
  StrCpy $HelperCommand 'force-close $\"$ExistingInstallDir$\"'
  Call ${PREFIX}RunInstallerHelper
  Call ${PREFIX}WaitForUpdateLock
  ${If} $UpdateLockHandle != ""
    Return
  ${EndIf}
  StrCpy $SetupLogLine "Forced exit did not release instance.lock ($HelperExit): $HelperOutput"
  Call ${PREFIX}SetupWriteLog
  MessageBox MB_OK|MB_ICONEXCLAMATION "EyeRelax 仍被占用。请退出 EyeRelax 并关闭其它安装窗口，再重试。"

  lock_cancel:
  SetErrorLevel 2
  Abort
FunctionEnd
!macroend
!insertmacro DEFINE_UPDATE_LOCK ""
!insertmacro DEFINE_UPDATE_LOCK "un."

Function ReleaseUpdateLock
  ${If} $UpdateLockHandle != ""
    StrCpy $0 $UpdateLockHandle
    System::Call 'kernel32::UnlockFile(p r0, i 0, i 0, i 1, i 0)'
    System::Call 'kernel32::CloseHandle(p r0)'
    StrCpy $UpdateLockHandle ""
  ${EndIf}
FunctionEnd

Function .onInit
  ; There is exactly one install section (index 0). Set its real uncompressed payload size
  ; explicitly so the native MUI directory page cannot show the bogus "0.0 KB" value.
  SectionSetSize 0 ${PAYLOAD_KIB}
  Call CheckWindows10OrLater
  ${If} $0 != 1
    MessageBox MB_OK|MB_ICONSTOP "此版本需要 Windows 10 或更高版本。"
    Abort
  ${EndIf}
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "此版本需要 64 位 Windows。"
    Abort
  ${EndIf}
  SetRegView 64
  StrCpy $UpdateLockHandle ""
  StrCpy $ForceAllUsersUpdate 0
  StrCpy $SetupCustom 0
  StrCpy $SetupCommitted 0
  StrCpy $SetupSucceeded 0
  StrCpy $SetupGuardNeedsAdmin 0
  System::Call 'kernel32::GetCurrentProcessId() i.r0'
  StrCpy $SetupLog "$TEMP\EyeRelax-setup-${VERSION}-$0.log"
  StrCpy $SetupRequestedScope ""
  ${GetParameters} $InstallArguments
  StrCpy $ModernWorker 0
  StrCpy $ModernEnabled 0
  ClearErrors
  ${GetOptions} "$InstallArguments" "/MODERNSESSION=" $ModernSession
  ${IfNot} ${Errors}
    IfFileExists "$ModernSession" 0 +2
      StrCpy $ModernWorker 1
  ${EndIf}
  UserInfo::GetName
  Pop $SetupAccount
  ReadEnvStr $0 "USERDOMAIN"
  StrCpy $SetupAccount "$0\$SetupAccount"
  ClearErrors
  ${GetOptions} "$InstallArguments" "/EyeRelaxAccount=" $SetupCallerAccount
  ${IfNot} ${Errors}
  ${AndIf} $SetupCallerAccount != $SetupAccount
    MessageBox MB_OK|MB_ICONSTOP "请使用原 Windows 账户授权安装，以保留该账户的数据和快捷方式。"
    SetErrorLevel 2
    Abort
  ${EndIf}
  SetShellVarContext current
  StrCpy $InstallUserStartupDir "$SMSTARTUP"
  ClearErrors
  ${GetOptions} "$InstallArguments" "/EyeRelaxStartupDir=" $0
  ${IfNot} ${Errors}
    StrCpy $InstallUserStartupDir "$0"
  ${EndIf}
  ClearErrors
  ${GetOptions} "$InstallArguments" "/AllUsers" $1
  ${IfNot} ${Errors}
    StrCpy $ForceAllUsersUpdate 1
    StrCpy $SetupRequestedScope "AllUsers"
  ${EndIf}
  ClearErrors
  Call DetectExistingInstall
  !insertmacro MULTIUSER_INIT
  ${If} $DetectedInstallScope == "CurrentUser"
    Call MultiUser.InstallMode.CurrentUser
  ${ElseIf} $DetectedInstallScope == "AllUsers"
    ; Read existing machine metadata without elevating merely to view setup.
    StrCpy $MultiUser.InstallMode "AllUsers"
    SetShellVarContext all
    Call OnInstallMode
  ${ElseIf} $SetupRequestedScope == "AllUsers"
    StrCpy $MultiUser.InstallMode "AllUsers"
    SetShellVarContext all
    Call OnInstallMode
  ${EndIf}
  ; Fresh-install defaults follow the project default profile. Upgrades preserve the
  ; previously installed shortcut/startup/shared-rest choices instead of resetting them.
  StrCpy $DesktopChoice ${BST_CHECKED}
  StrCpy $StartupChoice ${BST_CHECKED}
  StrCpy $SharedChoice ${BST_UNCHECKED}
  ${If} $IsUpgrade == 1
    ReadINIStr $0 "$ExistingInstallDir\install.ini" "Shortcuts" "Desktop"
    IfFileExists "$0" desktop_choice_done 0
      StrCpy $DesktopChoice ${BST_UNCHECKED}
    desktop_choice_done:
    ReadINIStr $0 "$ExistingInstallDir\install.ini" "Shortcuts" "Startup"
    IfFileExists "$0" startup_choice_done 0
      StrCpy $StartupChoice ${BST_UNCHECKED}
    startup_choice_done:
    ${ConfigRead} "$ExistingInstallDir\install.properties" "sharedRest=" $0
    ${If} $0 == "true"
      StrCpy $SharedChoice ${BST_CHECKED}
    ${EndIf}
  ${EndIf}
  ; Resolve the interactive user's profile from the environment once. Do not assume a drive, username,
  ; or rely on ShellVarContext (which can change in all-users mode).
  ReadEnvStr $UserBootstrapDir "USERPROFILE"
  ${If} $UserBootstrapDir == ""
    StrCpy $UserBootstrapDir "$PROFILE"
  ${EndIf}
  ClearErrors
  ${GetOptions} "$InstallArguments" "/EyeRelaxUserHome=" $SetupRequestedHome
  ${IfNot} ${Errors}
    StrCpy $UserBootstrapDir "$SetupRequestedHome"
  ${EndIf}
  StrCpy $UserHomeDir "$UserBootstrapDir"
  StrCpy $UserBootstrapDir "$UserBootstrapDir\${DATA_DIR_NAME}"
  InitPluginsDir
  StrCpy $VersionNeedsConsent 0
  StrCpy $VersionConsent ${BST_UNCHECKED}
  SetOutPath "$PLUGINSDIR\helper"
  File /oname=rest-1.0.0.jar "${PAYLOAD}\app\rest-1.0.0.jar"
  StrCpy $DataDir "$UserBootstrapDir"
  StrCpy $DataManaged 0
  StrCpy $DataChangeRequested 0
  IfFileExists "$UserBootstrapDir\data-location.properties" 0 +2
    StrCpy $DataManaged 1
  ${If} $IsUpgrade == 1
    StrCpy $HelperJava "$ExistingInstallDir\jre\bin\java.exe"
    StrCpy $HelperCommand 'resolve $\"$PLUGINSDIR\data-path.ini$\"'
    Call RunInstallerHelper
    ${If} $HelperExit != "0"
      ; Repair must not depend on the old installation's possibly damaged runtime.
      Call LoadFallbackHelperRuntime
      Call RunInstallerHelper
    ${EndIf}
    ${If} $HelperExit != "0"
      StrCpy $SetupLogLine "$HelperOutput"
      Call SetupWriteLog
      MessageBox MB_OK|MB_ICONSTOP "无法读取原来的数据位置。请确认该文件夹可以访问后重试。"
      Abort
    ${EndIf}
    ReadINIStr $DataDir "$PLUGINSDIR\data-path.ini" "Data" "Path"
    ReadINIStr $0 "$ExistingInstallDir\install.ini" "Install" "Version"
    ${If} $0 != ""
      StrCpy $ExistingVersion $0
    ${EndIf}
    StrCpy $HelperCommand 'compare-version $\"$ExistingVersion$\" $\"${VERSION}$\" $\"$PLUGINSDIR\version.ini$\"'
    Call RunInstallerHelper
    StrCpy $VersionComparison "unknown"
    ${If} $HelperExit == "0"
      ReadINIStr $VersionComparison "$PLUGINSDIR\version.ini" "Version" "Compare"
    ${EndIf}
    ${If} $VersionComparison == "-1"
    ${OrIf} $VersionComparison == "unknown"
      StrCpy $VersionNeedsConsent 1
      ${If} $ModernWorker != 1
        IfSilent 0 +3
          SetErrorLevel 4
          Abort
      ${Else}
        ReadINIStr $0 "$ModernSession" "Choice" "consent"
        ${If} $0 != "1"
          SetErrorLevel 4
          Abort
        ${EndIf}
      ${EndIf}
    ${EndIf}
  ${EndIf}
  StrCpy $ExistingDataDir "$DataDir"
  ${ConfigRead} "$ExistingDataDir\config.properties" "usageGuardEnabled=" $0
  ${If} $0 == "true"
    StrCpy $SetupGuardNeedsAdmin 1
  ${EndIf}
  ; Keep parameters outside the scratch registers used by path validation.
  ${GetParameters} $InstallArguments
  ClearErrors
  ${GetOptions} "$InstallArguments" "/APPDIR=" $1
  ${IfNot} ${Errors}
    StrCpy $INSTDIR "$1"
    Call ValidateDirectory
  ${EndIf}
  ClearErrors
  ${GetOptions} "$InstallArguments" "/DATADIR=" $1
  ${IfNot} ${Errors}
    StrCpy $DataDir "$1"
    StrCpy $DataChangeRequested 1
  ${EndIf}
  ClearErrors
  ${GetOptions} "$InstallArguments" "/CUSTOM=" $0
  ${If} $0 == "1"
    StrCpy $SetupCustom 1
  ${EndIf}
  !insertmacro SetupReadChoice "/DESKTOP=" $DesktopChoice
  !insertmacro SetupReadChoice "/STARTUP=" $StartupChoice
  !insertmacro SetupReadChoice "/SHARED=" $SharedChoice
  ClearErrors
  Call ValidateDirectory
  ; Product requirement: opening setup first asks the running app to exit.
  ; Keep the exclusive instance lock throughout the wizard, including custom pages.
  Call AcquireUpdateLock
  Call ModernStart
FunctionEnd

Function DirectoryPre
  ${If} $SetupCustom != 1
    Abort
  ${EndIf}
FunctionEnd

Function EnsureInstallScope
  ${If} $MultiUser.InstallMode != "AllUsers"
  ${AndIf} $SetupGuardNeedsAdmin != 1
    Return
  ${EndIf}
  UserInfo::GetAccountType
  Pop $0
  ${If} $0 == "Admin"
  ${OrIf} $0 == "Power"
    Return
  ${EndIf}
  ; The elevated wizard takes over the instance lock; do not deadlock the two processes.
  StrCpy $SetupElevationArguments '/$MultiUser.InstallMode /CUSTOM=$SetupCustom /DESKTOP=$DesktopChoice /STARTUP=$StartupChoice /SHARED=$SharedChoice /EyeRelaxAccount=$\"$SetupAccount$\" /EyeRelaxUserHome=$\"$UserHomeDir$\" /EyeRelaxStartupDir=$\"$InstallUserStartupDir$\" /APPDIR=$\"$INSTDIR$\" /DATADIR=$\"$DataDir$\"'
  IfSilent 0 +2
    StrCpy $SetupElevationArguments "$SetupElevationArguments /S"
  ${If} $ModernEnabled == 1
    StrCpy $SetupElevationArguments '$SetupElevationArguments /S /MODERNSESSION=$\"$ModernSession$\"'
  ${EndIf}
  Call ReleaseUpdateLock
  ClearErrors
  ExecShellWait "runas" "$EXEPATH" "$SetupElevationArguments" SW_SHOWNORMAL
  ${If} ${Errors}
    SetErrorLevel 2
    ${If} $ModernEnabled == 1
      WriteINIStr "$ModernSession" "State" "message" "没有获得管理员授权，尚未修改应用。请重新运行安装器后重试。"
      WriteINIStr "$ModernSession" "State" "phase" "error"
      Call ModernWaitFinish
      Quit
    ${EndIf}
    MessageBox MB_OK|MB_ICONINFORMATION "没有获得管理员授权。可以重试，或选择仅为当前用户安装。"
    Call AcquireUpdateLock
    Abort
  ${EndIf}
  ${If} $ModernEnabled == 1
    ReadINIStr $0 "$ModernSession" "State" "phase"
    ${If} $0 == "done"
      StrCpy $SetupSucceeded 1
      SetErrorLevel 0
    ${Else}
      SetErrorLevel 2
      WriteINIStr "$ModernSession" "State" "phase" "error"
    ${EndIf}
    Call ModernWaitFinish
  ${EndIf}
  Quit
FunctionEnd

!macro DEFINE_SETUP_LOG PREFIX
Function ${PREFIX}SetupWriteLog
  IfFileExists "$SetupLog" setup_log_append
    FileOpen $9 "$SetupLog" w
    FileWriteWord $9 0xFEFF
    FileClose $9
  setup_log_append:
  FileOpen $9 "$SetupLog" a
  FileSeek $9 0 END
  FileWriteUTF16LE $9 "$SetupLogLine$\r$\n"
  FileClose $9
  ClearErrors
FunctionEnd

!macroend
!insertmacro DEFINE_SETUP_LOG ""
!insertmacro DEFINE_SETUP_LOG "un."

; Initialization errors do not invoke .onInstFailed. Publish them explicitly while
; the parent still owns the bridge directory. Elevated workers never wait for UI.
!macro DEFINE_OPERATION_ERROR PREFIX
Function ${PREFIX}ShowOperationError
  IfFileExists "$SetupLog" operation_log_append
    FileOpen $9 "$SetupLog" w
    FileWriteWord $9 0xFEFF
    FileClose $9
  operation_log_append:
  FileOpen $9 "$SetupLog" a
  FileSeek $9 0 END
  FileWriteUTF16LE $9 "$ModernMessage$\r$\n"
  FileClose $9
  ${If} $ModernEnabled == 1
    WriteINIStr "$ModernSession" "State" "message" "$ModernMessage"
    WriteINIStr "$ModernSession" "State" "phase" "error"
    Call ${PREFIX}ReleaseUpdateLock
    Call ${PREFIX}ModernWaitFinish
  ${Else}
    MessageBox MB_OK|MB_ICONSTOP "$ModernMessage"
  ${EndIf}
FunctionEnd
!macroend
!insertmacro DEFINE_OPERATION_ERROR ""
!insertmacro DEFINE_OPERATION_ERROR "un."

Function PrepareInstall
  Call EnsureInstallScope
  Call ValidateDirectory
  Call ValidateDataDirectory
  Call AcquireUpdateLock
  StrCpy $SetupCommitted 1
  SetErrorLevel 0
  StrCpy $SetupLogLine "开始 ${VERSION}；应用：$INSTDIR；数据：$DataDir；原版本：$ExistingVersion"
  Call SetupWriteLog
FunctionEnd

Function BeginInstall
  ; All interactive validation belongs to the preceding leave callback. Abort in
  ; a page PRE would skip installation and could incorrectly reach the finish page.
  DetailPrint "正在检查运行环境…"
FunctionEnd

Function ValidateDirectory
  ; Installation location is intentionally unrestricted. Do not normalize/rewrite $INSTDIR here.
  ; The MUI directory page already owns path editing. We only capture its accepted value so later
  ; MultiUser/custom-page callbacks cannot accidentally leave payload extraction with an empty path.
  ${If} $INSTDIR == ""
    StrCpy $ModernMessage "请选择一个安装目录。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}
  ${If} $ExistingInstallDir != ""
  ${AndIf} $INSTDIR != $ExistingInstallDir
    StrCpy $0 "$INSTDIR"
    validate_new_app_parent:
      System::Call 'kernel32::lstrcmpiW(w r0, w "$ExistingInstallDir") i .r1'
      ${If} $1 == 0
        Goto invalid_app_overlap
      ${EndIf}
      ${GetParent} "$0" $2
      ${If} $2 != ""
      ${AndIf} $2 != $0
        StrCpy $0 "$2"
        Goto validate_new_app_parent
      ${EndIf}
    StrCpy $0 "$ExistingInstallDir"
    validate_old_app_parent:
      System::Call 'kernel32::lstrcmpiW(w r0, w "$INSTDIR") i .r1'
      ${If} $1 == 0
        Goto invalid_app_overlap
      ${EndIf}
      ${GetParent} "$0" $2
      ${If} $2 != ""
      ${AndIf} $2 != $0
        StrCpy $0 "$2"
        Goto validate_old_app_parent
      ${EndIf}
    Goto app_overlap_ok
    invalid_app_overlap:
      StrCpy $ModernMessage "新旧应用目录不能互相包含，请选择独立目录。"
      Call ShowOperationError
      SetErrorLevel 2
      Abort
    app_overlap_ok:
  ${EndIf}
  StrCpy $SelectedInstallDir "$INSTDIR"
FunctionEnd

Function DataPage
  ${If} $SetupCustom != 1
    Abort
  ${EndIf}
  !insertmacro MUI_HEADER_TEXT "个人数据位置" "计划、设置和历史记录的保存位置。"
  nsDialogs::Create 1018
  Pop $DialogHandle
  ${If} $DialogHandle == error
    Abort
  ${EndIf}
  SetCtlColors $DialogHandle 203D34 FFFFFF
  ${NSD_CreateText} 0 4u 78% 18u "$DataDir"
  Pop $DataDirControl
  SendMessage $DataDirControl ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $DataDirControl 203D34 FFFFFF
  ${NSD_CreateButton} 80% 4u 20% 18u "浏览…"
  Pop $DataBrowseControl
  SendMessage $DataBrowseControl ${WM_SETFONT} $SetupBodyFont 0
  ${NSD_OnClick} $DataBrowseControl BrowseDataDir
  ${NSD_CreateLabel} 0 32u 100% 20u "要迁移已有数据，请选择空文件夹。"
  Pop $0
  SendMessage $0 ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $0 203D34 FFFFFF
  ${If} $MultiUser.InstallMode == "AllUsers"
    ${NSD_CreateLabel} 0 56u 100% 20u "此位置仅用于当前账户。"
    Pop $0
    SendMessage $0 ${WM_SETFONT} $SetupBodyFont 0
    SetCtlColors $0 203D34 FFFFFF
  ${EndIf}
  ${NSD_OnBack} DataBack
  nsDialogs::Show
FunctionEnd

Function DataBack
  ${NSD_GetText} $DataDirControl $DataDir
FunctionEnd

Function BrowseDataDir
  nsDialogs::SelectFolderDialog "选择 EyeRelax 数据文件夹" "$DataDir"
  Pop $0
  ${If} $0 != error
  ${AndIf} $0 != ""
    StrCpy $DataDir $0
    StrCpy $DataChangeRequested 1
    EnableWindow $DataDirControl 1
    ${NSD_SetText} $DataDirControl $DataDir
  ${EndIf}
FunctionEnd

Function DataLeave
  ${NSD_GetText} $DataDirControl $DataDir
  Call ValidateDataDirectory
FunctionEnd

Function ValidateDataDirectory
  ${If} $DataDir == ""
    StrCpy $ModernMessage "请选择个人数据文件夹。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}
  ; NSIS GetFullPathName requires an existing path. Resolve new folders without creating them.
  System::Call 'kernel32::GetFullPathNameW(w "$DataDir", i ${NSIS_MAX_STRLEN}, w .r2, p 0) i .r1'
  ${If} $1 == 0
  ${OrIf} $1 >= ${NSIS_MAX_STRLEN}
    StrCpy $ModernMessage "个人数据路径无效或过长，请选择其他文件夹。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}
  StrCpy $DataDir "$2"

  ; Data may also live on the Windows/system drive. Reject only dangerous locations, not a drive.
  ${GetRoot} "$DataDir" $0
  StrCpy $0 "$0\"
  System::Call 'kernel32::lstrcmpiW(w r0, w "$DataDir") i .r1'
  ${If} $1 == 0
    StrCpy $ModernMessage "请选择磁盘上的子文件夹来保存个人数据。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}

  ; Do not place personal data inside Windows or Program Files trees. Compare ancestors so the
  ; rule works no matter which drive hosts Windows/Program Files.
  StrCpy $0 "$DataDir"
  validate_data_system_ancestor:
    System::Call 'kernel32::lstrcmpiW(w r0, w "$WINDIR") i .r1'
    ${If} $1 == 0
      Goto invalid_data_system_tree
    ${EndIf}
    System::Call 'kernel32::lstrcmpiW(w r0, w "$PROGRAMFILES64") i .r1'
    ${If} $1 == 0
      Goto invalid_data_system_tree
    ${EndIf}
    System::Call 'kernel32::lstrcmpiW(w r0, w "$PROGRAMFILES32") i .r1'
    ${If} $1 == 0
      Goto invalid_data_system_tree
    ${EndIf}
    ${GetParent} "$0" $2
    ${If} $2 == ""
    ${OrIf} $2 == "$0"
      Goto data_system_tree_ok
    ${EndIf}
    StrCpy $0 "$2"
    Goto validate_data_system_ancestor
  invalid_data_system_tree:
    StrCpy $ModernMessage "请选择 Windows 和 Program Files 目录以外的文件夹。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  data_system_tree_ok:

  ; The installation directory is unrestricted, so do not reject a data directory merely
  ; because it equals, contains, or is contained by $INSTDIR. Data-directory-specific rules
  ; below remain independent from the program installation location.
  System::Call 'kernel32::lstrcmpiW(w "$DataDir", w "$ExistingDataDir") i .r1'
  ${If} $1 == 0
    StrCpy $DataChangeRequested 0
    Return
  ${EndIf}
  StrCpy $DataChangeRequested 1
  ${If} $DataDir != "$UserBootstrapDir"
    IfFileExists "$DataDir\*.*" 0 data_dir_ok
      StrCpy $ModernMessage "目标文件夹已有文件，请选择空文件夹。"
      Call ShowOperationError
      SetErrorLevel 2
      Abort
    data_dir_ok:
  ${EndIf}
FunctionEnd

Function CheckVCRuntime
  StrCpy $VCRuntimeReady 0
  SetRegView 64
  ClearErrors
  ReadRegDWORD $0 HKLM "${VCRUNTIME_KEY}" "Installed"
  ${If} $0 != 1
    Return
  ${EndIf}
  StrCpy $1 0
  StrCpy $2 0
  StrCpy $3 0
  ClearErrors
  ReadRegDWORD $1 HKLM "${VCRUNTIME_KEY}" "Major"
  IfErrors 0 +2
    Return
  ReadRegDWORD $2 HKLM "${VCRUNTIME_KEY}" "Minor"
  IfErrors 0 +2
    Return
  ReadRegDWORD $3 HKLM "${VCRUNTIME_KEY}" "Bld"
  IfErrors 0 +2
    Return
  ${If} $1 > ${VCRUNTIME_MAJOR}
    StrCpy $VCRuntimeReady 1
    Return
  ${ElseIf} $1 < ${VCRUNTIME_MAJOR}
    Return
  ${EndIf}
  ${If} $2 > ${VCRUNTIME_MINOR}
    StrCpy $VCRuntimeReady 1
    Return
  ${ElseIf} $2 < ${VCRUNTIME_MINOR}
    Return
  ${EndIf}
  ${If} $3 >= ${VCRUNTIME_BUILD}
    StrCpy $VCRuntimeReady 1
  ${EndIf}
FunctionEnd

Function EnsureVCRuntime
  Call CheckVCRuntime
  ${If} $VCRuntimeReady == 1
    DetailPrint "Microsoft Visual C++ v14 x64 运行库已满足要求。"
    Return
  ${EndIf}

  DetailPrint "正在从 Microsoft 获取 Visual C++ v14 x64 运行库…"
  Delete "$PLUGINSDIR\vc_redist.x64.exe"
  System::Call 'urlmon::URLDownloadToFileW(p 0, w "${VCREDIST_URL}", w "$PLUGINSDIR\vc_redist.x64.exe", i 0, p 0) i.r0'
  ${If} $0 != 0
    StrCpy $ModernMessage "缺少 Microsoft Visual C++ 运行库，且无法从 Microsoft 下载官方安装包。请检查网络后重试。错误代码：$0"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}
  IfFileExists "$PLUGINSDIR\vc_redist.x64.exe" vc_download_ok 0
    StrCpy $ModernMessage "Microsoft Visual C++ 运行库下载未生成有效文件。请检查网络后重试。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  vc_download_ok:

  DetailPrint "正在安装 Microsoft 官方 Visual C++ v14 x64 运行库…"
  UserInfo::GetAccountType
  Pop $0
  ClearErrors
  ${If} $0 == "Admin"
    ExecWait '$\"$PLUGINSDIR\vc_redist.x64.exe$\" /install /passive /norestart' $0
  ${Else}
    ExecShellWait "runas" "$PLUGINSDIR\vc_redist.x64.exe" "/install /passive /norestart" SW_SHOWNORMAL
    StrCpy $0 0
  ${EndIf}
  ${If} ${Errors}
    StrCpy $ModernMessage "没有完成 Microsoft 运行库安装。请允许运行库的管理员授权后重试。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}
  ${If} $0 == 3010
    SetRebootFlag true
  ${ElseIf} $0 == 1638
    ; A same/newer product can report that another version is installed; registry check below decides.
  ${ElseIf} $0 != 0
    StrCpy $ModernMessage "Microsoft Visual C++ 运行库安装失败，EyeRelax 未写入应用文件。返回代码：$0"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}

  Call CheckVCRuntime
  ${If} $VCRuntimeReady != 1
    StrCpy $ModernMessage "Microsoft Visual C++ 运行库仍未达到 EyeRelax 所需版本，已停止安装。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}
FunctionEnd

Function CheckLegacyPayloadUnlocked
  ReadINIStr $0 "$INSTDIR\install.ini" "Install" "AppId"
  ${If} $0 == "${APP_ID}"
    !insertmacro RetiredFiles CheckFileUnlocked
  ${EndIf}
  StrCpy $OwnedFile "$INSTDIR\jre\bin\msvcp140.dll"
  Call CheckFileUnlocked
  StrCpy $OwnedFile "$INSTDIR\jre\bin\vcruntime140.dll"
  Call CheckFileUnlocked
  StrCpy $OwnedFile "$INSTDIR\jre\bin\vcruntime140_1.dll"
  Call CheckFileUnlocked
  StrCpy $OwnedFile "$INSTDIR\jre\bin\ucrtbase.dll"
  Call CheckFileUnlocked
FunctionEnd

Function DeleteLegacyPayload
  ReadINIStr $0 "$INSTDIR\install.ini" "Install" "AppId"
  ${If} $0 == "${APP_ID}"
    !insertmacro RetiredFiles DeleteOwnedFile
    !insertmacro RemoveRetiredDirectories
  ${EndIf}
  Delete "$INSTDIR\jre\bin\msvcp140.dll"
  Delete "$INSTDIR\jre\bin\vcruntime140.dll"
  Delete "$INSTDIR\jre\bin\vcruntime140_1.dll"
  Delete "$INSTDIR\jre\bin\ucrtbase.dll"
  IfFileExists "$INSTDIR\jre\bin\msvcp140.dll" legacy_vc_delete_failed 0
  IfFileExists "$INSTDIR\jre\bin\vcruntime140.dll" legacy_vc_delete_failed 0
  IfFileExists "$INSTDIR\jre\bin\vcruntime140_1.dll" legacy_vc_delete_failed 0
  IfFileExists "$INSTDIR\jre\bin\ucrtbase.dll" legacy_vc_delete_failed 0
  Return
  legacy_vc_delete_failed:
    StrCpy $ModernMessage "旧版内置 Visual C++ 运行库文件无法清理。请退出 EyeRelax 并处理文件占用后重试。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
FunctionEnd

; Refuse to overwrite/delete a file held by the running application or security software.
; No taskkill, process-name matching, hidden shell, service, or security-policy modification.
!macro DEFINE_FILE_CHECK PREFIX
Function ${PREFIX}CheckFileUnlocked
  IfFileExists "$OwnedFile" 0 file_available
  file_retry:
    System::Call 'kernel32::CreateFileW(w "$OwnedFile", i 0x40000000, i 0, p 0, i 3, i 0x80, p 0) p.r0'
    ${If} $0 == -1
      ${If} $ModernEnabled == 1
        StrCpy $ModernMessage "无法访问文件：$OwnedFile。请关闭占用该文件的应用，检查目录权限或安全软件提示，再重新运行安装器。"
        Call ${PREFIX}ShowOperationError
      ${Else}
        MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION "无法访问文件：$\r$\n$OwnedFile$\r$\n$\r$\n请先退出 EyeRelax，并确认文件权限。若安全软件拦截，请先核实告警；不要关闭防护。关闭占用后重试，或取消本次操作。" IDRETRY file_retry
      ${EndIf}
      SetErrorLevel 2
      Abort
    ${EndIf}
    System::Call 'kernel32::CloseHandle(p r0)'
  file_available:
FunctionEnd
!macroend
!insertmacro DEFINE_FILE_CHECK ""
!insertmacro DEFINE_FILE_CHECK "un."

Section "EyeRelax" SEC_APP
  !insertmacro ModernStage 5 "正在检查安装条件"
  ; Silent installs use the same validation and lock policy as the visible flow.
  Call PrepareInstall
  ; Restore the exact directory captured on leaving the directory page. Do not call the page
  ; callback again here: at install time other callbacks may have changed $INSTDIR incidentally.
  ${If} $SelectedInstallDir != ""
    StrCpy $INSTDIR "$SelectedInstallDir"
  ${EndIf}
  ${If} $INSTDIR == ""
    StrCpy $ModernMessage "安装目录状态丢失。请重新打开安装器，在安装选项中选择安装目录。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}
  ClearErrors
  CreateDirectory "$INSTDIR"
  SetOutPath "$INSTDIR"
  ${If} ${Errors}
    DetailPrint "无法创建或写入安装目录：$INSTDIR"
    Goto install_failed
  ${EndIf}
  DetailPrint "安装目录：$INSTDIR"
  ${If} $IsUpgrade == 1
    DetailPrint "检测到 EyeRelax $ExistingVersion；正在升级到 ${VERSION}。个人数据与现有配置不会被卸载。"
  ${EndIf}
  !insertmacro CheckOwnedPayload ""
  Call CheckLegacyPayloadUnlocked
  StrCpy $OwnedFile "$INSTDIR\${APP_EXE_NAME}"
  Call CheckFileUnlocked
  StrCpy $OwnedFile "$INSTDIR\Uninstall.exe"
  Call CheckFileUnlocked
  ; Prerequisite is resolved only after all existing application files are confirmed writable.
  ; The JRE no longer ships private msvcp140/vcruntime/ucrtbase copies.
  Call EnsureVCRuntime
  !insertmacro ModernStage 20 "正在安装应用"
  Call DeleteLegacyPayload
  ClearErrors
  !insertmacro InstallPayload
  IfErrors install_failed
  SetOutPath "$INSTDIR"
  File /oname=${APP_EXE_NAME} "assets\EyeRelax.exe"
  File /oname=EyeRelax.ico "assets\EyeRelax.ico"
  File /oname=RELEASE-NOTES.txt "RELEASE-NOTES.txt"
  IfErrors install_failed

  ; Scope metadata is read by the uninstaller and by the original Java application.
  WriteINIStr "$INSTDIR\install.ini" "Install" "AppId" "${APP_ID}"
  WriteINIStr "$INSTDIR\install.ini" "Install" "Mode" "$MultiUser.InstallMode"
  WriteINIStr "$INSTDIR\install.ini" "Install" "Version" "${VERSION}"
  WriteINIStr "$INSTDIR\install.ini" "Install" "DataLocation" "$DataDir"
  ${If} $IsUpgrade == 1
    WriteINIStr "$INSTDIR\install.ini" "Install" "UpgradeFrom" "$ExistingVersion"
  ${Else}
    DeleteINIStr "$INSTDIR\install.ini" "Install" "UpgradeFrom"
  ${EndIf}

  ; Complete migration now; do not defer data movement until a future app launch.
  !insertmacro ModernStage 65 "正在处理个人数据"
  StrCpy $HelperJava "$INSTDIR\jre\bin\java.exe"
  StrCpy $HelperCommand 'migrate $\"$DataDir$\"'
  Call RunInstallerHelper
  ${If} $HelperExit != "0"
    DetailPrint "数据迁移未完成：$HelperOutput"
    Goto install_failed
  ${EndIf}

  StrCpy $0 "false"
  ${If} $StartupChoice == ${BST_CHECKED}
    StrCpy $0 "true"
  ${EndIf}
  FileOpen $1 "$INSTDIR\install.properties" w
  FileWrite $1 "format=1$\r$\nautostartDefault=$0$\r$\n"
  StrCpy $0 "false"
  ${If} $MultiUser.InstallMode == "AllUsers"
  ${AndIf} $SharedChoice == ${BST_CHECKED}
    StrCpy $0 "true"
  ${EndIf}
  FileWrite $1 "sharedRest=$0$\r$\n"
  FileClose $1
  IfErrors install_failed
  StrCpy $HelperCommand 'refresh-guard $\"$INSTDIR$\"'
  !insertmacro ModernStage 78 "正在配置应用"
  Call RunInstallerHelper
  ${If} $HelperExit != "0"
    DetailPrint "运行保护未能迁移：$HelperOutput"
    Goto install_failed
  ${EndIf}

  ${If} $MultiUser.InstallMode == "AllUsers"
  ${AndIf} $SharedChoice == ${BST_CHECKED}
    ; APPDATA resolves to ProgramData only in all-users shell context.
    CreateDirectory "$APPDATA\EyeRelax\rest-state"
    nsExec::ExecToStack '$\"$SYSDIR\icacls.exe$\" $\"$APPDATA\EyeRelax\rest-state$\" /grant *S-1-5-32-545:(OI)(CI)M'
    Pop $0
    Pop $1
    ${If} $0 != "0"
      StrCpy $SetupLogLine "共享休息目录权限设置失败：$1"
      Call SetupWriteLog
      StrCpy $ModernMessage "共享休息目录权限设置失败。请查看诊断记录中的权限提示，重新授权后重试。诊断记录：$SetupLog"
      Call ShowOperationError
      SetErrorLevel 2
      Abort
    ${EndIf}
  ${EndIf}

  ; Legacy Run-key command is retained only so upgrades can remove the old javaw.exe form.
  StrCpy $RunCommand '$\"$INSTDIR\jre\bin\javaw.exe$\" -jar $\"$INSTDIR\${APP_SUBDIR}\${APP_JAR_NAME}$\" --startup'
  SetOutPath "$INSTDIR"
  ; Shell locations are resolved by Windows/NSIS at runtime. Record the exact paths used so
  ; uninstall still removes the right links if folders are redirected or later moved.
  StrCpy $ShortcutWarnings ""
  StrCpy $StartMenuDirectory "$SMPROGRAMS\${STARTMENU_DIR_NAME}"
  StrCpy $StartMenuShortcutPath "$StartMenuDirectory\${PRODUCT}.lnk"
  StrCpy $DesktopShortcutPath "$DESKTOP\${PRODUCT}.lnk"
  StrCpy $StartupTemplatePath "$INSTDIR\support\EyeRelax-Startup.lnk"
  DetailPrint "开始菜单目录（Windows Shell）：$StartMenuDirectory"
  DetailPrint "桌面目录（Windows Shell）：$DESKTOP"

  ; Shortcut updates are intentionally idempotent during in-place upgrades.
  ; First trust the exact shortcut path recorded by the previous EyeRelax install, because
  ; Windows known folders can be redirected and the current Shell context may resolve to a
  ; different physical folder. A launcher-based upgrade with an existing .lnk keeps it as-is;
  ; legacy javaw.exe installs are recreated once so their target migrates to EyeRelax.exe.
  StrCpy $0 0
  ${If} $IsUpgrade == 1
  ${AndIf} $ExistingHasNativeLauncher == 1
  ${AndIf} $INSTDIR == $ExistingInstallDir
    ReadINIStr $1 "$INSTDIR\install.ini" "Shortcuts" "StartMenu"
    ${If} $1 != ""
    ${AndIf} ${FileExists} "$1"
      StrCpy $StartMenuShortcutPath "$1"
      StrCpy $0 1
      DetailPrint "开始菜单快捷方式已存在；原位升级保留：$StartMenuShortcutPath"
      WriteINIStr "$INSTDIR\install.ini" "Shortcuts" "StartMenu" "$StartMenuShortcutPath"
    ${ElseIf} ${FileExists} "$StartMenuShortcutPath"
      StrCpy $0 1
      DetailPrint "开始菜单快捷方式已存在；原位升级保留：$StartMenuShortcutPath"
      WriteINIStr "$INSTDIR\install.ini" "Shortcuts" "StartMenu" "$StartMenuShortcutPath"
    ${EndIf}
  ${EndIf}
  ${If} $0 == 0
    ClearErrors
    CreateDirectory "$StartMenuDirectory"
    ${If} ${FileExists} "$StartMenuShortcutPath"
      Delete "$StartMenuShortcutPath"
      ClearErrors
    ${EndIf}
    CreateShortCut "$StartMenuShortcutPath" "$INSTDIR\${APP_EXE_NAME}" "" "$INSTDIR\${APP_EXE_NAME}" 0
    ${If} ${Errors}
      StrCpy $ShortcutWarnings "$ShortcutWarnings$\r$\n- 开始菜单：$StartMenuShortcutPath"
      DetailPrint "警告：开始菜单快捷方式创建/更新失败：$StartMenuShortcutPath"
      ClearErrors
    ${Else}
      WriteINIStr "$INSTDIR\install.ini" "Shortcuts" "StartMenu" "$StartMenuShortcutPath"
      ClearErrors
    ${EndIf}
  ${EndIf}

  ${If} $DesktopChoice == ${BST_CHECKED}
    StrCpy $0 0
    ${If} $IsUpgrade == 1
    ${AndIf} $ExistingHasNativeLauncher == 1
  ${AndIf} $INSTDIR == $ExistingInstallDir
      ; Prefer the previous install's recorded path. This fixes upgrades where $DESKTOP now
      ; resolves differently (for example user vs public/redirected Desktop).
      ReadINIStr $1 "$INSTDIR\install.ini" "Shortcuts" "Desktop"
      ${If} $1 != ""
      ${AndIf} ${FileExists} "$1"
        StrCpy $DesktopShortcutPath "$1"
        StrCpy $0 1
        DetailPrint "桌面快捷方式已存在；原位升级直接保留：$DesktopShortcutPath"
        WriteINIStr "$INSTDIR\install.ini" "Shortcuts" "Desktop" "$DesktopShortcutPath"
      ${ElseIf} ${FileExists} "$DesktopShortcutPath"
        StrCpy $0 1
        DetailPrint "桌面快捷方式已存在；原位升级直接保留：$DesktopShortcutPath"
        WriteINIStr "$INSTDIR\install.ini" "Shortcuts" "Desktop" "$DesktopShortcutPath"
      ${EndIf}
    ${EndIf}
    ${If} $0 == 0
      ; Fresh install or legacy javaw.exe shortcut: replace once with EyeRelax.exe.
      ${If} ${FileExists} "$DesktopShortcutPath"
        ClearErrors
        Delete "$DesktopShortcutPath"
        ${If} ${Errors}
          DetailPrint "提示：现有桌面快捷方式暂时无法删除，将尝试直接更新：$DesktopShortcutPath"
          ClearErrors
        ${EndIf}
      ${EndIf}
      ClearErrors
      CreateShortCut "$DesktopShortcutPath" "$INSTDIR\${APP_EXE_NAME}" "" "$INSTDIR\${APP_EXE_NAME}" 0
      ${If} ${Errors}
        ; Desktop shortcuts are optional. Keep the application install successful and record
        ; the failure only in the details log rather than interrupting a completed upgrade.
        StrCpy $ShortcutWarnings "$ShortcutWarnings$\r$\n- 桌面：$DesktopShortcutPath"
        DetailPrint "警告：桌面快捷方式创建/更新失败：$DesktopShortcutPath"
        ClearErrors
      ${Else}
        WriteINIStr "$INSTDIR\install.ini" "Shortcuts" "Desktop" "$DesktopShortcutPath"
        ClearErrors
      ${EndIf}
    ${EndIf}
  ${Else}
    ; The option is off: remove only the shortcut path recorded/owned by EyeRelax.
    ReadINIStr $0 "$INSTDIR\install.ini" "Shortcuts" "Desktop"
    ${If} $0 != ""
      Delete "$0"
      DeleteINIStr "$INSTDIR\install.ini" "Shortcuts" "Desktop"
      ClearErrors
    ${EndIf}
  ${EndIf}
  ; Build a normal .lnk template once. Runtime toggling later only copies/removes this
  ; shortcut in the current user's Windows Startup known folder. This avoids Java writing a
  ; Run persistence registry value, which is a common antivirus heuristic trigger.
  CreateDirectory "$INSTDIR\support"
  ClearErrors
  CreateShortCut "$StartupTemplatePath" "$INSTDIR\${APP_EXE_NAME}" "--startup" "$INSTDIR\${APP_EXE_NAME}" 0
  ${If} ${Errors}
    StrCpy $ModernMessage "无法创建 EyeRelax 登录启动快捷方式模板。请检查安装目录权限或安全软件提示后重试。"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}

  ; Login startup belongs to the interactive user even when the application itself is installed
  ; for all users. NSIS resolves $SMSTARTUP through the Windows shell instead of a username/path.
  SetShellVarContext current
  StrCpy $StartupShortcutPath "$InstallUserStartupDir\${PRODUCT}.lnk"
  ${If} $StartupChoice == ${BST_CHECKED}
    CreateDirectory "$InstallUserStartupDir"
    ClearErrors
    CreateShortCut "$StartupShortcutPath" "$INSTDIR\${APP_EXE_NAME}" "--startup" "$INSTDIR\${APP_EXE_NAME}" 0
    ${If} ${Errors}
      StrCpy $ShortcutWarnings "$ShortcutWarnings$\r$\n- 登录启动：$StartupShortcutPath"
      DetailPrint "警告：登录启动快捷方式创建失败：$StartupShortcutPath"
      ClearErrors
    ${Else}
      WriteINIStr "$INSTDIR\install.ini" "Shortcuts" "Startup" "$StartupShortcutPath"
    ${EndIf}
  ${Else}
    ReadINIStr $0 "$INSTDIR\install.ini" "Shortcuts" "Startup"
    ${If} $0 != ""
      Delete "$0"
      DeleteINIStr "$INSTDIR\install.ini" "Shortcuts" "Startup"
      ClearErrors
    ${EndIf}
  ${EndIf}
  ${If} $MultiUser.InstallMode == "AllUsers"
    SetShellVarContext all
  ${Else}
    SetShellVarContext current
  ${EndIf}

  ; Best-effort migration cleanup for old versions. Never make the new Startup-folder path fail
  ; merely because security software blocks deletion of a legacy Run value.
  ReadRegStr $0 HKCU "${LEGACY_RUN_KEY}" "EyeRelax"
  ${If} $0 == $RunCommand
    ClearErrors
    DeleteRegValue HKCU "${LEGACY_RUN_KEY}" "EyeRelax"
    ClearErrors
  ${EndIf}
  ClearErrors
  SetOutPath "$INSTDIR"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr SHCTX "${UNINSTALL_KEY}" "DisplayName" "EyeRelax 护眼计时器"
  WriteRegStr SHCTX "${UNINSTALL_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr SHCTX "${UNINSTALL_KEY}" "Publisher" "EyeRelax contributors"
  WriteRegStr SHCTX "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\${APP_EXE_NAME},0"
  WriteRegStr SHCTX "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr SHCTX "${UNINSTALL_KEY}" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\" /$MultiUser.InstallMode'
  WriteRegStr SHCTX "${UNINSTALL_KEY}" "QuietUninstallString" '$\"$INSTDIR\Uninstall.exe$\" /S /$MultiUser.InstallMode'
  WriteRegDWORD SHCTX "${UNINSTALL_KEY}" "NoModify" 1
  WriteRegDWORD SHCTX "${UNINSTALL_KEY}" "NoRepair" 1
  WriteRegDWORD SHCTX "${UNINSTALL_KEY}" "EstimatedSize" ${PAYLOAD_KIB}
  IfErrors install_failed
  ${If} $ShortcutWarnings != ""
    DetailPrint "提示：部分可选快捷方式未能创建：$ShortcutWarnings。程序文件与数据已正常安装。"
  ${EndIf}
  ${If} $IsUpgrade == 1
  ${AndIf} $ExistingInstallDir != $INSTDIR
    Call CleanFormerInstallation
  ${EndIf}
  Goto install_done
  install_failed:
    Call ReleaseUpdateLock
    StrCpy $SetupLogLine "安装未完成。$HelperOutput"
    Call SetupWriteLog
    StrCpy $ModernMessage "安装未完成。请查看诊断记录，排除文件占用或权限问题后重试。诊断记录：$SetupLog"
    Call ShowOperationError
    SetErrorLevel 2
    Abort
  install_done:
    StrCpy $SetupSucceeded 1
    StrCpy $SetupLogLine "安装完成。$ShortcutWarnings"
    Call SetupWriteLog
    Call ReleaseUpdateLock
    !insertmacro ModernStage 100 "安装已完成，个人数据已保留。"
    ${If} $ModernEnabled == 1
      WriteINIStr "$ModernSession" "State" "warning" "$ShortcutWarnings"
      WriteINIStr "$ModernSession" "State" "phase" "done"
    ${EndIf}
SectionEnd

Function .onGUIEnd
  Call ReleaseUpdateLock
FunctionEnd

Function .onInstSuccess
  IfSilent 0 +2
    Call ReleaseUpdateLock
  Call ModernWaitFinish
FunctionEnd

Function .onInstFailed
  Call ModernFailure
FunctionEnd

Function un.onInit
  StrCpy $ModernEnabled 0
  StrCpy $ModernWorker 0
  System::Call 'kernel32::GetCurrentProcessId() i.r0'
  StrCpy $SetupLog "$TEMP\EyeRelax-uninstall-${VERSION}-$0.log"
  SetRegView 64
  UserInfo::GetName
  Pop $SetupAccount
  ReadEnvStr $1 "USERDOMAIN"
  StrCpy $SetupAccount "$1\$SetupAccount"
  ${GetParameters} $InstallArguments
  ClearErrors
  ${GetOptions} "$InstallArguments" "/EyeRelaxAccount=" $SetupCallerAccount
  ${IfNot} ${Errors}
  ${AndIf} $SetupCallerAccount != $SetupAccount
    MessageBox MB_OK|MB_ICONSTOP "请使用原 Windows 账户授权卸载。"
    SetErrorLevel 2
    Abort
  ${EndIf}
  ReadINIStr $DataDir "$INSTDIR\install.ini" "Install" "DataLocation"
  StrCpy $SetupGuardNeedsAdmin 0
  ${ConfigRead} "$DataDir\config.properties" "usageGuardEnabled=" $1
  ${If} $1 == "true"
    StrCpy $SetupGuardNeedsAdmin 1
  ${EndIf}
  ReadINIStr $0 "$INSTDIR\install.ini" "Install" "Mode"
  ${If} $0 == "AllUsers"
  ${OrIf} $SetupGuardNeedsAdmin == 1
    StrCpy $SetupRequestedScope "$0"
    UserInfo::GetAccountType
    Pop $1
    ${If} $1 != "Admin"
    ${AndIf} $1 != "Power"
      StrCpy $SetupElevationArguments '/$SetupRequestedScope /EyeRelaxAccount=$\"$SetupAccount$\"'
      IfSilent 0 +2
        StrCpy $SetupElevationArguments "$SetupElevationArguments /S"
      ClearErrors
      ExecShellWait "runas" "$INSTDIR\Uninstall.exe" "$SetupElevationArguments" SW_SHOWNORMAL
      ${If} ${Errors}
        MessageBox MB_OK|MB_ICONINFORMATION "没有获得管理员授权，尚未卸载任何文件。"
        SetErrorLevel 2
      ${EndIf}
      Quit
    ${EndIf}
  ${EndIf}
  !insertmacro MULTIUSER_UNINIT
  ReadINIStr $0 "$INSTDIR\install.ini" "Install" "AppId"
  ${If} $0 != "${APP_ID}"
    MessageBox MB_OK|MB_ICONSTOP "找不到有效的安装标记，已停止卸载，以免删除其他文件。"
    Abort
  ${EndIf}
  ReadINIStr $0 "$INSTDIR\install.ini" "Install" "Mode"
  ${If} $0 == "AllUsers"
    Call un.MultiUser.InstallMode.AllUsers
  ${ElseIf} $0 == "CurrentUser"
    Call un.MultiUser.InstallMode.CurrentUser
  ${Else}
    MessageBox MB_OK|MB_ICONSTOP "安装范围信息损坏，已停止卸载。"
    Abort
  ${EndIf}
  ; NSIS runs the uninstaller from its own temporary copy. Do not work inside $INSTDIR.
  ReadEnvStr $UserHomeDir "USERPROFILE"
  StrCpy $UserBootstrapDir "$UserHomeDir\${DATA_DIR_NAME}"
  StrCpy $ExistingInstallDir "$INSTDIR"
  StrCpy $UpdateLockHandle ""
  ReadINIStr $DataDir "$INSTDIR\install.ini" "Install" "DataLocation"
  InitPluginsDir
  CreateDirectory "$PLUGINSDIR\helper"
  CopyFiles /SILENT "$INSTDIR\${APP_SUBDIR}\${APP_JAR_NAME}" "$PLUGINSDIR\helper\rest-1.0.0.jar"
  StrCpy $HelperJava "$INSTDIR\jre\bin\java.exe"
  SetOutPath "$TEMP"
  Call un.AcquireUpdateLock
  Call un.ModernStart
FunctionEnd

Function un.BeginUninstall
  DetailPrint "准备卸载；个人数据将保留。"
FunctionEnd

Function un.onGUIEnd
  Call un.ReleaseUpdateLock
FunctionEnd

Function un.onUninstSuccess
  Call un.ReleaseUpdateLock
  Call un.ModernWaitFinish
FunctionEnd

Function un.onUninstFailed
  Call un.ModernFailure
FunctionEnd

!macro DEFINE_DELETE_OWNED PREFIX
Function ${PREFIX}DeleteOwnedFile
  delete_retry:
    ClearErrors
    Delete "$OwnedFile"
    IfFileExists "$OwnedFile" 0 deleted_ok
    ${If} $ModernEnabled == 1
      StrCpy $ModernMessage "文件尚未删除：$OwnedFile。请关闭占用该文件的应用并检查目录权限，再重新运行安装器。此次操作未完成。"
      Call ${PREFIX}ShowOperationError
    ${Else}
      MessageBox MB_RETRYCANCEL|MB_ICONEXCLAMATION "文件尚未删除：$\r$\n$OwnedFile$\r$\n$\r$\n请关闭占用后重试。取消时不会报告卸载成功，可在排除占用后重新运行。" IDRETRY delete_retry
    ${EndIf}
    SetErrorLevel 2
    Abort
  deleted_ok:
FunctionEnd

!macroend
!insertmacro DEFINE_DELETE_OWNED ""
!insertmacro DEFINE_DELETE_OWNED "un."

Section "Uninstall"
  !insertmacro ModernStage 10 "正在停用应用保护"
  Call un.AcquireUpdateLock
  StrCpy $HelperCommand 'remove-guard $\"$INSTDIR$\"'
  Call un.RunInstallerHelper
  ${If} $HelperExit != "0"
    IfFileExists "$SetupLog" uninstall_guard_log_append
      FileOpen $9 "$SetupLog" w
      FileWriteWord $9 0xFEFF
      FileClose $9
    uninstall_guard_log_append:
    FileOpen $9 "$SetupLog" a
    FileSeek $9 0 END
    FileWriteUTF16LE $9 "运行保护清理失败：$HelperOutput$\r$\n"
    FileClose $9
    StrCpy $ModernMessage "运行保护尚未清理，未删除应用文件。请查看诊断记录中的路径或权限提示后重试。诊断记录：$SetupLog"
    Call un.ShowOperationError
    SetErrorLevel 2
    Abort
  ${EndIf}
  !insertmacro CheckOwnedPayload "un."
  !insertmacro ModernStage 35 "正在移除应用"
  !insertmacro DeletePayload "un."
  ; Verify the actual per-file removal before removing the application registration.
  !insertmacro VerifyPayloadRemoved
  !insertmacro ModernStage 80 "正在清理快捷方式"
  ClearErrors
  ReadINIStr $OwnedFile "$INSTDIR\install.ini" "Shortcuts" "StartMenu"
  ${If} $OwnedFile == ""
    StrCpy $OwnedFile "$SMPROGRAMS\${STARTMENU_DIR_NAME}\${PRODUCT}.lnk"
  ${EndIf}
  StrCpy $0 $OwnedFile
  Call un.DeleteOwnedFile
  ${GetParent} "$0" $1
  ${If} $1 != ""
    RMDir "$1"
  ${EndIf}
  ReadINIStr $OwnedFile "$INSTDIR\install.ini" "Shortcuts" "Desktop"
  ${If} $OwnedFile == ""
    StrCpy $OwnedFile "$DESKTOP\${PRODUCT}.lnk"
  ${EndIf}
  Call un.DeleteOwnedFile
  ; Remove the exact per-user Startup shortcut recorded by this installation.
  ReadINIStr $OwnedFile "$INSTDIR\install.ini" "Shortcuts" "Startup"
  ${If} $OwnedFile != ""
    Call un.DeleteOwnedFile
  ${EndIf}
  ; The template is an installer-owned artifact, not user data.
  StrCpy $OwnedFile "$INSTDIR\support\EyeRelax-Startup.lnk"
  Call un.DeleteOwnedFile
  RMDir "$INSTDIR\support"

  ; Best-effort cleanup of the retired Run-key form from older EyeRelax builds.
  StrCpy $RunCommand '$\"$INSTDIR\jre\bin\javaw.exe$\" -jar $\"$INSTDIR\${APP_SUBDIR}\${APP_JAR_NAME}$\" --startup'
  ReadRegStr $0 HKCU "${LEGACY_RUN_KEY}" "EyeRelax"
  ${If} $0 == $RunCommand
    ClearErrors
    DeleteRegValue HKCU "${LEGACY_RUN_KEY}" "EyeRelax"
    ClearErrors
  ${EndIf}
  StrCpy $OwnedFile "$INSTDIR\${APP_EXE_NAME}"
  Call un.DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\EyeRelax.ico"
  Call un.DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\RELEASE-NOTES.txt"
  Call un.DeleteOwnedFile
  ReadRegStr $0 SHCTX "${UNINSTALL_KEY}" "InstallLocation"
  ${If} $0 == $INSTDIR
    ClearErrors
    DeleteRegKey SHCTX "${UNINSTALL_KEY}"
    IfErrors uninstall_registry_failed
  ${EndIf}
  StrCpy $OwnedFile "$INSTDIR\install.properties"
  Call un.DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\Uninstall.exe"
  Call un.DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\install.ini"
  Call un.DeleteOwnedFile
  RMDir "$INSTDIR"
  StrCpy $UninstallSummary "个人数据已保留。"
  IfFileExists "$INSTDIR\*.*" 0 uninstall_done
  StrCpy $UninstallSummary "$UninstallSummary安装文件夹中的其他文件也已保留。"
  Goto uninstall_done
  uninstall_registry_failed:
    StrCpy $ModernMessage "未能移除卸载信息，请检查权限后重试。"
    Call un.ShowOperationError
    SetErrorLevel 2
    Abort
  uninstall_done:
    Call un.ReleaseUpdateLock
    !insertmacro ModernStage 100 "$UninstallSummary"
    ${If} $ModernEnabled == 1
      WriteINIStr "$ModernSession" "State" "phase" "done"
    ${EndIf}
SectionEnd


Function RunInstallerHelper
  nsExec::ExecToStack '$\"$HelperJava$\" $\"-Duser.home=$UserHomeDir$\" -cp $\"$PLUGINSDIR\helper\rest-1.0.0.jar$\" com.eye.app.InstallerSupport $HelperCommand'
  Pop $HelperExit
  Pop $HelperOutput
FunctionEnd

Function LoadFallbackHelperRuntime
  SetOutPath "$PLUGINSDIR\helper-runtime"
  File /r "${PAYLOAD}\jre\*"
  StrCpy $HelperJava "$PLUGINSDIR\helper-runtime\bin\java.exe"
FunctionEnd

Function CleanFormerInstallation
  ReadINIStr $0 "$ExistingInstallDir\install.ini" "Install" "AppId"
  ${If} $0 != "${APP_ID}"
    ${If} $ModernEnabled == 1
      StrCpy $ShortcutWarnings "$ShortcutWarnings 原应用目录缺少有效安装标记，未删除其中的文件：$ExistingInstallDir"
    ${Else}
      MessageBox MB_OK|MB_ICONSTOP "原应用目录缺少有效安装标记，未删除其中的文件：$ExistingInstallDir"
    ${EndIf}
    Goto install_cleanup_end
  ${EndIf}
  StrCpy $INSTDIR "$ExistingInstallDir"
  !insertmacro CheckOwnedPayload ""
  Call CheckLegacyPayloadUnlocked
  Call DeleteLegacyPayload
  !insertmacro DeletePayload ""
  StrCpy $OwnedFile "$INSTDIR\support\EyeRelax-Startup.lnk"
  Call DeleteOwnedFile
  RMDir "$INSTDIR\support"
  StrCpy $OwnedFile "$INSTDIR\${APP_EXE_NAME}"
  Call DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\EyeRelax.ico"
  Call DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\RELEASE-NOTES.txt"
  Call DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\Uninstall.exe"
  Call DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\install.properties"
  Call DeleteOwnedFile
  StrCpy $OwnedFile "$INSTDIR\install.ini"
  Call DeleteOwnedFile
  RMDir "$INSTDIR"
  StrCpy $INSTDIR "$SelectedInstallDir"
  install_cleanup_end:
FunctionEnd

Function LaunchInstalledApp
  Call ReleaseUpdateLock
  ; Exec creates the application process and returns without waiting for it to exit.
  IfFileExists "$INSTDIR\${APP_EXE_NAME}" launch_ready
    MessageBox MB_OK|MB_ICONEXCLAMATION "安装已完成，但启动文件不存在。请检查安全软件拦截记录，或重新运行安装器。"
    Return
  launch_ready:
  ClearErrors
  Exec '$\"$INSTDIR\${APP_EXE_NAME}$\"'
  IfErrors 0 launch_done
    MessageBox MB_OK|MB_ICONEXCLAMATION "安装已完成，但无法启动 EyeRelax。请检查安全软件拦截记录，或从开始菜单重试。"
  launch_done:
FunctionEnd


Function DetectExistingInstall
  StrCpy $DetectedInstallScope ""
  StrCpy $DetectedInstallDir ""

  ${If} $ForceAllUsersUpdate != 1
    ReadRegStr $0 HKCU "${UNINSTALL_KEY}" "InstallLocation"
    ${If} $0 != ""
      ReadINIStr $1 "$0\install.ini" "Install" "AppId"
      ${If} $1 == "${APP_ID}"
        StrCpy $DetectedInstallScope "CurrentUser"
        StrCpy $DetectedInstallDir "$0"
        Return
      ${EndIf}
      IfFileExists "$0\${APP_EXE_NAME}" 0 detect_existing_hklm
      IfFileExists "$0\${APP_SUBDIR}\${APP_JAR_NAME}" 0 detect_existing_hklm
      StrCpy $DetectedInstallScope "CurrentUser"
      StrCpy $DetectedInstallDir "$0"
      Return
    ${EndIf}
  ${EndIf}

  detect_existing_hklm:
  ReadRegStr $0 HKLM "${UNINSTALL_KEY}" "InstallLocation"
  ${If} $0 != ""
    ReadINIStr $1 "$0\install.ini" "Install" "AppId"
    ${If} $1 == "${APP_ID}"
      StrCpy $DetectedInstallScope "AllUsers"
      StrCpy $DetectedInstallDir "$0"
      Return
    ${EndIf}
    IfFileExists "$0\${APP_EXE_NAME}" 0 detect_existing_done
    IfFileExists "$0\${APP_SUBDIR}\${APP_JAR_NAME}" 0 detect_existing_done
    StrCpy $DetectedInstallScope "AllUsers"
    StrCpy $DetectedInstallDir "$0"
  ${EndIf}
  detect_existing_done:
FunctionEnd




Function un.ReleaseUpdateLock
  ${If} $UpdateLockHandle != ""
    StrCpy $0 $UpdateLockHandle
    System::Call 'kernel32::UnlockFile(p r0, i 0, i 0, i 1, i 0)'
    System::Call 'kernel32::CloseHandle(p r0)'
    StrCpy $UpdateLockHandle ""
  ${EndIf}
FunctionEnd


Function un.RunInstallerHelper
  nsExec::ExecToStack '$\"$HelperJava$\" $\"-Duser.home=$UserHomeDir$\" -cp $\"$PLUGINSDIR\helper\rest-1.0.0.jar$\" com.eye.app.InstallerSupport $HelperCommand'
  Pop $HelperExit
  Pop $HelperOutput
FunctionEnd
