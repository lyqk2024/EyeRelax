package com.eye.ui.theme;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Resolution-independent UI glyphs plus the packaged EyeRelax brand mark. */
public final class Icons {
    private static final BufferedImage APP_LOGO = loadAppLogo();

    private Icons() {}

    public static Icon of(String name, int size, Color color) {
        return new Glyph(name, size, color);
    }

    /** The exact same logo artwork is used by the EXE, window, tray, brand and status overlay. */
    public static Image appImage(int size) {
        int target = Math.max(1, size);
        if (APP_LOGO != null) {
            BufferedImage image = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            Theme.quality(g);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(APP_LOGO, 0, 0, target, target, null);
            g.dispose();
            return image;
        }
        return fallbackAppImage(target);
    }

    public static Icon appIcon(int size) {
        return new ImageIcon(appImage(size));
    }

    private static BufferedImage loadAppLogo() {
        try (InputStream in = Icons.class.getResourceAsStream("/com/eye/ui/logo.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Image fallbackAppImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Theme.quality(g);
        g.setColor(Theme.GREEN);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * .30, size * .30));
        int inset = Math.max(2, size / 6);
        of("eye", size - inset * 2, Color.WHITE).paintIcon(null, g, inset, inset);
        g.dispose();
        return image;
    }

    private static final class Glyph implements Icon {
        private final String name;
        private final int size;
        private final Color color;
        private Glyph(String name, int size, Color color) {
            this.name = name; this.size = size; this.color = color;
        }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.quality(g);
            g.translate(x, y);
            g.scale(size / 24.0, size / 24.0);
            Color ink = color != null ? color : (c == null ? Theme.GREEN : c.getForeground());
            if (c != null && !c.isEnabled()) ink = Theme.MUTED;
            g.setColor(ink);
            g.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (name) {
                case "eye" -> {
                    Path2D p = new Path2D.Double();
                    p.moveTo(2,12); p.curveTo(6,4,18,4,22,12); p.curveTo(18,20,6,20,2,12); g.draw(p);
                    g.draw(new Ellipse2D.Double(8.5,8.5,7,7));
                }
                case "timer" -> {
                    g.draw(new Ellipse2D.Double(4,5,16,16)); line(g,9,2,15,2); line(g,12,2,12,5);
                    line(g,12,9,12,13); line(g,12,13,15,15); line(g,19,4,21,6);
                }
                case "clock", "history" -> {
                    g.draw(new Arc2D.Double(3,3,18,18,40, name.equals("history") ? 290 : 360, Arc2D.OPEN));
                    line(g,12,7,12,12); line(g,12,12,16,14);
                    if (name.equals("history")) { line(g,3,3,3,8); line(g,3,8,8,8); }
                }
                case "book" -> {
                    Path2D book=new Path2D.Double();book.moveTo(12,6);book.curveTo(8,3,5,3,2,4);book.lineTo(2,20);book.curveTo(5,19,8,19,12,22);book.curveTo(16,19,19,19,22,20);book.lineTo(22,4);book.curveTo(19,3,16,3,12,6);book.closePath();g.draw(book);line(g,12,6,12,22);
                }
                case "message" -> {
                    Path2D box=new Path2D.Double();box.moveTo(4,3);box.lineTo(20,3);box.lineTo(20,17);box.lineTo(10,17);box.lineTo(4,22);box.closePath();g.draw(box);
                    line(g,8,8,16,8);line(g,8,12,14,12);
                }
                case "download" -> {
                    g.draw(new Line2D.Double(12,3,12,15));g.draw(new Line2D.Double(7,10,12,15));g.draw(new Line2D.Double(12,15,17,10));
                    g.draw(new Line2D.Double(4,17,4,21));g.draw(new Line2D.Double(4,21,20,21));g.draw(new Line2D.Double(20,21,20,17));
                }
                case "list" -> {
                    for(int lineY:new int[]{6,12,18}){g.fill(new Ellipse2D.Double(3,lineY-1,2,2));g.draw(new Line2D.Double(8,lineY,21,lineY));}
                }
                case "bell" -> {
                    Path2D p=new Path2D.Double(); p.moveTo(5,17); p.curveTo(7,15,7,13,7,10);
                    p.curveTo(7,3,17,3,17,10); p.curveTo(17,13,17,15,19,17); p.closePath(); g.draw(p);
                    line(g,12,2,12,4); g.draw(new Arc2D.Double(10,18,4,3,180,180,Arc2D.OPEN));
                }
                case "monitor" -> {
                    g.draw(new RoundRectangle2D.Double(2,4,20,14,2,2));
                    line(g,2,14,22,14); line(g,12,18,12,22); line(g,8,22,16,22);
                }
                case "calendar" -> {
                    g.draw(new RoundRectangle2D.Double(3,5,18,16,3,3)); line(g,3,10,21,10);
                    line(g,8,3,8,7); line(g,16,3,16,7);
                    for (int i=0;i<2;i++) for (int j=0;j<3;j++) g.fill(new Ellipse2D.Double(7+j*4,13+i*4,1.5,1.5));
                }
                case "folder" -> {
                    Path2D p=new Path2D.Double(); p.moveTo(3,7); p.lineTo(9,7); p.lineTo(11,5); p.lineTo(20,5);
                    p.curveTo(21,5,22,6,22,7); p.lineTo(22,18); p.curveTo(22,19,21,20,20,20);
                    p.lineTo(4,20); p.curveTo(3,20,2,19,2,18); p.lineTo(2,9); p.curveTo(2,8,2,7,3,7); g.draw(p);
                }
                case "language" -> {
                    g.draw(new Ellipse2D.Double(3,3,18,18));g.draw(new Ellipse2D.Double(8,3,8,18));line(g,3,12,21,12);
                }
                case "chart" -> {
                    line(g,4,4,4,20);line(g,4,20,21,20);
                    line(g,8,16,8,12);line(g,13,16,13,8);line(g,18,16,18,4);
                }
                case "target" -> {
                    g.draw(new Ellipse2D.Double(3,3,18,18));g.draw(new Ellipse2D.Double(7,7,10,10));
                    g.fill(new Ellipse2D.Double(10.5,10.5,3,3));
                }
                case "grid" -> {
                    for (int i=0;i<2;i++) for(int j=0;j<2;j++) g.draw(new RoundRectangle2D.Double(4+j*9,4+i*9,6,6,1.5,1.5));
                }
                case "settings" -> {
                    for (int i=0;i<8;i++) {
                        double a=i*Math.PI/4;
                        line(g,12+Math.cos(a)*8,12+Math.sin(a)*8,12+Math.cos(a)*10,12+Math.sin(a)*10);
                    }
                    g.draw(new Ellipse2D.Double(5,5,14,14)); g.draw(new Ellipse2D.Double(9,9,6,6));
                }
                case "play" -> {
                    Path2D p=new Path2D.Double(); p.moveTo(8,5); p.lineTo(19,12); p.lineTo(8,19); p.closePath(); g.fill(p);
                }
                case "pause" -> { g.fill(new RoundRectangle2D.Double(6,5,4,14,1.5,1.5)); g.fill(new RoundRectangle2D.Double(14,5,4,14,1.5,1.5)); }
                case "stop" -> g.fill(new RoundRectangle2D.Double(6,6,12,12,3,3));
                case "restart" -> {
                    g.draw(new Arc2D.Double(4,4,16,16,-35,290,Arc2D.OPEN)); line(g,3,3,3,9); line(g,3,9,9,9);
                }
                case "exit" -> {
                    line(g,10,4,5,4); line(g,5,4,5,20); line(g,5,20,10,20);
                    line(g,10,12,21,12); line(g,17,8,21,12); line(g,21,12,17,16);
                }
                case "leaf" -> {
                    Path2D p=new Path2D.Double(); p.moveTo(5,19); p.curveTo(0,6,12,3,21,3); p.curveTo(22,15,17,23,5,19); g.draw(p);
                    line(g,4,21,16,9); line(g,9,16,9,12); line(g,12,13,16,13);
                }
                case "sun" -> {
                    g.draw(new Ellipse2D.Double(7,7,10,10));
                    for(int i=0;i<8;i++) { double a=i*Math.PI/4; line(g,12+Math.cos(a)*8,12+Math.sin(a)*8,12+Math.cos(a)*10,12+Math.sin(a)*10); }
                }
                case "shield" -> {
                    Path2D p=new Path2D.Double(); p.moveTo(12,3); p.lineTo(20,6); p.lineTo(20,12); p.curveTo(20,16,16,20,12,22); p.curveTo(8,20,4,16,4,12); p.lineTo(4,6); p.closePath(); g.draw(p);
                    line(g,8,12,11,15); line(g,11,15,16,9);
                }
                case "plus" -> { line(g,12,5,12,19); line(g,5,12,19,12); }
                case "pin" -> { line(g,7,4,17,4);line(g,8,4,8,10);line(g,16,4,16,10);line(g,8,10,5,14);line(g,16,10,19,14);line(g,5,14,19,14);line(g,12,14,12,22); }
                case "lock" -> { g.draw(new RoundRectangle2D.Double(5,10,14,11,2,2)); g.draw(new Arc2D.Double(8,2,8,14,0,180,Arc2D.OPEN)); line(g,12,14,12,17); }
                case "check" -> { line(g,5,12,10,17); line(g,10,17,19,7); }
                case "blank" -> { /* reserve the normal icon lane without drawing */ }
                case "trash" -> {
                    line(g,4,6,20,6); line(g,9,3,15,3); line(g,7,6,8,21); line(g,8,21,16,21); line(g,16,21,17,6); line(g,10,10,10.5,17); line(g,14,10,13.5,17);
                }
                case "edit" -> {
                    Path2D p=new Path2D.Double(); p.moveTo(5,17); p.lineTo(5,20); p.lineTo(8,20); p.lineTo(18,10); p.lineTo(14,6); p.closePath(); g.draw(p);
                    line(g,13,7,17,11); line(g,4,21,10,21);
                }
                case "search" -> { g.draw(new Ellipse2D.Double(4,4,12,12)); line(g,14,14,21,21); }
                case "next", "arrow" -> { line(g,9,6,15,12); line(g,15,12,9,18); }
                case "prev" -> { line(g,15,6,9,12); line(g,9,12,15,18); }
                case "down" -> { line(g,7,10,12,15); line(g,12,15,17,10); }
                case "swap" -> { line(g,4,8,20,8); line(g,17,5,20,8); line(g,4,16,20,16); line(g,4,16,7,19); }
                case "drag" -> { for(int i=0;i<3;i++) for(int j=0;j<2;j++) g.fill(new Ellipse2D.Double(8+j*6,6+i*5,2,2)); }
                case "info", "warning" -> {
                    g.draw(new Ellipse2D.Double(3,3,18,18));
                    if (name.equals("info")) { g.fill(new Ellipse2D.Double(11,6.5,2,2)); line(g,12,11,12,17); }
                    else { line(g,12,7,12,13); g.fill(new Ellipse2D.Double(11,16,2,2)); }
                }
                case "dot" -> g.fill(new Ellipse2D.Double(8,8,8,8));
                default -> g.draw(new Ellipse2D.Double(4,4,16,16));
            }
            g.dispose();
        }
    }
    private static void line(Graphics2D g,double x,double y,double a,double b) { g.draw(new Line2D.Double(x,y,a,b)); }
}
