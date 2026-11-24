using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

[assembly: TargetFramework(".NETFramework,Version=v4.7.2", FrameworkDisplayName = ".NET Framework 4.7.2")]

namespace EyeRelax.Setup
{
    internal sealed class Bridge
    {
        private readonly string path;
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
        private static extern uint GetPrivateProfileString(string section, string key, string fallback, StringBuilder value, uint capacity, string filename);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool WritePrivateProfileString(string section, string key, string value, string filename);
        internal Bridge(string filename) { path = filename; }
        internal bool Exists { get { return File.Exists(path); } }
        internal void RecordError(Exception error)
        {
            try { File.AppendAllText(path + ".ui-error.log", DateTime.Now.ToString("s") + " " + error + Environment.NewLine, Encoding.UTF8); }
            catch (IOException) { } catch (UnauthorizedAccessException) { }
        }
        internal string Read(string section, string key, string fallback)
        {
            var value = new StringBuilder(16384);
            GetPrivateProfileString(section, key, fallback, value, (uint)value.Capacity, path);
            return value.ToString();
        }
        internal string State(string key, string fallback) { return Read("State", key, fallback); }
        internal void Write(string section, string key, string value)
        {
            if (!WritePrivateProfileString(section, key, value, path)) throw new Win32Exception(Marshal.GetLastWin32Error());
        }
    }

    internal sealed class SetupWindow : Window
    {
        private const string Ink = "#203F35", Muted = "#6B7D73", Forest = "#285B49", Soft = "#F1F5ED";
        private readonly Bridge bridge;
        private readonly bool preview;
        private readonly int owner;
        private readonly DispatcherTimer timer;
        private readonly Border content;
        private readonly Button primary, secondary;
        private readonly TextBlock footerNote;
        private bool uninstall, upgrade, needsConsent, submitted, terminal, closing, handedOff;
        private bool desktop, startup, shared, consent, launch = true;
        private string action, version, existingVersion, installDir, dataDir, mode, userDefaultDir, machineDefaultDir, logPath;
        private string page = "overview", lastMessage = "";
        private int previewProgress;
        private TextBox installInput, dataInput, logBox;
        private TextBlock inlineError, progressMessage, percent;
        private ProgressBar progress;
        private Border detailsPanel;
        private CheckBox launchControl;

