package com.bluedragonmc.server.command

import com.bluedragonmc.server.module.database.PermissionGroup
import com.bluedragonmc.server.module.database.Permissions
import net.kyori.adventure.text.Component

class PermissionCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {
    usage(usageString)

    val permission by LiteralArgument

    val get by LiteralArgument
    val set by LiteralArgument
    val unset by LiteralArgument
    val info by LiteralArgument

    val node by PermissionArgument
    val value by BooleanArgument

    subcommand("group") {
        val permissionGroup by PermissionGroupArgument

        val add by LiteralArgument
        val delete by LiteralArgument
        val groupName by WordArgument

        suspendSyntax(add, groupName) { // permission group add <groupName>
            if(Permissions.getGroupByName(get(groupName)) == null) {
                Permissions.createGroup(
                    PermissionGroup(
                        get(groupName)
                    )
                )
                sender.sendMessage(formatMessageTranslated("command.permission.group.create.success", get(groupName)))
            } else {
                sender.sendMessage(formatErrorTranslated("command.permission.group.create.already_exists", get(groupName)))
            }
        }
        suspendSyntax(delete, groupName) { // permission group delete <groupName>
            val groupName = get(groupName)
            val group = Permissions.getGroupByName(groupName)
            if (group != null) {
                Permissions.removeGroup(group)
                sender.sendMessage(formatMessageTranslated("command.permission.group.remove.success", groupName))
            } else {
                sender.sendMessage(formatErrorTranslated("command.permission.group.does_not_exist", groupName))
            }
        }
        suspendSyntax(permissionGroup, info) { // permission group <groupName> info
            val group = Permissions.getGroupByName(get(permissionGroup))
            if(group == null) {
                sender.sendMessage(formatErrorTranslated("command.permission.group.does_not_exist", get(permissionGroup)))
                return@suspendSyntax
            }
            sender.sendMessage(buildMessage {
                component(formatMessageTranslated("command.permission.group.info", group.name, group.permissions.size))
                component(Component.newline())
                for(permissionName in group.permissions) {
                    message("- ")
                    field(permissionName + "\n")
                }

                // todo display group name, color, prefix, suffix, etc.
            })
        }
        suspendSyntax(permissionGroup, permission, get, value) { // permission group <groupName> permission get <node>
            val group = Permissions.getGroupByName(get(permissionGroup))
            if(group == null) {
                sender.sendMessage(formatErrorTranslated("command.permission.group.does_not_exist", get(permissionGroup)))
                return@suspendSyntax
            }
            val result = group.permissions.contains(get(permission))
            if(result) {
                sender.sendMessage(formatMessageTranslated("command.permission.group.permission.get.true", group.name, get(node)))
            } else {
                sender.sendMessage(formatMessageTranslated("command.permission.group.permission.get.false", group.name, get(node)))
            }
        }
        suspendSyntax(permissionGroup, permission, set, node, value) {
            val group = Permissions.getGroupByName(get(permissionGroup))
            if(group == null) {
                sender.sendMessage(formatErrorTranslated("command.permission.group.does_not_exist", get(permissionGroup)))
                return@suspendSyntax
            }
            Permissions.setPermission(group, get(node), get(value))
            sender.sendMessage(formatMessageTranslated("command.permission.group.permission.set", get(node), group.name, get(value)))
        }
        suspendSyntax(permissionGroup, permission, unset, node) {
            val group = Permissions.getGroupByName(get(permissionGroup))
            if(group == null) {
                sender.sendMessage(formatErrorTranslated("command.permission.group.does_not_exist", get(permissionGroup)))
                return@suspendSyntax
            }
            Permissions.removePermission(group, get(node))
            sender.sendMessage(formatMessageTranslated("command.permission.group.permission.unset", get(node), group.name, get(value)))
        }
        suspendSyntax(permissionGroup, permission, info) { // permission group <groupName> permission info
            val group = get(permissionGroup)
            val perms = Permissions.getGroupByName(group)?.permissions
            if(perms == null) {
                sender.sendMessage(formatErrorTranslated("command.permission.group.does_not_exist", group))
                return@suspendSyntax
            }
            sender.sendMessage(buildMessage {
                component(formatMessageTranslated("command.permission.group.info", group, perms.size))
                component(Component.newline())
                for(permissionName in perms) {
                    message("- ")
                    field(permissionName + "\n")
                }
            })
        }
    }

    subcommand("user") {
        val playerName by OfflinePlayerArgument

        userSuspendSyntax(playerName, permission, get, node) { // permission user <player> permission get <node>
            val result = Permissions.getPermission(doc, get(node))
            if(result) {
                sender.sendMessage(formatMessageTranslated("command.permission.user.permission.get.true", doc.username, get(node)))
            } else {
                sender.sendMessage(formatMessageTranslated("command.permission.user.permission.get.false", doc.username, get(node)))
            }
        }
        userSuspendSyntax(playerName, permission, set, node, value) { // permission user <player> permission set <node> <true|false>
            val node = get(node)
            Permissions.setPermission(doc, node, get(value))
            sender.sendMessage(formatMessageTranslated("command.permission.user.permission.set", node, doc.username, get(value))) // TODO negated (false) permissions
        }
        userSuspendSyntax(playerName, permission, unset, node) { // permission user <player> permission unset <node>
            val node = get(node)
            if (Permissions.getPermission(doc, node)) {
                Permissions.removePermission(doc, node)
                sender.sendMessage(formatMessageTranslated("command.permission.user.permission.unset", node, doc.username)) // TODO negated (false) permissions
            } else {
                sender.sendMessage(formatErrorTranslated("command.permission.user.permission.unset.fail", doc.username, node))
            }
        }
        userSuspendSyntax(playerName, permission, info) { // permission user <player> permission info
            val perms = doc.getAllPermissions().toTypedArray()
            sender.sendMessage(buildMessage {
                component(formatMessageTranslated("command.permission.user.permission.info", doc.username, perms.size))
                component(Component.newline())
                for(permissionName in perms) {
                    message("- ")
                    field(permissionName + "\n")
                }
            })
        }

        val group by LiteralArgument
        val add by LiteralArgument
        val remove by LiteralArgument

        val groupName by PermissionGroupArgument

        userSuspendSyntax(playerName, group, add, groupName) { // permission user <player> group add <groupName>
            val groupName = get(groupName)
            if(Permissions.isInGroup(doc, groupName)) {
                sender.sendMessage(formatErrorTranslated("command.permission.user.group.add.fail", doc.username, groupName))
            } else {
                Permissions.addGroup(doc, groupName)
                sender.sendMessage(formatMessageTranslated("command.permission.user.group.add.success", doc.username, groupName))
            }
        }
        userSuspendSyntax(playerName, group, remove, groupName) { // permission user <player> group remove <groupName>
            val groupName = get(groupName)
            if(Permissions.isInGroup(doc, groupName)) {
                Permissions.removeGroup(doc, groupName)
                sender.sendMessage(formatMessageTranslated("command.permission.user.group.remove.success", doc.username, groupName))
            } else {
                sender.sendMessage(formatErrorTranslated("command.permission.user.group.remove.fail", doc.username, groupName))
            }
        }
        userSuspendSyntax(playerName, group, info) { // permission user <player> group info
            val groups = Permissions.getGroups(doc)
            sender.sendMessage(buildMessage {
                component(formatMessageTranslated("command.permission.user.group.list", doc.username))
                for(permissionGroup in groups) {
                    field(permissionGroup.name)
                    if(permissionGroup != groups.last()) message(", ")
                }
            })
        }
    }
})
