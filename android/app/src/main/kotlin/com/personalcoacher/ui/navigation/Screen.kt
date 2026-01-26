package com.personalcoacher.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Journal : Screen("journal")
    data object JournalEditor : Screen("journal/editor") {
        const val ROUTE_WITH_ARG = "journal/editor?entryId={entryId}"
        fun createRoute(entryId: String? = null): String {
            return if (entryId != null) "journal/editor?entryId=$entryId" else "journal/editor"
        }
    }
    data object NoteEditor : Screen("journal/note/editor") {
        const val ROUTE_WITH_ARG = "journal/note/editor?noteId={noteId}"
        fun createRoute(noteId: String? = null): String {
            return if (noteId != null) "journal/note/editor?noteId=$noteId" else "journal/note/editor"
        }
    }
    data object GoalEditor : Screen("journal/goal/editor") {
        const val ROUTE_WITH_ARG = "journal/goal/editor?goalId={goalId}"
        fun createRoute(goalId: String? = null): String {
            return if (goalId != null) "journal/goal/editor?goalId=$goalId" else "journal/goal/editor"
        }
    }
    data object TaskEditor : Screen("journal/task/editor") {
        const val ROUTE_WITH_ARG = "journal/task/editor?taskId={taskId}"
        fun createRoute(taskId: String? = null): String {
            return if (taskId != null) "journal/task/editor?taskId=$taskId" else "journal/task/editor"
        }
    }
    data object Coach : Screen("coach")
    data object Conversation : Screen("coach/conversation/{conversationId}") {
        fun createRoute(conversationId: String): String = "coach/conversation/$conversationId"
    }
    data object Summaries : Screen("summaries")
    data object SummaryDetail : Screen("summaries/{summaryId}") {
        fun createRoute(summaryId: String): String = "summaries/$summaryId"
    }
    data object Agenda : Screen("agenda")
    data object Recorder : Screen("recorder")
    data object Settings : Screen("settings")
    data object DailyTools : Screen("daily-tools")
    data object MyTools : Screen("my-tools")
}

enum class BottomNavItem(
    val screen: Screen,
    val labelResId: Int,
    val icon: String
) {
    JOURNAL(Screen.Journal, com.personalcoacher.R.string.nav_journal, "book"),
    COACH(Screen.Coach, com.personalcoacher.R.string.nav_coach, "chat"),
    SUMMARIES(Screen.Summaries, com.personalcoacher.R.string.nav_summaries, "insights")
}
