package com.chaossmp.oppanel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public final class OPPanel extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("OPPanel успешно включён!");
    }

    @Override
    public void onDisable() {
        getLogger().info("OPPanel выключен.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эта команда доступна только игрокам.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("oppanel")) {
            if (!player.hasPermission("oppanel.use")) {
                player.sendMessage("§cУ вас нет прав для использования этой команды!");
                return true;
            }
            openOwnerPanel(player);
            return true;
        }

        return false;
    }

    private void openOwnerPanel(Player player) {
        Inventory panel = Bukkit.createInventory(
                null,
                27,
                ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Owner Panel"
        );

        double tps = Bukkit.getTPS()[0];
        long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;

        panel.setItem(10, createItem(
                Material.CLOCK,
                "§c§lСостояние сервера",
                "§7TPS: §e" + String.format("%.2f", tps),
                "§7Память: §e" + freeMemory + "MB / " + totalMemory + "MB",
                "§7Онлайн: §e" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers()
        ));

        panel.setItem(12, createItem(
                Material.PLAYER_HEAD,
                "§6§lИнспектор игроков",
                "§7Нажмите, чтобы посмотреть список",
                "§7и состояние игроков онлайн."
        ));

        panel.setItem(14, createItem(
                Material.REDSTONE_BLOCK,
                "§c§lПерезагрузка сервера",
                "§7Нажмите для перезагрузки",
                "§cТребует подтверждения!"
        ));

        panel.setItem(16, createItem(
                Material.BARRIER,
                "§4§lВыключение сервера",
                "§7Нажмите для выключения",
                "§cТребует подтверждения!"
        ));

        panel.setItem(22, createItem(Material.ARROW, "§eЗакрыть", "§7Закрыть панель"));

        player.openInventory(panel);
    }

    private void openPlayerInspectorList(Player player) {
        Inventory list = Bukkit.createInventory(
                null,
                54,
                ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Инспектор: Игроки"
        );

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName("§f" + target.getName());
                meta.setLore(Arrays.asList(
                        "§7Здоровье: §c" + String.format("%.1f", target.getHealth()) + "❤",
                        "§7Ситость: §e" + target.getFoodLevel() + "/20",
                        "§7Мир: §b" + target.getWorld().getName(),
                        "",
                        "§eНажмите для телепортации"
                ));
                head.setItemMeta(meta);
            }
            list.setItem(slot++, head);
        }

        list.setItem(49, createItem(Material.ARROW, "§eНазад в Owner Panel", "§7Вернуться в главную панель"));
        player.openInventory(list);
    }

    private void openConfirmationMenu(Player player, String actionType) {
        Inventory confirm = Bukkit.createInventory(
                null,
                27,
                ChatColor.RED.toString() + ChatColor.BOLD + "Подтвердите действие: " + actionType
        );

        confirm.setItem(11, createItem(
                Material.EMERALD_BLOCK,
                "§a§lПОДТВЕРДИТЬ",
                "§7Вы действительно хотите",
                "§7выполнить действие: §e" + actionType + "§7?"
        ));

        confirm.setItem(15, createItem(
                Material.BARRIER,
                "§c§lОТМЕНА",
                "§7Вернуться назад в Owner Panel"
        ));

        player.openInventory(confirm);
    }

    private ItemStack createItem(Material material, String name, String... description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(description));
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        String ownerPanelTitle = ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Owner Panel";
        String inspectorTitle = ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Инспектор: Игроки";
        String restartConfirmTitle = ChatColor.RED.toString() + ChatColor.BOLD + "Подтвердите действие: Рестарт";
        String shutdownConfirmTitle = ChatColor.RED.toString() + ChatColor.BOLD + "Подтвердите действие: Выключение";

        if (!title.equals(ownerPanelTitle) && !title.equals(inspectorTitle) &&
            !title.equals(restartConfirmTitle) && !title.equals(shutdownConfirmTitle)) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (title.equals(ownerPanelTitle)) {
            switch (event.getRawSlot()) {
                case 12 -> openPlayerInspectorList(player);
                case 14 -> openConfirmationMenu(player, "Рестарт");
                case 16 -> openConfirmationMenu(player, "Выключение");
                case 22 -> player.closeInventory();
                default -> {}
            }
        } else if (title.equals(inspectorTitle)) {
            if (event.getRawSlot() == 49) {
                openOwnerPanel(player);
            } else {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                    String targetName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                    Player target = Bukkit.getPlayer(targetName);
                    if (target != null) {
                        player.teleport(target.getLocation());
                        player.sendMessage("§aВы телепортировались к игроку " + target.getName());
                        player.closeInventory();
                    }
                }
            }
        } else if (title.equals(restartConfirmTitle)) {
            if (event.getRawSlot() == 11) {
                player.closeInventory();
                Bukkit.broadcastMessage("§c§l[ChaosSMP] Сервер перезапускается владельцем!");
                Bukkit.spigot().restart();
            } else if (event.getRawSlot() == 15) {
                openOwnerPanel(player);
            }
        } else if (title.equals(shutdownConfirmTitle)) {
            if (event.getRawSlot() == 11) {
                player.closeInventory();
                Bukkit.broadcastMessage("§4§l[ChaosSMP] Сервер выключается владельцем!");
                Bukkit.shutdown();
            } else if (event.getRawSlot() == 15) {
                openOwnerPanel(player);
            }
        }
    }
}
