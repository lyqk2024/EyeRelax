; EyeRelax setup presentation. Native controls; installation and data work stay in EyeRelax.nsi.
!ifndef EYERELAX_SETUP_UI
!define EYERELAX_SETUP_UI

Var SetupCustom
Var SetupAction
Var SetupFinished
Var SetupDialog
Var SetupIcon
Var SetupIconHandle
Var SetupTitleFont
Var SetupHeadingFont
Var SetupBodyFont
Var SetupSmallFont
Var SetupCustomControl
Var SetupScopeUserControl
Var SetupScopeAllControl
Var SetupPreviousScope
Var SetupLaunchControl
Var SetupLaunchChoice
Var SetupMoveHandle
Var SetupRect
Var SetupClientWidth
Var SetupClientHeight
Var SetupWindowWidth
Var SetupWindowHeight
Var SetupFrameWidth
Var SetupFrameHeight
Var SetupWindowX
Var SetupWindowY

!define ER_SETUP_BG "F4F7F3"
!define ER_SETUP_INK "203D34"
!define ER_SETUP_MUTED "65776D"

; All geometry is in dialog units, mapped using the actual wizard font and DPI.
!macro ERSetupMove HANDLE X Y W H
  StrCpy $SetupMoveHandle "${HANDLE}"
  Push $0
  Push $1
  Push $2
  Push $3
  Push $4
  Push $5
  System::Alloc 16
  Pop $5
  IntOp $3 ${X} + ${W}
  IntOp $4 ${Y} + ${H}
  System::Call '*$5(i ${X}, i ${Y}, i r3, i r4)'
  System::Call 'user32::MapDialogRect(p $HWNDPARENT, p r5)'
  System::Call '*$5(i .r0, i .r1, i .r2, i .r3)'
  IntOp $2 $2 - $0
  IntOp $3 $3 - $1
  System::Call 'user32::MoveWindow(p $SetupMoveHandle, i r0, i r1, i r2, i r3, i 1)'
  System::Free $5
  Pop $5
  Pop $4
  Pop $3
  Pop $2
  Pop $1
  Pop $0
!macroend

!macro ERSetupLabel X Y W H TEXT FONT COLOR BACKGROUND
  ${NSD_CreateLabel} ${X}u ${Y}u ${W}u ${H}u "${TEXT}"
  Pop $0
  SendMessage $0 ${WM_SETFONT} ${FONT} 0
  SetCtlColors $0 "${COLOR}" "${BACKGROUND}"
!macroend

!macro ERSetupReadOnly X Y W H TEXT
  ${NSD_CreateText} ${X}u ${Y}u ${W}u ${H}u "${TEXT}"
  Pop $0
  ${NSD_AddStyle} $0 ${ES_READONLY}
  SendMessage $0 ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $0 "${ER_SETUP_INK}" "FFFFFF"
!macroend

!macro ERSetupFullEnter
  ShowWindow $mui.Header.Text ${SW_HIDE}
  ShowWindow $mui.Header.SubText ${SW_HIDE}
  ShowWindow $mui.Header.Image ${SW_HIDE}
  ShowWindow $mui.Header.Background ${SW_HIDE}
  ShowWindow $mui.Branding.Background ${SW_HIDE}
  ShowWindow $mui.Branding.Text ${SW_HIDE}
  ShowWindow $mui.Line.Standard ${SW_HIDE}
  ShowWindow $mui.Line.FullWindow ${SW_SHOW}
!macroend

!macro ERSetupFullLeave
  ShowWindow $mui.Header.Text ${SW_SHOW}
  ShowWindow $mui.Header.SubText ${SW_SHOW}
  ShowWindow $mui.Header.Image ${SW_HIDE}
  ShowWindow $mui.Header.Background ${SW_SHOW}
  ShowWindow $mui.Branding.Background ${SW_HIDE}
  ShowWindow $mui.Branding.Text ${SW_HIDE}
  ShowWindow $mui.Line.Standard ${SW_SHOW}
  ShowWindow $mui.Line.FullWindow ${SW_HIDE}
!macroend

