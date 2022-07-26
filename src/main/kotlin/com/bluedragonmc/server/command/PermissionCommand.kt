package com.bluedragonmc.server.command

import com.bluedragonmc.server.module.database.PermissionGroup
import com.bluedragonmc.server.module.database.Permissions

/*
To-do:
Commands:
/permission <user|group> <name> permission <set|unset> <node> <true|false>
/permission <user|group> <name> permission info

Module:
Create `PermissionsModule`
Make a function to evaluate permissions which considers wildcards
Create permission-based prefixes and suffixes (permission: something like `prefix.<red>OWNER`)
Add permissions to all commands (make it mandatory)
Add groups support to permissions - players have groups when they have `group.<groupname>` permission.
That permission inherits all permissions of the group, including prefix/suffix.

Future:
permission contexts like LuckPerms?

 */
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
        val groupArgument by PermissionGroupArgument

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
                sender.sendMessage(formatMessage("Group created successfully: {}", get(groupName)))
            } else {
                sender.sendMessage(formatErrorMessage("The group {} already exists!", get(groupName)))
            }
        }
        suspendSyntax(delete, groupName) { // permission group delete <groupName>
            val groupName = get(groupName)
            val group = Permissions.getGroupByName(groupName)
            if (group != null) {
                Permissions.removeGroup(group)
                sender.sendMessage(formatMessage("Successfully removed group {}.", groupName))
            } else {
                sender.sendMessage(formatErrorMessage("Group {} not found! Make sure you spelled it correctly.", groupName))
            }
        }
        suspendSyntax(groupArgument, info) { // permission group <groupName> info
            val group = Permissions.getGroupByName(get(groupArgument))
            if(group == null) {
                sender.sendMessage(formatErrorMessage("Group {} not found! Make sure you spelled it correctly.", get(groupArgument)))
                return@suspendSyntax
            }
            sender.sendMessage(buildMessage {
                message("All permissions for group ")
                field(group.name)
                message("(")
                field(group.permissions.size.toString())
                message("):\n")
                for(permissionName in group.permissions) {
                    message("- ")
                    field(permissionName + "\n")
                }

                // todo display group name, color, prefix, suffix, etc.
            })
        }
        suspendSyntax(groupArgument, permission, get, value) {
            val group = Permissions.getGroupByName(get(groupArgument))
            if(group == null) {
                sender.sendMessage(formatErrorMessage("Group {} not found! Make sure you spelled it correctly.", get(groupArgument)))
                return@suspendSyntax
            }
            val result = group.permissions.contains(get(permission))
            sender.sendMessage(formatMessage("{} {} permission: {}", group.name, if(result) "has" else "does not have", get(node)))
            sender.sendMessage(formatMessage("Group {}"))
        }
        suspendSyntax(groupArgument, permission, set, node, value) {
            val group = Permissions.getGroupByName(get(groupArgument))
            if(group == null) {
                sender.sendMessage(formatErrorMessage("Group {} not found! Make sure you spelled it correctly.", get(groupArgument)))
                return@suspendSyntax
            }
            Permissions.setPermission(group, get(node), get(value))
            sender.sendMessage(formatMessage("Set permission {} on group {} to {}.", get(node), group.name, get(value)))
        }
        suspendSyntax(groupArgument, permission, unset, node) {
            val group = Permissions.getGroupByName(get(groupArgument))
            if(group == null) {
                sender.sendMessage(formatErrorMessage("Group {} not found! Make sure you spelled it correctly.", get(groupArgument)))
                return@suspendSyntax
            }
            Permissions.removePermission(group, get(node))
            sender.sendMessage(formatMessage("Removed permission {} on group {}.", get(node), group.name, get(value)))
        }
        suspendSyntax(groupArgument, permission, info) { // permission group <groupName> permission info
            val group = get(groupArgument)
            val perms = Permissions.getGroupByName(group)?.permissions
            if(perms == null) {
                sender.sendMessage(formatErrorMessage("Group {} does not exist!", group))
                return@suspendSyntax
            }
            sender.sendMessage(buildMessage {
                message("All permissions for group ")
                field(group)
                message("(")
                field(perms.size.toString())
                message("):\n")
                for(permissionName in perms) {
                    message("- ")
                    field(permissionName + "\n")
                }
            })
        }

    }

    subcommand("user") {
        val userArgument by OfflinePlayerArgument

        userSuspendSyntax(userArgument, permission, get, node) { // permission user <player> permission get <node>
            val result = Permissions.getPermission(doc, get(node))
            sender.sendMessage(formatMessage("{} {} permission: {}", doc.username, if(result) "has" else "does not have", get(node)))
        }
        userSuspendSyntax(userArgument, permission, set, node, value) { // permission user <player> permission set <node> <true|false>
            val node = get(node)
            Permissions.setPermission(doc, node, get(value))
            sender.sendMessage(formatMessage("Set permission {} on player {} to {}", node, doc.username, get(value).toString())) // TODO negated (false) permissions
        }
        userSuspendSyntax(userArgument, permission, unset, node) { // permission user <player> permission unset <node>
            val node = get(node)
            if (Permissions.getPermission(doc, node)) {
                Permissions.removePermission(doc, node)
                sender.sendMessage(formatMessage("Removed permission {} on player {}.", node, doc.username)) // TODO negated (false) permissions
            } else {
                sender.sendMessage(formatErrorMessage("{} does not have the permission {}, so it cannot be removed!", doc.username, node))
            }
        }
        userSuspendSyntax(userArgument, permission, info) { // permission user <player> permission info
            val perms = doc.getAllPermissions().toTypedArray()
            sender.sendMessage(buildMessage {
                message("All permissions for player ")
                field(doc.username)
                message("(")
                field(perms.size.toString())
                message("):\n")
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

        userSuspendSyntax(userArgument, group, add, groupName) { // permission user <player> group add <groupName>
            val groupName = get(groupName)
            if(Permissions.isInGroup(doc, groupName)) {
                sender.sendMessage(formatErrorMessage("{} is already in group {}!", doc.username, groupName))
            } else {
                Permissions.addGroup(doc, groupName)
                sender.sendMessage(formatMessage("{} was added to group {}.", doc.username, groupName))
            }
        }
        userSuspendSyntax(userArgument, group, remove, groupName) { // permission user <player> group remove <groupName>
            val groupName = get(groupName)
            if(Permissions.isInGroup(doc, groupName)) {
                Permissions.removeGroup(doc, groupName)
                sender.sendMessage(formatMessage("{} was removed from group {}.", doc.username, groupName))
            } else {
                sender.sendMessage(formatErrorMessage("{} is not in group {}!", doc.username, groupName))
            }
        }
        userSuspendSyntax(userArgument, group, info) { // permission user <player> group info
            val groups = Permissions.getGroups(doc)
            sender.sendMessage(buildMessage {
                field(doc.username)
                message("'s groups: ")
                for(permissionGroup in groups) {
                    field(permissionGroup.name)
                    if(permissionGroup != groups.last()) message(", ")
                }
            })
        }
    }
})