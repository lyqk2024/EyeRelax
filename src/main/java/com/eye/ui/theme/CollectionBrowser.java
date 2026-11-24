package com.eye.ui.theme;
import com.eye.i18n.I18n;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.function.*;

/** Searchable virtual list: one reusable renderer, regardless of archive size. */
public final class CollectionBrowser<T> extends JPanel {
    private final Supplier<List<T>> supplier;
    private final Function<T,String> title,detail;
    private final Function<T,LocalDate> date;
    private final JList<T> list=new JList<>(){@Override public boolean getScrollableTracksViewportWidth(){return true;}};
    private final JTextField search=new SettingsUi.PlaceholderField(I18n.text("搜索名称")),from=new JTextField(),to=new JTextField();
    private final JLabel status=Theme.label("",11,Theme.SECONDARY);
    private final JPanel actions=Theme.panel(new FlowLayout(FlowLayout.LEFT,6,0));
    private final List<JButton> actionButtons=new ArrayList<>();
    private final java.util.Map<JButton,Predicate<T>> availability=new java.util.HashMap<>();
    private List<T> source=List.of();
    private Function<T,Object> identity=item->item;
    public void identity(Function<T,Object> identity){this.identity=identity;}
    /** Presentation hook only; stable IDs, filtering and selection remain owned here. */
    public void renderer(ListCellRenderer<? super T> renderer,int rowHeight){
        list.setCellRenderer(renderer);list.putClientProperty("eyerelax.listBaseHeight",rowHeight);
        list.setFixedCellHeight((int)Math.round(rowHeight*ProportionalPanel.scaleOf(list)));
    }
    private void updateActions(){T item=list.getSelectedValue();for(JButton button:actionButtons)button.setEnabled(item!=null&&availability.get(button).test(item));}
    private Predicate<T> predicate=item->true;
    public CollectionBrowser(Supplier<List<T>> supplier,Function<T,String> title,Function<T,String> detail,Function<T,LocalDate> date){
        super(new BorderLayout(0,10));setOpaque(false);this.supplier=supplier;this.title=title;this.detail=detail;this.date=date;
        JPanel top=Theme.panel(new BorderLayout(0,9));JPanel fields=Theme.panel(new FlowLayout(FlowLayout.LEFT,7,0));
        for(JTextField f:new JTextField[]{search,from,to}){Theme.field(f);f.setColumns(0);f.setFont(Theme.font(12));}
        search.setPreferredSize(new Dimension(190,34));from.setPreferredSize(new Dimension(110,34));to.setPreferredSize(new Dimension(110,34));
        search.getAccessibleContext().setAccessibleName(I18n.text("搜索名称"));from.getAccessibleContext().setAccessibleName(I18n.text("开始日期"));to.getAccessibleContext().setAccessibleName(I18n.text("结束日期"));
        fields.add(search);fields.add(Theme.label(I18n.text("从"),11,Theme.SECONDARY));fields.add(from);fields.add(Theme.label(I18n.text("到"),11,Theme.SECONDARY));fields.add(to);top.add(fields,BorderLayout.NORTH);top.add(actions,BorderLayout.CENTER);add(top,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setOpaque(false);list.setFixedCellHeight(60);list.putClientProperty("eyerelax.listBaseHeight",60);
        list.setCellRenderer(new ListCellRenderer<>(){
            final JPanel row=new JPanel(new BorderLayout(0,5)){@Override public void paint(Graphics g){doLayout();super.paint(g);}};final JLabel name=Theme.heading("",13),description=Theme.label("",10.5f,Theme.SECONDARY);
            {row.setBorder(new EmptyBorder(9,12,9,12));row.add(name,BorderLayout.NORTH);row.add(description);}
            public Component getListCellRendererComponent(JList<? extends T> l,T value,int index,boolean selected,boolean focused){
                name.setText(title.apply(value));description.setText(detail.apply(value));double scale=ProportionalPanel.scaleOf(l);
                name.setFont(Theme.bold((float)(13*scale)));description.setFont(Theme.font((float)(10.5*scale)));row.setBorder(new EmptyBorder((int)(9*scale),(int)(12*scale),(int)(9*scale),(int)(12*scale)));
                row.setOpaque(true);row.setBackground(selected?Theme.MINT:Theme.SURFACE);return row;
            }
        });
        JScrollPane scroll=new JScrollPane(list);Theme.scroll(scroll);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);add(scroll);add(status,BorderLayout.SOUTH);
        DocumentListener changes=new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}};
        for(JTextField f:new JTextField[]{search,from,to})f.getDocument().addDocumentListener(changes);
        list.addListSelectionListener(e->updateActions());
    }
    public void action(String label,String icon,Consumer<T> action){action(label,icon,item->true,action);}
    public void action(String label,String icon,Predicate<T> enabled,Consumer<T> action){JButton b=new JButton(label);Theme.smallButton(b,"secondary",icon);b.setEnabled(false);b.addActionListener(e->{T selected=list.getSelectedValue();if(selected!=null&&enabled.test(selected))action.accept(selected);});actions.add(b);actionButtons.add(b);availability.put(b,enabled);}
    public void predicate(Predicate<T> predicate){this.predicate=predicate;filter();}
    public void refresh(){source=supplier.get();filter();}
    private void filter(){
        T selected=list.getSelectedValue();Object selectedId=selected==null?null:identity.apply(selected);
        LocalDate first=null,last=null;
        try{if(!from.getText().isBlank())first=LocalDate.parse(from.getText().strip());if(!to.getText().isBlank())last=LocalDate.parse(to.getText().strip());if(first!=null&&last!=null&&first.isAfter(last))throw new IllegalArgumentException();}
        catch(RuntimeException ex){status.setForeground(Theme.DANGER);status.setText(I18n.text("请输入有效日期范围 yyyy-MM-dd。"));return;}
        String query=search.getText().strip().toLowerCase(java.util.Locale.ROOT);List<T> shown=new ArrayList<>();
        for(T item:source){LocalDate d=date.apply(item);if(!predicate.test(item)||!title.apply(item).toLowerCase(java.util.Locale.ROOT).contains(query))continue;
            if(first!=null&&(d==null||d.isBefore(first))||last!=null&&(d==null||d.isAfter(last)))continue;shown.add(item);}
        List<T> values=List.copyOf(shown);list.setModel(new AbstractListModel<>(){public int getSize(){return values.size();}public T getElementAt(int i){return values.get(i);}});
        if(selectedId!=null)for(int i=0;i<values.size();i++)if(java.util.Objects.equals(selectedId,identity.apply(values.get(i)))){list.setSelectedIndex(i);break;}
        updateActions();
        status.setForeground(Theme.SECONDARY);status.setText(I18n.text("显示 ")+shown.size()+I18n.text(" 条目"));
    }
}
