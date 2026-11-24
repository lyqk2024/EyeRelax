package com.eye.ui;

import com.eye.core.*;
import com.eye.sync.StrictSettings;
import com.eye.ui.theme.*;
import com.eye.win.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.eye.i18n.I18n.text;

/** Rule management and editing; validation stays in-place and never discards an unfinished form. */
public final class ApplicationLimitsPanel extends JPanel {
    private final Config config;
    private final ComputerActivity activity;
    private final ApplicationLimits usage;
    private final DefaultListModel<ApplicationLimitRule> model=new DefaultListModel<>();
    private final JList<ApplicationLimitRule> list=new JList<>(model);
    private final JTextField search=new SettingsUi.PlaceholderField(text("搜索应用名称或文件名"));
    private final JLabel feedback=Theme.label(" ",11,Theme.SECONDARY);
    private final JLabel count=Theme.label("",11,Theme.SECONDARY);
    private final JPanel results=Theme.panel(new CardLayout());
    private final JLabel emptyTitle=Theme.heading("还没有添加应用",18);
    private final Map<String,Icon> icons=new HashMap<>();
    private final Set<String> pending=new HashSet<>();
    private List<ApplicationSelectionList.Entry> scanCache;
    private long scannedAt;
    private JComponent tutorialAdd,tutorialActions,tutorialBehavior;
    public static void open(Component owner,Config config,ComputerActivity activity,ApplicationLimits usage) {
        Window parent=owner instanceof Window window?window:SwingUtilities.getWindowAncestor(owner);
        JDialog dialog=new JDialog(parent,text("应用限制"),Dialog.ModalityType.APPLICATION_MODAL);
        Theme.window(dialog);ApplicationLimitsPanel panel=new ApplicationLimitsPanel(config,activity,usage);dialog.setContentPane(panel);panel.attachTutorial(owner);dialog.setSize(880,680);
        dialog.setMinimumSize(new Dimension(720,500));dialog.setLocationRelativeTo(owner);dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);dialog.setVisible(true);
    }
    public ApplicationLimitsPanel(Config config,ComputerActivity activity,ApplicationLimits usage) {
        super(new BorderLayout(0,16));this.config=config;this.activity=activity;this.usage=usage;setBackground(Theme.BG);setBorder(new EmptyBorder(24,24,24,24));
        JPanel top=new SettingsUi.Column();
        JPanel heading=Theme.panel(new BorderLayout(16,0));JPanel titles=new SettingsUi.Column();
        titles.add(Theme.heading(text("应用限制"),24));
        titles.add(Box.createVerticalStrut(5));titles.add(new SettingsUi.Text(text("选择应用，可完全禁用、设置每日额度或限定使用时段。"),12,Theme.SECONDARY));
        heading.add(titles);heading.add(count,BorderLayout.EAST);top.add(heading);top.add(Box.createVerticalStrut(22));
        JPanel actions=Theme.panel(new BorderLayout(12,0)),create=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,0)),selected=Theme.panel(new FlowLayout(FlowLayout.RIGHT,8,0));
        JButton add=button("添加应用…","primary","plus"),manual=button("选择 EXE…","secondary","folder"),edit=button("编辑规则","secondary","edit"),remove=button("删除规则","ghost","trash");
        tutorialAdd=add;tutorialActions=results;add.putClientProperty("tutorial.region",actions);
        create.add(add);create.add(Box.createHorizontalStrut(10));create.add(manual);selected.add(edit);selected.add(remove);actions.add(create);actions.add(selected,BorderLayout.EAST);top.add(actions);top.add(Box.createVerticalStrut(14));
        Theme.field(search);search.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));top.add(search);add(top,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setBackground(Theme.BG);list.setFixedCellHeight(112);
        list.setCellRenderer((l,r,index,isSelected,focus)->ruleCard(r,isSelected));
        JScrollPane scroll=new JScrollPane(list);Theme.scroll(scroll);scroll.getViewport().setBackground(Theme.BG);results.add(scroll,"list");
        JPanel empty=Theme.panel(new GridBagLayout());JPanel emptyCopy=new SettingsUi.Column();
        JLabel emptyIcon=new JLabel(Icons.of("monitor",40,Theme.SAGE));emptyCopy.add(emptyIcon);emptyCopy.add(Box.createVerticalStrut(16));emptyCopy.add(emptyTitle);emptyCopy.add(Box.createVerticalStrut(8));emptyCopy.add(Theme.label(text("添加常用应用，设置禁用、额度或允许时段。"),12,Theme.SECONDARY));empty.add(emptyCopy);results.add(empty,"empty");add(results);
        feedback.setMaximumSize(new Dimension(Integer.MAX_VALUE,24));
        JPanel bottom=new SettingsUi.Column();bottom.add(feedback);bottom.add(Box.createVerticalStrut(6));
        tutorialBehavior=new SettingsUi.Text(text("先遮挡受限窗口，点击返回桌面后结束应用。每日额度按学习日分界重置。"),11,Theme.SECONDARY);bottom.add(tutorialBehavior);add(bottom,BorderLayout.SOUTH);
        edit.setEnabled(false);remove.setEnabled(false);list.addListSelectionListener(e->{edit.setEnabled(list.getSelectedValue()!=null);remove.setEnabled(list.getSelectedValue()!=null);});
        edit.addActionListener(e->{if(list.getSelectedValue()!=null)edit(List.of(list.getSelectedValue()));});
        list.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2&&list.getSelectedValue()!=null)edit(List.of(list.getSelectedValue()));}});
        remove.addActionListener(e->{ApplicationLimitRule r=list.getSelectedValue();if(r==null)return;Map<String,ApplicationLimitRule> next=ApplicationLimitRule.parse(config.applicationLimits);next.remove(r.path());if(save(next))refresh();});
        manual.addActionListener(e->{try{Path p=WindowsShell.choose(this,text("选择要限制的应用"),false);if(p!=null){if(!Files.isRegularFile(p)||!ApplicationProtection.eligible(p.toString()))throw new IllegalArgumentException(text("请选择普通应用的 EXE；桌面、系统安全组件和 EyeRelax 不可限制。"));edit(List.of(existing(p,p.getFileName().toString())));}}catch(Exception ex){feedback.setText(ex.getMessage());}});
        add.addActionListener(e->scan(add,false));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){refresh();}public void removeUpdate(javax.swing.event.DocumentEvent e){refresh();}public void changedUpdate(javax.swing.event.DocumentEvent e){refresh();}});
        javax.swing.Timer timer=new javax.swing.Timer(60_000,e->list.repaint());addHierarchyListener(e->{if(isShowing())timer.start();else timer.stop();});refresh();
    }
    private void attachTutorial(Component owner){com.eye.tutorial.TutorialFormHelp.create(owner,List.of(
        new com.eye.tutorial.TutorialFormHelp.Hint("添加要管理的应用；支持搜索、多选或手动选择 EXE。",tutorialAdd),
        new com.eye.tutorial.TutorialFormHelp.Hint("添加应用后，双击列表中的应用，设置它的额度和允许时段。",tutorialActions),
        new com.eye.tutorial.TutorialFormHelp.Hint("受限应用先显示原因；返回桌面后再关闭，其他应用可以继续使用。",tutorialBehavior)));}
    private Icon icon(String path){
        if(!icons.containsKey(path)&&pending.add(path))new SwingWorker<Icon,Void>(){protected Icon doInBackground(){return ApplicationSelectionList.entry(Path.of(path),"",32).icon();}protected void done(){pending.remove(path);try{icons.put(path,get());}catch(Exception e){icons.put(path,UIManager.getIcon("FileView.fileIcon"));}list.repaint();}}.execute();
        return ApplicationSelectionList.fitIcon(icons.getOrDefault(path,UIManager.getIcon("FileView.fileIcon")),32);
    }
    private ApplicationLimitRule existing(Path path,String name){return ApplicationLimitRule.parse(config.applicationLimits).getOrDefault(ApplicationLimitRule.key(path.toAbsolutePath().normalize().toString()),new ApplicationLimitRule(path.toAbsolutePath().normalize().toString(),name,true,false,60*60_000L,false,480,1380));}
    private void refresh(){
        String selected=list.getSelectedValue()==null?"":list.getSelectedValue().path();model.clear();
        var rules=ApplicationLimitRule.parse(config.applicationLimits);count.setText(rules.size()+text(" 项应用 · ")+rules.values().stream().filter(ApplicationLimitRule::enabled).count()+text(" 项启用"));
        for(var r:rules.values())if(ApplicationSearch.matches(r.name(),r.path(),search.getText())){model.addElement(r);if(r.path().equals(selected))list.setSelectedValue(r,true);}
        emptyTitle.setText(text(rules.isEmpty()?"还没有添加应用":"没有匹配的应用"));((CardLayout)results.getLayout()).show(results,model.isEmpty()?"empty":"list");
    }
    private JComponent ruleCard(ApplicationLimitRule r,boolean selected){
        JPanel margin=Theme.panel(new BorderLayout());margin.setBorder(new EmptyBorder(0,0,10,0));
        Theme.Surface card=new Theme.Surface(new BorderLayout(16,0),selected?Theme.MINT:Theme.SURFACE,selected?Theme.SAGE:Theme.LINE,14);card.setBorder(new EmptyBorder(16,18,16,18));
        card.add(new JLabel(icon(r.path())),BorderLayout.WEST);JPanel info=new SettingsUi.Column();
        JPanel top=Theme.panel(new BorderLayout(12,0));top.add(Theme.heading(r.name(),15));
        String state=!r.enabled()?"已暂停":r.blocked()?"完全禁用":"限时使用";
        JLabel badge=Theme.label(text(state),11,r.enabled()?Theme.GREEN:Theme.SECONDARY);top.add(badge,BorderLayout.EAST);info.add(top);info.add(Box.createVerticalStrut(9));
        String description=r.blocked()?text("打开时显示限制说明"): (r.daily()>0?text("每天 ")+minutes(r.daily()):text("不限每日时长"))+(r.window()?"  ·  "+clock(r.start())+" – "+clock(r.end()):"");
        String issue=usage.enforcementError(r.path());
        info.add(Theme.label(issue.isEmpty()?description:issue,12,issue.isEmpty()?Theme.SECONDARY:Theme.DANGER));
        card.add(info);JPanel amount=new SettingsUi.Column();amount.add(Theme.label(text("本统计日已用"),10,Theme.SECONDARY));amount.add(Box.createVerticalStrut(6));
        amount.add(Theme.heading(minutes(usage.used(r.path(),Instant.now())),16));amount.setPreferredSize(new Dimension(112,52));card.add(amount,BorderLayout.EAST);
        margin.add(card);margin.setToolTipText(r.path());return margin;
    }
    private void scan(JButton button,boolean force){
        if(!force&&scanCache!=null&&System.currentTimeMillis()-scannedAt<600_000){choose(button);return;}
        button.setEnabled(false);feedback.setText(text("正在扫描常用应用…"));
        new SwingWorker<List<ApplicationSelectionList.Entry>,Void>(){
            protected List<ApplicationSelectionList.Entry> doInBackground()throws Exception{
                Map<Path,String> apps=new TreeMap<>(WindowsUsageSources.commonApplications());
                if(activity!=null){activity.applications().forEach((p,n)->apps.put(Path.of(p),n));for(String p:activity.applicationUsageTotals().keySet())apps.putIfAbsent(Path.of(p),activity.displayName(p));}
                List<ApplicationSelectionList.Entry> out=new ArrayList<>();for(var e:apps.entrySet())if(Files.isRegularFile(e.getKey())&&ApplicationProtection.eligible(e.getKey().toString()))out.add(ApplicationSelectionList.entry(e.getKey(),e.getValue(),28));
                out.sort(Comparator.comparing(ApplicationSelectionList.Entry::name,String.CASE_INSENSITIVE_ORDER));return out;
            }
            protected void done(){button.setEnabled(true);try{scanCache=get();scannedAt=System.currentTimeMillis();choose(button);}catch(Exception ex){feedback.setText(text("扫描失败，可手动选择 EXE。"));}}
        }.execute();
    }
    private void choose(JButton button){
        var existing=ApplicationLimitRule.parse(config.applicationLimits);var candidates=scanCache.stream().filter(e->!existing.containsKey(ApplicationLimitRule.key(e.path().toString()))).toList();
        var result=ApplicationChooserDialog.choose(this,candidates,1,text("选择要限制的应用（可多选）"));
        if(result.rescan()){scan(button,true);return;}
        if(result.accepted()&&!result.selected().isEmpty())edit(result.selected().stream().map(e->existing(e.path(),e.name())).toList());
    }
    private boolean save(Map<String,ApplicationLimitRule> rules){
        Config candidate=StrictSettings.copy(config);candidate.applicationLimits=ApplicationLimitRule.encode(rules);
        if(candidate.hasApplicationLimits())candidate.launchAtLogin=true;
        if(!StrictSettings.allow(candidate)){feedback.setForeground(Theme.DANGER);feedback.setText(text(StrictSettings.MESSAGE));return false;}
        try{
            if(candidate.hasApplicationLimits())StartupRegistration.apply(true);
            String old=config.applicationLimits;boolean startup=config.launchAtLogin;
            config.applicationLimits=candidate.applicationLimits;config.launchAtLogin=candidate.launchAtLogin;
            if(!config.save()){config.applicationLimits=old;config.launchAtLogin=startup;throw new IllegalStateException(text("设置未保存，请检查严格规则或数据目录。"));}
            feedback.setForeground(Theme.GREEN);feedback.setText(text("应用限制已保存，立即生效。"));return true;
        }catch(Exception ex){feedback.setForeground(Theme.DANGER);feedback.setText(ex.getMessage());return false;}
    }
    private void edit(List<ApplicationLimitRule> selected){
        ApplicationRuleEditor.open(this,config,selected,changed->{
            Map<String,ApplicationLimitRule> next=ApplicationLimitRule.parse(config.applicationLimits);
            changed.forEach(rule->next.put(rule.path(),rule));
            if(!save(next))return feedback.getText();refresh();return null;
        });
    }
    private static String minutes(long ms){return String.format("%d:%02d:%02d",ms/3_600_000,(ms/60_000)%60,(ms/1000)%60);}
    private static String clock(int minute){return String.format("%02d:%02d",minute/60,minute%60);}
    private static JButton button(String label,String style,String icon){JButton b=new JButton(text(label));Theme.smallButton(b,style,icon);return b;}
}
