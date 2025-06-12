package com.example.powergems;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class PowerGemsPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> rightClickCooldown = new HashMap<>();
    private final Map<UUID, Long> shiftClickCooldown = new HashMap<>();
    private final Map<UUID, Long> leftClickCooldown = new HashMap<>();

    private final String GEM_NAME = ChatColor.GOLD + "Fire Gem";
    private final Material GEM_TYPE = Material.FIREBALL;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (isFireGem(item)) {
                        String name = ChatColor.GOLD + "Fire Gem" + ChatColor.GRAY + " [";
                        long now = System.currentTimeMillis();

                        long rc = Math.max(0, 60000 - (now - rightClickCooldown.getOrDefault(player.getUniqueId(), 0L)));
                        long lc = Math.max(0, 75000 - (now - leftClickCooldown.getOrDefault(player.getUniqueId(), 0L)));
                        long sc = Math.max(0, 90000 - (now - shiftClickCooldown.getOrDefault(player.getUniqueId(), 0L)));

                        name += ChatColor.RED + " R:" + rc / 1000 + "s";
                        name += ChatColor.GREEN + " L:" + lc / 1000 + "s";
                        name += ChatColor.BLUE + " S:" + sc / 1000 + "s";
                        name += ChatColor.GRAY + " ]";

                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(name));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("givefiregem")) {
            ItemStack gem = new ItemStack(GEM_TYPE);
            ItemMeta meta = gem.getItemMeta();
            meta.setDisplayName(GEM_NAME);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.RED + "Right-Click: Fiery Aura");
            lore.add(ChatColor.GRAY + " - Fire Resistance (20s), ignites nearby air");
            lore.add(ChatColor.DARK_GRAY + " - Cooldown: 60s");
            lore.add("");
            lore.add(ChatColor.BLUE + "Left-Click: Fireball Shot");
            lore.add(ChatColor.GRAY + " - Launches an explosive fireball");
            lore.add(ChatColor.DARK_GRAY + " - Cooldown: 75s");
            lore.add("");
            lore.add(ChatColor.DARK_RED + "Shift-Click: Inferno Burst");
            lore.add(ChatColor.GRAY + " - Triggers an explosion and burns enemies");
            lore.add(ChatColor.DARK_GRAY + " - Cooldown: 90s");
            lore.add("");
            lore.add(ChatColor.GOLD + "Upgrades: 0 / 3");
            meta.setLore(lore);
            gem.setItemMeta(meta);
            player.getInventory().addItem(gem);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isFireGem(item)) return;

        boolean isSneaking = player.isSneaking();
        UUID uuid = player.getUniqueId();

        if (event.getAction().toString().contains("RIGHT")) {
            long now = System.currentTimeMillis();
            if (now - rightClickCooldown.getOrDefault(uuid, 0L) < 60000) return;

            rightClickCooldown.put(uuid, now);
            player.setFireTicks(0);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 20 * 20, 1));

            World world = player.getWorld();
            Location loc = player.getLocation();
            for (int x = -3; x <= 3; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -3; z <= 3; z++) {
                        Location target = loc.clone().add(x, y, z);
                        if (target.getBlock().getType() == Material.AIR) {
                            target.getBlock().setType(Material.FIRE);
                        }
                    }
                }
            }
            spawnFireRing(player.getLocation());

        } else if (event.getAction().toString().contains("LEFT")) {
            long now = System.currentTimeMillis();
            if (now - leftClickCooldown.getOrDefault(uuid, 0L) < 75000) return;

            leftClickCooldown.put(uuid, now);
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setYield(2.0f);
            fireball.setIsIncendiary(true);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!fireball.isValid() || fireball.isDead()) {
                        cancel();
                        return;
                    }
                    fireball.getWorld().spawnParticle(Particle.FLAME, fireball.getLocation(), 3, 0, 0, 0, 0);
                }
            }.runTaskTimer(this, 0L, 1L);

        } else if (isSneaking) {
            long now = System.currentTimeMillis();
            if (now - shiftClickCooldown.getOrDefault(uuid, 0L) < 90000) return;

            shiftClickCooldown.put(uuid, now);
            Location loc = player.getLocation();
            Random rand = new Random();
            float power = 4.0f + rand.nextFloat() * 3.0f; // Between 4 and 7

            for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
                if (entity instanceof Player && !entity.equals(player)) {
                    ((Player) entity).damage(10);
                    entity.setFireTicks(100);
                }
            }

            World world = loc.getWorld();
            world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), power, true, false);
        }
    }

    private void spawnFireRing(Location center) {
        World world = center.getWorld();
        double radius = 3.5;
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            Location loc = center.clone().add(x, 0.1, z);
            world.spawnParticle(Particle.FLAME, loc, 0, 0, 0, 0, 1);
        }
    }

    private boolean isFireGem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasDisplayName()) return false;
        return item.getItemMeta().getDisplayName().equals(GEM_NAME);
    }
}
