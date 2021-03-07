# Lunar Client Bukkit API
> Integrate your server with Lunar Client!

## Server Owners:
> Quick and Easy Setup

 If you are looking for a simple one click solution to integrating LunarClient features then [BukkitImpl](https://github.com/LunarClient/BukkitImpl) is your best bet.
BukkitImpl is a simple install bukkit plugin which will allow you to enable and disable features at will all from a single `config.yml` file.

## Developers: 
> Technical Integration Setup

You'll first need to install [BukkitAPI-NetHandler](https://github.com/LunarClient/BukkitAPI-NetHandler), which will
define the protocol that can be sent between the server and client for this API to use.

#### Basics:
`LunarClientAPI.java` is a singleton, you can access it through `LunarClientAPI#getInstance()`.

To check if someone claims to be running the client, and is therefore able to integrate with server features, use 
`LunarClientAPI#isRunningLunarClient(Player|UUID)`. When the API registers a `Player` as running Lunar Client, a
`LCPlayerRegisterEvent` is fired. When unregistered, a `LCPlayerUnregisterEvent`.

If you are a protected server and wish to verify that someone is running the anti-cheat, use
`LunarClientAPI#isRunningAntiCheat(Player|UUID)`. Whenever this state changes, a `LCAntiCheatStatusEvent` is fired, 
so you can watch for people timing out from the anti-cheat server and things like that.
> If you don't have this integration setup at the proxy level, this
method will always return `false`. 

You can access a view of `org.bukkit.Player`s currently running the client and/or anti-cheat with these methods:
`LunarClientAPI#getPlayersRunningLunarClient()` and `LunarClientAPI#getPlayersRunningAntiCheat()`.

###### Protocol:
`LCPacket.java` represents a packet that can be sent to and from players running Lunar Client.

There are protocol events for these, any time a `LCPacket` is received by the server, a `LCPacketReceivedEvent` is fired,
and likewise, when sent, a `LCPacketSentEvent` -- both of which let you access but not modify the packet itself,
 and the sender/target.
 
#### Quick example on how to enforce the anti-cheat (for partners)
This will not allow any player who is not protected by the anti-cheat to log in, as well as kick
anyone who fails anti-cheat authentication thereafter.

```java
import com.lunarclient.bukkitapi.event.LCAntiCheatStatusEvent;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.HIGHEST)
            public void onLogin(PlayerLoginEvent event) {
                if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
                    return;
                }

                if (!LunarClientAPI.getInstance().isRunningAntiCheat(event.getPlayer())) {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatColor.RED + "You must be playing with AntiCheat ON to join this server.");
                }
            }

            @EventHandler
            public void onClientAntiCheatChange(LCAntiCheatStatusEvent event) {
                if (event.getStatus() == LCAntiCheatStatusEvent.Status.UNPROTECTED) {
                    event.getPlayer().kickPlayer(ChatColor.RED + "Lunar Client authentication failed, you are no longer allowed on this server.");
                }
            }

        }, this);
    }
}
```

#### Disabling a mod
* Instantiate a `ModSetting` that sets the enabled status to `false`.
* Send a `LCPacketModSettings` with `ModSettings` that affect the mods you wish to disable, like so:

```
ModSettings.ModSetting disabled = new ModSettings.ModSetting(false, new HashMap<>());

sendPacket(event.getPlayer(), new LCPacketModSettings(
    new ModSettings()
        .addModSetting("Coordinates", disabled)
        .addModSetting("textHotKey", disabled)
));
```

#### Changing a Server Rule
`ServerRule.java` represents a rule your server sets for each client. You can, for example, enable a quitting
confirmation for competitive games by using `LunarClientAPIServerRule.setRule(ServerRule.COMPETITIVE_GAME, true)`. You
will still need to send these server rules to users using `LunarClientAPIServerRule.sendServerRule(Player)` when they join.
(This pattern work for all ServerRule)

#### FAQ
- **Q**: How do I color a Waypoint or Border?
- **A**: Use an `int` which represents the RGB value of the color. Examples: `java.awt.Color.BLUE.getRGB()` | `new java.awt.Color(0, 0, 255).getRGB()`
-- `org.bukkit.Color`s can also be used, but since they store the color in `hex`, you call `#asRGB()`; for example: `org.bukkit.Color.BLUE.asRGB()`
