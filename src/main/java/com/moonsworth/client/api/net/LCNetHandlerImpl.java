package com.moonsworth.client.api.net;

import com.moonsworth.client.nethandler.client.LCPacketEmoteBroadcast;
import com.moonsworth.client.nethandler.client.LCPacketStaffModStatus;
import com.moonsworth.client.nethandler.client.LCPacketVersionNumber;
import com.moonsworth.client.nethandler.shared.LCPacketWaypointAdd;
import com.moonsworth.client.nethandler.shared.LCPacketWaypointRemove;

public class LCNetHandlerImpl extends LCNetHandler {

    @Override
    public void handleAddWaypoint(LCPacketWaypointAdd packet) {
    }

    @Override
    public void handleRemoveWaypoint(LCPacketWaypointRemove packet) {
    }

    @Override
    public void handleEmote(LCPacketEmoteBroadcast lcPacketEmoteBroadcast) {

    }

    @Override
    public void handlePacketVersionNumber(LCPacketVersionNumber lcPacketVersionNumber) {

    }

    @Override
    public void handleStaffModStatus(LCPacketStaffModStatus packet) {

    }

}