        private static readonly string Styles = @"
<ResourceDictionary xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation' xmlns:x='http://schemas.microsoft.com/winfx/2006/xaml'>
 <Style TargetType='TextBlock'><Setter Property='Foreground' Value='#203F35'/><Setter Property='FontSize' Value='14'/><Setter Property='TextWrapping' Value='Wrap'/></Style>
 <Style x:Key='Primary' TargetType='Button'>
  <Setter Property='Background' Value='#285B49'/><Setter Property='Foreground' Value='White'/><Setter Property='BorderBrush' Value='#285B49'/><Setter Property='BorderThickness' Value='1'/><Setter Property='Padding' Value='14,0'/><Setter Property='Height' Value='36'/><Setter Property='FontSize' Value='13'/><Setter Property='FontWeight' Value='SemiBold'/><Setter Property='Cursor' Value='Hand'/>
  <Setter Property='Template'><Setter.Value><ControlTemplate TargetType='Button'><Border x:Name='Surface' Background='{TemplateBinding Background}' BorderBrush='{TemplateBinding BorderBrush}' BorderThickness='{TemplateBinding BorderThickness}' CornerRadius='10'><ContentPresenter HorizontalAlignment='Center' VerticalAlignment='Center' Margin='{TemplateBinding Padding}' RecognizesAccessKey='True'/></Border><ControlTemplate.Triggers><Trigger Property='IsMouseOver' Value='True'><Setter TargetName='Surface' Property='Background' Value='#346D57'/></Trigger><Trigger Property='IsPressed' Value='True'><Setter TargetName='Surface' Property='Background' Value='#1C4738'/></Trigger><Trigger Property='IsKeyboardFocused' Value='True'><Setter TargetName='Surface' Property='BorderBrush' Value='#8AB59A'/><Setter TargetName='Surface' Property='BorderThickness' Value='2'/></Trigger><Trigger Property='IsEnabled' Value='False'><Setter TargetName='Surface' Property='Background' Value='#DCE5DC'/><Setter TargetName='Surface' Property='BorderBrush' Value='#DCE5DC'/><Setter Property='Foreground' Value='#839087'/><Setter Property='Cursor' Value='Arrow'/></Trigger></ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter>
 </Style>
 <Style x:Key='Secondary' TargetType='Button' BasedOn='{StaticResource Primary}'>
  <Setter Property='Foreground' Value='#536B5E'/><Setter Property='Background' Value='Transparent'/><Setter Property='BorderBrush' Value='Transparent'/><Setter Property='FontWeight' Value='Normal'/>
  <Setter Property='Template'><Setter.Value><ControlTemplate TargetType='Button'><Border x:Name='Surface' Background='{TemplateBinding Background}' CornerRadius='9' BorderBrush='Transparent' BorderThickness='1'><ContentPresenter HorizontalAlignment='Center' VerticalAlignment='Center' Margin='{TemplateBinding Padding}'/></Border><ControlTemplate.Triggers><Trigger Property='IsMouseOver' Value='True'><Setter TargetName='Surface' Property='Background' Value='#E8EFE6'/></Trigger><Trigger Property='IsKeyboardFocused' Value='True'><Setter TargetName='Surface' Property='BorderBrush' Value='#719F84'/></Trigger><Trigger Property='IsEnabled' Value='False'><Setter Property='Opacity' Value='.45'/><Setter Property='Cursor' Value='Arrow'/></Trigger></ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter>
 </Style>
 <Style x:Key='Browse' TargetType='Button' BasedOn='{StaticResource Secondary}'><Setter Property='Height' Value='34'/><Setter Property='Background' Value='#EDF3E9'/><Setter Property='Padding' Value='12,0'/></Style>
 <Style TargetType='TextBox'><Setter Property='FontSize' Value='13'/><Setter Property='Foreground' Value='#29493B'/><Setter Property='Background' Value='#FBFCF9'/><Setter Property='BorderBrush' Value='#D8E2D7'/><Setter Property='BorderThickness' Value='1'/><Setter Property='Padding' Value='10,0'/><Setter Property='VerticalContentAlignment' Value='Center'/><Setter Property='Height' Value='34'/><Setter Property='Template'><Setter.Value><ControlTemplate TargetType='TextBox'><Border x:Name='Surface' CornerRadius='7' Background='{TemplateBinding Background}' BorderBrush='{TemplateBinding BorderBrush}' BorderThickness='{TemplateBinding BorderThickness}'><ScrollViewer x:Name='PART_ContentHost' Margin='0' VerticalAlignment='{TemplateBinding VerticalContentAlignment}'/></Border><ControlTemplate.Triggers><Trigger Property='IsKeyboardFocusWithin' Value='True'><Setter TargetName='Surface' Property='BorderBrush' Value='#39795B'/></Trigger><Trigger Property='IsEnabled' Value='False'><Setter Property='Opacity' Value='.55'/></Trigger></ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter></Style>
 <Style TargetType='CheckBox'><Setter Property='Foreground' Value='#29493B'/><Setter Property='FontSize' Value='13'/><Setter Property='Cursor' Value='Hand'/><Setter Property='VerticalContentAlignment' Value='Center'/><Setter Property='Template'><Setter.Value><ControlTemplate TargetType='CheckBox'><Grid Background='Transparent'><Grid.ColumnDefinitions><ColumnDefinition Width='20'/><ColumnDefinition Width='9'/><ColumnDefinition Width='*'/></Grid.ColumnDefinitions><Border x:Name='Box' Width='18' Height='18' CornerRadius='4' BorderThickness='1.3' BorderBrush='#AABDAA' Background='White' VerticalAlignment='Center'><Path x:Name='Mark' Data='M 3,8 L 6.5,11.5 L 13,4.5' Stroke='White' StrokeThickness='1.8' StrokeStartLineCap='Round' StrokeEndLineCap='Round' Visibility='Collapsed'/></Border><ContentPresenter Grid.Column='2' VerticalAlignment='Center' RecognizesAccessKey='True'/></Grid><ControlTemplate.Triggers><Trigger Property='IsChecked' Value='True'><Setter TargetName='Box' Property='Background' Value='#285B49'/><Setter TargetName='Box' Property='BorderBrush' Value='#285B49'/><Setter TargetName='Mark' Property='Visibility' Value='Visible'/></Trigger><Trigger Property='IsKeyboardFocused' Value='True'><Setter TargetName='Box' Property='BorderBrush' Value='#699F7D'/><Setter TargetName='Box' Property='BorderThickness' Value='2'/></Trigger><Trigger Property='IsEnabled' Value='False'><Setter Property='Opacity' Value='.45'/><Setter Property='Cursor' Value='Arrow'/></Trigger></ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter></Style>
 <Style TargetType='RadioButton'><Setter Property='FontSize' Value='13'/><Setter Property='Foreground' Value='#29493B'/><Setter Property='Cursor' Value='Hand'/><Setter Property='Padding' Value='13,12'/><Setter Property='Template'><Setter.Value><ControlTemplate TargetType='RadioButton'><Border x:Name='Card' CornerRadius='10' Background='#FAFCF8' BorderBrush='#DEE6D9' BorderThickness='1' Padding='{TemplateBinding Padding}'><Grid><Grid.ColumnDefinitions><ColumnDefinition Width='18'/><ColumnDefinition Width='9'/><ColumnDefinition Width='*'/></Grid.ColumnDefinitions><Border x:Name='Ring' Width='16' Height='16' CornerRadius='8' BorderThickness='1.3' BorderBrush='#ADBFAF' VerticalAlignment='Center'><Ellipse x:Name='Dot' Fill='#285B49' Width='8' Height='8' Visibility='Collapsed'/></Border><ContentPresenter Grid.Column='2' VerticalAlignment='Center'/></Grid></Border><ControlTemplate.Triggers><Trigger Property='IsChecked' Value='True'><Setter TargetName='Card' Property='Background' Value='#EEF5EB'/><Setter TargetName='Card' Property='BorderBrush' Value='#87A88C'/><Setter TargetName='Ring' Property='BorderBrush' Value='#285B49'/><Setter TargetName='Dot' Property='Visibility' Value='Visible'/></Trigger><Trigger Property='IsKeyboardFocused' Value='True'><Setter TargetName='Card' Property='BorderBrush' Value='#285B49'/></Trigger><Trigger Property='IsEnabled' Value='False'><Setter Property='Opacity' Value='.6'/><Setter Property='Cursor' Value='Arrow'/></Trigger></ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter></Style>
 <Style TargetType='ProgressBar'><Setter Property='Height' Value='9'/><Setter Property='Minimum' Value='0'/><Setter Property='Maximum' Value='100'/><Setter Property='Template'><Setter.Value><ControlTemplate TargetType='ProgressBar'><Grid x:Name='PART_Track'><Border Background='#E7EDE3' CornerRadius='4.5'/><Border x:Name='PART_Indicator' Background='#39795B' CornerRadius='4.5' HorizontalAlignment='Left'/></Grid></ControlTemplate></Setter.Value></Setter></Style>
</ResourceDictionary>";

