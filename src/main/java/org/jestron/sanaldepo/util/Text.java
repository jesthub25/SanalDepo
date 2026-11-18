package org.jestron.sanaldepo.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renk ve yer tutucu (placeholder) yardimcilari.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /** &-renk kodlarini ve &#RRGGBB hex kodlarini cevirir. */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String s = input;
        if (s.contains("&#")) {
            Matcher m = HEX.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String replacement;
                try {
                    replacement = net.md_5.bungee.api.ChatColor.of("#" + m.group(1)).toString();
                } catch (Throwable t) {
                    replacement = "";
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** "anahtar", "deger", "anahtar2", "deger2" ... ciftleriyle yer tutuculari doldurur. */
    public static String apply(String input, String... placeholders) {
        if (input == null) {
            return "";
        }
        String s = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1];
            s = s.replace(placeholders[i], value == null ? "" : value);
        }
        return s;
    }

    public static String format(String input, String... placeholders) {
        return color(apply(input, placeholders));
    }

    public static List<String> format(List<String> lines, String... placeholders) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(format(line, placeholders));
        }
        return out;
    }
}
