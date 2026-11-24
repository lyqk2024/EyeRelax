package com.eye.ui.theme;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.plaf.metal.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** A presentation-only Swing design system. It owns no application or persisted state. */
public final class Theme {
    public static final Color BG = new Color(0xF5F6F4);
    public static final Color PAGE = new Color(0xF0F2EF);
    public static final Color GROUP = new Color(0xF6F7F5);
    public static final Color SURFACE = new Color(0xFFFFFF);
    public static final Color INK = new Color(0x233C35);
    public static final Color SECONDARY = new Color(0x687970);
    public static final Color MUTED = new Color(0x849087);
    public static final Color LINE = new Color(0xE2E7E1);
    public static final Color GREEN = new Color(0x285B4A);
    public static final Color GREEN_HOVER = new Color(0x34715D);
    public static final Color DARK = new Color(0x173F34);
    public static final Color MINT = new Color(0xE9F1E9);
    public static final Color SAGE = new Color(0xACC3A4);
    public static final Color AMBER = new Color(0x98662D);
    public static final Color SAND = new Color(0xF5EEDB);
    public static final Color DANGER = new Color(0xA94D43);
    public static final Color DANGER_BG = new Color(0xFAEFEB);
    public static final Color SIDEBAR = new Color(0xEDF0EC);

    private static final String FAMILY = selectFamily("Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Dialog");
    private static final String NUMBER_FAMILY = selectFamily("Segoe UI", "Helvetica Neue", "DejaVu Sans", "Dialog");
    private Theme() {}

