package com.eye.ui;
import com.eye.ui.theme.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
/** Application picking UI owns transient selection; callers decide how to save or rescan. */
final class ApplicationChooserDialog {
    record Result(boolean accepted,boolean rescan,List<ApplicationSelectionList.Entry> selected){}
    private ApplicationChooserDialog(){}
    static Result choose(Component parent,List<ApplicationSelectionList.Entry> apps,double scale){
        return choose(parent,apps,scale,com.eye.i18n.I18n.text("选择应用加入关注（可多选）"));
    }
    static Result choose(Component parent,List<ApplicationSelectionList.Entry> apps,double scale,String title){
        ApplicationSelectionList list=new ApplicationSelectionList(apps,scale);
        JScrollPane scroll=new JScrollPane(list);Theme.scroll(scroll);scroll.setPreferredSize(new Dimension((int)Math.round(500*scale),(int)Math.round(330*scale)));
        scroll.getViewport().setOpaque(true);scroll.getViewport().setBackground(Theme.BG);
        Theme.Surface frame=new Theme.Surface(new BorderLayout(),Theme.BG,Theme.LINE,12);frame.setBorder(new EmptyBorder(6,6,6,6));frame.add(scroll);
        JPanel body=Theme.panel(new BorderLayout(0,10));JPanel toolbar=Theme.panel(new FlowLayout(FlowLayout.LEFT,8,0));
        JButton all=button(com.eye.i18n.I18n.text("全选")),clear=button(com.eye.i18n.I18n.text("全部取消")),rescan=button(com.eye.i18n.I18n.text("重新扫描"));toolbar.add(all);toolbar.add(clear);toolbar.add(rescan);
        JTextField search=new SettingsUi.PlaceholderField(com.eye.i18n.I18n.text("搜索应用名称或文件名"));Theme.field(search);search.setPreferredSize(new Dimension((int)(500*scale),(int)(36*scale)));
        Map<Path,ApplicationSelectionList.Entry> selectedApps=new LinkedHashMap<>();boolean[] filtering={false};
        JLabel hint=Theme.label(com.eye.i18n.I18n.text("已选 0 个"),11,Theme.SECONDARY);
        Runnable updateHint=()->hint.setText(com.eye.i18n.I18n.text("显示 ")+list.getModel().getSize()+com.eye.i18n.I18n.text(" 个 · 已选 ")+selectedApps.size()+com.eye.i18n.I18n.text(" 个"));
        list.addListSelectionListener(e->{if(filtering[0])return;for(int i=0;i<list.getModel().getSize();i++)selectedApps.remove(list.getModel().getElementAt(i).path());for(var entry:list.getSelectedValuesList())selectedApps.put(entry.path(),entry);updateHint.run();});
        Runnable filter=()->{
            filtering[0]=true;try{
                var visible=apps.stream().filter(entry->ApplicationSearch.matches(entry.name(),entry.path().toString(),search.getText())).toList();
                list.setListData(visible.toArray(ApplicationSelectionList.Entry[]::new));
                for(int i=0;i<visible.size();i++)if(selectedApps.containsKey(visible.get(i).path()))list.addSelectionInterval(i,i);
                all.setEnabled(!visible.isEmpty());updateHint.run();
            }finally{filtering[0]=false;}
        };
        onSearch(search,filter);filter.run();
        all.addActionListener(e->{if(list.getModel().getSize()>0)list.setSelectionInterval(0,list.getModel().getSize()-1);});
        clear.addActionListener(e->{selectedApps.clear();list.clearSelection();updateHint.run();});
        JPanel header=Theme.panel(new BorderLayout(0,10));JPanel top=Theme.panel(new BorderLayout(0,10));top.add(toolbar,BorderLayout.NORTH);top.add(search);header.add(top,BorderLayout.NORTH);header.add(hint,BorderLayout.SOUTH);body.add(header,BorderLayout.NORTH);body.add(frame);
        body.add(com.eye.tutorial.TutorialFormHelp.create(parent,java.util.List.of(
            new com.eye.tutorial.TutorialFormHelp.Hint("输入应用名称或文件名，筛选扫描结果。",search),
            new com.eye.tutorial.TutorialFormHelp.Hint("勾选需要关注的应用，可一次选择多个。完成后点击确定。",list))),BorderLayout.SOUTH);
        JOptionPane pane=new JOptionPane(body,JOptionPane.PLAIN_MESSAGE,JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog=pane.createDialog(parent,title);boolean[] again={false};
        rescan.addActionListener(e->{again[0]=true;dialog.dispose();});
        try{dialog.setVisible(true);}finally{dialog.dispose();}
        return new Result(Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue()),again[0],List.copyOf(selectedApps.values()));
    }
    private static JButton button(String text){JButton button=new JButton(text);Theme.smallButton(button,"secondary",null);return button;}
    private static void onSearch(JTextField field,Runnable update){field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){update.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){update.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){update.run();}});}
}
