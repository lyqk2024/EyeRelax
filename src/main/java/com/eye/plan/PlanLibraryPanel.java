package com.eye.plan;
import com.eye.core.Config;
import com.eye.i18n.I18n;
import com.eye.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
/** Archived definitions and reusable templates; historical snapshots stay in Past plans. */
final class PlanLibraryPanel extends JPanel {
    private final CollectionBrowser<PlanItem> browser;
    private int category;
    private final JPanel tutorialTabs=Theme.panel(new FlowLayout(FlowLayout.LEFT,5,0));
    JComponent tutorialTarget(boolean templates){return (JComponent)tutorialTabs.getComponent(templates?1:0);}
    JComponent tutorialActions(){return browser;}
    void tutorialCategory(boolean templates){category=templates?1:0;for(int i=0;i<tutorialTabs.getComponentCount();i++)if(tutorialTabs.getComponent(i) instanceof AbstractButton b)b.setSelected(i==category);filter();}
    PlanLibraryPanel(PlanManager manager,Config config,Runnable changed){
        super(new BorderLayout(0,16));setOpaque(false);
        JPanel tabs=tutorialTabs;ButtonGroup group=new ButtonGroup();String[] labels={I18n.text("已归档计划"),I18n.text("计划模板"),I18n.text("可用计划")};
        browser=new CollectionBrowser<>(()->manager.listAll().stream().sorted(Comparator.comparing((PlanItem p)->!p.pinned).thenComparing(PlanLibraryPanel::planDate,Comparator.reverseOrder())).toList(),
            p->(p.pinned?"★ ":"")+p.name,p->(p.template?I18n.text("模板"):p.archivedOn!=null?I18n.text("已归档")+" · "+planDate(p):I18n.text("可用"))+" · "+PlanItem.fmtHms(p.plannedSeconds),PlanLibraryPanel::planDate);
        browser.identity(p->p.id);
        browser.renderer(new LibraryRenderer(),68);
        String[] icons={"folder","grid","calendar"};
        for(int i=0;i<labels.length;i++){int index=i;JToggleButton b=new JToggleButton(labels[i]);Theme.smallButton(b,"segment",icons[i]);group.add(b);tabs.add(b);b.setSelected(i==0);b.addActionListener(e->{category=index;filter();});}
        browser.action(I18n.text("复制为新计划"),"plus",p->new PlanEditorDialog(this,manager,config,()->{refresh();changed.run();}).showCopy(p));
        browser.action(I18n.text("保存为模板"),"grid",p->!p.template,p->{manager.saveTemplate(p.id);refresh();});
        browser.action(I18n.text("切换置顶"),"pin",p->{manager.setPinned(p.id,!p.pinned);refresh();changed.run();});
        browser.action(I18n.text("删除"),"trash",p->p.template||p.archivedOn!=null,p->{if(JOptionPane.showConfirmDialog(this,I18n.text(p.template?"删除此模板？历史统计会保留。":"删除此归档计划？历史统计会保留。"),I18n.text("确认删除"),JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){manager.deletePlan(p.id);refresh();changed.run();}});
        Theme.Surface track=new Theme.Surface(new BorderLayout(),Theme.PAGE,Theme.LINE,12);
        track.setBorder(new javax.swing.border.EmptyBorder(4,4,4,4));track.add(tabs);
        JPanel tabsRow=Theme.panel(new FlowLayout(FlowLayout.LEFT,0,0));tabsRow.add(track);
        add(tabsRow,BorderLayout.NORTH);add(browser);filter();
    }
    private void filter(){browser.predicate(p->category==0?p.archivedOn!=null&&!p.template:category==1?p.template:!p.template&&p.archivedOn==null);}
    void refresh(){browser.refresh();}
    private static java.time.LocalDate planDate(PlanItem p){return !p.template&&p.kind==PlanItem.PlanKind.MULTI_DAY?p.selectedDates.stream().max(java.time.LocalDate::compareTo).orElse(p.createdDate):p.createdDate;}

    /** One reusable, compact row; long names yield to the full planned duration. */
    private static final class LibraryRenderer extends JComponent implements ListCellRenderer<PlanItem>{
        private PlanItem plan;private boolean selected,focused;private double scale;
        public Component getListCellRendererComponent(JList<? extends PlanItem> list,PlanItem value,int index,boolean selected,boolean focused){
            plan=value;this.selected=selected;this.focused=focused;scale=ProportionalPanel.scaleOf(list);
            getAccessibleContext().setAccessibleName(value.name+" · "+PlanItem.fmtHms(value.plannedSeconds));return this;
        }
        @Override public javax.accessibility.AccessibleContext getAccessibleContext(){if(accessibleContext==null)accessibleContext=new AccessibleJComponent(){};return accessibleContext;}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();Theme.quality(g);g.scale(scale,scale);
            int w=(int)Math.round(getWidth()/scale),h=(int)Math.round(getHeight()/scale);
            g.setColor(selected?Theme.MINT:Theme.SURFACE);g.fillRoundRect(0,2,w,h-4,12,12);
            if(selected||focused){g.setColor(Theme.SAGE);g.drawRoundRect(1,2,w-3,h-5,12,12);}
            else{g.setColor(Theme.LINE);g.drawLine(58,h-1,w-14,h-1);}
            if(plan!=null){
                g.setColor(Theme.GROUP);g.fillRoundRect(12,15,34,34,10,10);
                Icons.of(plan.template?"grid":plan.archivedOn!=null?"folder":"calendar",18,Theme.GREEN).paintIcon(this,g,20,23);
                String duration=PlanItem.fmtHms(plan.plannedSeconds);g.setFont(Theme.numbers(16));int tw=g.getFontMetrics().stringWidth(duration),right=w-16;
                g.setColor(Theme.INK);g.drawString(duration,right-tw,31);
                String label=I18n.text("计划时长");g.setFont(Theme.font(10));g.setColor(Theme.SECONDARY);g.drawString(label,right-g.getFontMetrics().stringWidth(label),49);
                g.setFont(Theme.bold(13));g.setColor(Theme.INK);String name=(plan.pinned?"★ ":"")+plan.name;
                g.drawString(fit(g,name,Math.max(20,right-tw-76)),58,29);
                String detail=plan.template?I18n.text("模板"):plan.archivedOn!=null?I18n.text("已归档")+" · "+plan.archivedOn:I18n.text("可用")+" · "+planDate(plan);
                g.setFont(Theme.font(10.5f));g.setColor(Theme.SECONDARY);g.drawString(fit(g,detail,Math.max(20,right-tw-76)),58,49);
            }g.dispose();
        }
        private static String fit(Graphics2D g,String text,int width){int end=text.length();while(end>0&&g.getFontMetrics().stringWidth(text.substring(0,end)+(end<text.length()?"…":""))>width)end--;return text.substring(0,end)+(end<text.length()?"…":"");}
    }
}