!macro ERSetupHeading TITLE SUBTITLE
  ${NSD_CreateIcon} 20u 18u 26u 26u ""
  Pop $SetupIcon
  ${NSD_SetIconFromInstaller} $SetupIcon $SetupIconHandle
  !insertmacro ERSetupLabel 59 15 291 28 "${TITLE}" $SetupTitleFont "${ER_SETUP_INK}" "FFFFFF"
  !insertmacro ERSetupLabel 60 45 290 15 "${SUBTITLE}" $SetupSmallFont "${ER_SETUP_MUTED}" "FFFFFF"
!macroend

!macro ERSetupGuiInit PREFIX
Function ${PREFIX}SetupGuiInit
  CreateFont $SetupTitleFont "Microsoft YaHei UI" 18 700
  CreateFont $SetupHeadingFont "Microsoft YaHei UI" 11 700
  CreateFont $SetupBodyFont "Microsoft YaHei UI" 10
  CreateFont $SetupSmallFont "Microsoft YaHei UI" 9
  StrCpy $SetupFinished 0
  ${If} $SetupCustom != 1
    StrCpy $SetupCustom 0
  ${EndIf}
  SetCtlColors $HWNDPARENT "${ER_SETUP_INK}" "FFFFFF"

  ; Size the native template, including its page placeholders and fixed footer.
  System::Alloc 16
  Pop $SetupRect
  System::Call 'user32::GetWindowRect(p $HWNDPARENT, p $SetupRect)'
  System::Call '*$SetupRect(i .r0, i .r1, i .r2, i .r3)'
  IntOp $SetupWindowWidth $2 - $0
  IntOp $SetupWindowHeight $3 - $1
  System::Call 'user32::GetClientRect(p $HWNDPARENT, p $SetupRect)'
  System::Call '*$SetupRect(i, i, i .r2, i .r3)'
  IntOp $SetupFrameWidth $SetupWindowWidth - $2
  IntOp $SetupFrameHeight $SetupWindowHeight - $3
  System::Call '*$SetupRect(i 0, i 0, i 370, i 238)'
  System::Call 'user32::MapDialogRect(p $HWNDPARENT, p $SetupRect)'
  System::Call '*$SetupRect(i, i, i .r2, i .r3)'
  StrCpy $SetupClientWidth $2
  StrCpy $SetupClientHeight $3
  IntOp $SetupWindowWidth $SetupClientWidth + $SetupFrameWidth
  IntOp $SetupWindowHeight $SetupClientHeight + $SetupFrameHeight
  System::Call 'user32::SystemParametersInfoW(i 48, i 0, p $SetupRect, i 0)'
  System::Call '*$SetupRect(i .r0, i .r1, i .r2, i .r3)'
  IntOp $2 $2 - $0
  IntOp $3 $3 - $1
  IntOp $2 $2 - $SetupWindowWidth
  IntOp $3 $3 - $SetupWindowHeight
  IntOp $2 $2 / 2
  IntOp $3 $3 / 2
  IntOp $SetupWindowX $0 + $2
  IntOp $SetupWindowY $1 + $3
  System::Call 'user32::MoveWindow(p $HWNDPARENT, i $SetupWindowX, i $SetupWindowY, i $SetupWindowWidth, i $SetupWindowHeight, i 1)'
  System::Free $SetupRect

  GetDlgItem $0 $HWNDPARENT 1044
  !insertmacro ERSetupMove $0 0 0 370 208
  GetDlgItem $0 $HWNDPARENT 1018
  !insertmacro ERSetupMove $0 20 52 330 150
  !insertmacro ERSetupMove $mui.Header.Background 0 0 370 47
  !insertmacro ERSetupMove $mui.Header.Text 20 10 330 19
  !insertmacro ERSetupMove $mui.Header.SubText 20 30 330 16
  !insertmacro ERSetupMove $mui.Line.Standard 0 47 370 1
  !insertmacro ERSetupMove $mui.Line.FullWindow 0 208 370 1
  !insertmacro ERSetupMove $mui.Button.Back 153 212 65 22
  !insertmacro ERSetupMove $mui.Button.Next 224 212 70 22
  !insertmacro ERSetupMove $mui.Button.Cancel 300 212 50 22
  SendMessage $mui.Button.Back ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.Button.Next ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.Button.Cancel ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.Header.Text ${WM_SETFONT} $SetupHeadingFont 0
  SetCtlColors $mui.Header.Text "${ER_SETUP_INK}" "FFFFFF"
  SetCtlColors $mui.Header.SubText "${ER_SETUP_MUTED}" "FFFFFF"
  SetCtlColors $mui.Header.Background "" "FFFFFF"
  SendMessage $mui.Header.SubText ${WM_SETFONT} $SetupBodyFont 0
  ShowWindow $mui.Header.Image ${SW_HIDE}
  ShowWindow $mui.Branding.Text ${SW_HIDE}
  ShowWindow $mui.Branding.Background ${SW_HIDE}
