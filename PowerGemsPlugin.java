package com.example.powergems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public class PowerGemsPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> rightClickCooldown = new HashMap<>();
    private final Map<UUID, Long> shiftClickCooldown = new HashMap<>();
    private final Map<UUID, Long> leftClickCooldown = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isHoldingFireGem(player)) {
                        sendActionBar(player, getCooldownStatus(player));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void sendActionBar(Player player, String message) {
        try {
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer");
            Object handle = craftPlayer.getMethod("getHandle").invoke(player);
            Class<?> packetClass = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutChat");
            Class<?> chatComponentText = Class.forName("net.minecraft.server.v1_8_R3.ChatComponentText");
            Object chatComp = chatComponentText.getConstructor(String.class).newInstance(message);
            Constructor<?> constructor = packetClass.getConstructor(Class.forName("net.minecraft.server.v1_8_R3.IChatBaseComponent"), byte.class);
            Object packet = constructor.newInstance(chatComp, (byte) 2);
            Method sendPacket = handle.getClass().getField("playerConnection").getType().getMethod("sendPacket", Class.forName("net.minecraft.server.v1_8_R3.Packet"));
            sendPacket.invoke(handle.getClass().getField("playerConnection").get(handle), packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getCooldownStatus(Player player) {
        long now = System.currentTimeMillis();
        String r = getTimeRemaining(rightClickCooldown, player, 60000);
        String l = getTimeRemaining(leftClickCooldown, player, 75000);
        String s = getTimeRemaining(shiftClickCooldown, player, 90000);
        return ChatColor.RED + "Fire Gem" + ChatColor.GRAY + " [R:" + r + " L:" + l + " S:" + s + "]";
    }

    private String getTimeRemaining(Map<UUID, Long> map, Player player, long cd) {
        long now = System.currentTimeMillis();
        return map.containsKey(player.getUniqueId()) ? Math.max(0, (map.get(player.getUniqueId()) + cd - now) / 1000) + "s" : "Ready";
    }

    private boolean isHoldingFireGem(Player player) {
        ItemStack item = player.getItemInHand();
        if (item == null || item.getType() != Material.BLAZE_POWDER) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.RED + "Fire Gem");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("givefiregem") && sender instanceof Player) {
            Player player = (Player) sender;
            ItemStack gem = new ItemStack(Material.BLAZE_POWDER);
            ItemMeta meta = gem.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Fire Gem");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GOLD + "Right-Click: Fire Aura (20s) [1min CD]");
            lore.add(ChatColor.GOLD + "Shift-Click: Explosion (20 dmg) [1.5min CD]");
            lore.add(ChatColor.GOLD + "Left-Click: Fireball Launch [1.25min CD]");
            meta.setLore(lore);
            gem.setItemMeta(meta);
            player.getInventory().addItem(gem);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isHoldingFireGem(player)) return;

        switch (event.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                handleRightClick(player);
                break;
            case LEFT_CLICK_AIR:
            case LEFT_CLICK_BLOCK:
                handleLeftClick(player);
                break;
            default:
                if (player.isSneaking()) {
                    handleShiftClick(player);
                }
        }
    }

    private void handleRightClick(Player player) {
        UUID id = player.getUniqueId();
        if (rightClickCooldown.containsKey(id) && System.currentTimeMillis() - rightClickCooldown.get(id) < 60000) return;
        rightClickCooldown.put(id, System.currentTimeMillis());
        player.setFireTicks(0);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 20 * 20, 1));
    }

    private void handleLeftClick(Player player) {
        UUID id = player.getUniqueId();
        if (leftClickCooldown.containsKey(id) && System.currentTimeMillis() - leftClickCooldown.get(id) < 75000) return;
        leftClickCooldown.put(id, System.currentTimeMillis());
        Fireball fb = player.launchProjectile(Fireball.class);
        fb.setYield(2F);
        fb.setIsIncendiary(true);
    }

    private void handleShiftClick(Player player) {
        UUID id = player.getUniqueId();
        if (shiftClickCooldown.containsKey(id) && System.currentTimeMillis() - shiftClickCooldown.get(id) < 90000) return;
        shiftClickCooldown.put(id, System.currentTimeMillis());
        World world = player.getWorld();
        world.createExplosion(player.getLocation(), 4F, false);
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player && entity != player) {
                ((Player) entity).damage(10.0);
                entity.setFireTicks(60);
            }
        }
    }
}
