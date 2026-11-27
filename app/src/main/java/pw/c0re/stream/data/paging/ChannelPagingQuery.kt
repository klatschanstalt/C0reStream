package pw.c0re.stream.data.paging

/**
 * Single source of truth for the channel paging SQL used by ChannelDao.
 *
 * The strings are `const` so Room can consume them directly in `@Query` annotations, while the
 * pure [buildFilter] helper lets JVM tests verify filter composition without a database.
 * None of these queries contains a LIMIT: every matching row is reachable by scrolling.
 */
object ChannelPagingQuery {
    const val ORDER_BY = " ORDER BY orderIndex, id"
    const val GROUP_FILTER = "(:group IS NULL OR `group` = :group)"
    const val QUERY_FILTER = "(:query IS NULL OR name LIKE '%' || :query || '%')"

    /** Combined playlist/group/name filter; group and query are both applied in SQLite. */
    const val FILTERED_SQL =
        "SELECT * FROM channels WHERE playlistId = :playlistId AND " +
            GROUP_FILTER + " AND " + QUERY_FILTER + ORDER_BY

    const val SEARCH_ALL_SQL =
        "SELECT * FROM channels WHERE name LIKE '%' || :query || '%'" + ORDER_BY
    const val SEARCH_SQL =
        "SELECT * FROM channels WHERE playlistId = :playlistId AND " +
            "name LIKE '%' || :query || '%'" + ORDER_BY
    const val BY_GROUP_SQL =
        "SELECT * FROM channels WHERE playlistId = :playlistId AND `group` = :group" + ORDER_BY
    const val BY_PLAYLIST_SQL =
        "SELECT * FROM channels WHERE playlistId = :playlistId" + ORDER_BY

    data class FilterSpec(val where: String, val namedArgs: List<String>)

    /**
     * Pure description of the dynamic predicates in [FILTERED_SQL]. A null group or query is
     * still passed to the DAO; the `IS NULL OR ...` guards in SQL turn it into "no filter".
     */
    fun buildFilter(groupActive: Boolean, queryActive: Boolean): FilterSpec {
        val clauses = ArrayList<String>(3)
        val args = ArrayList<String>(3)
        clauses += "playlistId = :playlistId"
        args += "playlistId"
        if (groupActive) {
            clauses += GROUP_FILTER
            args += "group"
        }
        if (queryActive) {
            clauses += QUERY_FILTER
            args += "query"
        }
        return FilterSpec(clauses.joinToString(" AND "), args)
    }
}
