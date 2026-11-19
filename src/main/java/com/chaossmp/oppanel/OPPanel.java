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
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class OPPanel extends JavaPlugin implements Listener, CommandExecutor {

    private final String DESKTOP_TITLE = "§8» §4§lOPPanel v7.0 §7| Рабочий Стол";

    private final String FOLDER_PLAYERS = "§8[Модуль] §a§lУправление Игроками";
    private final String FOLDER_SERVER = "§8[Модуль] §4§lУправление Сервером";
    private final String FOLDER_ANTILAG = "§8[Модуль] §e§lАнти-Лаг и Доктор";
    private final String FOLDER_WORLDS = "§8[Модуль] §d§lМиры и Измерения";
    private final String FOLDER_FILES = "§8[Система] §6§lФайлы Сервера";
    private final String FOLDER_CONSOLE = "§8[Система] §b§lКонсоль и Логи";
    private final String FOLDER_SETTINGS = "§8[Система] §5§lНастройки OPPanel";

    private final String SUBFOLDER_PLUGINS = "§8[Папка] §3§lАктивные Плагины";
    private final String SUBFOLDER_ADMIN_TOOLS = "§8[Папка] §9§lИнструменты Админа";
    private final String SUBFOLDER_PANEL_CONFIG = "§8[Папка] §d§lПараметры Панели";

    private final String PLAYERS_LIST = "§8» §c§lВыбор Игрока";
    private final String PUNISH_PREFIX = "§8» §c§lМеню: ";
    private final String DURATION_PREFIX = "§8» §e§lСрок: ";
    private final String WEATHER_TITLE = "§8» §b§lВремя и Погода";

    private final Map<UUID, UUID> selectedTarget = new HashMap<>();
    private final Map<UUID, String> pendingPunishmentType = new HashMap<>();
    private final Map<UUID, File> currentBrowseDir = new HashMap<>();
    private final Set<UUID> awaitingConsoleCommand = new HashSet<>();

    private final List<Double> tpsHistory = new ArrayList<>();

    private final Set<UUID> adminNotificationsDisabled = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> godModeAdmins = new HashSet<>();
    private final Set<UUID> vanishedAdmins = new HashSet<>();

    private static boolean chatMuted = false;
    private static boolean maintenanceMode = false;
    private static boolean soundEffectsEnabled = true;
    private static boolean autoDoctorEnabled = true;
    private static double doctorTpsThreshold = 15.5;
    private static int mobChunkCap = 25;

    private ItemStack cachedBorderPane;
    private ItemStack cachedAccentPane;

    @Override
    public void onEnable() {
        cachedBorderPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        cachedAccentPane = createGuiItem(Material.CYAN_STAINED_GLASS_PANE, "§8▪");

        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("oppanel") != null) {
            getCommand("oppanel").setExecutor(this);
        }

        startServerDoctorTask();
        getLogger().info("OPPanel v7.0 успешно загружен!");
    }

    @Override
    public void onDisable() {
        selectedTarget.clear();
        pendingPunishmentType.clear();
        currentBrowseDir.clear();
        awaitingConsoleCommand.clear();
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

    private void playClick(Player player, float pitch) {
        if (soundEffectsEnabled) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, pitch);
        }
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
        UUID uuid = player.getUniqueId();

        if (awaitingConsoleCommand.remove(uuid)) {
            event.setCancelled(true);
            String cmd = event.getMessage().trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            final String finalCmd = cmd;
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                player.sendMessage("§b[OPPanel Console] §7Выполнено: §f/" + finalCmd);
            });
            return;
        }

        if (chatMuted && !hasAccess(player)) {
            event.setCancelled(true);
            player.sendMessage("§c[OPPanel] Чат заблокирован администратором.");
        }
    }

    private void startServerDoctorTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            double currentTps = Bukkit.getTPS()[0];
            tpsHistory.add(currentTps);
            if (tpsHistory.size() > 8) tpsHistory.remove(0);

            if (!autoDoctorEnabled) return;

            if (currentTps < 1.0 || currentTps < doctorTpsThreshold) {
                String report = executeSmartDoctor(currentTps);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (hasAccess(p) && !adminNotificationsDisabled.contains(p.getUniqueId())) {
                        p.sendMessage(report);
                    }
                }
            }
        }, 100L, 100L);
    }

    /**
     * Якщо TPS < 1.0 (наприклад, 0.43) — критична очистка: видаляє ВСІХ мобів крім гравців
     * та ВСІ предмети на землі.
     * Якщо TPS >= 1.0 — проста оптимізація: чистить зайві предмети й надлишок мобів.
     */
    public String executeSmartDoctor(double currentTps) {
        int itemsRemoved = 0;
        int mobsRemoved = 0;
        boolean criticalMode = currentTps < 1.0;

        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (criticalMode || !isValuable(item.getItemStack().getType())) {
                    item.remove();
                    itemsRemoved++;
                }
            }

            if (criticalMode) {
                for (LivingEntity living : world.getLivingEntities()) {
                    if (!(living instanceof Player)) {
                        living.remove();
                        mobsRemoved++;
                    }
                }
            } else {
                for (Chunk chunk : world.getLoadedChunks()) {
                    int livingCount = 0;
                    for (Entity entity : chunk.getEntities()) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            livingCount++;
                            if (livingCount > mobChunkCap && entity.getCustomName() == null) {
                                entity.remove();
                                mobsRemoved++;
                            }
                        }
                    }
                }
            }
        }

        if (criticalMode) {
            System.gc();
            return "§4§l[OPPanel Doctor КРИТИЧ] §cTPS " + String.format("%.2f", currentTps) +
                    " < 1.0! §7Удалены ВСЕ мобы (кроме игроков): §e" + mobsRemoved +
                    "§7, все предметы: §e" + itemsRemoved + " §7+ вызван GC.";
        }

        return "§e[OPPanel Doctor] §7TPS: §a" + String.format("%.2f", currentTps) +
                " §7| Оптимизация: удалено предметов §e" + itemsRemoved +
                "§7, лишних мобов §e" + mobsRemoved;
    }

    public String executeUltimateClean() {
        return executeSmartDoctor(Bukkit.getTPS()[0]);
    }

    private boolean isValuable(Material type) {
        String name = type.name();
        return name.contains("DIAMOND") || name.contains("NETHERITE") ||
                type == Material.NETHER_STAR || type == Material.TOTEM_OF_UNDYING ||
                type == Material.ELYTRA || type == Material.ENCHANTED_GOLDEN_APPLE ||
                type == Material.BEACON || type == Material.SHULKER_BOX;
    }

    private File getServerRoot() {
        return getDataFolder().getParentFile().getParentFile();
    }

    private boolean isInsideServerRoot(File target) {
        try {
            Path root = getServerRoot().toPath().toRealPath();
            Path check = target.toPath().toRealPath();
            return check.startsWith(root);
        } catch (IOException e) {
            return false;
        }
    }

    // ==================== МЕНЮ І НАВІГАЦІЯ ====================

    public void openDesktop(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 36, DESKTOP_TITLE);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.PLAYER_HEAD, FOLDER_PLAYERS,
                List.of("§7Список онлайн-игроков,", "§7телепорт, инвентарь, заморозка, баны.")));
        gui.setItem(11, createGuiItem(Material.REDSTONE_BLOCK, FOLDER_SERVER,
                List.of("§7Рестарт, выключение, техобслуживание,", "§7управление глобальным чатом.")));
        gui.setItem(12, createGuiItem(Material.EXPERIENCE_BOTTLE, FOLDER_ANTILAG,
                List.of("§7Мониторинг TPS/ОЗУ, история замеров,", "§7умный Доктор (<1.0 TPS чистит всё).")));
        gui.setItem(13, createGuiItem(Material.GRASS_BLOCK, FOLDER_WORLDS,
                List.of("§7Телепорт по измерениям,", "§7быстрый переход в Незер/Энд.")));
        gui.setItem(14, createGuiItem(Material.CHEST, FOLDER_FILES,
                List.of("§7Проводник по файлам сервера,", "§7чтение конфигов, логов и папок.")));
        gui.setItem(15, createGuiItem(Material.COMMAND_BLOCK, FOLDER_CONSOLE,
                List.of("§7Просмотр latest.log, отправка команд", "§7от имени консоли, быстрые действия.")));
        gui.setItem(16, createGuiItem(Material.COMPARATOR, FOLDER_SETTINGS,
                List.of("§7Настройки OPPanel, тумблеры,", "§7плагины, погода и инструменты админа.")));

        double tps = Bukkit.getTPS()[0];
        long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
        long maxMb = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        gui.setItem(31, createGuiItem(Material.NETHER_STAR, "§b§lСостояние Системы", List.of(
                "§7TPS: §a" + String.format("%.2f", tps),
                "§7ОЗУ: §b" + usedMb + "MB / " + maxMb + "MB",
                "§7Онлайн: §e" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(),
                "§7Авто-Доктор: " + (autoDoctorEnabled ? "§aВКЛ" : "§cВЫКЛ")
        )));

        player.openInventory(gui);
        playClick(player, 1.0f);
    }

    public void openSettingsFolder(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, FOLDER_SETTINGS);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.REPEATER, SUBFOLDER_PANEL_CONFIG,
                List.of("§7Тумблеры OPPanel, порог Доктора,", "§7лимит мобов и звуки интерфейса.")));
        gui.setItem(12, createGuiItem(Material.BOOKSHELF, SUBFOLDER_PLUGINS,
                List.of("§7Просмотр активированных плагинов,", "§7версия ядра и состояние памяти.")));
        gui.setItem(14, createGuiItem(Material.CLOCK, WEATHER_TITLE,
                List.of("§7Управление временем суток", "§7и погодными условиями.")));
        gui.setItem(16, createGuiItem(Material.NETHERITE_INGOT, SUBFOLDER_ADMIN_TOOLS,
                List.of("§7Godmode, Vanish, Режимы игры,", "§7получение Жезла администратора.")));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад на Рабочий Стол"));

        player.openInventory(gui);
        playClick(player, 1.1f);
    }

    public void openPanelConfigMenu(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, SUBFOLDER_PANEL_CONFIG);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(autoDoctorEnabled ? Material.LIME_DYE : Material.RED_DYE,
                "§e§lАвто-Доктор", List.of(
                        "§7Статус: " + (autoDoctorEnabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН"),
                        "§7При TPS < 1.0 сносит всех мобов (кроме игроков) и дроп."
                )));

        gui.setItem(11, createGuiItem(soundEffectsEnabled ? Material.NOTE_BLOCK : Material.GRAY_DYE,
                "§b§lЗвуки Интерфейса", List.of(
                        "§7Статус: " + (soundEffectsEnabled ? "§aВКЛЮЧЕНЫ" : "§cВЫКЛЮЧЕНЫ"),
                        "§7Кликните, чтобы переключить."
                )));

        gui.setItem(13, createGuiItem(Material.COMPARATOR,
                "§6§lПорог Доктора: §f" + String.format("%.1f", doctorTpsThreshold) + " TPS", List.of(
                        "§7ЛКМ: §a+0.5 TPS",
                        "§7ПКМ: §c-0.5 TPS"
                )));

        gui.setItem(15, createGuiItem(Material.BEEF,
                "§d§lЛимит мобов на чанк: §f" + mobChunkCap, List.of(
                        "§7ЛКМ: §a+5",
                        "§7ПКМ: §c-5 (мин. 5)"
                )));

        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад в Настройки"));
        player.openInventory(gui);
        playClick(player, 1.1f);
    }

    public void openFilesFolder(Player player, File dir) {
        if (!hasAccess(player)) return;
        if (dir == null || !dir.exists() || !dir.isDirectory() || !isInsideServerRoot(dir)) {
            dir = getServerRoot();
        }
        currentBrowseDir.put(player.getUniqueId(), dir);

        Inventory gui = Bukkit.createInventory(null, 54, FOLDER_FILES);
        File[] entries = dir.listFiles();
        int slot = 0;

        if (entries != null) {
            Arrays.sort(entries, Comparator.comparing(File::isDirectory).reversed().thenComparing(File::getName));
            for (File f : entries) {
                if (slot >= 45) break;
                Material mat = f.isDirectory() ? Material.CHEST : Material.PAPER;
                String prefix = f.isDirectory() ? "§6[Папка] §e" : "§f[Файл] §7";
                long sizeKb = f.isFile() ? f.length() / 1024 : 0;
                List<String> lore = f.isDirectory()
                        ? List.of("§7ЛКМ: открыть папку")
                        : List.of("§7Размер: §b" + sizeKb + " KB", "§7ЛКМ: вывести содержимое в чат");
                gui.setItem(slot++, createGuiItem(mat, prefix + f.getName(), lore));
            }
        }

        File parent = dir.getParentFile();
        if (parent != null && isInsideServerRoot(parent)) {
            gui.setItem(45, createGuiItem(Material.ARROW, "§eВверх: §7" + parent.getName()));
        }
        gui.setItem(49, createGuiItem(Material.MAP, "§aПуть: §f" + dir.getName(), List.of("§7" + dir.getAbsolutePath())));
        gui.setItem(53, createGuiItem(Material.BARRIER, "§cНазад на Рабочий Стол"));

        player.openInventory(gui);
        playClick(player, 1.1f);
    }

    public void openConsoleMenu(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, FOLDER_CONSOLE);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.WRITABLE_BOOK, "§b§lОтправить команду консоли",
                List.of("§7Нажмите и напишите команду в чат", "§7(выполнится от имени CONSOLE).")));
        gui.setItem(12, createGuiItem(Material.PAPER, "§e§lПоказать последние логи (чат)",
                List.of("§7Выведет последние 20 строк logs/latest.log")));
        gui.setItem(14, createGuiItem(Material.EMERALD, "§a§lСохранить миры (save-all)",
                List.of("§7Принудительно сохраняет данные миров.")));
        gui.setItem(16, createGuiItem(Material.REDSTONE, "§c§lВызвать сборку мусора (GC)",
                List.of("§7Очистка памяти JVM.")));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад на Рабочий Стол"));

        player.openInventory(gui);
        playClick(player, 1.1f);
    }

    public void openPluginsSubfolder(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, SUBFOLDER_PLUGINS);
        fillBorder(gui);

        int pluginCount = Bukkit.getPluginManager().getPlugins().length;
        List<String> pluginLore = new ArrayList<>();
        pluginLore.add("§7Активных плагинов: §a" + pluginCount);
        pluginLore.add("§7Ядро: §f" + Bukkit.getVersion());
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (pluginLore.size() < 13) {
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
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад на Рабочий Стол"));

        player.openInventory(gui);
    }

    public void openAntilagFolder(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, FOLDER_ANTILAG);
        fillBorder(gui);

        double currentTps = Bukkit.getTPS()[0];
        double ramUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024.0 / 1024.0;
        double ramMax = Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0;

        List<String> tpsLore = new ArrayList<>();
        tpsLore.add("§7Текущий TPS: §a" + String.format("%.2f", currentTps));
        tpsLore.add("§7Использование ОЗУ: §b" + (int) ramUsed + "MB / " + (int) ramMax + "MB");
        tpsLore.add("§7Режим при TPS < 1.0: §cСнос всех мобов (кроме игроков) + дроп");
        tpsLore.add("§7История замера TPS:");
        for (double t : tpsHistory) {
            tpsLore.add("§8- §a" + String.format("%.2f", t));
        }

        gui.setItem(11, createGuiItem(Material.PAPER, "§3§lМониторинг ресурсов", tpsLore));
        gui.setItem(15, createGuiItem(Material.EXPERIENCE_BOTTLE, "§a§lЗапустить Доктор сейчас",
                List.of("§7При TPS < 1.0 — полная зачистка мобов и дропа,", "§7при TPS >= 1.0 — стандартная оптимизация.")));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад на Рабочий Стол"));

        player.openInventory(gui);
    }

    public void openWorldTpMenu(Player player) {
        if (!hasAccess(player)) return;
        Inventory gui = Bukkit.createInventory(null, 27, FOLDER_WORLDS);
        fillBorder(gui);

        gui.setItem(10, createGuiItem(Material.GRASS_BLOCK, "§a§lОверворлд (Главный мир)", List.of("§7Телепорт на спавн")));
        gui.setItem(12, createGuiItem(Material.NETHERRACK, "§c§lНезер (Нижний мир)", List.of("§7Телепорт в ад")));
        gui.setItem(14, createGuiItem(Material.END_STONE, "§e§lЭнд (Край)", List.of("§7Телепорт к дракону")));
        gui.setItem(22, createGuiItem(Material.BARRIER, "§cНазад на Рабочий Стол"));

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
        gui.setItem(53, createGuiItem(Material.BARRIER, "§cНазад на Рабочий Стол"));
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

    // ==================== ОБРОБКА КЛІКІВ ====================

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
                case 14 -> openFilesFolder(player, getServerRoot());
                case 15 -> openConsoleMenu(player);
                case 16 -> openSettingsFolder(player);
            }
        } else if (title.equals(FOLDER_SETTINGS)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> openPanelConfigMenu(player);
                case 12 -> openPluginsSubfolder(player);
                case 14 -> openWeatherMenu(player);
                case 16 -> openAdminToolsSubfolder(player);
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(SUBFOLDER_PANEL_CONFIG)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> {
                    autoDoctorEnabled = !autoDoctorEnabled;
                    openPanelConfigMenu(player);
                }
                case 11 -> {
                    soundEffectsEnabled = !soundEffectsEnabled;
                    openPanelConfigMenu(player);
                }
                case 13 -> {
                    doctorTpsThreshold = event.isRightClick()
                            ? Math.max(1.0, doctorTpsThreshold - 0.5)
                            : Math.min(20.0, doctorTpsThreshold + 0.5);
                    openPanelConfigMenu(player);
                }
                case 15 -> {
                    mobChunkCap = event.isRightClick()
                            ? Math.max(5, mobChunkCap - 5)
                            : Math.min(200, mobChunkCap + 5);
                    openPanelConfigMenu(player);
                }
                case 22 -> openSettingsFolder(player);
            }
        } else if (title.equals(FOLDER_FILES)) {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot == 53) {
                openDesktop(player);
                return;
            }
            File currentDir = currentBrowseDir.getOrDefault(player.getUniqueId(), getServerRoot());
            if (slot == 45) {
                File parent = currentDir.getParentFile();
                if (parent != null && isInsideServerRoot(parent)) {
                    openFilesFolder(player, parent);
                }
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;
            String rawName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (rawName.startsWith("[Папка] ")) {
                String folderName = rawName.substring("[Папка] ".length());
                File nextDir = new File(currentDir, folderName);
                if (nextDir.isDirectory() && isInsideServerRoot(nextDir)) {
                    openFilesFolder(player, nextDir);
                }
            } else if (rawName.startsWith("[Файл] ")) {
                String fileName = rawName.substring("[Файл] ".length());
                File targetFile = new File(currentDir, fileName);
                if (targetFile.isFile() && isInsideServerRoot(targetFile)) {
                    sendFilePreviewToChat(player, targetFile);
                }
            }
        } else if (title.equals(FOLDER_CONSOLE)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> {
                    awaitingConsoleCommand.add(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage("§b[OPPanel Console] §eВведите команду в чат (без / или с /):");
                }
                case 12 -> {
                    player.closeInventory();
                    sendLatestLogsToChat(player);
                }
                case 14 -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
                    player.sendMessage("§a[OPPanel Console] §7Выполнено сохранение миров.");
                }
                case 16 -> {
                    System.gc();
                    player.sendMessage("§a[OPPanel Console] §7Запущен сборщик мусора (GC).");
                }
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
                case 14 -> {
                    giveAdminWand(player);
                    player.closeInventory();
                }
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
                String rep = executeSmartDoctor(Bukkit.getTPS()[0]);
                player.sendMessage(rep);
                openAntilagFolder(player);
            } else if (event.getSlot() == 22) openDesktop(player);
        } else if (title.equals(FOLDER_WORLDS)) {
            event.setCancelled(true);
            switch (event.getSlot()) {
                case 10 -> {
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                    player.closeInventory();
                }
                case 12 -> {
                    World n = Bukkit.getWorld("world_nether");
                    if (n != null) player.teleport(n.getSpawnLocation());
                    player.closeInventory();
                }
                case 14 -> {
                    World e = Bukkit.getWorld("world_the_end");
                    if (e != null) player.teleport(e.getSpawnLocation());
                    player.closeInventory();
                }
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(WEATHER_TITLE)) {
            event.setCancelled(true);
            World w = player.getWorld();
            switch (event.getSlot()) {
                case 10 -> w.setTime(1000);
                case 12 -> w.setTime(13000);
                case 14 -> {
                    w.setStorm(false);
                    w.setThundering(false);
                }
                case 16 -> w.setStorm(true);
                case 22 -> openSettingsFolder(player);
            }
        } else if (title.equals(PLAYERS_LIST)) {
            event.setCancelled(true);
            if (event.getSlot() == 53) {
                openDesktop(player);
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta meta) {
                if (meta.getOwningPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    if (target != null) openPunishMenu(player, target);
                }
            }
        } else if (title.startsWith(PUNISH_PREFIX)) {
            event.setCancelled(true);
            if (event.getSlot() == 22) {
                openPlayerListMenu(player);
                return;
            }
            UUID targetUUID = selectedTarget.get(player.getUniqueId());
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;
            if (target == null || !target.isOnline()) {
                player.closeInventory();
                return;
            }

            switch (event.getSlot()) {
                case 9 -> player.teleport(target.getLocation());
                case 10 -> target.teleport(player.getLocation());
                case 11 -> player.openInventory(target.getInventory());
                case 12 -> {
                    target.setHealth(20.0);
                    target.setFoodLevel(20);
                    player.sendMessage("§aИгрок вылечен.");
                }
                case 13 -> {
                    if (frozenPlayers.contains(target.getUniqueId())) frozenPlayers.remove(target.getUniqueId());
                    else frozenPlayers.add(target.getUniqueId());
                    openPunishMenu(player, target);
                }
                case 14 -> {
                    target.kickPlayer("§cКикнут администратором.");
                    openPlayerListMenu(player);
                }
                case 15 -> openDurationMenu(player, event.isRightClick() ? "Warn" : "Mute");
                case 16 -> openDurationMenu(player, "Ban");
            }
        } else if (title.startsWith(DURATION_PREFIX)) {
            event.setCancelled(true);
            UUID targetUUID = selectedTarget.get(player.getUniqueId());
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;
            String type = pendingPunishmentType.get(player.getUniqueId());
            if (target == null || type == null) {
                player.closeInventory();
                return;
            }

            String duration = switch (event.getSlot()) {
                case 10 -> "1m";
                case 11 -> "1h";
                case 13 -> "1d";
                case 14 -> "7d";
                case 15 -> "30d";
                case 16 -> "perm";
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

    private void sendFilePreviewToChat(Player player, File file) {
        if (file.length() > 64 * 1024) {
            player.sendMessage("§c[OPPanel Files] Файл слишком большой (>64KB) для вывода в чат.");
            return;
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")
                || lower.endsWith(".json") || lower.endsWith(".txt") || lower.endsWith(".log")
                || lower.endsWith(".toml") || lower.endsWith(".conf"))) {
            player.sendMessage("§e[OPPanel Files] Выбран бинарный/нетекстовый файл: §f" + file.getName());
            return;
        }

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            player.closeInventory();
            player.sendMessage("§6§m---------- §e" + file.getName() + " §6§m----------");
            int start = Math.max(0, lines.size() - 18);
            for (int i = start; i < lines.size(); i++) {
                player.sendMessage("§7" + lines.get(i));
            }
        } catch (IOException ex) {
            player.sendMessage("§c[OPPanel Files] Ошибка чтения файла: " + ex.getMessage());
        }
    }

    private void sendLatestLogsToChat(Player player) {
        File logFile = new File(getServerRoot(), "logs/latest.log");
        if (!logFile.exists()) {
            player.sendMessage("§c[OPPanel Console] Файл logs/latest.log не найден.");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
            player.sendMessage("§b§m------ §3Последние строки latest.log §b§m------");
            int start = Math.max(0, lines.size() - 20);
            for (int i = start; i < lines.size(); i++) {
                player.sendMessage("§8| §7" + lines.get(i));
            }
        } catch (IOException ex) {
            player.sendMessage("§c[OPPanel Console] Ошибка чтения логов: " + ex.getMessage());
        }
    }

    private void fillBorder(Inventory gui) {
        int size = gui.getSize();
        for (int i = 0; i < size; i++) {
            boolean edge = i < 9 || i >= size - 9 || i % 9 == 0 || (i + 1) % 9 == 0;
            if (edge) {
                gui.setItem(i, (i % 2 == 0) ? cachedBorderPane : cachedAccentPane);
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
