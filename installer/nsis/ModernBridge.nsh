; The WPF window owns presentation; only NSIS may validate or mutate an installation.
; Each invocation has a private NSIS temporary directory and a single UI process.
Var ModernEnabled
Var ModernSession
Var ModernProcess
Var ModernPid
Var ModernRequest
Var ModernProgress
Var ModernMessage
Var ModernWorker

!macro ModernStage VALUE TEXT
  ${If} $ModernEnabled == 1
    WriteINIStr "$ModernSession" "State" "progress" "${VALUE}"
    WriteINIStr "$ModernSession" "State" "message" "${TEXT}"
  ${EndIf}
!macroend

!macro ModernFunctions PREFIX UNINSTALL
Function ${PREFIX}ModernStart
  ${If} $ModernWorker == 1
    StrCpy $ModernEnabled 1
    WriteINIStr "$ModernSession" "State" "phase" "working"
    Return
  ${EndIf}
  StrCpy $ModernEnabled 0
  StrCpy $ModernProcess 0
  IfSilent modern_skip
  InitPluginsDir
  SetOutPath "$PLUGINSDIR\modern"
  File /oname=SetupWindow.exe "assets\SetupWindow.exe"
  StrCpy $ModernSession "$PLUGINSDIR\modern\session.ini"
  FileOpen $0 "$ModernSession" w
  FileWriteWord $0 0xFEFF
  FileClose $0
  WriteINIStr "$ModernSession" "State" "version" "${VERSION}"
  WriteINIStr "$ModernSession" "State" "existingVersion" "$ExistingVersion"
  WriteINIStr "$ModernSession" "State" "installDir" "$INSTDIR"
  WriteINIStr "$ModernSession" "State" "dataDir" "$DataDir"
  WriteINIStr "$ModernSession" "State" "mode" "$MultiUser.InstallMode"
  WriteINIStr "$ModernSession" "State" "isUpgrade" "$IsUpgrade"
  WriteINIStr "$ModernSession" "State" "desktop" "$DesktopChoice"
  WriteINIStr "$ModernSession" "State" "startup" "$StartupChoice"
  WriteINIStr "$ModernSession" "State" "shared" "$SharedChoice"
  WriteINIStr "$ModernSession" "State" "needsConsent" "$VersionNeedsConsent"
  WriteINIStr "$ModernSession" "State" "uninstall" "${UNINSTALL}"
  WriteINIStr "$ModernSession" "State" "log" "$SetupLog"
  WriteINIStr "$ModernSession" "State" "userDefaultDir" "$LOCALAPPDATA\Programs\${APP_DIR_NAME}"
  WriteINIStr "$ModernSession" "State" "machineDefaultDir" "$PROGRAMFILES64\${APP_DIR_NAME}"
  WriteINIStr "$ModernSession" "State" "phase" "options"
  StrCpy $0 "安装"
  ${If} $IsUpgrade == 1
    StrCpy $0 "更新"
    ${If} $VersionComparison == "0"
      StrCpy $0 "修复"
    ${ElseIf} $VersionNeedsConsent == 1
      StrCpy $0 "安装此版本"
    ${EndIf}
  ${EndIf}
  !if ${UNINSTALL} == 1
    StrCpy $0 "卸载"
  !endif
  WriteINIStr "$ModernSession" "State" "actionLabel" "$0"
  System::Call 'kernel32::GetCurrentProcessId() i.r0'
  ClearErrors
  Exec '$\"$PLUGINSDIR\modern\SetupWindow.exe$\" --session $\"$ModernSession$\" --owner $0'
  IfErrors modern_start_failed
  StrCpy $ModernProgress 0
  modern_wait_ready:
    ReadINIStr $0 "$ModernSession" "Bridge" "ready"
    ${If} $0 == "1"
      ReadINIStr $ModernPid "$ModernSession" "Bridge" "pid"
      System::Call 'kernel32::OpenProcess(i 0x100000, i 0, i $ModernPid) p.r0'
      StrCpy $ModernProcess $0
      ${If} $ModernProcess == 0
        Goto modern_start_failed
      ${EndIf}
      StrCpy $ModernEnabled 1
      Goto modern_wait_request
    ${EndIf}
    IntOp $ModernProgress $ModernProgress + 1
    ${If} $ModernProgress >= 100
      Goto modern_start_failed
    ${EndIf}
    Sleep 100
    Goto modern_wait_ready

  modern_wait_request:
    ReadINIStr $ModernRequest "$ModernSession" "Bridge" "request"
    ${If} $ModernRequest == "cancel"
      Call ${PREFIX}ReleaseUpdateLock
      Call ${PREFIX}ModernCloseHandle
      Quit
    ${ElseIf} $ModernRequest == "install"
      Goto modern_choices
    ${EndIf}
    Call ${PREFIX}ModernIsAlive
    ${If} $0 != 1
      Call ${PREFIX}ReleaseUpdateLock
      Call ${PREFIX}ModernCloseHandle
      SetErrorLevel 2
      Quit
    ${EndIf}
    Sleep 100
    Goto modern_wait_request

  modern_choices:
  !if ${UNINSTALL} == 0
    ReadINIStr $INSTDIR "$ModernSession" "Choice" "installDir"
    ReadINIStr $DataDir "$ModernSession" "Choice" "dataDir"
    ReadINIStr $DesktopChoice "$ModernSession" "Choice" "desktop"
    ReadINIStr $StartupChoice "$ModernSession" "Choice" "startup"
    ReadINIStr $SharedChoice "$ModernSession" "Choice" "shared"
    ReadINIStr $VersionConsent "$ModernSession" "Choice" "consent"
    ReadINIStr $0 "$ModernSession" "Choice" "mode"
    ${If} $IsUpgrade != 1
      ${If} $0 == "AllUsers"
        StrCpy $MultiUser.InstallMode "AllUsers"
        SetShellVarContext all
      ${ElseIf} $0 == "CurrentUser"
        StrCpy $MultiUser.InstallMode "CurrentUser"
        SetShellVarContext current
      ${Else}
        SetErrorLevel 2
        Abort
      ${EndIf}
    ${EndIf}
    ${If} $VersionNeedsConsent == 1
    ${AndIf} $VersionConsent != 1
      SetErrorLevel 4
      Abort
    ${EndIf}
    StrCpy $DataChangeRequested 1
    Call PrepareInstall
  !endif
  WriteINIStr "$ModernSession" "Bridge" "request" ""
  WriteINIStr "$ModernSession" "State" "phase" "working"
  SetSilent silent
  Return

  modern_start_failed:
    MessageBox MB_OK|MB_ICONSTOP "无法打开安装界面，尚未修改应用。请检查 Windows 的 .NET Framework 组件后重试。"
    Call ${PREFIX}ReleaseUpdateLock
    SetErrorLevel 2
    Abort
  modern_skip:
