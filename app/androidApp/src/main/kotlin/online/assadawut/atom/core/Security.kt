package online.assadawut.atom.core

object Security {
    val whitelistApps = listOf(
        "jp.naver.line.android",
        "org.telegram.messenger",
        "com.Slack"
    )
    val readOnlyApps = listOf(
        "com.android.chrome",
        "com.google.android.youtube"
    )
    fun canExecuteApp(packageName: String): Boolean {
        return whitelistApps.contains(packageName)
    }
    fun canReadApp(packageName: String): Boolean {
        return readOnlyApps.contains(packageName) || whitelistApps.contains(packageName)
    }
}