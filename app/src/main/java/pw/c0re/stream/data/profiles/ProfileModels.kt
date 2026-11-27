package pw.c0re.stream.data.profiles

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A local profile. [pinHash] holds an opaque PBKDF2 record created by `PinManager.hash`, never
 * the PIN itself; `null` means this profile has no PIN. [avatarRes] is an optional drawable
 * resource id (`null` = use the default avatar).
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarRes: Int? = null,
    val pinHash: String? = null,
    val createdAt: Long = 0,
)

/**
 * UI-facing profile. The PIN hash is deliberately replaced by [hasPin] so it is never exposed
 * to a screen or a log.
 */
data class ProfileUiModel(
    val id: Long,
    val name: String,
    val avatarRes: Int?,
    val hasPin: Boolean,
    val createdAt: Long,
) {
    companion object {
        fun from(entity: ProfileEntity): ProfileUiModel = ProfileUiModel(
            id = entity.id,
            name = entity.name,
            avatarRes = entity.avatarRes,
            hasPin = !entity.pinHash.isNullOrEmpty(),
            createdAt = entity.createdAt,
        )
    }
}
