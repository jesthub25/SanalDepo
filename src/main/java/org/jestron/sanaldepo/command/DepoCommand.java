package org.jestron.sanaldepo.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jestron.sanaldepo.SanalDepo;
import org.jestron.sanaldepo.config.Messages;
import org.jestron.sanaldepo.gui.MenuLayout;
import org.jestron.sanaldepo.vault.VaultManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /depo                    -> depo menusu
 * /depo <numara>           -> depoyu direkt ac
 * /depo <oyuncu>           -> (yonetici) oyuncunun depo menusu
 * /depo <oyuncu> <numara>  -> (yonetici) oyuncunun deposu
 * /depo yenile             -> config + mesajlar
 * /depo yardim             -> yardim
 */
public final class DepoCommand implements CommandExecutor, TabCompleter {

    private final SanalDepo plugin;

    public DepoCommand(SanalDepo plugin) {
        this.plugin = plugin;
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty() || s.length() > 4) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return null;
            }
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isHelp(String low) {
        return low.equals("yardim") || low.equals("yardım") || low.equals("help") || low.equals("?");
    }

    private static boolean isReload(String low) {
        return low.equals("yenile") || low.equals("reload");
    }

    private void help(CommandSender sender) {
        Messages m = plugin.messages();
        m.sendList(sender, "yardim");
        if (sender.hasPermission(VaultManager.PERM_ADMIN_VIEW) || sender.hasPermission(VaultManager.PERM_RELOAD)) {
            m.sendList(sender, "yardim-admin");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        VaultManager vm = plugin.vaults();

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                m.send(sender, "sadece-oyuncu");
                help(sender);
                return true;
            }
            Player p = (Player) sender;
            if (!p.hasPermission(VaultManager.PERM_USE)) {
                m.send(p, "yetki-yok");
                return true;
            }
            openOwnMenu(p);
            return true;
        }

        String first = args[0];
        String low = first.toLowerCase(Locale.ROOT);

        if (isHelp(low)) {
            help(sender);
            return true;
        }
        if (isReload(low) && sender.hasPermission(VaultManager.PERM_RELOAD)) {
            plugin.reloadAll();
            m.send(sender, "yenilendi");
            return true;
        }

        Integer number = parseInt(first);
        if (number != null) {
            if (!(sender instanceof Player)) {
                m.send(sender, "sadece-oyuncu");
                return true;
            }
            Player p = (Player) sender;
            if (!p.hasPermission(VaultManager.PERM_USE)) {
                m.send(p, "yetki-yok");
                return true;
            }
            openOwnVault(p, number);
            return true;
        }

        // ---- yonetici: /depo <oyuncu> [numara] ----
        if (!sender.hasPermission(VaultManager.PERM_ADMIN_VIEW)) {
            help(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            m.send(sender, "sadece-oyuncu");
            return true;
        }
        Player admin = (Player) sender;
        Integer vault = null;
        if (args.length >= 2) {
            vault = parseInt(args[1]);
            if (vault == null || vault < 1 || vault > MenuLayout.HARD_CAP) {
                m.send(admin, "gecersiz-numara", "%maks%", String.valueOf(MenuLayout.HARD_CAP));
                return true;
            }
        }
        final Integer fVault = vault;
        vm.resolvePlayer(first, rec -> {
            if (!admin.isOnline()) {
                return;
            }
            if (rec == null) {
                m.send(admin, "oyuncu-bulunamadi", "%oyuncu%", first);
                return;
            }
            if (rec.uuid.equals(admin.getUniqueId())) {
                // kendi deposu: normal kurallar gecerli (OP olsa bile hak siniri)
                if (fVault == null) {
                    openOwnMenu(admin);
                } else {
                    openOwnVault(admin, fVault);
                }
                return;
            }
            if (fVault == null) {
                vm.openMenu(admin, rec.uuid, rec.name, true);
            } else {
                vm.openVault(admin, rec.uuid, rec.name, fVault, true, false);
            }
        });
        return true;
    }

    private void openOwnMenu(Player p) {
        VaultManager vm = plugin.vaults();
        if (vm.allowedVaults(p) <= 0) {
            plugin.messages().send(p, "hic-depo-yok");
            return;
        }
        if (!vm.checkEnvironment(p)) {
            return;
        }
        vm.openMenu(p, p.getUniqueId(), p.getName(), false);
    }

    private void openOwnVault(Player p, int number) {
        VaultManager vm = plugin.vaults();
        int max = plugin.settings().maxVaults;
        if (number < 1 || number > max) {
            plugin.messages().send(p, "gecersiz-numara", "%maks%", String.valueOf(max));
            return;
        }
        int allowed = vm.allowedVaults(p);
        if (allowed <= 0) {
            plugin.messages().send(p, "hic-depo-yok");
            return;
        }
        if (number > allowed) {
            plugin.messages().send(p, "depo-yok", "%no%", String.valueOf(number), "%hak%", String.valueOf(allowed));
            return;
        }
        vm.openVault(p, p.getUniqueId(), p.getName(), number, false, false);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            if (sender instanceof Player) {
                int allowed = plugin.vaults().allowedVaults((Player) sender);
                for (int i = 1; i <= allowed; i++) {
                    options.add(String.valueOf(i));
                }
            }
            options.add("yardim");
            if (sender.hasPermission(VaultManager.PERM_RELOAD)) {
                options.add("yenile");
            }
            if (sender.hasPermission(VaultManager.PERM_ADMIN_VIEW)) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    options.add(online.getName());
                }
            }
            return filter(options, prefix);
        }
        if (args.length == 2 && sender.hasPermission(VaultManager.PERM_ADMIN_VIEW)) {
            String low = args[0].toLowerCase(Locale.ROOT);
            if (parseInt(args[0]) != null || isHelp(low) || isReload(low)) {
                return Collections.emptyList();
            }
            List<String> options = new ArrayList<>();
            int max = Math.max(plugin.settings().maxVaults, 1);
            for (int i = 1; i <= max; i++) {
                options.add(String.valueOf(i));
            }
            return filter(options, args[1].toLowerCase(Locale.ROOT));
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        if (prefix.isEmpty()) {
            return options;
        }
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(o);
            }
        }
        return out;
    }
}