FunctionEnd
!macroend

!insertmacro ERSetupGuiInit ""
!insertmacro ERSetupGuiInit "un."

; NSIS resets navigation captions while changing pages. Apply these from SHOW
; callbacks, then let summary/finish pages override only their primary action.
!macro ERSetupStandardPage PREFIX
Function ${PREFIX}SetupStandardPage
  ${NSD_SetText} $mui.Button.Back "上一步"
  ${NSD_SetText} $mui.Button.Next "下一步"
  ${NSD_SetText} $mui.Button.Cancel "取消"
  SendMessage $mui.Button.Back ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.Button.Next ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.Button.Cancel ${WM_SETFONT} $SetupBodyFont 0
FunctionEnd
!macroend
!insertmacro ERSetupStandardPage ""
!insertmacro ERSetupStandardPage "un."

Function SetupChooseAction
  StrCpy $SetupAction "安装"
  ${If} $IsUpgrade == 1
    StrCpy $SetupAction "更新"
    ${If} $VersionComparison == "0"
      StrCpy $SetupAction "修复"
    ${ElseIf} $VersionNeedsConsent == 1
      StrCpy $SetupAction "覆盖安装"
    ${EndIf}
  ${EndIf}
FunctionEnd

Function SetupOverviewChanged
  Pop $0
  ${NSD_GetState} $SetupCustomControl $SetupCustom
  Call SetupOverviewButton
FunctionEnd

Function SetupOverviewConsentChanged
  Pop $0
  ${NSD_GetState} $VersionCheckbox $VersionConsent
  Call SetupOverviewButton
FunctionEnd

Function SetupOverviewButton
  ${If} $SetupCustom == ${BST_CHECKED}
    ${NSD_SetText} $mui.Button.Next "下一步"
  ${Else}
    ${NSD_SetText} $mui.Button.Next "$SetupAction EyeRelax"
  ${EndIf}
  ${If} $VersionNeedsConsent == 1
  ${AndIf} $VersionConsent != ${BST_CHECKED}
    EnableWindow $mui.Button.Next 0
  ${Else}
    EnableWindow $mui.Button.Next 1
  ${EndIf}
FunctionEnd

