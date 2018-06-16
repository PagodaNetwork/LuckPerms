/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.commands.group;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.Maps;

import me.lucko.luckperms.api.HeldPermission;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.common.bulkupdate.comparisons.Constraint;
import me.lucko.luckperms.common.bulkupdate.comparisons.StandardComparison;
import me.lucko.luckperms.common.command.CommandManager;
import me.lucko.luckperms.common.command.CommandResult;
import me.lucko.luckperms.common.command.abstraction.SubCommand;
import me.lucko.luckperms.common.command.access.ArgumentPermissions;
import me.lucko.luckperms.common.command.access.CommandPermission;
import me.lucko.luckperms.common.command.utils.ArgumentParser;
import me.lucko.luckperms.common.command.utils.MessageUtils;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.command.CommandSpec;
import me.lucko.luckperms.common.locale.message.Message;
import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.HolderType;
import me.lucko.luckperms.common.node.comparator.HeldPermissionComparator;
import me.lucko.luckperms.common.node.factory.NodeFactory;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.utils.DurationFormatter;
import me.lucko.luckperms.common.utils.Iterators;
import me.lucko.luckperms.common.utils.Predicates;
import me.lucko.luckperms.common.utils.TextUtils;

import net.kyori.text.BuildableComponent;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GroupListMembers extends SubCommand<Group> {
    public GroupListMembers(LocaleManager locale) {
        super(CommandSpec.GROUP_LISTMEMBERS.localize(locale), "listmembers", CommandPermission.GROUP_LIST_MEMBERS, Predicates.notInRange(0, 1));
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, Group group, List<String> args, String label) {
        if (ArgumentPermissions.checkViewPerms(plugin, sender, getPermission().get(), group)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        Constraint constraint = Constraint.of(StandardComparison.EQUAL, NodeFactory.groupNode(group.getName()));
        int page = ArgumentParser.parseIntOrElse(0, args, 1);

        Message.SEARCH_SEARCHING_MEMBERS.send(sender, group.getName());

        List<HeldPermission<UUID>> matchedUsers = plugin.getStorage().getUsersWithPermission(constraint).join();
        List<HeldPermission<String>> matchedGroups = plugin.getStorage().getGroupsWithPermission(constraint).join();

        int users = matchedUsers.size();
        int groups = matchedGroups.size();

        Message.SEARCH_RESULT.send(sender, users + groups, users, groups);

        if (!matchedUsers.isEmpty()) {
            LoadingCache<UUID, String> uuidLookups = Caffeine.newBuilder()
                    .build(u -> {
                        String s = plugin.getStorage().getPlayerName(u).join();
                        if (s != null && !s.isEmpty() && !s.equals("null")) {
                            return s;
                        }

                        if (plugin.getConfiguration().get(ConfigKeys.USE_SERVER_UUID_CACHE)) {
                            s = plugin.getBootstrap().lookupUsername(u).orElse(null);
                            if (s != null) {
                                return s;
                            }
                        }

                        return u.toString();
                    });
            sendResult(sender, matchedUsers, uuidLookups::get, Message.SEARCH_SHOWING_USERS, HolderType.USER, label, page);
        }

        if (!matchedGroups.isEmpty()) {
            sendResult(sender, matchedGroups, Function.identity(), Message.SEARCH_SHOWING_GROUPS, HolderType.GROUP, label, page);
        }

        return CommandResult.SUCCESS;
    }

    private static <T extends Comparable<T>> void sendResult(Sender sender, List<HeldPermission<T>> results, Function<T, String> lookupFunction, Message headerMessage, HolderType holderType, String label, int page) {
        results = new ArrayList<>(results);
        results.sort(HeldPermissionComparator.normal());

        int pageIndex = page - 1;
        List<List<HeldPermission<T>>> pages = Iterators.divideIterable(results, 15);

        if (pageIndex < 0 || pageIndex >= pages.size()) {
            page = 1;
            pageIndex = 0;
        }

        List<HeldPermission<T>> content = pages.get(pageIndex);

        List<Map.Entry<String, HeldPermission<T>>> mappedContent = content.stream()
                .map(hp -> Maps.immutableEntry(lookupFunction.apply(hp.getHolder()), hp))
                .collect(Collectors.toList());

        // send header
        headerMessage.send(sender, page, pages.size(), results.size());

        for (Map.Entry<String, HeldPermission<T>> ent : mappedContent) {
            String s = "&3> &b" + ent.getKey() + " " + getNodeExpiryString(ent.getValue().asNode()) + MessageUtils.getAppendableNodeContextString(ent.getValue().asNode());
            TextComponent message = TextUtils.fromLegacy(s, CommandManager.AMPERSAND_CHAR).toBuilder().applyDeep(makeFancy(ent.getKey(), holderType, label, ent.getValue())).build();
            sender.sendMessage(message);
        }
    }

    private static String getNodeExpiryString(Node node) {
        if (!node.isTemporary()) {
            return "";
        }

        return " &8(&7expires in " + DurationFormatter.LONG.formatDateDiff(node.getExpiryUnixTime()) + "&8)";
    }

    private static Consumer<BuildableComponent.Builder<? ,?>> makeFancy(String holderName, HolderType holderType, String label, HeldPermission<?> perm) {
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextUtils.fromLegacy(TextUtils.joinNewline(
                "&3> &b" + perm.asNode().getGroupName(),
                " ",
                "&7Click to remove this parent from " + holderName
        ), CommandManager.AMPERSAND_CHAR));

        String command = "/" + label + " " + NodeFactory.nodeAsCommand(perm.asNode(), holderName, holderType, false);
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);

        return component -> {
            component.hoverEvent(hoverEvent);
            component.clickEvent(clickEvent);
        };
    }
}
