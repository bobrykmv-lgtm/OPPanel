package com.chaossmp.oppanel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OPPanel extends JavaPlugin implements Listener {

    private boolean maintenanceMode = false;
    private final Map<UUID, PendingPunishment> awaitingPunishmentInput = new HashMap<>();

    private static class PendingPunishment {
        final String targetName;
        final String punishmentType;

        PendingPunishment(String targetName, String punishmentType) {
            this.targetName = targetName;
            this.punishmentType = punishmentType;
        }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("OPPanel успешно включён!");
    }

    @Override
    public void onDisable() {
        awaitingPunishmentInput.clear();
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

        // Кнопка телепортації до гравців
        panel.setItem(11, createItem(
                Material.ENDER_PEARL,
                "§5§lТелепортация к игрокам",
                "§7Выбрать игрока онлайн",
                "§7и телепортироваться к нему."
        ));

        // Управління покараннями
        panel.setItem(12, createItem(
                Material.IRON_SWORD,
                "§4§lУправление наказаниями",
                "§7Выдать бан, мут или варн."
        ));

        // Управління погодою та часом
        panel.setItem(13, createItem(
                Material.SUNFLOWER,
                "§e§lПогода и Время",
                "§7Управление погодой и временем."
        ));

        // Режим техробіт (динамічний колір залежно від статусу)
        if (maintenanceMode) {
            panel.setItem(14, createItem(
                    Material.LIME_CONCRETE,
                    "§a§lВыключить тех. работы",
                    "§cСейчас тех. работы ВКЛЮЧЕНЫ!",
                    "§7Нажмите, чтобы открыть сервер."
            ));
        } else {
            panel.setItem(14, createItem(
                    Material.RED_CONCRETE,
                    "§c§lВключить тех. работы",
                    "§aСейчас сервер открыт.",
                    "§7Требует подтверждения!"
            ));
        }

        panel.setItem(15, createItem(
                Material.REDSTONE_BLOCK,
                "§c§lПерезагрузка сервера",
                "§7Требует подтверждения!"
        ));

        panel.setItem(16, createItem(
                Material.BARRIER,
                "§4§lВыключение сервера",
                "§7Требует подтверждения!"
        ));

        panel.setItem(22, createItem(Material.ARROW, "§eЗакрыть", "§7Закрыть панель"));

        player.openInventory(panel);
    }

    private void openWeatherTimePanel(Player player) {
        Inventory panel = Bukkit.createInventory(
                null,
                27,
                ChatColor.GOLD.toString() + ChatColor.BOLD + "Погода и Время"
        );

        panel.setItem(10, createItem(Material.SUNFLOWER, "§e§lВключить День", "§7Установить время на 1000 тиков"));
        panel.setItem(12, createItem(Material.ENDER_EYE, "§5§lВключить Ночь", "§7Установить время на 13000 тиков"));
        panel.setItem(14, createItem(Material.WATER_BUCKET, "§b§lЯсная погода", "§7Отключить дождь/грозу"));
        panel.setItem(16, createItem(Material.SPLASH_POTION, "§9§lВключить Дождь", "§7Вызвать осадки"));

        panel.setItem(22, createItem(Material.ARROW, "§eНазад в Owner Panel", "§7Вернуться в главную панель"));
        player.openInventory(panel);
    }

    // Меню телепортації до гравців
    private void openTeleportPlayerList(Player player) {
        Inventory list = Bukkit.createInventory(
                null,
                54,
                ChatColor.DARK_PURPLE.toString() + ChatColor.BOLD + "Телепорт: Выбор игрока"
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
                        "§7Мир: §b" + target.getWorld().getName(),
                        "§7Здоровье: §c" + String.format("%.1f", target.getHealth()) + "❤",
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

    private void openPunishmentPlayerList(Player player) {
        Inventory list = Bukkit.createInventory(
                null,
                54,
                ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Наказания: Выбор игрока"
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
                        "§7Нажмите, чтобы выбрать",
                        "§7меру наказания для игрока."
                ));
                head.setItemMeta(meta);
            }
            list.setItem(slot++, head);
        }

        list.setItem(49, createItem(Material.ARROW, "§eНазад в Owner Panel", "§7Вернуться в главную панель"));
        player.openInventory(list);
    }

    private void openPunishmentTypeMenu(Player player, String targetName) {
        Inventory menu = Bukkit.createInventory(
                null,
                27,
                ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Наказание: " + targetName
        );

        menu.setItem(11, createItem(
                Material.RED_WOOL,
                "§c§lБан",
                "§7Цель: §e" + targetName,
                "§7Нажмите, чтобы ввести срок (напр. 1d, 1w)"
        ));

        menu.setItem(13, createItem(
                Material.YELLOW_WOOL,
                "§e§lМут",
                "§7Цель: §e" + targetName,
                "§7Нажмите, чтобы ввести срок (напр. 1h, 30m)"
        ));

        menu.setItem(15, createItem(
                Material.ORANGE_WOOL,
                "§6§lПредупреждение",
                "§7Цель: §e" + targetName,
                "§7Выдать варн игроку"
        ));

        menu.setItem(22, createItem(Material.ARROW, "§eНазад к списку игроков", "§7Вернуться назад"));
        player.openInventory(menu);
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
        String weatherPanelTitle = ChatColor.GOLD.toString() + ChatColor.BOLD + "Погода и Время";
        String tpListTitle = ChatColor.DARK_PURPLE.toString() + ChatColor.BOLD + "Телепорт: Выбор игрока";
        String punishmentListTitle = ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Наказания: Выбор игрока";
        String restartConfirmTitle = ChatColor.RED.toString() + ChatColor.BOLD + "Подтвердите действие: Рестарт";
        String shutdownConfirmTitle = ChatColor.RED.toString() + ChatColor.BOLD + "Подтвердите действие: Выключение";
        String maintenanceConfirmTitle = ChatColor.RED.toString() + ChatColor.BOLD + "Подтвердите действие: Тех. работы";

        boolean isPunishmentTypeMenu = title.startsWith(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "Наказание: ");

        if (!title.equals(ownerPanelTitle) && !title.equals(weatherPanelTitle) && !title.equals(tpListTitle) &&
            !title.equals(punishmentListTitle) && !isPunishmentTypeMenu &&
            !title.equals(restartConfirmTitle) && !title.equals(shutdownConfirmTitle) && !title.equals(maintenanceConfirmTitle)) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (title.equals(ownerPanelTitle)) {
            switch (event.getRawSlot()) {
                case 11 -> openTeleportPlayerList(player);
                case 12 -> openPunishmentPlayerList(player);
                case 13 -> openWeatherTimePanel(player);
                case 14 -> {
                    if (maintenanceMode) {
                        // Вимикаємо одразу без підтвердження
                        maintenanceMode = false;
                        Bukkit.broadcastMessage("§a§l[ChaosSMP] Технические работы завершены! Сервер открыт для всех.");
                        openOwnerPanel(player);
                    } else {
                        // Відкриваємо підтвердження для ввімкнення техробіт
                        openConfirmationMenu(player, "Тех. работы");
                    }
                }
                case 15 -> openConfirmationMenu(player, "Рестарт");
                case 16 -> openConfirmationMenu(player, "Выключение");
                case 22 -> player.closeInventory();
                default -> {}
            }
        } else if (title.equals(weatherPanelTitle)) {
            World world = player.getWorld();
            switch (event.getRawSlot()) {
                case 10 -> {
                    world.setTime(1000);
                    player.sendMessage("§e[OPPanel] Установлено дневное время.");
                }
                case 12 -> {
                    world.setTime(13000);
                    player.sendMessage("§e[OPPanel] Установлено ночное время.");
                }
                case 14 -> {
                    world.setStorm(false);
                    world.setThundering(false);
                    player.sendMessage("§a[OPPanel] Включена ясная погода.");
                }
                case 16 -> {
                    world.setStorm(true);
                    player.sendMessage("§c[OPPanel] Включен дождь.");
                }
                case 22 -> openOwnerPanel(player);
                default -> {}
            }
        } else if (title.equals(tpListTitle)) {
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
        } else if (title.equals(punishmentListTitle)) {
            if (event.getRawSlot() == 49) {
                openOwnerPanel(player);
            } else {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                    String targetName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                    openPunishmentTypeMenu(player, targetName);
                }
            }
        } else if (isPunishmentTypeMenu) {
            String targetName = ChatColor.stripColor(title.replace("Наказание: ", ""));
            if (event.getRawSlot() == 22) {
                openPunishmentPlayerList(player);
            } else {
                String pType = null;
                if (event.getRawSlot() == 11) pType = "Бан";
                else if (event.getRawSlot() == 13) pType = "Мут";
                else if (event.getRawSlot() == 15) pType = "Предупреждение";

                if (pType != null) {
                    player.closeInventory();
                    if (pType.equals("Предупреждение")) {
                        Bukkit.dispatchCommand(player, "warn " + targetName + " Нарушение правил панели");
                        player.sendMessage("§aИгроку " + targetName + " вынесено предупреждение.");
                    } else {
                        awaitingPunishmentInput.put(player.getUniqueId(), new PendingPunishment(targetName, pType));
                        player.sendMessage("§8§m--------------------------------");
                        player.sendMessage("§c§l[OPPanel] Введите срок для " + pType.toUpperCase() + " (§e" + targetName + "§c) в чат.");
                        player.sendMessage("§7Пример: §e1m§7, §e1h§7, §e1d§7, §e1w§7, §e30m");
                        player.sendMessage("§7Для отмены напишите: §ccancel");
                        player.sendMessage("§8§m--------------------------------");
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
        } else if (title.equals(maintenanceConfirmTitle)) {
            if (event.getRawSlot() == 11) {
                player.closeInventory();
                maintenanceMode = true;
                Bukkit.broadcastMessage("§c§l[ChaosSMP] Внимание! Включены технические работы. Все игроки без OP будут отключены.");
                
                // Кікаємо всіх гравців без прав
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.hasPermission("oppanel.bypass") && !onlinePlayer.isOp()) {
                        onlinePlayer.kickPlayer("§cСервер закрыт на технические работы владельцем!");
                    }
                }
            } else if (event.getRawSlot() == 15) {
                openOwnerPanel(player);
            }
        }
    }

    // Блокування входу під час техробіт для гравців без OP
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (maintenanceMode) {
            Player player = event.getPlayer();
            if (!player.hasPermission("oppanel.bypass") && !player.isOp()) {
                event.disallow(
                        PlayerLoginEvent.Result.KICK_WHITELIST,
                        "§c§lСервер на технических работах!\n§7Зайти могут только администраторы."
                );
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingPunishmentInput.containsKey(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();
        PendingPunishment pending = awaitingPunishmentInput.remove(player.getUniqueId());

        Bukkit.getScheduler().runTask(this, () -> {
            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("отмена")) {
                player.sendMessage("§cВвод наказания отменен.");
                return;
            }

            if (pending.punishmentType.equals("Бан")) {
                String cmd = "ban " + pending.targetName + " " + message + " Нарушение правил";
                Bukkit.dispatchCommand(player, cmd);
                player.sendMessage("§aИгрок " + pending.targetName + " успешно забанен на срок: " + message);
            } else if (pending.punishmentType.equals("Мут")) {
                String cmd = "mute " + pending.targetName + " " + message;
                Bukkit.dispatchCommand(player, cmd);
                player.sendMessage("§aИгроку " + pending.targetName + " выдан мут на срок: " + message);
            }
        });
    }
}