Function SetupOverview
  Call SetupChooseAction
  Call SetupStandardPage
  nsDialogs::Create 1044
  Pop $SetupDialog
  ${If} $SetupDialog == error
    Abort
  ${EndIf}
  SetCtlColors $SetupDialog "${ER_SETUP_INK}" "FFFFFF"
  ${If} $IsUpgrade == 1
    !insertmacro ERSetupHeading "$SetupAction EyeRelax" "$ExistingVersion  →  ${VERSION}"
  ${Else}
    !insertmacro ERSetupHeading "$SetupAction EyeRelax" "版本 ${VERSION}"
  ${EndIf}
  ${If} $IsUpgrade == 1
    !insertmacro ERSetupLabel 20 68 330 16 "保留计划、设置和历史记录。" $SetupBodyFont "${ER_SETUP_INK}" "FFFFFF"
  ${Else}
    !insertmacro ERSetupLabel 20 68 330 16 "安排专注时段和休息提醒，回顾每天的专注时间。" $SetupBodyFont "${ER_SETUP_INK}" "FFFFFF"
  ${EndIf}
  !insertmacro ERSetupLabel 20 90 330 13 "安装位置" $SetupSmallFont "${ER_SETUP_MUTED}" "FFFFFF"
  !insertmacro ERSetupReadOnly 20 106 330 16 "$INSTDIR"
  ${NSD_CreateCheckbox} 20u 134u 330u 16u "创建桌面快捷方式"
  Pop $DesktopControl
  SendMessage $DesktopControl ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $DesktopControl "${ER_SETUP_INK}" "FFFFFF"
  ${NSD_SetState} $DesktopControl $DesktopChoice
  ${NSD_CreateCheckbox} 20u 153u 330u 16u "登录 Windows 后启动 EyeRelax"
  Pop $StartupControl
  SendMessage $StartupControl ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $StartupControl "${ER_SETUP_INK}" "FFFFFF"
  ${NSD_SetState} $StartupControl $StartupChoice
  ${NSD_CreateCheckbox} 20u 172u 330u 16u "自定义安装位置和更多选项"
  Pop $SetupCustomControl
  SendMessage $SetupCustomControl ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $SetupCustomControl "${ER_SETUP_MUTED}" "FFFFFF"
  ${NSD_SetState} $SetupCustomControl $SetupCustom
  ${NSD_OnClick} $SetupCustomControl SetupOverviewChanged
  StrCpy $VersionCheckbox 0
  ${If} $VersionNeedsConsent == 1
    ${NSD_CreateCheckbox} 20u 191u 330u 16u "确认覆盖已安装的版本（可能降级）"
    Pop $VersionCheckbox
    SendMessage $VersionCheckbox ${WM_SETFONT} $SetupBodyFont 0
    SetCtlColors $VersionCheckbox "A14E29" "FFFFFF"
    ${NSD_SetState} $VersionCheckbox $VersionConsent
    ${NSD_OnClick} $VersionCheckbox SetupOverviewConsentChanged
  ${EndIf}
  Call SetupOverviewButton
  !insertmacro ERSetupFullEnter
  nsDialogs::Show
  !insertmacro ERSetupFullLeave
  ${NSD_FreeIcon} $SetupIconHandle
FunctionEnd

Function SetupOverviewLeave
  ${NSD_GetState} $DesktopControl $DesktopChoice
  ${NSD_GetState} $StartupControl $StartupChoice
  ${NSD_GetState} $SetupCustomControl $SetupCustom
  ${If} $VersionNeedsConsent == 1
    ${NSD_GetState} $VersionCheckbox $VersionConsent
    ${If} $VersionConsent != ${BST_CHECKED}
      Abort
    ${EndIf}
  ${EndIf}
  Call ValidateDirectory
  Call EnsureInstallScope
  ${If} $SetupCustom != 1
    Call PrepareInstall
  ${EndIf}
FunctionEnd

Function SetupScope
  ${If} $SetupCustom != 1
  ${OrIf} $IsUpgrade == 1
    Abort
  ${EndIf}
  !insertmacro MUI_HEADER_TEXT "安装范围" "每个账户使用自己的计划和记录。"
  nsDialogs::Create 1018
  Pop $SetupDialog
  ${If} $SetupDialog == error
    Abort
  ${EndIf}
  SetCtlColors $SetupDialog "${ER_SETUP_INK}" "FFFFFF"
  ${NSD_CreateRadioButton} 10u 10u 310u 18u "仅当前用户"
  Pop $SetupScopeUserControl
  SendMessage $SetupScopeUserControl ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $SetupScopeUserControl "${ER_SETUP_INK}" "${ER_SETUP_BG}"
  ${NSD_CreateRadioButton} 10u 36u 310u 18u "所有用户（需管理员授权）"
  Pop $SetupScopeAllControl
  SendMessage $SetupScopeAllControl ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $SetupScopeAllControl "${ER_SETUP_INK}" "${ER_SETUP_BG}"
  ${If} $MultiUser.InstallMode == "AllUsers"
    ${NSD_Check} $SetupScopeAllControl
  ${Else}
    ${NSD_Check} $SetupScopeUserControl
  ${EndIf}
  Call SetupStandardPage
  nsDialogs::Show
FunctionEnd