    private static String selectFamily(String... choices) {
        Set<String> available = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String choice : choices) if (available.contains(choice)) return choice;
        return Font.DIALOG;
    }
    public static Font font(float size) { return new Font(FAMILY, Font.PLAIN, Math.round(size)); }
    public static Font bold(float size) { return new Font(FAMILY, Font.BOLD, Math.round(size)); }
    public static Font numbers(float size) { return new Font(NUMBER_FAMILY, Font.PLAIN, Math.round(size)); }

    /** Called once, before constructing the existing application. No new runtime dependency. */
    public static void install() {
        try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
        catch (UnsupportedLookAndFeelException ignored) { /* existing LAF remains available */ }
        FontUIResource base = new FontUIResource(font(13));
        for (Object key : UIManager.getDefaults().keySet().toArray()) {
            if (key.toString().endsWith(".font")) UIManager.put(key, base);
        }
        String[] surfaces = {"Panel.background", "OptionPane.background", "Viewport.background", "ScrollPane.background", "MenuBar.background", "ToolBar.background"};
        for (String key : surfaces) UIManager.put(key, new ColorUIResource(BG));
        String[] foregrounds = {"Label.foreground", "Button.foreground", "RadioButton.foreground", "CheckBox.foreground", "TextField.foreground", "ComboBox.foreground", "List.foreground"};
        for (String key : foregrounds) UIManager.put(key, new ColorUIResource(INK));
        UIManager.put("LabelUI", LabelUI.class.getName());
        UIManager.put("ButtonUI", ButtonUI.class.getName());
        UIManager.put("ToggleButtonUI", ToggleUI.class.getName());
        UIManager.put("TextFieldUI", FieldUI.class.getName());
        UIManager.put("ComboBoxUI", ComboUI.class.getName());
        UIManager.put("ScrollBarUI", ScrollUI.class.getName());
        UIManager.put("CheckBoxUI", CheckUI.class.getName());
        UIManager.put("RadioButtonUI", RadioUI.class.getName());
        UIManager.put("CheckBox.icon", new SelectionIcon(false));
        UIManager.put("RadioButton.icon", new SelectionIcon(true));
        UIManager.put("CheckBox.background", new ColorUIResource(BG));
        UIManager.put("RadioButton.background", new ColorUIResource(BG));
        UIManager.put("Button.border", new EmptyBorder(9, 16, 9, 16));
        UIManager.put("ToggleButton.border", new EmptyBorder(8, 10, 8, 10));
        UIManager.put("Button.margin", new InsetsUIResource(2, 4, 2, 4));
        UIManager.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);
        UIManager.put("Button.disabledText", new ColorUIResource(MUTED));
        UIManager.put("Label.disabledForeground", new ColorUIResource(MUTED));
        UIManager.put("TextField.border", new FieldBorder());
        UIManager.put("TextField.background", new ColorUIResource(SURFACE));
        UIManager.put("TextField.inactiveBackground", new ColorUIResource(new Color(0xEFF0EB)));
        UIManager.put("TextField.inactiveForeground", new ColorUIResource(MUTED));
        UIManager.put("TextField.selectionBackground", new ColorUIResource(new Color(0xCFE1D3)));
        UIManager.put("TextField.selectionForeground", new ColorUIResource(INK));
        UIManager.put("ComboBox.border", new FieldBorder());
        UIManager.put("ComboBox.background", new ColorUIResource(SURFACE));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(MINT));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(GREEN));
        UIManager.put("List.background", new ColorUIResource(SURFACE));
        UIManager.put("List.selectionBackground", new ColorUIResource(MINT));
        UIManager.put("List.selectionForeground", new ColorUIResource(GREEN));
        UIManager.put("MenuItem.background", new ColorUIResource(SURFACE));
        UIManager.put("MenuItem.foreground", new ColorUIResource(INK));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(MINT));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(GREEN));
        UIManager.put("MenuItem.disabledForeground", new ColorUIResource(MUTED));
        UIManager.put("MenuItem.border", new EmptyBorder(8,12,8,12));
        UIManager.put("PopupMenu.background", new ColorUIResource(SURFACE));
        UIManager.put("PopupMenu.border", new CompoundBorder(new LineBorder(LINE),new EmptyBorder(5,5,5,5)));
        UIManager.put("ScrollPane.border", new EmptyBorder(0, 0, 0, 0));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Separator.foreground", new ColorUIResource(LINE));
        UIManager.put("ToolTip.background", new ColorUIResource(DARK));
        UIManager.put("ToolTip.foreground", new ColorUIResource(Color.WHITE));
        UIManager.put("ToolTip.border", new EmptyBorder(6, 10, 6, 10));
        UIManager.put("OptionPane.border", new EmptyBorder(24, 24, 20, 24));
        UIManager.put("OptionPane.messageAreaBorder", new EmptyBorder(0, 0, 6, 0));
        UIManager.put("OptionPane.buttonAreaBorder", new EmptyBorder(20, 0, 0, 0));
        UIManager.put("OptionPane.minimumSize", new DimensionUIResource(410, 145));
        UIManager.put("OptionPane.buttonPadding", 10);
        UIManager.put("OptionPane.okButtonText", com.eye.i18n.I18n.text("确定"));
        UIManager.put("OptionPane.cancelButtonText", com.eye.i18n.I18n.text("取消"));
        UIManager.put("OptionPane.yesButtonText", com.eye.i18n.I18n.text("是"));
        UIManager.put("OptionPane.noButtonText", com.eye.i18n.I18n.text("否"));
        UIManager.put("OptionPane.sameSizeButtons", Boolean.FALSE);
        UIManager.put("OptionPane.messageForeground", new ColorUIResource(INK));
        UIManager.put("OptionPane.informationIcon", Icons.of("info", 32, GREEN));
        UIManager.put("OptionPane.questionIcon", Icons.of("eye", 34, GREEN));
        UIManager.put("OptionPane.warningIcon", Icons.of("warning", 32, AMBER));
        UIManager.put("OptionPane.errorIcon", Icons.of("warning", 32, DANGER));
    }

    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
    public static Color alpha(Color color, int alpha) { return new Color(color.getRed(),color.getGreen(),color.getBlue(),alpha); }

    public static JPanel panel(LayoutManager layout) { JPanel p = new JPanel(layout); p.setOpaque(false); return p; }
    public static JPanel column() { JPanel p = panel(null); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    public static JLabel label(String text, float size, Color color) {
        JLabel l = new JLabel(text); l.setFont(font(size)); l.setForeground(color); return l;
    }
    public static JLabel heading(String text, float size) {
        JLabel l = label(text,size,INK); l.setFont(bold(size)); return l;
    }
    public static JLabel eyebrow(String text) { JLabel l=label(text,10,SECONDARY); l.setFont(numbers(10)); return l; }

    public static void button(AbstractButton b, String variant, String icon) {
        b.putClientProperty("rest.variant",variant);
        b.setOpaque(false); b.setContentAreaFilled(false); b.setRolloverEnabled(true);
        b.setBorder(new EmptyBorder(10,16,10,16)); b.setFont(font(13)); b.setIconTextGap(9);
        boolean dark = "primary".equals(variant) || "dark".equals(variant);
        b.setForeground(dark ? Color.WHITE : "danger".equals(variant) ? DANGER : INK);
        if (icon != null) b.setIcon(Icons.of(icon,17,null));
    }
    public static void smallButton(AbstractButton b, String variant, String icon) {
        button(b,variant,icon); b.setBorder(new EmptyBorder(7,12,7,12)); b.setFont(font(12));
        if (icon != null) b.setIcon(Icons.of(icon,15,null));
    }
    public static void navigation(JButton b, String icon) {
        button(b,"nav",icon); b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(13,15,13,15));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
    public static JLabel badge(String text, Color ink, Color fill) {
        JLabel l = new JLabel(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create(); quality(g); g.setColor(fill);
                g.fill(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(),getHeight(),getHeight()));
                g.dispose(); super.paintComponent(graphics);
            }
        };
        l.setFont(font(11)); l.setForeground(ink); l.setBorder(new EmptyBorder(5,10,5,10)); return l;
    }
    public static JPanel brand() {
        JPanel p=panel(new BorderLayout(10,0));
        JLabel mark=new JLabel(Icons.appIcon(38));
        p.add(mark,BorderLayout.WEST);
        JPanel words=column();
        JLabel name=heading("EyeRelax",18); name.setFont(numbers(18).deriveFont(Font.BOLD));
        words.add(name); words.add(label(com.eye.i18n.I18n.text("护眼计时器"),10,SECONDARY));
        p.add(words,BorderLayout.CENTER); p.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        return p;
    }
    public static void scroll(JScrollPane scroll) {
        scroll.setBorder(new EmptyBorder(0,0,0,0)); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
    }
    public static void window(Window w) {
        java.util.List<Image> icons = new java.util.ArrayList<>();
        for (int size : new int[]{16,24,32,48,64,128,256}) icons.add(Icons.appImage(size));
        w.setIconImages(icons);
    }
    public static void fitInitialWindow(Window w,int width,int height) {
        Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        w.setSize(Math.min(width,Math.max(360,screen.width-32)),Math.min(height,Math.max(300,screen.height-48)));
    }

    /** Scroll only below the design's minimum canvas; do not crop controls on smaller desktops. */
    public static final class ResponsiveCanvas extends JPanel implements Scrollable {
        private final Dimension canvas;
        public ResponsiveCanvas(LayoutManager layout,int width,int height) {
            super(layout);canvas=new Dimension(width,height);
        }
        @Override public Dimension getPreferredSize() { return new Dimension(canvas); }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(canvas); }
        @Override public int getScrollableUnitIncrement(Rectangle r,int axis,int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle r,int axis,int direction) { return Math.max(20,(axis==SwingConstants.VERTICAL?r.height:r.width)-40); }
        @Override public boolean getScrollableTracksViewportWidth() { return getParent()!=null&&getParent().getWidth()>=canvas.width; }
        @Override public boolean getScrollableTracksViewportHeight() { return getParent()!=null&&getParent().getHeight()>=canvas.height; }
    }
    public static void field(JTextField field) { field.setFont(font(14)); field.setColumns(15); }
    public static String escape(String s) {
        return s==null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    /** Rounded surface; borders and shadow are entirely local to this component. */
    public static class Surface extends JPanel {
        private final Color fill;
        private Color outline;
        private final int radius;
        public Surface(LayoutManager layout, Color fill, Color outline, int radius) {
            super(layout); this.fill=fill; this.outline=outline; this.radius=radius; setOpaque(false);
        }
        public void setOutline(Color outline) { this.outline=outline; repaint(); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g=(Graphics2D)graphics.create(); quality(g);
            double arc=radius*ProportionalPanel.scaleOf(this);
            g.setColor(fill); g.fill(new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,arc,arc));
            if(outline!=null) { g.setColor(outline); g.draw(new RoundRectangle2D.Double(.5,.5,getWidth()-1,getHeight()-1,arc,arc)); }
            g.dispose(); super.paintComponent(graphics);
        }
    }
    public static Surface card(LayoutManager layout) { return new Surface(layout,SURFACE,LINE,18); }

    private static String variant(AbstractButton b) {
        Object value=b.getClientProperty("rest.variant");
        if(value!=null) return value.toString();
        if(b.getRootPane()!=null && b.getRootPane().getDefaultButton()==b) return "primary";
        return "secondary";
    }

    /** Standard AbstractButton models, listeners and keyboard actions remain in charge. */
    private static void buttonBackground(Graphics graphics, AbstractButton b) {
        Graphics2D g=(Graphics2D)graphics.create(); quality(g);
        String v=variant(b);
        boolean selected=b.getModel().isSelected() || Boolean.TRUE.equals(b.getClientProperty("rest.selected"));
        boolean down=b.getModel().isPressed()&&b.getModel().isArmed();
        boolean hover=b.getModel().isRollover();
        boolean enabled=b.isEnabled();
        Color fill=SURFACE, border=LINE;
        if("primary".equals(v)) { fill=down ? DARK : hover ? GREEN_HOVER : GREEN; border=fill; }
        else if("dark".equals(v)) { fill=hover ? new Color(0x3E6556):new Color(0x305346); border=fill; }
        else if("nav".equals(v)) { fill=selected ? new Color(0xDBE8DE) : hover ? new Color(0xE5EBE4) : null; border=null; }
        else if("segment".equals(v)) { fill=selected ? SURFACE : hover ? new Color(0xE8EEE7) : null; border=selected ? LINE : null; }
        else if("ghost".equals(v)) { fill=hover?MINT:null; border=null; }
        else if("danger".equals(v)) { fill=hover?DANGER_BG:null; border=null; }
        else if("calendar".equals(v)) { fill=selected?GREEN:hover?MINT:SURFACE; border=Boolean.TRUE.equals(b.getClientProperty("rest.today"))?SAGE:null; }
        else if(selected) { fill=MINT; border=SAGE; }
        if(!enabled) { fill="primary".equals(v)?new Color(0xE4E9E2):"calendar".equals(v)?null:new Color(0xF0F1ED); border=null; }
        if(down && enabled && !"primary".equals(v))
            fill="dark".equals(v)?DARK:"calendar".equals(v)&&selected?GREEN:new Color(0xDCE8DC);
        double scale=ProportionalPanel.scaleOf(b),arc=12*scale;
        Shape shape=new RoundRectangle2D.Double(1,1,b.getWidth()-2,b.getHeight()-2,arc,arc);
        if(enabled&&("primary".equals(v)||"segment".equals(v)&&selected)&&!down){
            g.setColor(alpha(DARK,14));g.fill(new RoundRectangle2D.Double(1,2,b.getWidth()-2,b.getHeight()-3,arc,arc));
        }
        if(fill!=null) {
            if("primary".equals(v)&&enabled&&!down)g.setPaint(new GradientPaint(0,0,hover?GREEN_HOVER:new Color(0x326C58),0,b.getHeight(),fill));
            else g.setColor(fill);
            g.fill(shape);
        }
        if(border!=null) { g.setColor(border); g.draw(new RoundRectangle2D.Double(1,1,b.getWidth()-2,b.getHeight()-2,arc,arc)); }
        if(enabled&&"primary".equals(v)&&!down){g.setColor(alpha(Color.WHITE,28));g.draw(new RoundRectangle2D.Double(2,2,b.getWidth()-4,b.getHeight()-4,arc-2,arc-2));}
        if(b.isFocusPainted()&&b.hasFocus()) { g.setColor(SAGE); g.setStroke(new BasicStroke(2)); g.draw(new RoundRectangle2D.Double(2,2,b.getWidth()-4,b.getHeight()-4,arc,arc)); }
        if("nav".equals(v)&&selected) { g.setColor(GREEN); g.fillRoundRect(1,b.getHeight()/2-9,3,18,3,3); }
        g.dispose();
    }
    private static void buttonText(Graphics g, AbstractButton b, Rectangle rect, String text) {
        Graphics2D copy=(Graphics2D)g.create(); quality(copy);
        String v=variant(b);
        boolean sel=b.getModel().isSelected();
        Color ink=b.getForeground();
        if("primary".equals(v)||"dark".equals(v)) ink=Color.WHITE;
        if("calendar".equals(v)&&sel) ink=Color.WHITE;
        if(!b.isEnabled()) ink=MUTED;
        copy.setColor(ink); copy.setFont(b.getFont());
        BasicGraphicsUtils.drawStringUnderlineCharAt(copy,text,b.getDisplayedMnemonicIndex(),rect.x,rect.y+copy.getFontMetrics().getAscent());
        copy.dispose();
    }
    public static class LabelUI extends BasicLabelUI {
        public static ComponentUI createUI(JComponent c) { return new LabelUI(); }
        @Override public void paint(Graphics graphics,JComponent c) {
            Graphics2D g=(Graphics2D)graphics.create();quality(g);super.paint(g,c);g.dispose();
        }
    }
    public static class ButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) { return new ButtonUI(); }
        @Override public void installUI(JComponent c) { super.installUI(c); c.setOpaque(false); ((AbstractButton)c).setRolloverEnabled(true); }
        @Override public void paint(Graphics g,JComponent c) { buttonBackground(g,(AbstractButton)c); super.paint(g,c); }
        @Override protected void paintButtonPressed(Graphics g,AbstractButton b) {}
        @Override protected void paintText(Graphics g,AbstractButton b,Rectangle r,String t) { buttonText(g,b,r,t); }
        @Override protected void paintFocus(Graphics g,AbstractButton b,Rectangle v,Rectangle t,Rectangle i) {}
    }
    public static class ToggleUI extends BasicToggleButtonUI {
        public static ComponentUI createUI(JComponent c) { return new ToggleUI(); }
        @Override public void installUI(JComponent c) { super.installUI(c); c.setOpaque(false); ((AbstractButton)c).setRolloverEnabled(true); }
        @Override public void paint(Graphics g,JComponent c) { buttonBackground(g,(AbstractButton)c); super.paint(g,c); }
        @Override protected void paintButtonPressed(Graphics g,AbstractButton b) {}
        @Override protected void paintText(Graphics g,AbstractButton b,Rectangle r,String t) { buttonText(g,b,r,t); }
        @Override protected void paintFocus(Graphics g,AbstractButton b,Rectangle v,Rectangle t,Rectangle i) {}
    }
    private static final class SelectionIcon implements Icon, UIResource {
        private final boolean radio;
        private SelectionIcon(boolean radio) { this.radio=radio; }
        @Override public int getIconWidth(){return 22;}
        @Override public int getIconHeight(){return 22;}
        @Override public void paintIcon(Component c,Graphics graphics,int x,int y) {
            AbstractButton b=(AbstractButton)c; Graphics2D g=(Graphics2D)graphics.create(); quality(g); g.translate(x,y);
            boolean selected=b.isSelected();
            g.setColor(!b.isEnabled()?LINE:selected?GREEN:SURFACE);
            Shape shape=radio?new Ellipse2D.Double(2,2,17,17):new RoundRectangle2D.Double(2,2,17,17,5,5);
            g.fill(shape); g.setColor(selected?GREEN:b.hasFocus()?GREEN:SAGE); g.draw(shape);
            if(selected) {
                g.setColor(Color.WHITE);
                if(radio) g.fill(new Ellipse2D.Double(7,7,7,7));
                else { g.setStroke(new BasicStroke(1.9f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); Path2D p=new Path2D.Double();p.moveTo(6,10);p.lineTo(9,14);p.lineTo(15,7);g.draw(p); }
            }
            if(b.hasFocus()) { g.setColor(alpha(GREEN,100));g.draw(new RoundRectangle2D.Double(.5,.5,21,21,radio?21:7,radio?21:7)); }
            g.dispose();
        }
    }
    public static class CheckUI extends BasicCheckBoxUI {
        public static ComponentUI createUI(JComponent c){return new CheckUI();}
        @Override public void installUI(JComponent c){super.installUI(c);c.setOpaque(false);}
        @Override public synchronized void paint(Graphics graphics,JComponent c) {
            Graphics2D g=(Graphics2D)graphics.create();quality(g);super.paint(g,c);g.dispose();
        }
        @Override protected void paintFocus(Graphics g,Rectangle r,Dimension d){}
    }
    public static class RadioUI extends BasicRadioButtonUI {
        public static ComponentUI createUI(JComponent c){return new RadioUI();}
        @Override public void installUI(JComponent c){super.installUI(c);c.setOpaque(false);}
        @Override public synchronized void paint(Graphics graphics,JComponent c) {
            Graphics2D g=(Graphics2D)graphics.create();quality(g);super.paint(g,c);g.dispose();
        }
        @Override protected void paintFocus(Graphics g,Rectangle r,Dimension d){}
    }
    private static final class FieldBorder extends AbstractBorder implements UIResource {
        @Override public Insets getBorderInsets(Component c){return new Insets(9,12,9,12);}
        @Override public Insets getBorderInsets(Component c,Insets i){i.set(9,12,9,12);return i;}
        @Override public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){
            Graphics2D g=(Graphics2D)graphics.create();quality(g);
            boolean focused=c.isFocusOwner();
            Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if(c instanceof Container && focus!=null) focused|=SwingUtilities.isDescendingFrom(focus,c);
            g.setColor(focused?GREEN:LINE);g.setStroke(new BasicStroke(focused?1.6f:1));
            g.draw(new RoundRectangle2D.Double(x+1,y+1,w-3,h-3,10,10));g.dispose();
        }
    }
    public static class FieldUI extends BasicTextFieldUI {
        public static ComponentUI createUI(JComponent c){return new FieldUI();}
        @Override protected void paintSafely(Graphics graphics) {
            Graphics2D g=(Graphics2D)graphics.create();quality(g);
            paintBackground(g);super.paintSafely(g);g.dispose();
        }
        @Override protected void paintBackground(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();quality(g);JComponent c=getComponent();
            g.setColor(c.isEnabled()?SURFACE:new Color(0xEEF0E9));g.fill(new RoundRectangle2D.Double(0,0,c.getWidth(),c.getHeight(),10,10));g.dispose();
        }
        @Override public void installUI(JComponent c){super.installUI(c);c.setOpaque(false);}
    }
    public static class ComboUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c){return new ComboUI();}
        @Override public void installUI(JComponent c){super.installUI(c);c.setOpaque(false);}
        @Override protected JButton createArrowButton(){
            JButton b=new JButton(Icons.of("down",14,SECONDARY));button(b,"ghost",null);b.setBorder(new EmptyBorder(0,4,0,4));b.setFocusable(false);return b;
        }
        @Override public void paint(Graphics graphics,JComponent c){
            Graphics2D g=(Graphics2D)graphics.create();quality(g);g.setColor(c.isEnabled()?SURFACE:new Color(0xEEF0E9));
            g.fill(new RoundRectangle2D.Double(0,0,c.getWidth(),c.getHeight(),10,10));g.dispose();super.paint(graphics,c);
        }
        @Override public void paintCurrentValueBackground(Graphics g,Rectangle bounds,boolean focus){}
        @Override protected ListCellRenderer<Object> createRenderer(){
            return new DefaultListCellRenderer(){
                @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                    super.getListCellRendererComponent(list,value,index,selected,focus);setBorder(new EmptyBorder(index<0?0:9,0,index<0?0:9,8));
                    setForeground(!comboBox.isEnabled()?MUTED:INK);setBackground(selected?MINT:SURFACE);setOpaque(index>=0);return this;
                }
            };
        }
    }
    public static class ScrollUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c){return new ScrollUI();}
        @Override protected void configureScrollBarColors(){trackColor=BG;thumbColor=new Color(0xC8D2C6);}
        @Override protected JButton createDecreaseButton(int o){return zeroButton();}
        @Override protected JButton createIncreaseButton(int o){return zeroButton();}
        private JButton zeroButton(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));b.setMaximumSize(new Dimension(0,0));return b;}
        @Override protected void paintTrack(Graphics g,JComponent c,Rectangle r){}
        @Override protected void paintThumb(Graphics graphics,JComponent c,Rectangle r){
            if(r.isEmpty()||!scrollbar.isEnabled())return;Graphics2D g=(Graphics2D)graphics.create();quality(g);g.setColor(isThumbRollover()?SAGE:new Color(0xCDD5C9));
            g.fillRoundRect(r.x+2,r.y+2,Math.max(4,r.width-4),Math.max(4,r.height-4),8,8);g.dispose();
        }
    }
}
