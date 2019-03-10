package com.moonsworth.client.api.object;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

public class LCGhost {

    @Getter private final List<UUID> ghostedPlayers;

    public LCGhost(List<UUID> ghostedPlayers) {
        this.ghostedPlayers = ghostedPlayers;
    }
}
