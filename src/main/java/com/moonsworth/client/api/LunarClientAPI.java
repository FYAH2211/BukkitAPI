package com.moonsworth.client.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.moonsworth.client.api.event.PlayerRegisterLCEvent;
import com.moonsworth.client.api.event.PlayerUnregisterLCEvent;
import com.moonsworth.client.api.net.LCNetHandler;
import com.moonsworth.client.api.net.LCNetHandlerImpl;
import com.moonsworth.client.api.net.event.LCPacketReceivedEvent;
import com.moonsworth.client.api.net.event.LCPacketSentEvent;
import com.moonsworth.client.api.object.*;
import com.moonsworth.client.api.voice.VoiceChannel;
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

public final class LunarClientAPI extends JavaPlugin implements Listener {

    private static final String MESSAGE_CHANNEL = "Lunar-Client";

    @Getter private static LunarClientAPI instance;
    private final Set<UUID> playersRunningLunarClient = new HashSet<>();

    private final Set<UUID> playersNotRegistered = new HashSet<>();
    @Getter private final Map<UUID, VoiceChannel> playerActiveChannels = new HashMap<>();
    private final Map<UUID, List<LCPacket>> packetQueue = new HashMap<>();
    private final Map<UUID, List<UUID>> muteMap = new HashMap<>();
    private final Map<UUID, Function<World, String>> worldIdentifiers = new HashMap<>();
    @Setter private LCNetHandler netHandlerServer = new LCNetHandlerImpl();
    private boolean voiceEnabled;
    @Getter private List<VoiceChannel> voiceChannels = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;

        Messenger messenger = getServer().getMessenger();

        messenger.registerOutgoingPluginChannel(this, MESSAGE_CHANNEL);
        messenger.registerIncomingPluginChannel(this, MESSAGE_CHANNEL, (channel, player, bytes) -> {
            LCPacket packet = LCPacket.handle(bytes, player);
            LCPacketReceivedEvent event;
            Bukkit.getPluginManager().callEvent(event = new LCPacketReceivedEvent(player, packet));
            if (!event.isCancelled()) {
                packet.process(netHandlerServer);
            }
        });

        getServer().getPluginManager().registerEvents(new Listener() {

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
//        sendPacket(player, new LCPacketGhost(ghost.getGhostedPlayers()));
    }

    public void clearCooldown(Player player, LCCooldown cooldown) {
        sendPacket(player, new LCPacketCooldown(cooldown.getMessage(), 0L, cooldown.getIcon().getId()));
    }

    public void voiceEnabled(boolean enabled) {
        voiceEnabled = enabled;
    }

    public void createVoiceChannels(VoiceChannel... voiceChannels) {
        this.voiceChannels.addAll(Arrays.asList(voiceChannels));
        for (VoiceChannel channel : voiceChannels) {
            for (Player player : channel.getPlayersInChannel()) {
                sendVoiceChannel(player, channel);
            }
        }
    }

    public void deleteVoiceChannel(VoiceChannel channel) {
        this.voiceChannels.removeIf(c -> {
            boolean remove = c == channel;
            if (remove) {
                channel.validatePlayers();
                for (Player player : channel.getPlayersInChannel()) {
                    sendPacket(player, new LCPacketVoiceChannelRemove(channel.getUuid()));
                    if (getPlayerActiveChannels().get(player.getUniqueId()) == channel) {
                        getPlayerActiveChannels().remove(player.getUniqueId());
                    }
                }
            }
            return remove;
        });
    }

    public void deleteVoiceChannel(UUID channelUUID) {
        getChannel(channelUUID).ifPresent(this::deleteVoiceChannel);
    }

    public List<VoiceChannel> getPlayerChannels(Player player) {
        return this.voiceChannels.stream().filter(channel -> channel.hasPlayer(player)).collect(Collectors.toList());
    }

    public void sendVoiceChannel(Player player, VoiceChannel channel) {
        channel.validatePlayers();
        sendPacket(player, new LCPacketVoiceChannel(channel.getUuid(), channel.getName(), channel.toPlayersMap(), channel.toListeningMap()));
    }

    public void setActiveChannel(Player player, UUID uuid) {
        getChannel(uuid).ifPresent(channel -> setActiveChannel(player, channel));
    }

    public Optional<VoiceChannel> getChannel(UUID uuid) {
        return voiceChannels.stream().filter(channel -> channel.getUuid().equals(uuid)).findFirst();
    }

    public void setActiveChannel(Player player, VoiceChannel channel) {
        channel.setActive(player);
    }

    public void toggleVoiceMute(Player player, UUID other) {
        if (!muteMap.get(player.getUniqueId()).removeIf(uuid -> uuid.equals(other))) {
            muteMap.get(player.getUniqueId()).add(other);
        }
    }

    public boolean playerHasPlayerMuted(Player player, Player other) {
        return muteMap.get(other.getUniqueId()).contains(player.getUniqueId());
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