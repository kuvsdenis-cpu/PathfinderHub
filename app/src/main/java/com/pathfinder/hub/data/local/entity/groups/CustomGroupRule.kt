package com.pathfinder.hub.data.local.entity.groups

/**
 * B1: правило пользовательской группы (r-1).
 *
 * Логика применения:
 *   - между полями — И;
 *   - внутри поля — ИЛИ.
 *
 * Пример: roles=["teen"], levelIds=["level_3","level_4"], statuses=["active"]
 *   → "все teen'ы со ступенью 3 ИЛИ 4, со статусом active".
 *
 * Пустой список = фильтр не применяется.
 * null в isOwnerClub = не фильтруем.
 */
data class CustomGroupRule(
    val roles: List<String> = emptyList(),
    val levelIds: List<String> = emptyList(),
    val honorIds: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val isOwnerClub: Boolean? = null,
)
