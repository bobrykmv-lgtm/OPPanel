package com.chaossmp.oppanel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OPPanel extends JavaPlugin implements Listener, CommandExecutor {

    private final String MAIN_TITLE = "§8» §c§lOPPanel v2.2 §7| Главная";
    private final String PLAYERS_TITLE = "§8» §c§lOPPanel §7| Выбор Игрока";
    private final String PUNISH_PREFIX = "§8» §c§lМеню: ";
    private final String DURATION_PREFIX = "§8» §e§lСрок: ";
    private final String UNBAN_TITLE = "§8» §2§lРазбан игроков";

    // Зберігаємо обраного гравця та тип покарання для адміністратора
    private final Map<UUID, UUID> selectedTarget = new HashMap<>();
    private final Map<UUID, String> pendingPunishmentType = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("oppanel").setExecutor(this);
        getLogger().info("OPPanel 2.2 успешно активирован!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        if (!player.hasPermission("oppanel.admin") && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "У вас нет прав на использование OPPanel!");
            return true;
        }

        openMainMenu(player);
        return true;
    }

    // --- 1. ГОЛОВНЕ МЕНЮ ---
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, MAIN_TITLE);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.PLAYER_HEAD, "§a§lУправление Игроками",
                List.of("§7Список онлайн-игроков,", "§7телепортация, инспекция и наказания.")));

        double ramUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024.0 / 1024.0;
        double ramMax = Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0;
        gui.setItem(12, createGuiItem(Material.NETHER_STAR, "§e§lСтатус Сервера",
                List.of("§7TPS: §a" + String.format("%.2f", Bukkit.getTPS()[0]),
                        "§7ОЗУ: §b" + (int) ramUsed + "MB / " + (int) ramMax + "MB",
                        "§7Онлайн: §f" + Bukkit.getOnlinePlayers().size() + " чел.")));

        gui.setItem(14, createGuiItem(Material.CLOCK, "§b§lМир и Погода",
                List.of("§7Смена времени суток (День/Ночь)", "§7и погоды.")));

        // Кнопка списку забанених для розбану (Слот 15)
        gui.setItem(15, createGuiItem(Material.GOLDEN_APPLE, "§2§lСистема Разбана",
                List.of("§7Список заблокированных игроков", "§7для быстрого помилования.")));

        boolean isSpectator = player.getGameMode() == GameMode.SPECTATOR;
        gui.setItem(16, createGuiItem(Material.ENDER_EYE, "§d§lРежим Спектатора",
                List.of("§7Статус: " + (isSpectator ? "§aВключен" : "§cВыключен"))));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    // --- 2. СПИСОК ОНЛАЙН-ГРАВЦІВ ---
    public void openPlayerListMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, PLAYERS_TITLE);

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 53) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName("§e" + target.getName());
                meta.setLore(List.of(
                        "§7Здоровье: §c" + (int) target.getHealth() + "/20",
                        "§7Пипнг: §a" + target.getPing() + "ms",
                        "",
                        "§eНажмите для меню действий!"
                ));
                head.setItemMeta(meta);
            }
            gui.setItem(slot++, head);
        }

        gui.setItem(53, createGuiItem(Material.BARRIER, "§cНазад в главное меню"));
        player.openInventory(gui);
    }

    // --- 3. МЕНЮ ДІЙ НАД ГРАВЦЕМ ---
    public void openPunishMenu(Player admin, Player target) {
        selectedTarget.put(admin.getUniqueId(), target.getUniqueId());
        Inventory gui = Bukkit.createInventory(null, 27, PUNISH_PREFIX + target.getName());
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.COMPASS, "§aТП к игроку", List.of("§7Телепортироваться к §f" + target.getName())));
        gui.setItem(11, createGuiItem(Material.ENDER_PEARL, "§2ТП игрока к себе", List.of("§7Притянуть §f" + target.getName() + " §7к себе")));
        gui.setItem(12, createGuiItem(Material.CHEST, "§eИнспекция инвентаря", List.of("§7Просмотр вещей игрока")));
        
        gui.setItem(13, createGuiItem(Material.FEATHER, "§cКикнуть игрока", List.of("§7Мгновенный кик с сервера")));
        gui.setItem(14, createGuiItem(Material.PAPER, "§6Выдать Варн", List.of("§7Нажмите для выбора срока")));
        gui.setItem(15, createGuiItem(Material.ANVIL, "§dВыдать Мут", List.of("§7Нажмите для выбора срока")));
        gui.setItem(16, createGuiItem(Material.REDSTONE_BLOCK, "§4Забанить игрока", List.of("§7Нажмите для выбора срока / пермамента")));

        admin.openInventory(gui);
    }

    // --- 4. МЕНЮ ВИБОРУ ТЕРМІНУ (Срок) ---
    public void openDurationMenu(Player admin, String punishType) {
        pendingPunishmentType.put(admin.getUniqueId(), punishType);
        Inventory gui = Bukkit.createInventory(null, 27, DURATION_PREFIX + punishType);
        fillBorder(gui);

        gui.setItem(11, createGuiItem(Material.CLOCK, "§e1 Час (1h)", List.of("§7Длительность: 1 час")));
        gui.setItem(13, createGuiItem(Material.PAPER, "§61 День (1d)", List.of("§7Длительность: 24 часа")));
        gui.setItem(14, createGuiItem(Material.BOOK, "§c7 Дней (7d)", List.of("§7Длительность: 1 неделя")));
        gui.setItem(16, createGuiItem(Material.NETHERITE_BLOCK, "§4Перманентно (Perm)", List.of("§4Навсегда")));

        admin.openInventory(gui);
    }

    // --- 5. МЕНЮ РОЗБАНУ (Список заблокованих) ---
    public void openUnbanMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, UNBAN_TITLE);
        fillBorder(gui);

        int slot = 10;
        for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
            if (slot >= 44) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getTarget());
                meta.setOwningPlayer(target);
                meta.setDisplayName("§c" + entry.getTarget());
                meta.setLore(List.of(
                        "§7Причина: §f" + entry.getReason(),
                        "§7Кем выдано: §e" + entry.getSource(),
                        "",
                        "§2ЛКМ — Разбанить игрока!"
                ));
                head.setItemMeta(meta);
            }
            gui.setItem(slot, head);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // Перехід на наступний рядок усередині рамки
        }

        gui.setItem(53, createGuiItem(Material.BARRIER, "§cНазад в главное меню"));
        player.openInventory(gui);
    }

    // --- 6. ОБРОБКА КЛІКІВ У GUI ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        Inventory clickedInv = event.getClickedInventory();

        if (clickedInv == null) return;

        // Головне меню
        if (title.equals(MAIN_TITLE)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> openPlayerListMenu(player);
                case 12 -> openMainMenu(player);
                case 14 -> {
                    World world = player.getWorld();
                    if (world.getTime() > 13000) world.setTime(1000);
                    else world.setTime(13000);
                    player.sendMessage(ChatColor.YELLOW + "[OPPanel] Время изменено!");
                }
                case 15 -> openUnbanMenu(player); // Відкрити меню розбану
                case 16 -> {
                    if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
                    else player.setGameMode(GameMode.SPECTATOR);
                    openMainMenu(player);
                }
            }
        } 
        // Вибір гравця
        else if (title.equals(PLAYERS_TITLE)) {
            event.setCancelled(true);
            if (event.getSlot() == 53) {
                openMainMenu(player);
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) item.getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    if (target != null) openPunishMenu(player, target);
                    else player.sendMessage(ChatColor.RED + "Игрок оффлайн!");
                }
            }
        } 
        // Меню дій над гравцем
        else if (title.startsWith(PUNISH_PREFIX)) {
            event.setCancelled(true);
            UUID targetUUID = selectedTarget.get(player.getUniqueId());
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;

            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Игрок вышел из игры!");
                player.closeInventory();
                return;
            }

            switch (event.getSlot()) {
                case 10 -> {
                    player.teleport(target.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Вы телепортировались к " + target.getName());
                }
                case 11 -> {
                    target.teleport(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Игрок телепортирован к вам.");
                }
                case 12 -> player.openInventory(target.getInventory());
                case 13 -> {
                    target.kickPlayer("§cВы были кикнуты администратором.\n§7Причина: Нарушение правил");
                    player.sendMessage(ChatColor.YELLOW + "Игрок " + target.getName() + " кикнут.");
                    openPlayerListMenu(player);
                }
                case 14 -> openDurationMenu(player, "Warn");  // Вибір терміну для Варна
                case 15 -> openDurationMenu(player, "Mute");  // Вибір терміну для Мута
                case 16 -> openDurationMenu(player, "Ban");   // Вибір терміну для Бана
            }
        } 
        // Меню вибору терміну (Срок)
        else if (title.startsWith(DURATION_PREFIX)) {
            event.setCancelled(true);
            UUID targetUUID = selectedTarget.get(player.getUniqueId());
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;
            String type = pendingPunishmentType.get(player.getUniqueId());

            if (target == null || type == null) {
                player.closeInventory();
                return;
            }

            String duration = switch (event.getSlot()) {
                case 11 -> "1h";
                case 13 -> "1d";
                case 14 -> "7d";
                case 16 -> "perm";
                default -> null;
            };

            if (duration != null) {
                switch (type) {
                    case "Warn" -> {
                        player.performCommand("warn " + target.getName() + " " + duration + " Нарушение правил");
                        player.sendMessage(ChatColor.GOLD + "Выдан варн (" + duration + ") игроку " + target.getName());
                    }
                    case "Mute" -> {
                        player.performCommand("mute " + target.getName() + " " + duration + " Нарушение правил");
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "Выдан мут (" + duration + ") игроку " + target.getName());
                    }
                    case "Ban" -> {
                        if (duration.equals("perm")) {
                            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), "Нарушение правил ChaosSMP", null, player.getName());
                            target.kickPlayer("§cВы навсегда забанены на сервере!");
                        } else {
                            player.performCommand("tempban " + target.getName() + " " + duration + " Нарушение правил");
                        }
                        player.sendMessage(ChatColor.RED + "Игрок " + target.getName() + " забанен (" + duration + ").");
                    }
                }
                openPlayerListMenu(player);
            }
        } 
        // Меню розбану гравців
        else if (title.equals(UNBAN_TITLE)) {
            event.setCancelled(true);
            if (event.getSlot() == 53) {
                openMainMenu(player);
                return;
            }

            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) item.getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    String targetName = meta.getOwningPlayer().getName();
                    if (targetName != null) {
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(targetName);
                        player.sendMessage(ChatColor.GREEN + "Игрок " + targetName + " успешно разбанен!");
                        openUnbanMenu(player); // Оновити список
                    }
                }
            }
        }
    }

    private void fillBorder(Inventory gui) {
        ItemStack glass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < gui.getSize(); i++) {
            if (i < 9 || i >= gui.getSize() - 9 || i % 9 == 0 || (i + 1) % 9 == 0) {
                gui.setItem(i, glass);
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuiItem(Material material, String name) {
        return createGuiItem(material, name, null);
    }
}
