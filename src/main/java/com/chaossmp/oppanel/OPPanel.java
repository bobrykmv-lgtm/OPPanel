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

    private static final Set<String> READABLE_EXTENSIONS = Set.of(
            "yml", "yaml", "json", "properties", "txt", "log", "toml", "conf", "cfg", "sk"
    );

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

            if (currentTps < doctorTpsThreshold) {
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
     * Проста оптимізація: прибирає звичайні предмети на землі (крім цінних)
     * та надлишок неіменованих мобів понад mobChunkCap у завантажених чанках.
     */
    public String executeSmartDoctor(double currentTps) {
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
                        if (livingCount > mobChunkCap && entity.getCustomName() == null) {
                            entity.remove();
                            mobsRemoved++;
                        }
                    }
                }
            }
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
        File dataFolder = getDataFolder().getAbsoluteFile();
        File pluginsDir = dataFolder.getParentFile();
        if (pluginsDir != null && pluginsDir.getParentFile() != null) {
            return pluginsDir.getParentFile();
        }
        return new File(".").getAbsoluteFile();
    }

    private boolean isInsideServerRoot(File target) {
        try {
            Path root = getServerRoot().toPath().normalize().toAbsolutePath();
            Path check = target.toPath().normalize().toAbsolutePath();
            return check.startsWith(root);
        } catch (Exception e) {
            return false;
        }
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void inspectFile(Player player, File file) {
        if (!file.exists() || !file.isFile() || !isInsideServerRoot(file)) {
            player.sendMessage("§c[OPPanel] Файл недоступен или не найден.");
            return;
        }

        String ext = getExtension(file.getName());
        if (!READABLE_EXTENSIONS.contains(ext) && file.length() > 65536) {
            player.sendMessage("§e[OPPanel] Файл §f" + file.getName() + " §eслишком большой (" + (file.length() / 1024) + " KB) для чтения в чате.");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - 15);
            player.sendMessage("§6§m--------------------------------");
            player.sendMessage("§6Файл: §f" + file.getName() + " §7(строк: " + lines.size() + ")");
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.length() > 140) {
                    line = line.substring(0, 140) + "...";
                }
                player.sendMessage("§7" + line);
            }
            player.sendMessage("§6§m--------------------------------");
        } catch (IOException | RuntimeException e) {
            player.sendMessage("§c[OPPanel] Не удалось прочитать файл §f" + file.getName() + "§c: " + e.getMessage());
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
                List.of("§7Мониторинг TPS/ОЗУ, история замеров,", "§7умный Доктор (очистка дропа и мобов).")));
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
                        "§7Автоматически очищает лишний дроп и мобов при просадке TPS."
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
                List.of("§7Выведет последние строки logs/latest.log")));
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
        tpsLore.add("§7Режим: §eОчистка лишнего дропа и мобов в чанках");
        tpsLore.add("§7История замера TPS:");
        for (double t : tpsHistory) {
            tpsLore.add("§8- §a" + String.format("%.2f", t));
        }

        gui.setItem(11, createGuiItem(Material.PAPER, "§3§lМониторинг ресурсов", tpsLore));
        gui.setItem(15, createGuiItem(Material.EXPERIENCE_BOTTLE, "§a§lЗапустить Доктор сейчас",
                List.of("§7Очищает обычный дроп и лишних мобов сверх лимита.")));
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

    private void fillBorder(Inventory gui) {
        if (cachedBorderPane == null) {
            cachedBorderPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        }
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, cachedBorderPane);
        }
    }

    private ItemStack createGuiItem(Material material, String name) {
        return createGuiItem(material, name, null);
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== ОБРОБКА КЛІКІВ ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!hasAccess(player)) return;

        String title = event.getView().getTitle();
        if (event.getClickedInventory() == null) return;

        if (!title.equals(DESKTOP_TITLE) &&
                !title.equals(FOLDER_SETTINGS) &&
                !title.equals(SUBFOLDER_PANEL_CONFIG) &&
                !title.equals(FOLDER_FILES) &&
                !title.equals(FOLDER_CONSOLE) &&
                !title.equals(SUBFOLDER_PLUGINS) &&
                !title.equals(SUBFOLDER_ADMIN_TOOLS) &&
                !title.equals(FOLDER_SERVER) &&
                !title.equals(FOLDER_ANTILAG) &&
                !title.equals(FOLDER_WORLDS) &&
                !title.equals(PLAYERS_LIST) &&
                !title.startsWith(PUNISH_PREFIX) &&
                !title.startsWith(DURATION_PREFIX) &&
                !title.equals(WEATHER_TITLE)) {
            return;
        }

        event.setCancelled(true);
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getRawSlot();

        if (title.equals(DESKTOP_TITLE)) {
            switch (slot) {
                case 10 -> openPlayerListMenu(player);
                case 11 -> openServerControlMenu(player);
                case 12 -> openAntilagFolder(player);
                case 13 -> openWorldTpMenu(player);
                case 14 -> openFilesFolder(player, getServerRoot());
                case 15 -> openConsoleMenu(player);
                case 16 -> openSettingsFolder(player);
            }
        } else if (title.equals(FOLDER_SETTINGS)) {
            switch (slot) {
                case 10 -> openPanelConfigMenu(player);
                case 12 -> openPluginsSubfolder(player);
                case 14 -> openWeatherMenu(player);
                case 16 -> openAdminToolsSubfolder(player);
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(SUBFOLDER_PANEL_CONFIG)) {
            switch (slot) {
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
            File currentDir = currentBrowseDir.getOrDefault(player.getUniqueId(), getServerRoot());
            if (slot == 53) {
                openDesktop(player);
                return;
            }
            if (slot == 45) {
                File parent = currentDir.getParentFile();
                if (parent != null && isInsideServerRoot(parent)) {
                    openFilesFolder(player, parent);
                }
                return;
            }
            if (slot >= 0 && slot < 45) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;
                String rawName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                String targetName = rawName.replaceFirst("^\\[Папка\\]\\s*", "").replaceFirst("^\\[Файл\\]\\s*", "");
                File target = new File(currentDir, targetName);
                if (!isInsideServerRoot(target) || !target.exists()) {
                    player.sendMessage("§c[OPPanel] Путь недоступен.");
                    return;
                }
                if (target.isDirectory()) {
                    openFilesFolder(player, target);
                } else {
                    player.closeInventory();
                    inspectFile(player, target);
                }
            }
        } else if (title.equals(FOLDER_CONSOLE)) {
            switch (slot) {
                case 10 -> {
                    player.closeInventory();
                    awaitingConsoleCommand.add(player.getUniqueId());
                    player.sendMessage("§b[OPPanel Console] §fВведите команду в чат (без / или с /):");
                }
                case 12 -> {
                    player.closeInventory();
                    File latestLog = new File(getServerRoot(), "logs/latest.log");
                    inspectFile(player, latestLog);
                }
                case 14 -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
                case 16 -> {
                    System.gc();
                    player.sendMessage("§a[OPPanel] Выполнена очистка памяти GC.");
                }
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(SUBFOLDER_PLUGINS)) {
            if (slot == 22) openSettingsFolder(player);
        } else if (title.equals(SUBFOLDER_ADMIN_TOOLS)) {
            switch (slot) {
                case 10 -> {
                    if (godModeAdmins.contains(player.getUniqueId())) {
                        godModeAdmins.remove(player.getUniqueId());
                    } else {
                        godModeAdmins.add(player.getUniqueId());
                    }
                    openAdminToolsSubfolder(player);
                }
                case 11 -> {
                    UUID uuid = player.getUniqueId();
                    if (vanishedAdmins.contains(uuid)) {
                        vanishedAdmins.remove(uuid);
                        for (Player p : Bukkit.getOnlinePlayers()) p.showPlayer(this, player);
                    } else {
                        vanishedAdmins.add(uuid);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!hasAccess(p)) p.hidePlayer(this, player);
                        }
                    }
                    openAdminToolsSubfolder(player);
                }
                case 12 -> {
                    GameMode next = switch (player.getGameMode()) {
                        case SURVIVAL -> GameMode.CREATIVE;
                        case CREATIVE -> GameMode.ADVENTURE;
                        default -> GameMode.SURVIVAL;
                    };
                    player.setGameMode(next);
                    openAdminToolsSubfolder(player);
                }
                case 14 -> {
                    player.closeInventory();
                    giveAdminWand(player);
                }
                case 16 -> {
                    if (adminNotificationsDisabled.contains(player.getUniqueId())) {
                        adminNotificationsDisabled.remove(player.getUniqueId());
                    } else {
                        adminNotificationsDisabled.add(player.getUniqueId());
                    }
                    openAdminToolsSubfolder(player);
                }
                case 22 -> openSettingsFolder(player);
            }
        } else if (title.equals(FOLDER_SERVER)) {
            switch (slot) {
                case 10 -> {
                    maintenanceMode = !maintenanceMode;
                    openServerControlMenu(player);
                }
                case 12 -> {
                    chatMuted = !chatMuted;
                    openServerControlMenu(player);
                }
                case 13 -> {
                    for (int i = 0; i < 40; i++) Bukkit.broadcastMessage("");
                    Bukkit.broadcastMessage("§e[OPPanel] Чат очищен администратором.");
                }
                case 15 -> {
                    Bukkit.broadcastMessage("§c[OPPanel] Сервер перезапускается...");
                    Bukkit.spigot().restart();
                }
                case 16 -> {
                    Bukkit.broadcastMessage("§4[OPPanel] Сервер выключается...");
                    Bukkit.shutdown();
                }
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(FOLDER_ANTILAG)) {
            if (slot == 15) {
                String res = executeSmartDoctor(Bukkit.getTPS()[0]);
                player.sendMessage(res);
                openAntilagFolder(player);
            } else if (slot == 22) {
                openDesktop(player);
            }
        } else if (title.equals(FOLDER_WORLDS)) {
            switch (slot) {
                case 10 -> {
                    World w = Bukkit.getWorld("world");
                    if (w != null) player.teleport(w.getSpawnLocation());
                }
                case 12 -> {
                    World w = Bukkit.getWorld("world_nether");
                    if (w != null) player.teleport(w.getSpawnLocation());
                }
                case 14 -> {
                    World w = Bukkit.getWorld("world_the_end");
                    if (w != null) player.teleport(w.getSpawnLocation());
                }
                case 22 -> openDesktop(player);
            }
        } else if (title.equals(PLAYERS_LIST)) {
            if (slot == 53) {
                openDesktop(player);
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                Player target = Bukkit.getPlayerExact(name);
                if (target != null) {
                    openPunishMenu(player, target);
                }
            }
        } else if (title.startsWith(PUNISH_PREFIX)) {
            if (slot == 22) {
                openPlayerListMenu(player);
                return;
            }
            UUID targetUuid = selectedTarget.get(player.getUniqueId());
            Player target = targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;
            if (target == null) {
                player.sendMessage("§cИгрок вышел из сети.");
                openPlayerListMenu(player);
                return;
            }
            switch (slot) {
                case 9 -> player.teleport(target.getLocation());
                case 10 -> target.teleport(player.getLocation());
                case 11 -> player.openInventory(target.getInventory());
                case 12 -> {
                    target.setHealth(20.0);
                    target.setFoodLevel(20);
                    player.sendMessage("§aИгрок " + target.getName() + " вылечен.");
                }
                case 13 -> {
                    if (frozenPlayers.contains(target.getUniqueId())) {
                        frozenPlayers.remove(target.getUniqueId());
                        player.sendMessage("§eИгрок " + target.getName() + " разморожен.");
                    } else {
                        frozenPlayers.add(target.getUniqueId());
                        player.sendMessage("§bИгрок " + target.getName() + " заморожен.");
                    }
                }
                case 14 -> {
                    player.closeInventory();
                    target.kickPlayer("§cКикнут администратором.");
                }
                case 15 -> openDurationMenu(player, "mute");
                case 16 -> openDurationMenu(player, "ban");
            }
        } else if (title.startsWith(DURATION_PREFIX)) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String duration = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            String type = pendingPunishmentType.remove(player.getUniqueId());
            UUID targetUuid = selectedTarget.get(player.getUniqueId());
            Player target = targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;
            player.closeInventory();
            if (target != null && type != null) {
                if ("ban".equals(type)) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + target.getName() + " Нарушение правил (" + duration + ")");
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mute " + target.getName() + " " + duration + " Нарушение правил");
                }
            }
        } else if (title.equals(WEATHER_TITLE)) {
            World world = player.getWorld();
            switch (slot) {
                case 10 -> world.setTime(1000L);
                case 12 -> world.setTime(13000L);
                case 14 -> {
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case 16 -> {
                    world.setStorm(true);
                    world.setThundering(true);
                }
                case 22 -> openSettingsFolder(player);
            }
        }
    }
}
