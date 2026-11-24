package com.eye.ui;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import javax.swing.*;
import javax.swing.plaf.LabelUI;

/** Plain report labels measured and painted with the same output font context.
 * Screen FontMetrics can be narrower than the glyphs painted into a 2x PNG.
 * This delegate is installed only on the detached export, never on live controls.
 */
final class ExportLabelUI extends LabelUI {
    private final FontRenderContext context;
    ExportLabelUI(FontRenderContext context) { this.context = context; }

    private record Text(TextLayout layout, float left, float ascent, int width, int height) {}
    private Text measure(JLabel label) {
        String value = label.getText();
        if (value == null || value.isEmpty()) return new Text(null, 0, 0, 0, 0);
        TextLayout layout = new TextLayout(value, label.getFont(), context);
        Rectangle2D bounds = layout.getBounds();
        float left = (float)Math.min(0, bounds.getMinX());
        float ascent = (float)Math.max(layout.getAscent(), -bounds.getMinY());
        int width = (int)Math.ceil(Math.max(layout.getAdvance(), bounds.getMaxX()) - left);
        int height = (int)Math.ceil(ascent + Math.max(layout.getDescent() + layout.getLeading(), bounds.getMaxY()));
        return new Text(layout, left, ascent, width, height);
    }

    @Override public Dimension getPreferredSize(JComponent component) {
        JLabel label = (JLabel)component;
        Text text = measure(label);
        Icon icon = label.getIcon();
        int iconWidth = icon == null ? 0 : icon.getIconWidth();
        int gap = icon != null && text.layout != null ? label.getIconTextGap() : 0;
        Insets in = label.getInsets();
        return new Dimension(in.left + in.right + iconWidth + gap + text.width + 4,
                in.top + in.bottom + Math.max(icon == null ? 0 : icon.getIconHeight(), text.height) + 4);
    }
    @Override public Dimension getMinimumSize(JComponent c) { return getPreferredSize(c); }
    @Override public Dimension getMaximumSize(JComponent c) { return getPreferredSize(c); }

    @Override public void paint(Graphics graphics, JComponent component) {
        JLabel label = (JLabel)component;
        Text text = measure(label);
        Icon icon = label.getIcon();
        Insets in = label.getInsets();
        int iconWidth = icon == null ? 0 : icon.getIconWidth();
        int gap = icon != null && text.layout != null ? label.getIconTextGap() : 0;
        int width = iconWidth + gap + text.width;
        int available = label.getWidth() - in.left - in.right - 4;
        int alignment = label.getHorizontalAlignment();
        if (alignment == SwingConstants.LEADING) alignment = label.getComponentOrientation().isLeftToRight() ? SwingConstants.LEFT : SwingConstants.RIGHT;
        if (alignment == SwingConstants.TRAILING) alignment = label.getComponentOrientation().isLeftToRight() ? SwingConstants.RIGHT : SwingConstants.LEFT;
        int x = in.left + 2 + (alignment == SwingConstants.RIGHT ? Math.max(0, available - width)
                : alignment == SwingConstants.CENTER ? Math.max(0, (available - width) / 2) : 0);
        int height = Math.max(icon == null ? 0 : icon.getIconHeight(), text.height);
        int availableHeight = label.getHeight() - in.top - in.bottom - 4;
        int y = in.top + 2 + (label.getVerticalAlignment() == SwingConstants.TOP ? 0
                : label.getVerticalAlignment() == SwingConstants.BOTTOM ? Math.max(0, availableHeight - height)
                : Math.max(0, (availableHeight - height) / 2));
        if (icon != null) icon.paintIcon(label, graphics, x, y + (height - icon.getIconHeight()) / 2);
        if (text.layout != null) {
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.setColor(label.getForeground());
                text.layout.draw(g, x + iconWidth + gap - text.left, y + (height - text.height) / 2 + text.ascent);
            } finally { g.dispose(); }
        }
    }
}
