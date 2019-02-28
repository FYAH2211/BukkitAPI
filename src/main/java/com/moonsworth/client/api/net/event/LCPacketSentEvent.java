package com.moonsworth.client.api.net.event;

import com.moonsworth.client.nethandler.LCPacket;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class LCPacketSentEvent extends PlayerEvent {

    @Getter private static HandlerList handlerList = new HandlerList();

    @Getter private final LCPacket packet;

    public LCPacketSentEvent(Player who, LCPacket packet) {
        super(who);

        this.packet = packet;
    }

    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

}