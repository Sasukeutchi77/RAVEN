package com.example.data

import com.example.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CommunityPermissions {

    fun normalizeHandle(rawHandle: String, fallbackName: String = "community"): String {
        val base = rawHandle.trim().removePrefix("@").ifBlank { fallbackName.trim() }
        val sanitized = base.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_\\-]+"), "-")
            .trim('-')
            .take(36)
        return if (sanitized.length >= 2) sanitized else "rv-$sanitized".padEnd(3, '0')
    }

    fun isValidHandleFormat(handle: String): Boolean =
        handle.matches(Regex("^[a-zA-Z0-9_\\-]{2,40}$"))

    fun isOwner(server: Server, userId: String): Boolean =
        userId.isNotBlank() && server.ownerId == userId

    fun effectiveRole(server: Server, userId: String): UserRole {
        if (isOwner(server, userId) || userId in server.adminIds) return UserRole.ADMIN
        if (userId in server.moderatorIds) return UserRole.MODERATOR
        val member = server.members.find { it.id == userId }
        return member?.role ?: UserRole.MEMBER
    }

    fun getEffectivePermissions(server: Server, userId: String): Set<RolePermission> {
        if (isOwner(server, userId)) {
            return RolePermission.entries.toSet()
        }
        val member = server.members.find { it.id == userId }
        val baseRole = effectiveRole(server, userId)
        val customRole = member?.customRoleId?.let { rid -> server.customRoles.find { it.id == rid } }

        val perms = mutableSetOf(
            RolePermission.SEND_MESSAGES,
            RolePermission.CONNECT_VOICE,
            RolePermission.SPEAK_VOICE,
            RolePermission.STREAM_VIDEO,
            RolePermission.SHARE_SCREEN
        )
        if (customRole != null) {
            perms.clear()
            perms.addAll(customRole.permissions)
            if (customRole.isAdmin) {
                perms.addAll(RolePermission.entries.filter { it != RolePermission.DELETE_COMMUNITY })
            }
        }
        when (baseRole) {
            UserRole.ADMIN -> perms.addAll(RolePermission.entries.filter { it != RolePermission.DELETE_COMMUNITY })
            UserRole.MODERATOR -> {
                perms.add(RolePermission.MODERATE_MEMBERS)
                perms.add(RolePermission.MANAGE_INVITES)
                perms.add(RolePermission.MANAGE_EVENTS)
                perms.add(RolePermission.POST_ANNOUNCEMENTS)
                perms.add(RolePermission.MANAGE_CHANNELS)
                perms.add(RolePermission.MANAGE_VOICE)
            }
            else -> {}
        }
        if (member?.isTimedOut == true) {
            perms.remove(RolePermission.SEND_MESSAGES)
            perms.remove(RolePermission.CONNECT_VOICE)
            perms.remove(RolePermission.SPEAK_VOICE)
            perms.remove(RolePermission.STREAM_VIDEO)
            perms.remove(RolePermission.SHARE_SCREEN)
        }
        return perms
    }

    fun hasPermission(server: Server, userId: String, permission: RolePermission): Boolean =
        permission in getEffectivePermissions(server, userId)

    fun onlineMembersCount(server: Server): Int =
        server.members.count { it.status != UserStatus.OFFLINE }

    fun sortedMembers(server: Server): List<User> =
        server.members.sortedWith(
            compareBy<User> { it.status == UserStatus.OFFLINE }
                .thenByDescending { it.id == server.ownerId }
                .thenByDescending { it.role == UserRole.ADMIN || it.id in server.adminIds }
                .thenByDescending { it.role == UserRole.MODERATOR || it.id in server.moderatorIds }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )

    fun isChannelAccessible(server: Server, channel: Channel, userId: String): Boolean {
        if (!channel.isPrivate && channel.allowedRoles.isEmpty() && channel.allowedRoleIds.isEmpty()) return true
        if (isOwner(server, userId)) return true
        val userRole = effectiveRole(server, userId)
        if (userRole == UserRole.ADMIN) return true
        val memberCustomRoleId = server.members.find { it.id == userId }?.customRoleId
        val matchesBaseRole = channel.allowedRoles.isEmpty() || userRole in channel.allowedRoles
        val matchesCustomRole = channel.allowedRoleIds.isEmpty() ||
            (memberCustomRoleId != null && memberCustomRoleId in channel.allowedRoleIds)
        return when {
            channel.allowedRoles.isNotEmpty() && channel.allowedRoleIds.isNotEmpty() ->
                (userRole in channel.allowedRoles) || (memberCustomRoleId != null && memberCustomRoleId in channel.allowedRoleIds)
            else -> matchesBaseRole && matchesCustomRole
        }
    }

    data class VoiceJoinCheckResult(
        val allowed: Boolean,
        val reason: String? = null
    )

    fun canJoinVoiceChannel(
        server: Server?,
        channel: Channel,
        userId: String,
        currentParticipantsCount: Int = channel.activeVoiceUsers.count { it.id != userId }
    ): VoiceJoinCheckResult {
        if (server != null && !server.isDmTab) {
            val member = server.members.find { it.id == userId }
            if (member?.isTimedOut == true) {
                return VoiceJoinCheckResult(false, "Accès vocal suspendu : vous êtes actuellement en timeout.")
            }
            if (!hasPermission(server, userId, RolePermission.CONNECT_VOICE)) {
                return VoiceJoinCheckResult(false, "Votre rôle ne dispose pas de la permission Rejoindre le vocal.")
            }
            if (!isChannelAccessible(server, channel, userId)) {
                return VoiceJoinCheckResult(false, "Ce salon vocal est réservé à certains rôles.")
            }
            val canBypassLock = isOwner(server, userId) ||
                hasPermission(server, userId, RolePermission.MANAGE_VOICE) ||
                hasPermission(server, userId, RolePermission.MANAGE_CHANNELS)
            if (channel.isLocked && !canBypassLock) {
                return VoiceJoinCheckResult(false, "Ce salon vocal est verrouillé par la modération.")
            }
            if (userId in channel.kickedVoiceUids && !canBypassLock) {
                return VoiceJoinCheckResult(false, "Vous avez été exclu de ce salon vocal.")
            }
            if (channel.maxParticipants > 0 && currentParticipantsCount >= channel.maxParticipants && !isOwner(server, userId)) {
                return VoiceJoinCheckResult(
                    false,
                    "Salon vocal complet (${currentParticipantsCount}/${channel.maxParticipants} participants)."
                )
            }
        } else {
            if (channel.isLocked) {
                return VoiceJoinCheckResult(false, "Ce salon vocal est verrouillé.")
            }
            if (channel.maxParticipants > 0 && currentParticipantsCount >= channel.maxParticipants) {
                return VoiceJoinCheckResult(
                    false,
                    "Salon vocal complet (${currentParticipantsCount}/${channel.maxParticipants} participants)."
                )
            }
        }
        return VoiceJoinCheckResult(true, null)
    }

    fun serverToPublicListing(server: Server): PublicServerListing = PublicServerListing(
        id = server.id,
        name = server.name,
        iconText = server.iconText,
        iconBgColor = server.iconBgColor,
        categoryTag = server.categoryTag,
        description = server.description,
        inviteCode = server.inviteCode,
        memberCount = server.members.size.coerceAtLeast(1),
        boostLevel = server.boostLevel,
        isVerified = server.isVerified
    )

    fun nowTimeLabel(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
