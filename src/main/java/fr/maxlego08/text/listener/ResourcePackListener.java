package fr.maxlego08.text.listener;

import fr.maxlego08.text.TextPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Sends the resource pack generated from the {@code .ttf} files to the players.
 */
public class ResourcePackListener implements Listener {

    private final TextPlugin plugin;

    public ResourcePackListener(TextPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {

        if (!this.plugin.getTtfFontManager().isActive() || !this.plugin.getTtfFontManager().isSendOnJoin()) {
            return;
        }

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plugin.getTtfFontManager().sendPack(player), 20L);
    }
}
