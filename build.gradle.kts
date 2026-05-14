plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// --- Local backend tunnel tasks -----------------------------------------------------------
// Thin Gradle wrappers around scripts/start-local-tunnel.sh and stop-local-tunnel.sh.
// The script is the source of truth — these tasks just expose `./gradlew fortressTunnel`
// and `./gradlew fortressTunnelStop` so they show up in IDE run configs.
tasks.register<Exec>("fortressTunnel") {
    group = "fortress"
    description = "Start ngrok against the local backend and write fortress.baseUrl into local.properties."
    workingDir = rootDir
    commandLine("bash", "scripts/start-local-tunnel.sh")
    // ngrok output is interactive enough that we don't want the configuration cache
    // to memoise this task.
    notCompatibleWithConfigurationCache("spawns an external ngrok process")
}

tasks.register<Exec>("fortressTunnelStop") {
    group = "fortress"
    description = "Stop the ngrok tunnel and clear fortress.baseUrl from local.properties."
    workingDir = rootDir
    commandLine("bash", "scripts/stop-local-tunnel.sh")
    notCompatibleWithConfigurationCache("kills an external ngrok process")
}
