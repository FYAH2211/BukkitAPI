package com.lunarclient.bukkitapi;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.lunarclient.bukkitapi.net.event.LCPacketReceivedEvent;
import com.lunarclient.bukkitapi.net.event.LCPacketSentEvent;
import com.lunarclient.bukkitapi.object.*;
import com.lunarclient.bukkitapi.event.ClientAntiCheatEvent;
import com.lunarclient.bukkitapi.event.PlayerRegisterLCEvent;
import com.lunarclient.bukkitapi.event.PlayerUnregisterLCEvent;
import com.lunarclient.bukkitapi.net.LCNetHandler;
import com.lunarclient.bukkitapi.net.LCNetHandlerImpl;
import com.moonsworth.client.api.object.*;
import com.lunarclient.bukkitapi.voice.VoiceChannel;
import com.moonsworth.client.nethandler.LCPacket;
import com.moonsworth.client.nethandler.obj.ServerRule;
import com.moonsworth.client.nethandler.server.*;
import com.moonsworth.client.nethandler.shared.LCPacketWaypointAdd;
import com.moonsworth.client.nethandler.shared.LCPacketWaypointRemove;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class LunarClientAPI extends JavaPlugin implements Listener {

    private static final String MESSAGE_CHANNEL = "Lunar-Client";
    private static final String FROM_BUNGEE_CHANNEL = "LC|ACU";

    @Getter private static LunarClientAPI instance;
    private final Set<UUID> playersRunningLunarClient = Sets.newConcurrentHashSet();
    private final Set<UUID> playersRunningAntiCheat = Sets.newConcurrentHashSet();

    private final Set<UUID> playersNotRegistered = new HashSet<>();
    private final Map<UUID, List<LCPacket>> packetQueue = new HashMap<>();
    private final Map<UUID, Function<World, String>> worldIdentifiers = new HashMap<>();
    @Setter private LCNetHandler netHandlerServer = new LCNetHandlerImpl();
    private final Map<UUID, ClientAntiCheatEvent.Status> preJoinStatuses = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        Messenger messenger = getServer().getMessenger();

        messenger.registerOutgoingPluginChannel(this, MESSAGE_CHANNEL);
        messenger.registerIncomingPluginChannel(this, MESSAGE_CHANNEL, (channel, player, bytes) -> {
            LCPacket packet = LCPacket.handle(bytes, player);
            LCPacketReceivedEvent event = new LCPacketReceivedEvent(player, packet);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                packet.process(netHandlerServer);
            }
        });

        messenger.registerIncomingPluginChannel(this, FROM_BUNGEE_CHANNEL, (channel, player, bytes) -> {
            boolean prot = Boolean.parseBoolean(new String(bytes, UTF_8));

            anticheatUpdate(player, prot ? ClientAntiCheatEvent.Status.PROTECTED : ClientAntiCheatEvent.Status.UNPROTECTED);
        });

        getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.LOWEST)
            public void onPlayerJoin(PlayerJoinEvent event) {
                if (preJoinStatuses.containsKey(event.getPlayer().getUniqueId())) {
                    anticheatUpdate(event.getPlayer(), preJoinStatuses.remove(event.getPlayer().getUniqueId()));
                }
            }

            @EventHandler
            public void onRegister(PlayerRegisterChannelEvent event) {
                if (!event.getChannel().equals(MESSAGE_CHANNEL)) {
                    return;
                }

                playersNotRegistered.remove(event.getPlayer().getUniqueId());
                playersRunningLunarClient.add(event.getPlayer().getUniqueId());

                muteMap.put(event.getPlayer().getUniqueId(), new ArrayList<>());

                if (voiceEnabled) {
                    sendPacket(event.getPlayer(), new LCPacketServerRule(ServerRule.VOICE_ENABLED, true));
                }

                if (packetQueue.containsKey(event.getPlayer().getUniqueId())) {
                    packetQueue.get(event.getPlayer().getUniqueId()).forEach(p -> {
                        sendPacket(event.getPlayer(), p);
                    });

                    packetQueue.remove(event.getPlayer().getUniqueId());
                }

                getServer().getPluginManager().callEvent(new PlayerRegisterLCEvent(event.getPlayer()));
                updateWorld(event.getPlayer());
            }

            @EventHandler
            public void onUnregister(PlayerUnregisterChannelEvent event) {
                if (event.getChannel().equals(MESSAGE_CHANNEL)) {
                    playersRunningLunarClient.remove(event.getPlayer().getUniqueId());
                    playerActiveChannels.remove(event.getPlayer().getUniqueId());
                    muteMap.remove(event.getPlayer().getUniqueId());

                    getServer().getPluginManager().callEvent(new PlayerUnregisterLCEvent(event.getPlayer()));
                }
            }

            @EventHandler
            public void onUnregister(PlayerQuitEvent event) {
                getPlayerChannels(event.getPlayer()).forEach(channel -> channel.removePlayer(event.getPlayer()));

                playersRunningLunarClient.remove(event.getPlayer().getUniqueId());
                playersNotRegistered.remove(event.getPlayer().getUniqueId());
                playerActiveChannels.remove(event.getPlayer().getUniqueId());
                muteMap.remove(event.getPlayer().getUniqueId());
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onJoin(PlayerJoinEvent event) {
                Bukkit.getScheduler().runTaskLater(instance, () -> {
                    if (!isRunningLunarClient(event.getPlayer())) {
                        playersNotRegistered.add(event.getPlayer().getUniqueId());
                        packetQueue.remove(event.getPlayer().getUniqueId());
                    }
                }, 2 * 20L);
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onWorldChange(PlayerChangedWorldEvent event) {
                updateWorld(event.getPlayer());
            }

            private void updateWorld(Player player) {
                String worldIdentifier = getWorldIdentifier(player.getWorld());

                sendPacket(player, new LCPacketUpdateWorld(worldIdentifier));
            }

        }, this);
    }

    public boolean isRunningAntiCheat(Player player) {
        return isRunningAntiCheat(player.getUniqueId());
    }

    public boolean isRunningAntiCheat(UUID playerUuid) {
        return playersRunningAntiCheat.contains(playerUuid);
    }

    public void anticheatUpdate(Player player, ClientAntiCheatEvent.Status status) {
        if (!playersRunningAntiCheat.contains(player.getUniqueId()) && status == ClientAntiCheatEvent.Status.PROTECTED) {
            playersRunningAntiCheat.add(player.getUniqueId());
            Bukkit.getPluginManager().callEvent(new ClientAntiCheatEvent(player, status));
        } else if (playersRunningAntiCheat.contains(player.getUniqueId()) && status == ClientAntiCheatEvent.Status.UNPROTECTED) {
            playersRunningAntiCheat.remove(player.getUniqueId());
            Bukkit.getPluginManager().callEvent(new ClientAntiCheatEvent(player, status));
        }
    }

    public boolean isRunningLunarClient(Player player) {
        return isRunningLunarClient(player.getUniqueId());
    }

    public boolean isRunningLunarClient(UUID playerUuid) {
        return playersRunningLunarClient.contains(playerUuid);
    }

    public Set<Player> getPlayersRunningLunarClient() {
        return ImmutableSet.copyOf(playersRunningLunarClient.stream().map(Bukkit::getPlayer).collect(Collectors.toSet()));
    }

    public void sendNotification(Player player, LCNotification notification) {
        sendPacket(player, new LCPacketNotification(notification.getMessage(), notification.getDurationMs(), notification.getLevel().name()));
    }

    public String getWorldIdentifier(World world) {
        String worldIdentifier = world.getUID().toString();

        if (worldIdentifiers.containsKey(world.getUID())) {
            worldIdentifier = worldIdentifiers.get(world.getUID()).apply(world);
        }

        return worldIdentifier;
    }

    public void registerWorldIdentifier(World world, Function<World, String> identifier) {
        worldIdentifiers.put(world.getUID(), identifier);
    }

    public void sendNotificationOrFallback(Player player, LCNotification notification, Runnable fallback) {
        if (isRunningLunarClient(player)) {
            sendNotification(player, notification);
        } else {
            fallback.run();
        }
    }

    public void setStaffModuleState(Player player, StaffModule module, boolean state) {
        sendPacket(player, new LCPacketStaffModState(module.name(), state));
    }

    public void setMinimapStatus(Player player, MinimapStatus status) {
        sendPacket(player, new LCPacketServerRule(ServerRule.MINIMAP_STATUS, status.name()));
    }

    public void setCompetitiveGame(Player player, boolean isCompetitive) {
        sendPacket(player, new LCPacketServerRule(ServerRule.COMPETITIVE_GAMEMODE, isCompetitive));
    }

    public void giveAllStaffModules(Player player) {
        for (StaffModule module : StaffModule.values()) {
            LunarClientAPI.getInstance().setStaffModuleState(player, module, true);
        }

        sendNotification(player, new LCNotification("Staff modules enabled", Duration.ofSeconds(3)));
    }

    public void disableAllStaffModules(Player player) {
        for (StaffModule module : StaffModule.values()) {
            LunarClientAPI.getInstance().setStaffModuleState(player, module, false);
        }

        sendNotification(player, new LCNotification("Staff modules disabled", Duration.ofSeconds(3)));
    }

    public void sendTeammates(Player player, LCPacketTeammates packet) {
        validatePlayers(player, packet);
        sendPacket(player, packet);
    }

    public void validatePlayers(Player sendingTo, LCPacketTeammates packet) {
        packet.getPlayers().entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) != null && !Bukkit.getPlayer(entry.getKey()).getWorld().equals(sendingTo.getWorld()));
    }

    public void addHologram(Player player, UUID id, Vector position, String[] lines) {
        sendPacket(player, new LCPacketHologram(id, position.getX(), position.getY(), position.getZ(), Arrays.asList(lines)));
    }

    public void updateHologram(Player player, UUID id, String[] lines) {
        sendPacket(player, new LCPacketHologramUpdate(id, Arrays.asList(lines)));
    }

    public void removeHologram(Player player, UUID id) {
        sendPacket(player, new LCPacketHologramRemove(id));
    }

    public void overrideNametag(Player target, List<String> nametag, Player viewer) {
        sendPacket(viewer, new LCPacketNametagsOverride(target.getUniqueId(), nametag));
    }

    public void resetNametag(Player target, Player viewer) {
        sendPacket(viewer, new LCPacketNametagsOverride(target.getUniqueId(), null));
    }

    public void hideNametag(Player target, Player viewer) {
        sendPacket(viewer, new LCPacketNametagsOverride(target.getUniqueId(), ImmutableList.of()));
    }

    public void sendTitle(Player player, TitleType type, String message, Duration displayTime) {
        sendTitle(player, type, message, Duration.ofMillis(500), displayTime, Duration.ofMillis(500));
    }

    public void sendTitle(Player player, TitleType type, String message, float scale, Duration displayTime) {
        sendTitle(player, type, message, scale, Duration.ofMillis(500), displayTime, Duration.ofMillis(500));
    }

    public void sendTitle(Player player, TitleType type, String message, Duration fadeInTime, Duration displayTime, Duration fadeOutTime) {
        sendTitle(player, type, message, 1F, fadeInTime, displayTime, fadeOutTime);
    }

    public void sendTitle(Player player, TitleType type, String message, float scale, Duration fadeInTime, Duration displayTime, Duration fadeOutTime) {
        sendPacket(player, new LCPacketTitle(type.name().toLowerCase(), message, scale, displayTime.toMillis(), fadeInTime.toMillis(), fadeOutTime.toMillis()));
    }

    public void sendWaypoint(Player player, LCWaypoint waypoint) {
        sendPacket(player, new LCPacketWaypointAdd(waypoint.getName(), waypoint.getWorld(), waypoint.getColor(), waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.isForced(), waypoint.isVisible()));
    }

    public void removeWaypoint(Player player, LCWaypoint waypoint) {
        sendPacket(player, new LCPacketWaypointRemove(waypoint.getName(), waypoint.getWorld()));
    }

    public void sendCooldown(Player player, LCCooldown cooldown) {
        sendPacket(player, new LCPacketCooldown(cooldown.getMessage(), cooldown.getDurationMs(), cooldown.getIcon().getId()));
    }

    public void sendGhost(Player player, LCGhost ghost) {
        sendPacket(player, new LCPacketGhost(ghost.getGhostedPlayers(), ghost.getUnGhostedPlayers()));
    }

    public void clearCooldown(Player player, LCCooldown cooldown) {
        sendPacket(player, new LCPacketCooldown(cooldown.getMessage(), 0L, cooldown.getIcon().getId()));
    }

    public void setBossbar(Player player, String text, float health) {
        sendPacket(player, new LCPacketBossBar(0, text, health));
    }

    public void unsetBossbar(Player player) {
        sendPacket(player, new LCPacketBossBar(1, null, 0));
    }

    /*
     *  This is a boolean to indicate whether or not a LC message was sent.
     *  An example use-case is when you want to send a Lunar Client
     *  notification if a player is running Lunar Client, and a chat
     *  message if not.
     */
    public boolean sendPacket(Player player, LCPacket packet) {
        if (isRunningLunarClient(player)) {
            player.sendPluginMessage(this, MESSAGE_CHANNEL, LCPacket.getPacketData(packet));
            Bukkit.getPluginManager().callEvent(new LCPacketSentEvent(player, packet));
            return true;
        } else if (!playersNotRegistered.contains(player.getUniqueId())) {
            packetQueue.putIfAbsent(player.getUniqueId(), new ArrayList<>());
            packetQueue.get(player.getUniqueId()).add(packet);
            return false;
        }
        return false;
    }

}
