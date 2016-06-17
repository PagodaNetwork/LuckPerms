package me.lucko.luckperms.commands.user;

import me.lucko.luckperms.LuckPermsPlugin;
import me.lucko.luckperms.commands.MainCommand;
import me.lucko.luckperms.commands.Sender;
import me.lucko.luckperms.commands.SubCommand;
import me.lucko.luckperms.commands.Util;
import me.lucko.luckperms.users.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserMainCommand extends MainCommand{

    private final List<UserSubCommand> subCommands = new ArrayList<>();

    public UserMainCommand() {
        super("User", "/perms user <user>", 2);
    }

    @Override
    protected void execute(LuckPermsPlugin plugin, Sender sender, List<String> args) {
        if (args.size() <= 1) {
            sendUsage(sender);
            return;
        }

        // The first argument is the name of the user, the second is the command
        String command = args.get(1);
        UserSubCommand tempSub = null;

        // Try to identify the command used
        for (UserSubCommand s : subCommands) {
            if (s.getName().equalsIgnoreCase(command)) {
                tempSub = s;
                break;
            }
        }

        // The command the sender used
        final UserSubCommand sub = tempSub;

        if (sub == null) {
            Util.sendPluginMessage(sender, "Command not recognised.");
            return;
        }

        if (!sub.isAuthorized(sender)) {
            Util.sendPluginMessage(sender, "You do not have permission to use this command!");
            return;
        }

        // The arguments to be passed onto the sub command
        List<String> strippedArgs = new ArrayList<>();
        if (args.size() > 2) {
            strippedArgs.addAll(args.subList(2, args.size()));
        }

        String user = args.get(0);

        UUID u = Util.parseUuid(user);
        if (u != null) {
            runSub(plugin, sender, u, sub, strippedArgs);
            return;
        }

        if (user.length() <= 16) {
            Util.sendPluginMessage(sender, "&7(Attempting UUID lookup, since you specified a user)");

            plugin.getDatastore().getUUID(user, uuid -> {
                if (uuid == null) {
                    Util.sendPluginMessage(sender, "&eUser could not be found.");
                    return;
                }
                runSub(plugin, sender, uuid, sub, strippedArgs);
            });
            return;
        }

        Util.sendPluginMessage(sender, "&d" + user + "&c is not a valid username/uuid.");
    }

    private void runSub(LuckPermsPlugin plugin, Sender sender, UUID uuid, UserSubCommand command, List<String> strippedArgs) {
        plugin.getDatastore().loadUser(uuid, success -> {
            if (!success) {
                Util.sendPluginMessage(sender, "&eUser could not be found.");
                return;
            }

            User user1 = plugin.getUserManager().getUser(uuid);

            if (user1 == null) {
                Util.sendPluginMessage(sender, "&eUser could not be found.");
            }

            if (command.isArgLengthInvalid(strippedArgs.size())) {
                command.sendUsage(sender);
                return;
            }

            command.execute(plugin, sender, user1, strippedArgs);
            plugin.getUserManager().cleanupUser(user1);
        });
    }

    @Override
    public List<? extends SubCommand> getSubCommands() {
        return subCommands;
    }

    public void registerSubCommand(UserSubCommand subCommand) {
        subCommands.add(subCommand);
    }
}