Function SetupScopeLeave
  StrCpy $SetupPreviousScope "$MultiUser.InstallMode"
  ${NSD_GetState} $SetupScopeAllControl $0
  ${If} $0 == ${BST_CHECKED}
    StrCpy $MultiUser.InstallMode "AllUsers"
    ${If} $SetupPreviousScope != "AllUsers"
      StrCpy $INSTDIR "$PROGRAMFILES64\${APP_DIR_NAME}"
      StrCpy $SelectedInstallDir "$INSTDIR"
    ${EndIf}
    Call EnsureInstallScope
    ${If} $SetupPreviousScope != "AllUsers"
      Call MultiUser.InstallMode.AllUsers
      StrCpy $SelectedInstallDir "$INSTDIR"
    ${EndIf}
  ${Else}
    ${If} $SetupPreviousScope != "CurrentUser"
      Call MultiUser.InstallMode.CurrentUser
      StrCpy $SelectedInstallDir "$INSTDIR"
    ${EndIf}
    StrCpy $SharedChoice ${BST_UNCHECKED}
  ${EndIf}
FunctionEnd

Function SetupDirectoryShow
  !insertmacro ERSetupMove $mui.DirectoryPage 20 52 330 150
  !insertmacro ERSetupMove $mui.DirectoryPage.Text 0 0 330 31
  !insertmacro ERSetupMove $mui.DirectoryPage.DirectoryBox 0 41 330 55
  !insertmacro ERSetupMove $mui.DirectoryPage.Directory 10 63 248 18
  !insertmacro ERSetupMove $mui.DirectoryPage.BrowseButton 264 63 56 18
  !insertmacro ERSetupMove $mui.DirectoryPage.SpaceRequired 0 113 330 15
  !insertmacro ERSetupMove $mui.DirectoryPage.SpaceAvailable 0 133 330 15
  SendMessage $mui.DirectoryPage.DirectoryBox ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.DirectoryPage.Directory ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.DirectoryPage.BrowseButton ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.DirectoryPage.Text ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.DirectoryPage.SpaceRequired ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.DirectoryPage.SpaceAvailable ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $mui.DirectoryPage "${ER_SETUP_INK}" "FFFFFF"
  SetCtlColors $mui.DirectoryPage.Text "${ER_SETUP_INK}" "FFFFFF"
  SetCtlColors $mui.DirectoryPage.DirectoryBox "${ER_SETUP_INK}" "FFFFFF"
  SetCtlColors $mui.DirectoryPage.Directory "${ER_SETUP_INK}" "FFFFFF"
  SetCtlColors $mui.DirectoryPage.BrowseButton "${ER_SETUP_INK}" "FFFFFF"
  SetCtlColors $mui.DirectoryPage.SpaceRequired "${ER_SETUP_MUTED}" "FFFFFF"
  SetCtlColors $mui.DirectoryPage.SpaceAvailable "${ER_SETUP_MUTED}" "FFFFFF"
  Call SetupStandardPage
FunctionEnd

Function SetupReady
  ${If} $SetupCustom != 1
    Abort
  ${EndIf}
  Call SetupChooseAction
  !insertmacro MUI_HEADER_TEXT "准备$SetupAction" "确认安装位置和选项。"
  nsDialogs::Create 1018
  Pop $SetupDialog
  ${If} $SetupDialog == error
    Abort
  ${EndIf}
  SetCtlColors $SetupDialog "${ER_SETUP_INK}" "FFFFFF"
  ${If} $MultiUser.InstallMode == "AllUsers"
    !insertmacro ERSetupLabel 0 0 330 13 "安装位置 · 所有用户" $SetupSmallFont "${ER_SETUP_MUTED}" "FFFFFF"
  ${Else}
    !insertmacro ERSetupLabel 0 0 330 13 "安装位置 · 当前用户" $SetupSmallFont "${ER_SETUP_MUTED}" "FFFFFF"
  ${EndIf}
  !insertmacro ERSetupReadOnly 0 16 330 16 "$INSTDIR"
  !insertmacro ERSetupLabel 0 42 330 13 "个人数据位置" $SetupSmallFont "${ER_SETUP_MUTED}" "FFFFFF"
  !insertmacro ERSetupReadOnly 0 58 330 16 "$DataDir"
  ${NSD_CreateCheckbox} 0u 81u 330u 16u "创建桌面快捷方式"
  Pop $DesktopControl
  SendMessage $DesktopControl ${WM_SETFONT} $SetupBodyFont 0
  ${NSD_SetState} $DesktopControl $DesktopChoice
  SetCtlColors $DesktopControl "${ER_SETUP_INK}" "FFFFFF"
  ${NSD_CreateCheckbox} 0u 101u 330u 16u "登录 Windows 后启动 EyeRelax"
  Pop $StartupControl
  SendMessage $StartupControl ${WM_SETFONT} $SetupBodyFont 0
  ${NSD_SetState} $StartupControl $StartupChoice
  SetCtlColors $StartupControl "${ER_SETUP_INK}" "FFFFFF"
  StrCpy $SharedControl 0
  ${If} $MultiUser.InstallMode == "AllUsers"
    ${NSD_CreateCheckbox} 0u 121u 330u 16u "跨账户延续未完成的强制休息"
    Pop $SharedControl
    SendMessage $SharedControl ${WM_SETFONT} $SetupBodyFont 0
    SetCtlColors $SharedControl "${ER_SETUP_INK}" "FFFFFF"
    ${NSD_SetState} $SharedControl $SharedChoice
    !insertmacro ERSetupLabel 14 138 316 12 "各账户的计划和记录仍然独立。" $SetupSmallFont "${ER_SETUP_MUTED}" "FFFFFF"
  ${EndIf}
  Call SetupStandardPage
  ${NSD_SetText} $mui.Button.Next "$SetupAction EyeRelax"
  ${NSD_OnBack} SetupReadySave
  nsDialogs::Show
