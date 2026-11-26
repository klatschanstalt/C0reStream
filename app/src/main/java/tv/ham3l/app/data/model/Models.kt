package tv.ham3l.app.data.model

data class Channel(
    val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val url: String,
    val tvgId: String?,
    val catchupSource: String? = null,
)

data class EpgEntry(
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)
