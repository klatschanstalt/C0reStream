package pw.c0re.stream.data.model

data class EpgEntry(
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)
