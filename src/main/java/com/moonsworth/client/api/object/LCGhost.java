package com.moonsworth.client.api.object;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

public class LCGhost {

    @Getter private final List<UUID> ghostedPlayers;
    @Getter private final List<UUID> unGhostedPlayers;

    public LCGhost(List<UUID> ghostedPlayers, List<UUID> unGhostedPlayers) {
        this.ghostedPlayers = ghostedPlayers;
        this.unGhostedPlayers = unGhostedPlayers;
    }
}
