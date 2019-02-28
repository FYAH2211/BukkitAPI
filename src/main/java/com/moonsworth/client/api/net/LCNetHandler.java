package com.moonsworth.client.api.net;

import com.moonsworth.client.api.LunarClientAPI;
import com.moonsworth.client.api.voice.VoiceChannel;
import com.moonsworth.client.nethandler.client.LCPacketClientVoice;
import com.moonsworth.client.nethandler.client.LCPacketVoiceChannelSwitch;
import com.moonsworth.client.nethandler.client.LCPacketVoiceMute;
import com.moonsworth.client.nethandler.server.ILCNetHandlerServer;
import com.moonsworth.client.nethandler.server.LCPacketVoice;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class LCNetHandler implements ILCNetHandlerServer {

    @Override
    public void handleVoice(LCPacketClientVoice packet) {
        Player player = packet.getAttachment();
        VoiceChannel channel = LunarClientAPI.getInstance().getPlayerActiveChannels().get(player.getUniqueId());
        if (channel == null) return;

        channel.getPlayersListening().stream().filter(p -> p != player && !LunarClientAPI.getInstance().playerHasPlayerMuted(p, p) && !LunarClientAPI.getInstance().playerHasPlayerMuted(player, p)).forEach(other -> LunarClientAPI.getInstance().sendPacket(other, new LCPacketVoice(player.getUniqueId(), packet.getData())));
    }

    @Override
    public void handleVoiceChannelSwitch(LCPacketVoiceChannelSwitch packet) {
        Player player = packet.getAttachment();
        LunarClientAPI.getInstance().setActiveChannel(player, packet.getSwitchingTo());
    }

    @Override
    public void handleVoiceMute(LCPacketVoiceMute packet) {
        Player player = packet.getAttachment();
        UUID muting = packet.getMuting();

        VoiceChannel channel = LunarClientAPI.getInstance().getPlayerActiveChannels().get(player.getUniqueId());
        if (channel == null) return;

        LunarClientAPI.getInstance().toggleVoiceMute(player, muting);
    }

}
