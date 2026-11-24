package com.eye.ui;

import com.eye.core.ApplicationLimits;
import com.eye.ui.theme.*;
import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.eye.i18n.I18n.text;

/** A visible, window-sized input surface. Never disables another process's windows or global input. */
public final class ApplicationLimitOverlay {
    private JFrame frame;
    private JLabel name;
    private JPanel reason;
    private JButton back;
    private ApplicationLimits.Status shown;
    private Runnable returnAction;
    private volatile long handle;
    public boolean owns(WinDef.HWND window){return window!=null&&handle!=0&&Pointer.nativeValue(window.getPointer())==handle;}
    public void show(ApplicationLimits.Status status,Rectangle bounds,Runnable onReturn){
        if(frame==null){
            frame=new JFrame(text("应用使用限制"));frame.setUndecorated(true);frame.setType(Window.Type.UTILITY);frame.setAlwaysOnTop(true);frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JPanel root=new JPanel(new GridBagLayout());root.setBackground(Theme.MINT);root.setBorder(BorderFactory.createLineBorder(Theme.SAGE,2));
            Theme.Surface card=new Theme.Surface(new BorderLayout(0,14),Theme.SURFACE,Theme.LINE,16){
                @Override public Dimension getPreferredSize(){return new Dimension(Math.max(1,Math.min(460,frame.getWidth()-32)),Math.max(1,Math.min(330,frame.getHeight()-32)));}
            };card.setBorder(new EmptyBorder(20,20,20,20));
            JPanel copy=new SettingsUi.Column();JLabel heading=Theme.heading(text("暂时无法使用"),22);heading.setIcon(Icons.of("lock",24,Theme.GREEN));heading.setIconTextGap(10);copy.add(heading);copy.add(Box.createVerticalStrut(14));
            name=Theme.heading("",17);copy.add(name);copy.add(Box.createVerticalStrut(12));reason=new JPanel(new BorderLayout());reason.setOpaque(false);copy.add(reason);copy.add(Box.createVerticalStrut(12));
            copy.add(new SettingsUi.Text(text("可以切换到其他应用继续使用电脑。"),12,Theme.SECONDARY));copy.add(Box.createVerticalStrut(10));
            copy.add(new SettingsUi.Text(text("返回桌面会关闭此应用，包括后台运行的部分。未保存的内容可能丢失。"),11,Theme.SECONDARY));
            JScrollPane scroll=new JScrollPane(copy);Theme.scroll(scroll);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);card.add(scroll);
            back=new JButton(text("返回桌面"));Theme.button(back,"primary","prev");back.addActionListener(e->{if(returnAction!=null){back.setEnabled(false);returnAction.run();}});card.add(back,BorderLayout.SOUTH);
            root.add(card);frame.setContentPane(root);frame.getRootPane().setDefaultButton(back);
        }
        returnAction=onReturn;
        if(!status.equals(shown)){
            shown=status;name.setText(status.name());name.setToolTipText(status.name());reason.removeAll();reason.add(new SettingsUi.Text(explanation(status),13,Theme.INK));reason.revalidate();back.setEnabled(true);
        }
        // Native bounds avoid rounding and monitor-scale drift between the target and shield.
        boolean opening=!frame.isVisible();
        if(opening){frame.setBounds(bounds);frame.setVisible(true);handle=Pointer.nativeValue(Native.getComponentPointer(frame));}
        WinDef.HWND hwnd=new WinDef.HWND(new Pointer(handle));
        boolean needsFocus=opening||!owns(User32.INSTANCE.GetForegroundWindow());
        User32.INSTANCE.SetWindowPos(hwnd,new WinDef.HWND(new Pointer(-1)),bounds.x,bounds.y,bounds.width,bounds.height,needsFocus?0x0040:0x0010);
        if(needsFocus){frame.toFront();back.requestFocusInWindow();User32.INSTANCE.SetForegroundWindow(hwnd);}
    }
    public static String explanation(ApplicationLimits.Status status){return switch(status.reason()){
        case BANNED->text("这个应用已设置为完全禁用。");
        case DAILY->text("本统计日的使用额度已用完。")+"\n"+text("已使用 ")+com.eye.stats.Statistics.duration(status.used())+" / "+com.eye.stats.Statistics.duration(status.budget());
        case WINDOW->text("当前不在这个应用的允许使用时段内。");
        case STORAGE->text("暂时无法读取应用用量记录，请检查数据目录。");
        default->"";
    };}
    public void hide(){if(frame!=null){handle=0;frame.setVisible(false);shown=null;returnAction=null;}}
    public void dispose(){hide();if(frame!=null){frame.dispose();frame=null;}}
}
