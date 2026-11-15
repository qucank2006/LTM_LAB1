package util;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;

public final class ThemeManager {
    public static final Color BASE = Color.decode("#1E1E2E"), MANTLE = Color.decode("#181825"),
            CRUST = Color.decode("#11111B"), SURFACE = Color.decode("#313244"),
            TEXT = Color.decode("#CDD6F4"), MUTED = Color.decode("#A6ADC8"),
            MAUVE = Color.decode("#CBA6F7"), RED = Color.decode("#F38BA8"), GREEN = Color.decode("#A6E3A1");

    private ThemeManager() {
    }

    public static void install() {
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 14));
        for (String key : new String[]{"Panel.background", "OptionPane.background", "Viewport.background", "TextPane.background"})
            UIManager.put(key, BASE);
        for (String key : new String[]{"Label.foreground", "TextField.foreground", "PasswordField.foreground", "TextArea.foreground", "List.foreground", "Button.foreground"})
            UIManager.put(key, TEXT);
        for (String key : new String[]{"TextField.background", "PasswordField.background", "TextArea.background", "Button.background", "ComboBox.background"})
            UIManager.put(key, SURFACE);
        UIManager.put("List.background", MANTLE);
        UIManager.put("List.selectionBackground", SURFACE);
        UIManager.put("List.selectionForeground", MAUVE);
        UIManager.put("Component.focusColor", MAUVE);
        UIManager.put("Button.hoverBackground", Color.decode("#45475A"));
        UIManager.put("Button.pressedBackground", Color.decode("#585B70"));
        UIManager.put("Button.focusedBorderColor", MAUVE);
        UIManager.put("TextComponent.selectionBackground", Color.decode("#585B70"));
        UIManager.put("MenuItem.selectionBackground", SURFACE);
        UIManager.put("PopupMenu.background", MANTLE);
        UIManager.put("Separator.foreground", SURFACE);
        UIManager.put("Button.default.background", MAUVE);
        UIManager.put("Button.default.foreground", CRUST);
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 12);
        UIManager.put("ToolTip.background", SURFACE);
        UIManager.put("ToolTip.foreground", TEXT);
    }
}