FunctionEnd

Function SetupReadySave
  ${NSD_GetState} $DesktopControl $DesktopChoice
  ${NSD_GetState} $StartupControl $StartupChoice
  ${If} $SharedControl != 0
    ${NSD_GetState} $SharedControl $SharedChoice
  ${EndIf}
FunctionEnd

Function SetupReadyLeave
  Call SetupReadySave
  Call PrepareInstall
FunctionEnd

!macro ERSetupInstallShow PREFIX TITLE SUBTITLE DETAILS
Function ${PREFIX}SetupInstallShow
  Call ${PREFIX}SetupStandardPage
  !insertmacro MUI_HEADER_TEXT "${TITLE}" "${SUBTITLE}"
  !insertmacro ERSetupMove $mui.InstFilesPage 20 52 330 150
  !insertmacro ERSetupMove $mui.InstFilesPage.Text 0 8 330 25
  !insertmacro ERSetupMove $mui.InstFilesPage.ProgressBar 0 41 330 10
  !insertmacro ERSetupMove $mui.InstFilesPage.ShowLogButton 0 66 85 20
  !insertmacro ERSetupMove $mui.InstFilesPage.Log 0 94 330 56
  SendMessage $mui.InstFilesPage.Text ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.InstFilesPage.ShowLogButton ${WM_SETFONT} $SetupBodyFont 0
  SendMessage $mui.InstFilesPage.Log ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $mui.InstFilesPage "${ER_SETUP_INK}" "FFFFFF"
  SetCtlColors $mui.InstFilesPage.Text "${ER_SETUP_MUTED}" "FFFFFF"
  SetCtlColors $mui.InstFilesPage.ShowLogButton "${ER_SETUP_INK}" "FFFFFF"
  SetCtlColors $mui.InstFilesPage.Log "${ER_SETUP_INK}" "FFFFFF"
  ${NSD_SetText} $mui.InstFilesPage.ShowLogButton "${DETAILS}"
FunctionEnd
!macroend
!insertmacro ERSetupInstallShow "" "正在$SetupAction EyeRelax" "" "查看安装详情"
!insertmacro ERSetupInstallShow "un." "正在移除 EyeRelax" "" "查看卸载详情"

