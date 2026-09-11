package de.deinname.plotmenu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class PlotMenuPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private String mainTitle;
    private String subTitlePrefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        this.getCommand("plotgui").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Voll-dynamisches PlotMenu erfolgreich aktiviert!");
    }

    private void loadConfigValues() {
        this.mainTitle = translateColor(getConfig().getString("gui.main-title", "&8Plot-Kategorien"));
        this.subTitlePrefix = translateColor(getConfig().getString("gui.sub-title-prefix", "&8Kategorie: "));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args.equalsIgnoreCase("reload")) {
            if (sender.hasPermission("plotmenu.admin")) {
                reloadConfig();
                loadConfigValues();
                sender.sendMessage("§a[PlotMenu] Config erfolgreich neu geladen!");
                return true;
            }
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cNur Spieler können diesen Befehl nutzen.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("plotmenu.use")) {
            player.sendMessage(translateColor(getConfig().getString("messages.no-permission-gui", "&cKeine Rechte.")));
            return true;
        }

        openMainMenu(player);
        return true;
    }

    public void openMainMenu(Player player) {
        int rows = getConfig().getInt("gui.rows", 3) * 9;
        Inventory inv = Bukkit.createInventory(null, rows, mainTitle);
        fillBackground(inv);

        ConfigurationSection categories = getConfig().getConfigurationSection("categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                Material mat = Material.matchMaterial(getConfig().getString("categories." + key + ".icon", "BOOK"));
                String name = translateColor(getConfig().getString("categories." + key + ".name", key));
                int slot = getConfig().getInt("categories." + key + ".slot", 0);
                
                List<String> lore = new ArrayList<>();
                lore.add("§7Klicke, um diese Kategorie zu öffnen.");
                
                inv.setItem(slot, createItem(mat == null ? Material.BOOK : mat, name, lore));
            }
        }
        player.openInventory(inv);
    }

    public void openSubMenu(Player player, String categoryKey, String categoryName) {
        int rows = getConfig().getInt("gui.rows", 3) * 9;
        Inventory inv = Bukkit.createInventory(null, rows, subTitlePrefix + categoryName);
        fillBackground(inv);

        ConfigurationSection items = getConfig().getConfigurationSection("categories." + categoryKey + ".items");
        if (items != null) {
            for (String itemKey : items.getKeys(false)) {
                String path = "categories." + categoryKey + ".items." + itemKey;
                Material mat = Material.matchMaterial(getConfig().getString(path + ".material", "STONE"));
                String name = translateColor(getConfig().getString(path + ".name", "Block"));
                int slot = getConfig().getInt(path + ".slot", 0);
                
                List<String> lore = getConfig().getStringList(path + ".lore");
                for (int i = 0; i < lore.size(); i++) lore.set(i, translateColor(lore.get(i)));

                inv.setItem(slot, createItem(mat == null ? Material.STONE : mat, name, lore));
            }
        }

        inv.setItem(inv.getSize() - 1, createItem(Material.BARRIER, "§c§lZurück zum Hauptmenü", new ArrayList<>()));
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(mainTitle) && !title.startsWith(subTitlePrefix)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (title.equals(mainTitle)) {
            ConfigurationSection categories = getConfig().getConfigurationSection("categories");
            if (categories != null) {
                for (String key : categories.getKeys(false)) {
                    if (event.getSlot() == getConfig().getInt("categories." + key + ".slot")) {
                        openSubMenu(player, key, translateColor(getConfig().getString("categories." + key + ".name")));
                        return;
                    }
                }
            }
        } 
        else if (title.startsWith(subTitlePrefix)) {
            if (clicked.getType() == Material.BARRIER) {
                openMainMenu(player);
                return;
            }

            String currentSubTitle = title.replace(subTitlePrefix, "");
            ConfigurationSection categories = getConfig().getConfigurationSection("categories");
            if (categories != null) {
                for (String catKey : categories.getKeys(false)) {
                    if (translateColor(getConfig().getString("categories." + catKey + ".name")).equals(currentSubTitle)) {
                        ConfigurationSection items = getConfig().getConfigurationSection("categories." + catKey + ".items");
                        if (items != null) {
                            for (String itemKey : items.getKeys(false)) {
                                String path = "categories." + catKey + ".items." + itemKey;
                                if (event.getSlot() == getConfig().getInt(path + ".slot")) {
                                    String perm = getConfig().getString(path + ".permission", "plotmenu.use");
                                    
                                    if (player.hasPermission(perm)) {
                                        String action = getConfig().getString(path + ".action");
                                        String fullCmd = "plotsquared:plot component set " + action + " " + player.getName();
                                        
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fullCmd);
                                        player.sendMessage(translateColor(getConfig().getString("messages.success")));
                                    } else {
                                        player.sendMessage(translateColor(getConfig().getString("messages.no-permission-action")));
                                    }
                                    player.closeInventory();
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void fillBackground(Inventory inv) {
        Material fillerMat = Material.matchMaterial(getConfig().getString("gui.filler", "GRAY_STAINED_GLASS_PANE"));
        ItemStack placeholder = createItem(fillerMat == null ? Material.GRAY_STAINED_GLASS_PANE : fillerMat, " ", new ArrayList<>());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, placeholder);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String translateColor(String text) {
        return text == null ? "" : text.replace('&', '§');
    }
}
