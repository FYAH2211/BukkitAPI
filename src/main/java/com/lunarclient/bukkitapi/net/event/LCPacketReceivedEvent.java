package com.lunarclient.bukkitapi.net.event;

import com.moonsworth.client.nethandler.LCPacket;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class LCPacketReceivedEvent extends PlayerEvent implements Cancellable {

    @Getter private static HandlerList handlerList = new HandlerList();

    @Getter private final LCPacket packet;

    private boolean cancelled;

    public LCPacketReceivedEvent(Player who, LCPacket packet) {
        super(who);

        this.packet = packet;
    }

    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.cancelled = b;
    }
}