FunctionEnd

Function ${PREFIX}ModernIsAlive
  StrCpy $0 0
  ${If} $ModernProcess != 0
    System::Call 'kernel32::WaitForSingleObject(p $ModernProcess, i 0) i.r0'
    ${If} $0 == 258
      StrCpy $0 1
    ${Else}
      StrCpy $0 0
    ${EndIf}
  ${EndIf}
FunctionEnd

Function ${PREFIX}ModernCloseHandle
  ${If} $ModernProcess != 0
    System::Call 'kernel32::CloseHandle(p $ModernProcess)'
    StrCpy $ModernProcess 0
  ${EndIf}
FunctionEnd

Function ${PREFIX}ModernWaitFinish
  ${If} $ModernEnabled != 1
  ${OrIf} $ModernWorker == 1
    Return
  ${EndIf}
  modern_wait_finish:
    ReadINIStr $ModernRequest "$ModernSession" "Bridge" "request"
    ${If} $ModernRequest == "launch"
    ${OrIf} $ModernRequest == "close"
      !if ${UNINSTALL} == 0
        ${If} $ModernRequest == "launch"
        ${AndIf} $SetupSucceeded == 1
          Call LaunchInstalledApp
        ${EndIf}
      !endif
      Call ${PREFIX}ModernCloseHandle
      Return
    ${EndIf}
    Call ${PREFIX}ModernIsAlive
    ${If} $0 != 1
      Call ${PREFIX}ModernCloseHandle
      Return
    ${EndIf}
    Sleep 100
    Goto modern_wait_finish
FunctionEnd

Function ${PREFIX}ModernFailure
  Call ${PREFIX}ReleaseUpdateLock
  ${If} $ModernEnabled == 1
    ReadINIStr $0 "$ModernSession" "State" "phase"
    ${If} $0 != "error"
      WriteINIStr "$ModernSession" "State" "message" "操作未完成。请查看安装日志，解决文件占用或权限问题后重试。"
    ${EndIf}
    WriteINIStr "$ModernSession" "State" "phase" "error"
    Call ${PREFIX}ModernWaitFinish
  ${EndIf}
FunctionEnd
!macroend
!insertmacro ModernFunctions "" 0
!insertmacro ModernFunctions "un." 1
