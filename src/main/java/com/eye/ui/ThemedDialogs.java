package com.eye.ui;
import java.awt.Component;
import javax.swing.*;
import com.eye.ui.theme.*;
/** Shared application dialog presentation; callers own validation and acceptance. */
final class ThemedDialogs {
 private ThemedDialogs(){}
    public static int option(Component parent, Object message, String title, int messageType, Object[] options, Object defaultValue) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION,
                Icons.appIcon(42), options, defaultValue);
        JDialog dialog = pane.createDialog(parent, title);
        Theme.window(dialog);
        dialog.setModal(true);
        Object value;
        try{dialog.setVisible(true);value=pane.getValue();}finally{dialog.dispose();}
        if (value == null) return JOptionPane.CLOSED_OPTION;
        for (int i = 0; i < options.length; i++) if (options[i].equals(value)) return i;
        return JOptionPane.CLOSED_OPTION;
    }
    public static void message(Component parent, Object message, String title, int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION, Icons.appIcon(38));
        JDialog dialog = pane.createDialog(parent, title);
        Theme.window(dialog);
        dialog.setModal(true);
        try{dialog.setVisible(true);}finally{dialog.dispose();}
    }
}