        internal SetupWindow(Bridge session, int parent, bool isPreview, bool previewUninstall)
        {
            bridge = session; owner = parent; preview = isPreview;
            Resources = (ResourceDictionary)XamlReader.Parse(Styles);
            FontFamily = new FontFamily("Microsoft YaHei UI, Segoe UI");
            Background = Brush(Soft); UseLayoutRounding = true; SnapsToDevicePixels = true;
            Title = preview ? "EyeRelax · 界面预览" : "EyeRelax · 安装向导";
            Width = 560; MinWidth = 520; SizeToContent = SizeToContent.Height;
            MaxHeight = Math.Min(460, SystemParameters.WorkArea.Height - 24);
            WindowStartupLocation = WindowStartupLocation.CenterScreen; ResizeMode = ResizeMode.CanMinimize;
            if (SystemParameters.WorkArea.Width < Width) { MinWidth = 0; Width = SystemParameters.WorkArea.Width - 24; }
            ReadInitial(previewUninstall);
            var root = new Grid { Margin = new Thickness(18) };
            root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var header = new Grid { Margin = new Thickness(1, 0, 1, 10) };
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var brand = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            try
            {
                using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("EyeRelax.ico"))
                {
                    var decoder = new IconBitmapDecoder(stream, BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad);
                    var icon = decoder.Frames[decoder.Frames.Count - 1];
                    Icon = icon; brand.Children.Add(new Image { Source = icon, Width = 24, Height = 24, Margin = new Thickness(0, 0, 8, 0) });
                }
            }
            catch { }
            brand.Children.Add(Text("EyeRelax", 16, Ink, FontWeights.SemiBold));
            header.Children.Add(brand);
            var headerLabel = Text(preview ? "界面预览 · 不会安装" : "", 11, Muted);
            headerLabel.VerticalAlignment = VerticalAlignment.Center; Grid.SetColumn(headerLabel, 2); header.Children.Add(headerLabel);
            root.Children.Add(header);
            content = new Border { Background = Brushes.White, CornerRadius = new CornerRadius(10), BorderBrush = Brush("#E0E8DC"), BorderThickness = new Thickness(1), Padding = new Thickness(18) };
            Grid.SetRow(content, 1); root.Children.Add(content);
            var footer = new Grid { Margin = new Thickness(1, 10, 1, 0) };
            footer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            footerNote = Text("", 12, Muted); footerNote.VerticalAlignment = VerticalAlignment.Center; footer.Children.Add(footerNote);
            secondary = Button("取消", "Secondary", delegate { Secondary(); }); secondary.MinWidth = 62; secondary.Margin = new Thickness(8, 0, 6, 0); Grid.SetColumn(secondary, 1); footer.Children.Add(secondary);
            primary = Button("安装 EyeRelax", "Primary", delegate { Primary(); }); primary.MinWidth = 110; Grid.SetColumn(primary, 2); footer.Children.Add(primary);
            Grid.SetRow(footer, 2); root.Children.Add(footer); Content = root;
            ShowOverview();
            timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(300) }; timer.Tick += Poll;
            Loaded += delegate
            {
                if (!preview)
                {
                    try
                    {
                        bridge.Write("Bridge", "pid", Process.GetCurrentProcess().Id.ToString());
                        bridge.Write("Bridge", "ready", "1");
                    }
                    catch (Win32Exception error) { bridge.RecordError(error); ShowError("无法连接安装程序，请关闭窗口后重试。"); return; }
                }
                timer.Start();
            };
            Closing += OnClosing;
            Closed += delegate { timer.Stop(); };
        }

        private void ReadInitial(bool previewUninstall)
        {
            if (preview)
            {
                uninstall = previewUninstall; action = uninstall ? "卸载" : "更新"; version = "1.0.0"; existingVersion = "0.1.24"; upgrade = true;
                string previewRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "EyeRelax Preview");
                installDir = Path.Combine(previewRoot, "App"); dataDir = Path.Combine(previewRoot, "Data"); mode = "CurrentUser"; desktop = true; startup = false;
                userDefaultDir = installDir; machineDefaultDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "EyeRelax Preview"); return;
            }
            uninstall = bridge.State("uninstall", "0") == "1"; action = bridge.State("actionLabel", uninstall ? "卸载" : "安装");
            version = bridge.State("version", ""); existingVersion = bridge.State("existingVersion", "");
            installDir = bridge.State("installDir", ""); dataDir = bridge.State("dataDir", "");
            upgrade = bridge.State("isUpgrade", "0") == "1"; mode = bridge.State("mode", "CurrentUser");
            desktop = bridge.State("desktop", "1") == "1"; startup = bridge.State("startup", "0") == "1"; shared = bridge.State("shared", "0") == "1";
            needsConsent = bridge.State("needsConsent", "0") == "1";
            userDefaultDir = bridge.State("userDefaultDir", installDir); machineDefaultDir = bridge.State("machineDefaultDir", installDir);
            logPath = bridge.State("log", "");
        }

        private static SolidColorBrush Brush(string value) { return (SolidColorBrush)new BrushConverter().ConvertFromString(value); }
        private static TextBlock Text(string value, double size, string color, FontWeight? weight = null)
        {
            return new TextBlock { Text = value, FontSize = size, Foreground = Brush(color), FontWeight = weight ?? FontWeights.Normal, TextWrapping = TextWrapping.Wrap, VerticalAlignment = VerticalAlignment.Center };
        }
        private Button Button(string label, string style, RoutedEventHandler click)
        {
            var result = new Button { Content = label, Style = (Style)Resources[style] }; result.Click += click; return result;
        }
        private static StackPanel Stack() { return new StackPanel(); }
        private void Put(StackPanel stack)
        {
            content.Child = new ScrollViewer { Content = stack, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled, Padding = new Thickness(0, 0, 3, 0) };
        }
        private void Heading(StackPanel stack, string title, string description, bool chip)
        {
            var row = new Grid(); row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }); row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            row.Children.Add(Text(title, 22, Ink, FontWeights.SemiBold));
            if (chip)
            {
                var tag = new Border { Background = Brush("#EDF3E8"), CornerRadius = new CornerRadius(8), Padding = new Thickness(8, 4, 8, 4), VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0, 0, 0), Child = Text(version, 11, Forest), ToolTip = upgrade ? existingVersion + " → " + version : version };
                Grid.SetColumn(tag, 1); row.Children.Add(tag);
            }
            stack.Children.Add(row);
            if (String.IsNullOrEmpty(description)) row.Margin = new Thickness(0, 0, 0, 12);
            else
            {
                var sub = Text(description, 13, Muted); sub.Margin = new Thickness(0, 5, 0, 12); stack.Children.Add(sub);
            }
        }
        private void ShowOverview()
        {
            page = "overview"; var stack = Stack();
            Heading(stack, action + " EyeRelax", (uninstall || upgrade) ? "保留计划、设置和历史记录。" : "安排专注时段和休息提醒，回顾每天的专注时间。", true);
            var pathCard = new Border { Background = Brush("#F5F8F1"), CornerRadius = new CornerRadius(7), Padding = new Thickness(11, 8, 8, 8) };
            var pathGrid = new Grid(); pathGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }); pathGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var pathText = Stack(); pathText.Children.Add(PathSummary("应用", installDir)); pathGrid.Children.Add(pathText);
            if (!String.IsNullOrWhiteSpace(dataDir))
            {
                pathText.Children.Add(PathSummary("数据", dataDir));
            }
            if (!uninstall) { var change = Button("安装选项", "Secondary", delegate { ShowAdvanced(); }); change.Height = 34; change.Padding = new Thickness(12, 0, 12, 0); Grid.SetColumn(change, 1); pathGrid.Children.Add(change); }
            pathCard.Child = pathGrid; stack.Children.Add(pathCard);
            if (!uninstall)
            {
                var choices = new Grid { Margin = new Thickness(0, 14, 0, 7) }; choices.ColumnDefinitions.Add(new ColumnDefinition()); choices.ColumnDefinitions.Add(new ColumnDefinition());
                var desktopCheck = Check("创建桌面快捷方式", desktop, delegate(bool value) { desktop = value; });
                var startupCheck = Check("登录 Windows 后启动", startup, delegate(bool value) { startup = value; });
                choices.Children.Add(desktopCheck); startupCheck.Margin = new Thickness(8, 0, 0, 0); Grid.SetColumn(startupCheck, 1); choices.Children.Add(startupCheck); stack.Children.Add(choices);
                if (needsConsent)
                {
                    var check = Check("确认覆盖已安装的版本（可能降级）", consent, delegate(bool value) { consent = value; UpdatePrimary(); });
                    check.Margin = new Thickness(0, 10, 0, 0); stack.Children.Add(check);
                }
            }
            inlineError = Text("", 12, "#A34635"); inlineError.Margin = new Thickness(0, 8, 0, 0); inlineError.Visibility = Visibility.Collapsed; stack.Children.Add(inlineError);
            Put(stack); secondary.Content = "取消"; secondary.Visibility = Visibility.Visible; secondary.IsEnabled = true;
            primary.Content = action + " EyeRelax"; primary.IsEnabled = !needsConsent || consent || uninstall;
            footerNote.Text = mode == "AllUsers" ? "此电脑的所有用户可用" : "仅当前 Windows 账户可用";
        }
        private static Grid PathSummary(string label, string value)
        {
            var row = new Grid { Height = 26 }; row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(38) }); row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }); row.Children.Add(Text(label, 12, Muted));
            var path = Text(value, 13, Ink); path.TextWrapping = TextWrapping.NoWrap; path.TextTrimming = TextTrimming.CharacterEllipsis; path.ToolTip = value; path.Margin = new Thickness(0, 0, 9, 0); Grid.SetColumn(path, 1); row.Children.Add(path); return row;
        }
        private CheckBox Check(string text, bool value, Action<bool> changed)
        {
            var result = new CheckBox { Content = Text(text, 13, Ink), IsChecked = value };
            result.Checked += delegate { changed(true); }; result.Unchecked += delegate { changed(false); }; return result;
        }
        private void ShowAdvanced()
        {
            page = "advanced"; var stack = Stack(); Heading(stack, "安装选项", "应用可以共用，每个账户的计划和记录分别保存。", false);
            installInput = PathField(stack, "应用位置", installDir);
            dataInput = PathField(stack, "个人数据位置（更改时请选择空文件夹）", dataDir);
            if (upgrade)
            {
                stack.Children.Add(Text("安装范围 · " + (mode == "AllUsers" ? "所有用户" : "仅当前用户") + "（沿用现有安装）", 12, Muted));
                if (mode == "AllUsers") { var upgradeShared = Check("跨账户延续未完成的强制休息", shared, delegate(bool value) { shared = value; }); upgradeShared.Margin = new Thickness(0, 10, 0, 0); stack.Children.Add(upgradeShared); }
            }
            else
            {
            var rangeLabel = Text("安装范围", 12, Muted); rangeLabel.Margin = new Thickness(0, 0, 0, 6); stack.Children.Add(rangeLabel);
            var scopes = new Grid(); scopes.ColumnDefinitions.Add(new ColumnDefinition()); scopes.ColumnDefinitions.Add(new ColumnDefinition());
            var personal = new RadioButton { Content = Text("仅当前用户", 13, Ink), GroupName = "Scope", IsChecked = mode != "AllUsers", Margin = new Thickness(0, 0, 5, 0), Padding = new Thickness(10, 8, 10, 8) };
            var machine = new RadioButton { Content = Text("所有用户（需管理员授权）", 13, Ink), GroupName = "Scope", IsChecked = mode == "AllUsers", Margin = new Thickness(5, 0, 0, 0), Padding = new Thickness(10, 8, 10, 8) };
            personal.Checked += delegate { SetScope("CurrentUser"); }; machine.Checked += delegate { SetScope("AllUsers"); };
            scopes.Children.Add(personal); Grid.SetColumn(machine, 1); scopes.Children.Add(machine); stack.Children.Add(scopes);
            var sharedCheck = Check("跨账户延续未完成的强制休息", shared, delegate(bool value) { shared = value; }); sharedCheck.Margin = new Thickness(0, 9, 0, 0); sharedCheck.Visibility = mode == "AllUsers" ? Visibility.Visible : Visibility.Collapsed;
            personal.Checked += delegate { sharedCheck.Visibility = Visibility.Collapsed; sharedCheck.IsChecked = false; }; machine.Checked += delegate { sharedCheck.Visibility = Visibility.Visible; }; stack.Children.Add(sharedCheck);
            }
            inlineError = Text("", 12, "#A34635"); inlineError.Margin = new Thickness(0, 8, 0, 0); inlineError.Visibility = Visibility.Collapsed; stack.Children.Add(inlineError);
            Put(stack); primary.Content = "保存选项"; primary.IsEnabled = true; secondary.Content = "返回"; footerNote.Text = "";
        }
        private TextBox PathField(StackPanel stack, string label, string value)
        {
            var heading = Text(label, 12, Muted); heading.Margin = new Thickness(0, 0, 0, 4); stack.Children.Add(heading);
            var row = new Grid { Margin = new Thickness(0, 0, 0, 10) }; row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }); row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var input = new TextBox { Text = value, MaxLength = 900 }; row.Children.Add(input);
            var browse = Button("浏览…", "Browse", delegate { string chosen = FolderPicker.Pick(this, input.Text); if (!String.IsNullOrEmpty(chosen)) input.Text = chosen; }); browse.Margin = new Thickness(9, 0, 0, 0); Grid.SetColumn(browse, 1); row.Children.Add(browse); stack.Children.Add(row); return input;
        }
        private void SetScope(string next)
        {
            if (mode == next) return;
            string oldDefault = mode == "AllUsers" ? machineDefaultDir : userDefaultDir;
            mode = next;
            if (String.Equals(installInput.Text.TrimEnd('\\'), oldDefault.TrimEnd('\\'), StringComparison.OrdinalIgnoreCase)) installInput.Text = mode == "AllUsers" ? machineDefaultDir : userDefaultDir;
        }
        private bool SaveAdvanced()
        {
            string install = installInput.Text.Trim(), data = dataInput.Text.Trim();
            if (!ValidPath(install) || !ValidPath(data)) { ErrorInline("请选择完整的本地文件夹路径，不要包含引号或换行。"); return false; }
            installDir = install; dataDir = data; return true;
        }
        private static bool ValidPath(string value)
        {
            if (String.IsNullOrWhiteSpace(value) || value.Length > 900 || value.IndexOfAny(new[] { '"', '\r', '\n', '\0' }) >= 0) return false;
            try { return Path.IsPathRooted(value) && Path.GetFullPath(value).Length > 0; } catch { return false; }
        }
        private void ErrorInline(string message) { inlineError.Text = message; inlineError.Visibility = Visibility.Visible; }
        private void UpdatePrimary() { if (page == "overview") primary.IsEnabled = uninstall || !needsConsent || consent; }
        private void Primary()
        {
            if (page == "advanced") { if (SaveAdvanced()) ShowOverview(); return; }
            if (page == "done") { launch = launchControl != null && launchControl.IsChecked == true; Finish(launch && !uninstall ? "launch" : "close"); return; }
            if (page == "error") { Finish("close"); return; }
            if (page != "overview" || submitted) return;
            if (!ValidPath(installDir) || (!uninstall && !ValidPath(dataDir))) { ErrorInline("安装位置或个人数据位置无效，请在安装选项中重新选择。"); return; }
            if (needsConsent && !consent && !uninstall) return;
            if (!preview)
            {
                try
                {
                    bridge.Write("Choice", "installDir", installDir); bridge.Write("Choice", "dataDir", dataDir); bridge.Write("Choice", "mode", mode);
                    bridge.Write("Choice", "desktop", desktop ? "1" : "0"); bridge.Write("Choice", "startup", startup ? "1" : "0"); bridge.Write("Choice", "shared", shared && mode == "AllUsers" ? "1" : "0");
                    bridge.Write("Choice", "consent", consent ? "1" : "0"); bridge.Write("Choice", "launch", uninstall ? "0" : "1");
                    bridge.Write("Bridge", "request", "install");
                }
                catch (Win32Exception error) { bridge.RecordError(error); ShowError("无法提交安装选项，请关闭窗口后重试。"); return; }
            }
            submitted = true; ShowProgress();
        }
        private void Secondary()
        {
            if (page == "advanced") { if (SaveAdvanced()) ShowOverview(); return; }
            if (!submitted) Finish("cancel");
        }
        private void ShowProgress()
        {
            page = "working"; var stack = Stack(); Heading(stack, "正在" + action + " EyeRelax", null, false);
            progressMessage = Text("正在准备…", 13, Muted); progressMessage.Margin = new Thickness(0, 0, 0, 8); stack.Children.Add(progressMessage);
            var progressRow = new Grid { Margin = new Thickness(0, 0, 0, 12) }; progressRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }); progressRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            progress = new ProgressBar { VerticalAlignment = VerticalAlignment.Center }; progressRow.Children.Add(progress); percent = Text("0%", 12, Muted); percent.Margin = new Thickness(14, 0, 0, 0); Grid.SetColumn(percent, 1); progressRow.Children.Add(percent); stack.Children.Add(progressRow);
            var detailsButton = Button("查看详情  ﹀", "Secondary", delegate { detailsPanel.Visibility = detailsPanel.Visibility == Visibility.Visible ? Visibility.Collapsed : Visibility.Visible; ReadLog(); }); detailsButton.Height = 30; detailsButton.HorizontalAlignment = HorizontalAlignment.Left; detailsButton.Padding = new Thickness(0, 0, 10, 0); detailsButton.Margin = new Thickness(0, 7, 0, 0); stack.Children.Add(detailsButton);
            logBox = new TextBox { IsReadOnly = true, TextWrapping = TextWrapping.Wrap, Height = 94, VerticalContentAlignment = VerticalAlignment.Stretch, Padding = new Thickness(11, 8, 11, 8), VerticalScrollBarVisibility = ScrollBarVisibility.Auto, FontSize = 11, BorderThickness = new Thickness(0), Background = Brush("#F4F7F0") };
            detailsPanel = new Border { CornerRadius = new CornerRadius(8), Background = Brush("#F4F7F0"), Child = logBox, Margin = new Thickness(0, 6, 0, 0), Visibility = Visibility.Collapsed }; stack.Children.Add(detailsPanel);
            Put(stack); primary.Content = action + "中…"; primary.IsEnabled = false; secondary.Visibility = Visibility.Collapsed; footerNote.Text = "";
        }
        private void Poll(object sender, EventArgs args)
        {
            if (closing) return;
            if (preview)
            {
                if (page == "working") { previewProgress = Math.Min(100, previewProgress + 7); SetProgress(previewProgress, previewProgress < 20 ? "准备安装环境" : previewProgress < 82 ? "正在更新应用文件" : "正在配置快捷方式"); if (previewProgress == 100) ShowDone(null); }
                return;
            }
            string phase = bridge.State("phase", ""), message = bridge.State("message", "");
            if (phase == "handoff") { handedOff = true; closing = true; Close(); return; }
            if (phase == "done") { if (page != "done") ShowDone(message); return; }
            if (phase == "error") { if (page != "error" || message != lastMessage) ShowError(message); return; }
            if (!ParentAlive()) { ShowError("安装程序已退出，未能确认操作完成。请关闭此窗口后重试。"); return; }
            if (!bridge.Exists) { ShowError("安装会话已结束，未能确认操作完成。请关闭此窗口后重试。"); return; }
            if (phase == "working" && page == "working")
            {
                int value; if (!Int32.TryParse(bridge.State("progress", "0"), out value)) value = 0;
                SetProgress(Math.Max(0, Math.Min(100, value)), message); ReadLog();
            }
        }
        private bool ParentAlive()
        {
            try { using (var process = Process.GetProcessById(owner)) return !process.HasExited; }
            catch (ArgumentException) { return false; } catch (InvalidOperationException) { return false; } catch (Win32Exception) { return true; }
        }
        private void SetProgress(int value, string message)
        {
            progress.Value = value; percent.Text = value + "%"; if (!String.IsNullOrWhiteSpace(message)) progressMessage.Text = message;
        }
        private void ReadLog()
        {
            if (detailsPanel == null || detailsPanel.Visibility != Visibility.Visible) return;
            if (preview) { logBox.Text = "这是界面预览。没有写入应用目录，也没有执行安装。"; return; }
            try
            {
                if (String.IsNullOrEmpty(logPath) || !File.Exists(logPath)) { logBox.Text = progressMessage.Text; return; }
                using (var stream = new FileStream(logPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                using (var reader = new StreamReader(stream, Encoding.UTF8, true))
                {
                    string text = reader.ReadToEnd(); if (text.Length > 16000) text = text.Substring(text.Length - 16000); if (logBox.Text != text) { logBox.Text = text; logBox.ScrollToEnd(); }
                }
            }
            catch (IOException) { }
        }
        private void ShowDone(string message)
        {
            terminal = true; page = "done"; timer.Stop(); var stack = Stack();
            var badge = new Border { Width = 44, Height = 44, Background = Brush("#E8F2E3"), CornerRadius = new CornerRadius(22), HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 0, 0, 14), Child = Text("✓", 24, Forest, FontWeights.Medium) }; ((TextBlock)badge.Child).HorizontalAlignment = HorizontalAlignment.Center; stack.Children.Add(badge);
            Heading(stack, action + "完成", uninstall ? (String.IsNullOrWhiteSpace(message) ? "个人数据已保留。" : message) : null, !uninstall);
            if (!uninstall) { launchControl = Check("立即打开 EyeRelax", true, delegate(bool value) { launch = value; }); stack.Children.Add(launchControl); }
            string warning = preview ? "" : bridge.State("warning", "");
            if (!String.IsNullOrWhiteSpace(warning))
            {
                var note = new Border { Background = Brush("#FBF6E6"), CornerRadius = new CornerRadius(9), Padding = new Thickness(13, 11, 13, 11), Margin = new Thickness(0, 17, 0, 0), Child = Text(warning, 12, "#937329") };
                stack.Children.Add(note);
            }
            Put(stack); primary.Content = "完成"; primary.IsEnabled = true; secondary.Visibility = Visibility.Collapsed; footerNote.Text = "";
        }
        private void ShowError(string message)
        {
            terminal = true; page = "error"; timer.Stop(); lastMessage = message; var stack = Stack(); Heading(stack, "未能完成" + action, null, false);
            var explanation = new Border { Background = Brush("#FBF2EA"), BorderBrush = Brush("#EEDACB"), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(10), Padding = new Thickness(16), Child = Text(String.IsNullOrWhiteSpace(message) ? "请重新运行安装程序。" : message, 13, "#8E4938") }; stack.Children.Add(explanation);
            if (!preview && !String.IsNullOrEmpty(logPath) && File.Exists(logPath))
            {
                var openLog = Button("打开诊断日志", "Secondary", delegate
                {
                    try { Process.Start(new ProcessStartInfo(logPath) { UseShellExecute = true }); }
                    catch (Exception error) { bridge.RecordError(error); }
                });
                openLog.HorizontalAlignment = HorizontalAlignment.Left; openLog.Margin = new Thickness(0, 10, 0, 0); stack.Children.Add(openLog);
            }
            Put(stack); primary.Content = "关闭"; primary.IsEnabled = true; secondary.Visibility = Visibility.Collapsed; footerNote.Text = "";
        }
        private void Finish(string request)
        {
            if (!preview && ParentAlive() && bridge.Exists)
            {
                try { bridge.Write("Choice", "launch", request == "launch" ? "1" : "0"); bridge.Write("Bridge", "request", request); }
                catch (Win32Exception error) { bridge.RecordError(error); }
            }
            closing = true; Close();
        }
        private void OnClosing(object sender, CancelEventArgs e)
        {
            if (closing || handedOff) return;
            if (submitted && !terminal) { e.Cancel = true; return; }
            if (!preview && ParentAlive() && bridge.Exists)
            {
                try { bridge.Write("Bridge", "request", terminal ? "close" : "cancel"); }
                catch (Win32Exception error) { bridge.RecordError(error); }
            }
            closing = true;
        }
    }

    // The Windows shell folder picker keeps navigation, keyboard access and new
    // folder creation native while the installation pages remain consistently styled.
    internal static class FolderPicker
    {
        [ComImport, Guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7")] private class FileOpenDialog { }
        [ComImport, Guid("42F85136-DB7E-439C-85F1-E4075D135FC8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IFileDialog
        {
            [PreserveSig] int Show(IntPtr owner);
            void SetFileTypes(uint count, IntPtr types); void SetFileTypeIndex(uint index); void GetFileTypeIndex(out uint index);
            void Advise(IntPtr events, out uint cookie); void Unadvise(uint cookie); void SetOptions(uint flags); void GetOptions(out uint flags);
            void SetDefaultFolder(IShellItem folder); void SetFolder(IShellItem folder); void GetFolder(out IShellItem folder); void GetCurrentSelection(out IShellItem item);
            void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string name); void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string name);
            void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string title); void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string text); void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
            void GetResult(out IShellItem item); void AddPlace(IShellItem item, uint place); void SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string extension);
            void Close(int result); void SetClientGuid(ref Guid guid); void ClearClientData(); void SetFilter(IntPtr filter);
        }
        [ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IShellItem
        {
            void BindToHandler(IntPtr context, ref Guid handler, ref Guid iid, out IntPtr result); void GetParent(out IShellItem parent);
            void GetDisplayName(uint kind, out IntPtr name); void GetAttributes(uint mask, out uint attributes); void Compare(IShellItem other, uint hint, out int order);
        }
        [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
        private static extern void SHCreateItemFromParsingName(string path, IntPtr context, ref Guid iid, out IShellItem item);
        internal static string Pick(Window owner, string initial)
        {
            IFileDialog dialog = null; IShellItem folder = null, result = null;
            try
            {
                dialog = (IFileDialog)new FileOpenDialog(); dialog.SetOptions(0x20 | 0x40 | 0x800 | 0x1000); dialog.SetTitle("选择文件夹"); dialog.SetOkButtonLabel("选择此文件夹");
                if (Directory.Exists(initial)) { Guid iid = typeof(IShellItem).GUID; SHCreateItemFromParsingName(initial, IntPtr.Zero, ref iid, out folder); dialog.SetFolder(folder); }
                int status = dialog.Show(new WindowInteropHelper(owner).Handle); if (status != 0) return null;
                dialog.GetResult(out result); IntPtr name; result.GetDisplayName(0x80058000, out name);
                try { return Marshal.PtrToStringUni(name); } finally { Marshal.FreeCoTaskMem(name); }
            }
            catch (COMException) { return null; }
            finally { if (result != null) Marshal.ReleaseComObject(result); if (folder != null) Marshal.ReleaseComObject(folder); if (dialog != null) Marshal.ReleaseComObject(dialog); }
        }
    }

    internal static class Program
    {
        [STAThread]
        private static int Main(string[] args)
        {
            bool preview = false, uninstall = false; string path = null; int owner = 0;
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] == "--preview") preview = true;
                else if (args[i] == "--uninstall") uninstall = true;
                else if (args[i] == "--session" && i + 1 < args.Length) path = args[++i];
                else if (args[i] == "--owner" && i + 1 < args.Length) Int32.TryParse(args[++i], out owner);
            }
            Bridge session = null;
            try
            {
                if (!preview && (String.IsNullOrWhiteSpace(path) || !Path.IsPathRooted(path) || !File.Exists(path) || owner <= 0)) throw new ArgumentException("安装会话无效，请重新打开安装程序。");
                var app = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
                session = preview ? null : new Bridge(path);
                app.DispatcherUnhandledException += delegate(object sender, DispatcherUnhandledExceptionEventArgs e)
                {
                    if (session != null) session.RecordError(e.Exception);
                    MessageBox.Show("安装界面意外中断，请重新运行安装程序。", "EyeRelax", MessageBoxButton.OK, MessageBoxImage.Error);
                    e.Handled = true; app.Shutdown(1);
                };
                return app.Run(new SetupWindow(session, owner, preview, uninstall));
            }
            catch (Exception e)
            {
                if (session != null) session.RecordError(e);
                MessageBox.Show("无法打开安装界面，请重新运行安装程序。", "EyeRelax", MessageBoxButton.OK, MessageBoxImage.Error);
                return 1;
            }
        }
    }
}