Function SetupFinish
  ${If} $SetupSucceeded != 1
    Abort
  ${EndIf}
  StrCpy $SetupFinished 1
  StrCpy $SetupLaunchControl 0
  nsDialogs::Create 1044
  Pop $SetupDialog
  ${If} $SetupDialog == error
    Abort
  ${EndIf}
  SetCtlColors $SetupDialog "${ER_SETUP_INK}" "FFFFFF"
  !insertmacro ERSetupHeading "$SetupAction完成" "EyeRelax ${VERSION}"
  ${NSD_CreateCheckbox} 20u 79u 330u 18u "立即启动 EyeRelax"
  Pop $SetupLaunchControl
  SendMessage $SetupLaunchControl ${WM_SETFONT} $SetupBodyFont 0
  SetCtlColors $SetupLaunchControl "${ER_SETUP_INK}" "FFFFFF"
  ${NSD_Check} $SetupLaunchControl
  ${If} $ShortcutWarnings != ""
    !insertmacro ERSetupLabel 20 105 330 66 "$ShortcutWarnings" $SetupSmallFont "A14E29" "FFFFFF"
  ${EndIf}
  Call SetupStandardPage
  ${NSD_SetText} $mui.Button.Next "完成"
  !insertmacro ERSetupMove $mui.Button.Next 280 212 70 22
  ShowWindow $mui.Button.Back ${SW_HIDE}
  ShowWindow $mui.Button.Cancel ${SW_HIDE}
  !insertmacro ERSetupFullEnter
  nsDialogs::Show
  !insertmacro ERSetupFullLeave
  ${NSD_FreeIcon} $SetupIconHandle
FunctionEnd

Function SetupFinishLeave
  IfSilent setup_finish_no_launch
  ${If} $SetupLaunchControl != 0
    ${NSD_GetState} $SetupLaunchControl $SetupLaunchChoice
    ${If} $SetupLaunchChoice == ${BST_CHECKED}
      Call LaunchInstalledApp
    ${EndIf}
  ${EndIf}
  setup_finish_no_launch:
FunctionEnd

Function un.SetupConfirm
  nsDialogs::Create 1044
  Pop $SetupDialog
  ${If} $SetupDialog == error
    Abort
  ${EndIf}
  SetCtlColors $SetupDialog "${ER_SETUP_INK}" "FFFFFF"
  !insertmacro ERSetupHeading "卸载 EyeRelax" "版本 ${VERSION}"
  !insertmacro ERSetupLabel 20 79 330 32 "保留计划、设置和历史记录。" $SetupBodyFont "${ER_SETUP_INK}" "FFFFFF"
  !insertmacro ERSetupLabel 20 105 330 13 "应用位置" $SetupSmallFont "${ER_SETUP_MUTED}" "FFFFFF"
  !insertmacro ERSetupReadOnly 20 121 330 16 "$INSTDIR"
  Call un.SetupStandardPage
  ${NSD_SetText} $mui.Button.Next "卸载 EyeRelax"
  !insertmacro ERSetupFullEnter
  nsDialogs::Show
  !insertmacro ERSetupFullLeave
  ${NSD_FreeIcon} $SetupIconHandle
FunctionEnd

Function un.SetupFinish
  StrCpy $SetupFinished 1
  nsDialogs::Create 1044
  Pop $SetupDialog
  ${If} $SetupDialog == error
    Abort
  ${EndIf}
  SetCtlColors $SetupDialog "${ER_SETUP_INK}" "FFFFFF"
  IfAbort setup_uninstall_failed
    !insertmacro ERSetupHeading "卸载完成" "EyeRelax ${VERSION}"
    !insertmacro ERSetupLabel 20 79 330 103 "$UninstallSummary" $SetupBodyFont "${ER_SETUP_INK}" "FFFFFF"
    Goto setup_uninstall_finish_show
  setup_uninstall_failed:
    !insertmacro ERSetupHeading "尚未完成卸载" "EyeRelax ${VERSION}"
    !insertmacro ERSetupLabel 20 79 330 64 "请退出正在运行的 EyeRelax 后重试。$\r$\n可在卸载详情中查看具体原因。" $SetupBodyFont "${ER_SETUP_MUTED}" "FFFFFF"
  setup_uninstall_finish_show:
  Call un.SetupStandardPage
  ${NSD_SetText} $mui.Button.Next "完成"
  !insertmacro ERSetupMove $mui.Button.Next 280 212 70 22
  ShowWindow $mui.Button.Back ${SW_HIDE}
  ShowWindow $mui.Button.Cancel ${SW_HIDE}
  !insertmacro ERSetupFullEnter
  nsDialogs::Show
  !insertmacro ERSetupFullLeave
  ${NSD_FreeIcon} $SetupIconHandle
FunctionEnd

!endif
