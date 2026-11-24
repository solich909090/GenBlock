package pl.genblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class GenBlockPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey genKey, tierKey, shopKey;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, Integer> placed = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, Long> lastCollect = new HashMap<>();
    private static final int SHOP_SIZE = 54;
    private static final int SHOP_ITEMS = 45;

    @Override public void onEnable() {
        saveDefaultConfig();
        genKey = new NamespacedKey(this, "generator");
        tierKey = new NamespacedKey(this, "tier");
        shopKey = new NamespacedKey(this, "shop_item");
        loadBalances();
        Objects.requireNonNull(getCommand("sklepik")).setExecutor(this);
        Objects.requireNonNull(getCommand("genblock")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        long interval = Math.max(5, getConfig().getLong("generator-interval-seconds", 30));
        getServer().getScheduler().runTaskTimer(this, this::produce, interval * 20L, interval * 20L);
        getLogger().info("GenBlock uruchomiony. Poziomy generatorow: 100.");
    }

    @Override public void onDisable() { saveBalances(); }

    private void loadBalances() {
        if (!getConfig().isConfigurationSection("balances")) return;
        for (String id : Objects.requireNonNull(getConfig().getConfigurationSection("balances")).getKeys(false))
            try { balances.put(UUID.fromString(id), getConfig().getDouble("balances." + id)); } catch (IllegalArgumentException ignored) {}
    }
    private void saveBalances() {
        for (var e : balances.entrySet()) getConfig().set("balances." + e.getKey(), e.getValue());
        saveConfig();
    }
    private double money(Player p) { return balances.computeIfAbsent(p.getUniqueId(), k -> getConfig().getDouble("start-money", 500)); }
    private void setMoney(Player p, double value) { balances.put(p.getUniqueId(), value); }

    private ItemStack generatorItem(int tier) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Generator GenBlock poziom " + tier, NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Postaw, aby generowac surowce.", NamedTextColor.GRAY),
                Component.text("Poziom: " + tier, NamedTextColor.YELLOW)));
        meta.getPersistentDataContainer().set(genKey, PersistentDataType.BYTE, (byte)1);
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
        item.setItemMeta(meta);
        return item;
    }
    private boolean isGeneratorItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(genKey, PersistentDataType.BYTE);
    }
    private int tierOf(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 1);
    }

    @EventHandler public void onPlace(BlockPlaceEvent e) {
        if (!isGeneratorItem(e.getItemInHand())) return;
        Block b = e.getBlockPlaced();
        if (!(b.getState() instanceof TileState tile)) { e.setCancelled(true); e.getPlayer().sendMessage("Ten blok nie obsluguje generatora."); return; }
        int tier = tierOf(e.getItemInHand());
        tile.getPersistentDataContainer().set(genKey, PersistentDataType.BYTE, (byte)1);
        tile.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
        tile.update(true, false);
        placed.merge(e.getPlayer().getUniqueId(), 1, Integer::sum);
        e.getPlayer().sendMessage("Postawiono generator poziomu " + tier + ".");
    }

    @EventHandler public void onBreak(BlockBreakEvent e) {
        if (!(e.getBlock().getState() instanceof TileState tile)) return;
        if (!tile.getPersistentDataContainer().has(genKey, PersistentDataType.BYTE)) return;
        e.setDropItems(false);
        int tier = tile.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 1);
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), generatorItem(tier));
        placed.computeIfPresent(e.getPlayer().getUniqueId(), (k,v) -> Math.max(0, v-1));
    }

    @EventHandler public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (!(e.getClickedBlock().getState() instanceof TileState tile)) return;
        if (!tile.getPersistentDataContainer().has(genKey, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        long previous = lastCollect.getOrDefault(p.getUniqueId(), 0L);
        if (now - previous < 700) return;
        lastCollect.put(p.getUniqueId(), now);
        int tier = tile.getPersistentDataContainer().getOrDefault(tierKey, PersistentDataType.INTEGER, 1);
        Material reward = rewardFor(tier);
        HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(new ItemStack(reward, 1 + tier / 20));
        overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
        p.sendMessage("Generator poziomu " + tier + ": otrzymujesz " + reward.name() + ".");
    }

    private Material rewardFor(int tier) {
        Material[] rewards = {Material.COBBLESTONE, Material.COAL, Material.RAW_IRON, Material.RAW_GOLD,
                Material.REDSTONE, Material.LAPIS_LAZULI, Material.QUARTZ, Material.AMETHYST_SHARD,
                Material.DIAMOND, Material.EMERALD, Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP};
        return rewards[Math.min(rewards.length - 1, (tier - 1) / 9)];
    }

    private void produce() {
        for (World world : Bukkit.getWorlds()) for (var chunk : world.getLoadedChunks()) {
            for (var state : chunk.getTileEntities()) {
                if (!(state instanceof TileState tile)) continue;
                var pdc = tile.getPersistentDataContainer();
                if (!pdc.has(genKey, PersistentDataType.BYTE)) continue;
                int tier = pdc.getOrDefault(tierKey, PersistentDataType.INTEGER, 1);
                Material reward = rewardFor(tier);
                world.dropItemNaturally(state.getLocation().add(.5, 1, .5), new ItemStack(reward, 1 + tier / 25));
            }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sklepik")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Ta komende moze uzyc gracz."); return true; }
            pages.put(p.getUniqueId(), 0); openShop(p); return true;
        }
        if (!sender.hasPermission("genblock.admin")) { sender.sendMessage("Brak uprawnien."); return true; }
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            int tier;
            try { tier = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage("Poziom musi byc liczba 1-100."); return true; }
            if (target == null || tier < 1 || tier > 100) { sender.sendMessage("Uzycie: /genblock give <gracz> <1-100>"); return true; }
            target.getInventory().addItem(generatorItem(tier));
            sender.sendMessage("Dano generator poziomu " + tier + " graczowi " + target.getName() + ".");
            return true;
        }
        sender.sendMessage("Komendy: /sklepik, /genblock give <gracz> <1-100>");
        return true;
    }

    private List<Material> shopMaterials() {
        return Arrays.stream(Material.values()).filter(Material::isItem).filter(m -> m != Material.AIR)
                .filter(m -> !m.isLegacy()).sorted(Comparator.comparing(Enum::name)).toList();
    }
    private double price(Material m) {
        String path = "item-prices." + m.name();
        if (getConfig().contains(path)) return Math.max(1, getConfig().getDouble(path));
        String n = m.name();
        double base = 10;
        if (n.contains("NETHERITE") || n.contains("ELYTRA") || n.contains("DRAGON")) base = 1500;
        else if (n.contains("DIAMOND") || n.contains("TOTEM") || n.contains("BEACON")) base = 300;
        else if (n.contains("EMERALD") || n.contains("GOLD")) base = 100;
        else if (n.contains("IRON") || n.contains("REDSTONE")) base = 45;
        else if (n.contains("WOOD") || n.contains("PLANK") || n.contains("STONE")) base = 8;
        return base;
    }
    private void openShop(Player p) {
        int page = pages.getOrDefault(p.getUniqueId(), 0);
        Inventory inv = Bukkit.createInventory(null, SHOP_SIZE, Component.text("GENBLOCK | Sklepik " + (page+1)));
        List<Material> all = shopMaterials();
        int start = page * SHOP_ITEMS;
        for (int i=0; i<SHOP_ITEMS && start+i<all.size(); i++) {
            Material m = all.get(start+i);
            ItemStack it = new ItemStack(m);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.text(pretty(m), NamedTextColor.GREEN));
            meta.lore(List.of(Component.text("Cena: " + (long)price(m) + " monet", NamedTextColor.YELLOW),
                    Component.text("Kliknij, aby kupic 1 sztuke", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, m.name());
            it.setItemMeta(meta); inv.setItem(i, it);
        }
        inv.setItem(45, named(Material.ARROW, "Poprzednia strona"));
        inv.setItem(49, named(Material.GOLD_INGOT, "Saldo: " + (long)money(p) + " monet"));
        inv.setItem(53, named(Material.ARROW, "Nastepna strona"));
        p.openInventory(inv);
    }
    private ItemStack named(Material mat, String name) {
        ItemStack it = new ItemStack(mat); ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW)); it.setItemMeta(meta); return it;
    }
    private String pretty(Material m) {
        String[] words = m.name().toLowerCase(Locale.ROOT).split("_");
        StringJoiner j = new StringJoiner(" ");
        for (String w : words) j.add(w.substring(0,1).toUpperCase(Locale.ROOT)+w.substring(1));
        return j.toString();
    }
    @EventHandler public void onShopClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().title().toString().contains("GENBLOCK | Sklepik")) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == 45) { pages.put(p.getUniqueId(), Math.max(0, pages.getOrDefault(p.getUniqueId(),0)-1)); openShop(p); return; }
        if (slot == 53) { pages.put(p.getUniqueId(), pages.getOrDefault(p.getUniqueId(),0)+1); openShop(p); return; }
        if (slot < 0 || slot >= SHOP_ITEMS) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String key = clicked.getItemMeta().getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        if (key == null) return;
        Material mat;
        try { mat = Material.valueOf(key); } catch (IllegalArgumentException ex) { return; }
        double cost = price(mat);
        if (money(p) < cost) { p.sendMessage("Masz za malo monet. Saldo: " + (long)money(p)); return; }
        setMoney(p, money(p)-cost);
        HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(new ItemStack(mat));
        overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
        p.sendMessage("Kupiono: " + pretty(mat) + " za " + (long)cost + " monet.");
        openShop(p);
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) pages.remove(p.getUniqueId());
    }
}
