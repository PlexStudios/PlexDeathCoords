package plex.deathcoords;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class DeathCommands implements TabExecutor {
    private final DeathStore store;
    private final Supplier<Settings> settings;
    private final Supplier<CompletableFuture<Boolean>> reload;
    private boolean reloading;

    DeathCommands(DeathStore store, Supplier<Settings> settings, Supplier<CompletableFuture<Boolean>> reload) {
        this.store = store;
        this.settings = settings;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Settings current = settings.get();
        if (command.getName().equalsIgnoreCase("plexdeathcoords")) {
            if (!sender.hasPermission("plexdeathcoords.admin")) {
                sender.sendMessage(current.message("no-permission"));
            } else if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
                sender.sendMessage(current.message("admin-usage", Placeholder.unparsed("command", label)));
            } else if (reloading) {
                sender.sendMessage(current.message("reload-in-progress"));
            } else {
                reloading = true;
                reload.get().thenAccept(success -> {
                    reloading = false;
                    sender.sendMessage(settings.get().message(success ? "reloaded" : "reload-failed"));
                });
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(current.message("players-only"));
        } else if (!sender.hasPermission("plexdeathcoords.use")) {
            sender.sendMessage(current.message("no-permission"));
        } else if (args.length == 0) {
            DeathLocation death = store.get(player.getUniqueId());
            sender.sendMessage(death == null ? current.message("no-death")
                    : current.coordinates(death, System.currentTimeMillis()));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("compass")) {
            trackCompass(player, current);
        } else if (args.length != 1 || !args[0].equalsIgnoreCase("clear")) {
            sender.sendMessage(current.message("usage", Placeholder.unparsed("command", label)));
        } else if (!sender.hasPermission("plexdeathcoords.clear")) {
            sender.sendMessage(current.message("no-permission"));
        } else if (!current.allowClear()) {
            sender.sendMessage(current.message("clear-disabled"));
        } else {
            sender.sendMessage(current.message(store.clear(player.getUniqueId()) ? "cleared" : "no-death"));
        }
        return true;
    }

    private void trackCompass(Player player, Settings current) {
        if (!player.hasPermission("plexdeathcoords.compass")) {
            player.sendMessage(current.message("no-permission"));
            return;
        }
        if (!current.allowCompass()) {
            player.sendMessage(current.message("compass-disabled"));
            return;
        }
        DeathLocation death = store.get(player.getUniqueId());
        if (death == null) {
            player.sendMessage(current.message("no-death"));
            return;
        }
        World world = player.getServer().getWorld(death.worldId());
        if (world == null) {
            player.sendMessage(current.message("compass-world-unavailable"));
            return;
        }
        if (!world.getUID().equals(player.getWorld().getUID())) {
            player.sendMessage(current.message("compass-different-world"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.COMPASS || !(held.getItemMeta() instanceof CompassMeta meta)) {
            player.sendMessage(current.message("compass-held"));
            return;
        }
        meta.setLodestone(new Location(world, death.x(), death.y(), death.z()));
        meta.setLodestoneTracked(false);
        if (!held.setItemMeta(meta)) {
            player.sendMessage(current.message("compass-failed"));
            return;
        }
        player.sendMessage(current.coordinates("compass-enabled", death, System.currentTimeMillis()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("plexdeathcoords")) {
            if (!sender.hasPermission("plexdeathcoords.admin")) {
                return List.of();
            }
            return "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : List.of();
        }
        if (!(sender instanceof Player) || !sender.hasPermission("plexdeathcoords.use")) {
            return List.of();
        }
        Settings current = settings.get();
        return List.of("clear", "compass").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .filter(value -> value.equals("clear")
                        ? current.allowClear() && sender.hasPermission("plexdeathcoords.clear")
                        : current.allowCompass() && sender.hasPermission("plexdeathcoords.compass"))
                .toList();
    }
}
