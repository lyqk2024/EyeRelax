package com.eye.ui;

import com.eye.ui.theme.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.nio.file.Path;

/** A roomy multi-selection list with Windows executable icons loaded off the EDT. */
public final class ApplicationSelectionList extends ToggleSelectionList<ApplicationSelectionList.Entry> {
    public record Entry(Path path,String name,Icon icon) {}
    public static Entry entry(Path path,String name,int iconSize) {
        Icon icon=FileSystemView.getFileSystemView().getSystemIcon(path.toFile(),iconSize,iconSize);
        if(icon==null)icon=UIManager.getIcon("FileView.fileIcon");
        return new Entry(path,name,fitIcon(icon,iconSize));
    }
    /** Windows may return a larger stock icon than requested, especially at high DPI. */
    public static Icon fitIcon(Icon source,int size) {
        int target=Math.max(1,size);
        return new Icon(){
            public int getIconWidth(){return target;}
            public int getIconHeight(){return target;}
            public void paintIcon(Component component,Graphics graphics,int x,int y){
                if(source==null)return;
                double ratio=Math.min((double)target/Math.max(1,source.getIconWidth()),(double)target/Math.max(1,source.getIconHeight()));
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.translate(x+(target-source.getIconWidth()*ratio)/2,y+(target-source.getIconHeight()*ratio)/2);
                g.scale(ratio,ratio);source.paintIcon(component,g,0,0);g.dispose();
            }
        };
    }
    public ApplicationSelectionList(java.util.List<Entry> entries,double scale) {
        super(entries.toArray(Entry[]::new));
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        setVisibleRowCount(9);setFixedCellHeight((int)Math.round(44*scale));
        setFont(Theme.font(12).deriveFont((float)(12*scale)));
        setBackground(Theme.BG);setSelectionBackground(Theme.MINT);setSelectionForeground(Theme.GREEN);
        setCellRenderer((list,value,index,selected,focus)->{
            JPanel row=new JPanel(new BorderLayout((int)Math.round(12*scale),0));
            row.setBackground(selected?Theme.MINT:Theme.BG);
            row.setBorder(new EmptyBorder((int)Math.round(6*scale),(int)Math.round(12*scale),(int)Math.round(6*scale),(int)Math.round(12*scale)));
            JLabel name=new JLabel(value.name(),value.icon(),SwingConstants.LEFT);
            name.setFont(list.getFont());name.setIconTextGap((int)Math.round(12*scale));name.setForeground(selected?Theme.GREEN:Theme.INK);
            JCheckBox check=new JCheckBox();check.setSelected(selected);check.setOpaque(false);check.setFocusable(false);
            Icon checkboxIcon=UIManager.getIcon("CheckBox.icon");
            if(checkboxIcon!=null)check.setIcon(new Icon(){
                public int getIconWidth(){return (int)Math.round(checkboxIcon.getIconWidth()*scale);}
                public int getIconHeight(){return (int)Math.round(checkboxIcon.getIconHeight()*scale);}
                public void paintIcon(Component component,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();g.translate(x,y);g.scale(scale,scale);checkboxIcon.paintIcon(component,g,0,0);g.dispose();}
            });
            row.add(name,BorderLayout.CENTER);row.add(check,BorderLayout.EAST);
            row.setSize(Math.max(1,list.getWidth()),getFixedCellHeight());row.doLayout();return row;
        });
    }
}
