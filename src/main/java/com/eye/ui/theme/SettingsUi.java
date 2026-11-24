package com.eye.ui.theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.font.*;
import java.awt.geom.RoundRectangle2D;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

/** Presentation-only settings primitives; owns no configuration, timers or persistence. */
public final class SettingsUi {
    private SettingsUi() {}

    /** A left category list with separately scrolling pages and a stationary save bar. */
    public static final class View extends JPanel {
        private final JPanel nav = Theme.column();
        private final JPanel host = new JPanel(new CardLayout());
        private final List<JButton> buttons = new ArrayList<>();
        private String selected;
        private final JPanel side = Theme.panel(new BorderLayout());
        private final JPanel heading = Theme.panel(new BorderLayout(12, 0));
        public View(String title, String subtitle, JLabel status, JButton save) {
            super(new BorderLayout(0, 18)); setOpaque(false);
            setBorder(new EmptyBorder(24, 28, 24, 28));

            JPanel copy = Theme.column();
            copy.add(Theme.heading(title, 24));
            if (subtitle != null && !subtitle.isBlank()) {
                copy.add(Box.createVerticalStrut(5));
                copy.add(new Text(subtitle, 12, Theme.SECONDARY));
            }
            heading.add(copy); add(heading, BorderLayout.NORTH);
            Theme.Surface shell = new Theme.Surface(new BorderLayout(), Theme.GROUP, Theme.LINE, 18);
            shell.setBorder(new EmptyBorder(1, 1, 1, 1));
            nav.setBorder(new EmptyBorder(18, 10, 16, 10));
            side.setPreferredSize(new Dimension(150, 0));
            side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE));
            JScrollPane navScroll = new JScrollPane(nav); Theme.scroll(navScroll);
            navScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            side.add(navScroll, BorderLayout.CENTER);
            shell.add(side, BorderLayout.WEST);
            host.setOpaque(false); shell.add(host, BorderLayout.CENTER);
            add(shell, BorderLayout.CENTER);
            JPanel footer = Theme.panel(new BorderLayout(12, 0));
            footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,Theme.LINE),new EmptyBorder(14,0,0,0)));
            status.setFont(Theme.font(11));
            status.setMinimumSize(new Dimension(0, 30));
            footer.add(status, BorderLayout.CENTER);
            JPanel right = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); right.add(save);
            footer.add(right, BorderLayout.EAST); add(footer, BorderLayout.SOUTH);
        }
        public void section(String id, String name, String icon, String subtitle, JComponent... groups) {
            JButton b = new JButton(name); Theme.navigation(b, icon);
            b.setFont(Theme.font(12)); b.setBorder(new EmptyBorder(11, 12, 11, 8));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            b.setActionCommand(id); b.getAccessibleContext().setAccessibleName(name);
            // Give translated navigation labels their measured width. Clipping here hides
            // the category distinction; widening only this rail preserves the page layout.
            int needed=b.getPreferredSize().width+22;
            if(needed>side.getPreferredSize().width)side.setPreferredSize(new Dimension(needed,0));
            b.addActionListener(e -> select(id)); buttons.add(b);
            nav.add(b); nav.add(Box.createVerticalStrut(5));
            Column content = new Column(); content.setBorder(new EmptyBorder(22, 24, 20, 24));
            JLabel title = Theme.heading(name, 18); title.setAlignmentX(LEFT_ALIGNMENT);
            content.add(title);
            if (subtitle != null && !subtitle.isBlank()) {
                content.add(Box.createVerticalStrut(6));
                Text sub = new Text(subtitle, 11, Theme.SECONDARY);
                sub.setAlignmentX(LEFT_ALIGNMENT);
                content.add(sub);
            }
            content.add(Box.createVerticalStrut(16));
            for (JComponent group : groups) { group.setAlignmentX(LEFT_ALIGNMENT); content.add(group); content.add(Box.createVerticalStrut(16)); }
            JScrollPane scroll = new JScrollPane(content); Theme.scroll(scroll);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            host.add(scroll, id);
            if (selected == null) select(id);
        }
        private java.util.function.Consumer<String> selectionListener=id->{};
        public void setSelectionListener(java.util.function.Consumer<String> listener){selectionListener=listener;}
        public void select(String id) {
            if (buttons.stream().noneMatch(b -> id.equals(b.getActionCommand()))) return;
            selected = id; ((CardLayout) host.getLayout()).show(host, id);
            for (JButton b : buttons) {
                boolean active = id.equals(b.getActionCommand());
                b.putClientProperty("rest.selected", active); ProportionalPanel.setBaseFont(b,active ? Theme.bold(12) : Theme.font(12)); b.repaint();
            }
            selectionListener.accept(id);
        }
        public String selected() { return selected; }
        public JComponent selectedContent(){for(Component c:host.getComponents())if(c.isVisible()&&c instanceof JComponent j)return j;return host;}
        public void divider(String text) {
            if (nav.getComponentCount() > 0) nav.add(Box.createVerticalStrut(10));
            JLabel label = Theme.label(text, 10, Theme.SECONDARY);
            label.setBorder(new EmptyBorder(5, 12, 8, 0)); label.setAlignmentX(LEFT_ALIGNMENT);
            nav.add(label);
        }
        public void backAction(Runnable action) {
            JButton back = iconButton("back", com.eye.i18n.I18n.text("返回工作区"), "ghost");
            back.setText("‹"); back.setIcon(null); back.setFont(Theme.font(25));
            back.addActionListener(e -> action.run()); heading.add(back, BorderLayout.WEST);
        }
    }

    public static JPanel group(String title, JComponent... rows) {
        Theme.Surface group = new Theme.Surface(new BorderLayout(0, 3), Theme.SURFACE, Theme.LINE, 12) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        group.setBorder(new EmptyBorder(12, 16, 8, 16));
        JLabel label = Theme.heading(title, 12); label.setBorder(new EmptyBorder(0, 0, 4, 0));
        group.add(label, BorderLayout.NORTH);
        JPanel items = Theme.column();
        for (int i = 0; i < rows.length; i++) {
            rows[i].putClientProperty("tutorial.region",group); rows[i].setAlignmentX(Component.LEFT_ALIGNMENT); items.add(rows[i]);
            if (i < rows.length - 1) {
                JSeparator line = new JSeparator(); line.setForeground(Theme.LINE);
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1)); items.add(line);
            }
        }
        group.add(items, BorderLayout.CENTER); return group;
    }

    public static JPanel row(String name, String help, JComponent control) { return new Row(name, help, control); }
    private static final class Row extends JPanel {
        private final Text name, help;
        private final JComponent control;
        Row(String title, String detail, JComponent editor) {
            setOpaque(false); setLayout(null);
            name = new Text(title, 12, Theme.INK); help = new Text(detail, 10.5f, Theme.SECONDARY);
            control = editor; editor.putClientProperty("tutorial.region",this); add(name); add(help); add(control);
            if (editor.getAccessibleContext() != null) editor.getAccessibleContext().setAccessibleName(title);
        }
        private int width() { return getWidth() > 0 ? getWidth() : 560; }
        private int controlWidth(int w) { return Math.min(control.getPreferredSize().width, Math.max(80, w)); }
        private boolean stacked(int w) { return w - controlWidth(w) - 18 < 145; }
        private int copyHeight(int w) { return name.heightFor(w) + (help.getText().isBlank() ? 0 : 4 + help.heightFor(w)); }
        @Override public Dimension getPreferredSize() {
            int w = width(), cw = controlWidth(w), h;
            if (stacked(w)) h = copyHeight(w) + 9 + control.getPreferredSize().height;
            else h = Math.max(copyHeight(w - cw - 18), control.getPreferredSize().height);
            return new Dimension(360, h + 18);
        }
        @Override public void setBounds(int x, int y, int width, int height) {
            boolean changed = width != getWidth(); super.setBounds(x, y, width, height);
            if (changed) SwingUtilities.invokeLater(this::revalidate);
        }
        @Override public Dimension getMinimumSize() { return new Dimension(160, getPreferredSize().height); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public void doLayout() {
            int w = getWidth(), cw = controlWidth(w), ch = control.getPreferredSize().height;
            boolean stack = stacked(w); int tw = stack ? w : w - cw - 18;
            int nh = name.heightFor(tw), hh = help.getText().isBlank() ? 0 : help.heightFor(tw);
            name.setBounds(0, 9, tw, nh); help.setBounds(0, 9 + nh + 4, tw, hh);
            control.setBounds(stack ? 0 : w - cw, stack ? 9 + copyHeight(w) + 9 : Math.max(9, (getHeight() - ch) / 2), cw, ch);
        }
    }

    public static void combo(JComboBox<?> box, int width) {
        box.setFont(Theme.font(12)); box.setPreferredSize(new Dimension(width, 36));
        box.setMinimumSize(new Dimension(130, 36)); box.setMaximumRowCount(8);
    }
    public static void valueButton(JButton button, String icon) {
        Theme.smallButton(button, "secondary", icon); button.setPreferredSize(new Dimension(138, 34));
    }
    /** Keeps the original JCheckBox/model/listeners and changes only its rendering. */
    public static void toggle(JCheckBox box, String accessibleName) {
        box.setText(""); box.setOpaque(false); box.setBorder(new EmptyBorder(2, 2, 2, 2));
        box.setIcon(new SwitchIcon()); box.setSelectedIcon(new SwitchIcon());
        box.setPreferredSize(new Dimension(48, 28)); box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.getAccessibleContext().setAccessibleName(accessibleName);
    }
    private static final class SwitchIcon implements Icon {
        public int getIconWidth() { return 42; } public int getIconHeight() { return 24; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            AbstractButton b = (AbstractButton) c; Graphics2D g = (Graphics2D) graphics.create(); Theme.quality(g);
            boolean on = b.isSelected();
            g.setColor(!b.isEnabled() ? Theme.LINE : on ? Theme.GREEN : new Color(0xAAB5AD));
            g.fill(new RoundRectangle2D.Double(x, y + 1, 42, 22, 22, 22));
            g.setColor(Color.WHITE); g.fillOval(x + (on ? 23 : 3), y + 4, 16, 16);
            if (b.hasFocus()) { g.setColor(Theme.SAGE); g.drawRoundRect(x - 1, y, 44, 24, 24, 24); }
            g.dispose();
        }
    }
    public static JButton iconButton(String icon, String help, String variant) {
        JButton b = new JButton(); Theme.smallButton(b, variant, icon);
        b.setBorder(new EmptyBorder(7, 7, 7, 7)); b.setPreferredSize(new Dimension(32, 32));
        b.setToolTipText(null); b.getAccessibleContext().setAccessibleName(help); return b;
    }

    public static final class PlaceholderField extends JTextField {
        private final String hint;
        public PlaceholderField(String hint) { this.hint = hint; }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty() || hasFocus()) return;
            Graphics2D g = (Graphics2D) graphics.create(); Theme.quality(g);
            g.setFont(getFont()); g.setColor(Theme.MUTED);
            Insets i = getInsets(); FontMetrics fm = g.getFontMetrics();
            g.drawString(hint, i.left + 1, (getHeight() - fm.getHeight()) / 2 + fm.getAscent()); g.dispose();
        }
    }

    public static void resizeDialogToContent(JComponent content) {
        Window w = SwingUtilities.getWindowAncestor(content);
        if (!(w instanceof JDialog)) return;
        Rectangle screen = w.getGraphicsConfiguration().getBounds();
        Insets i = Toolkit.getDefaultToolkit().getScreenInsets(w.getGraphicsConfiguration());
        int available = screen.height - i.top - i.bottom - 60;
        int h = Math.min(Math.max(304, content.getPreferredSize().height + 200), Math.max(220, available));
        w.setSize(w.getWidth(), h);
        int bottom = screen.y + screen.height - i.bottom;
        if (w.getY() + h > bottom) w.setLocation(w.getX(), Math.max(screen.y + i.top, bottom - h - 10));
    }

    /** Wrapped secondary copy, measured at the actual available width (including high-DPI fonts). */
    public static final class Text extends JComponent {
        private final String text;
        public Text(String text, float size, Color color) {
            this.text = text == null ? "" : text; setFont(Theme.font(size)); setForeground(color);
            setOpaque(false); setAlignmentX(LEFT_ALIGNMENT);
        }
        public String getText() { return text; }
        @Override public void setBounds(int x, int y, int width, int height) {
            boolean heightChanged = heightFor(Math.max(20, getWidth())) != heightFor(Math.max(20, width));
            super.setBounds(x, y, width, height);
            if (heightChanged) SwingUtilities.invokeLater(this::revalidate);
        }
        private List<TextLayout> lines(int width) {
            ArrayList<TextLayout> result = new ArrayList<>();
            if (text.isEmpty()) return result;
            FontRenderContext context = new FontRenderContext(null, true, true);
            for (String part : text.split("\n", -1)) {
                AttributedString s = new AttributedString(part.isEmpty() ? " " : part);
                s.addAttribute(java.awt.font.TextAttribute.FONT, getFont());
                LineBreakMeasurer m = new LineBreakMeasurer(s.getIterator(), context);
                while (m.getPosition() < s.getIterator().getEndIndex()) result.add(m.nextLayout(Math.max(20, width)));
            }
            return result;
        }
        public int heightFor(int width) {
            int height = 0; for (TextLayout l : lines(width)) height += (int) Math.ceil(l.getAscent() + l.getDescent() + l.getLeading()) + 2;
            return height;
        }
        @Override public Dimension getPreferredSize() { return new Dimension(200, heightFor(getWidth() > 0 ? getWidth() : 560)); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); Theme.quality(g); g.setColor(getForeground());
            float y = 0;
            for (TextLayout l : lines(getWidth())) { y += l.getAscent(); l.draw(g, 0, y); y += l.getDescent() + l.getLeading() + 2; }
            g.dispose();
        }
    }

    /** A vertical stack whose width tracks the viewport, never shrinking controls vertically. */
    public static class Column extends JPanel implements Scrollable {
        public Column() { setOpaque(false); setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
        @Override protected void addImpl(Component comp, Object constraints, int index) {
            if (comp instanceof JComponent c) c.setAlignmentX(Component.LEFT_ALIGNMENT);
            super.addImpl(comp, constraints, index);
        }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(40, r.height - 40); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
        @Override public void setBounds(int x, int y, int width, int height) {
            boolean changed = width != getWidth(); super.setBounds(x, y, width, height);
            if (changed) SwingUtilities.invokeLater(() -> { revalidate(); repaint(); });
        }
    }

    /** Shared resizable, scroll-safe dialog; buttons remain visible on short screens. */
    public static boolean confirm(Component owner, String title, String subtitle, JComponent content, String okText) {
        return confirm(owner,title,subtitle,content,okText,()->true);
    }
    public static boolean confirm(Component owner, String title, String subtitle, JComponent content, String okText, java.util.function.BooleanSupplier validate) {
        return confirm(owner,title,subtitle,content,okText,validate,false);
    }
    public static boolean confirmForeground(Component owner,String title,String subtitle,JComponent content,String okText,java.util.function.BooleanSupplier validate){return confirm(owner,title,subtitle,content,okText,validate,true);}
    private static boolean confirm(Component owner,String title,String subtitle,JComponent content,String okText,java.util.function.BooleanSupplier validate,boolean foreground){
        Window parent = owner instanceof Window w ? w : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(parent, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); Theme.window(dialog);
        JPanel root = new JPanel(new BorderLayout(0, 16)); root.setBackground(Theme.SURFACE);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));
        JPanel head = Theme.panel(new BorderLayout(12, 0));
        head.add(new JLabel(Icons.of("settings", 24, Theme.GREEN)), BorderLayout.WEST);
        JPanel copy = Theme.column(); copy.add(Theme.heading(title, 19));
        if (subtitle != null && !subtitle.isBlank()) {
            copy.add(Box.createVerticalStrut(5));
            copy.add(new Text(subtitle, 11, Theme.SECONDARY));
        }
        head.add(copy); root.add(head, BorderLayout.NORTH);
        Column column = new Column(); content.setAlignmentX(Component.LEFT_ALIGNMENT); column.add(content);
        JScrollPane scroll = new JScrollPane(column); Theme.scroll(scroll);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); root.add(scroll, BorderLayout.CENTER);
        boolean[] accepted = {false};
        JButton cancel = new JButton(com.eye.i18n.I18n.text("取消")); Theme.smallButton(cancel, "secondary", null);
        JButton ok = new JButton(okText); Theme.smallButton(ok, "primary", "check");
        ok.addActionListener(e -> { InlineErrors.clear(content);if(!validate.getAsBoolean()){content.revalidate();dialog.revalidate();return;}accepted[0] = true; dialog.dispose(); }); cancel.addActionListener(e -> dialog.dispose());
        JPanel footer = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); footer.add(cancel); footer.add(ok); root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root); dialog.getRootPane().setDefaultButton(ok);
        root.registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle screen = parent != null && parent.getGraphicsConfiguration() != null
                ? parent.getGraphicsConfiguration().getBounds() : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (parent != null && parent.getGraphicsConfiguration() != null) {
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(parent.getGraphicsConfiguration());
            screen = new Rectangle(screen.x + insets.left, screen.y + insets.top,
                    screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom);
        }
        int width = Math.min(Math.max(490, content.getPreferredSize().width + 56), Math.max(300, screen.width - 40));
        int height = Math.min(Math.max(304, content.getPreferredSize().height + 200), Math.max(220, screen.height - 60));
        dialog.setSize(width, height); dialog.setMinimumSize(new Dimension(Math.min(420, width), Math.min(270, height)));
        dialog.setResizable(true); dialog.setLocationRelativeTo(parent);
        if(foreground)com.eye.ui.DialogPresentation.foreground(dialog);
        try{dialog.setVisible(true);}finally{dialog.dispose();}return accepted[0];
    }
}
