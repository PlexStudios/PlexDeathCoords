package plex.deathcoords;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String suggestion;
        if (command.getName().equalsIgnoreCase("plexdeathcoords")) {
            if (!sender.hasPermission("plexdeathcoords.admin")) {
                return List.of();
            }
            suggestion = "reload";
        } else {
            if (!(sender instanceof Player) || !sender.hasPermission("plexdeathcoords.use")
                    || !sender.hasPermission("plexdeathcoords.clear") || !settings.get().allowClear()) {
                return List.of();
            }
            suggestion = "clear";
        }
        return suggestion.startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of(suggestion) : List.of();
    }
}
