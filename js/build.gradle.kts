import org.apache.tools.ant.taskdefs.condition.Os
import kotlin.String

group = "io.github.revenge.plugin"


tasks {
    fun registeringBunTask(vararg args: String) = registering(Exec::class) {
        group = "build"
        description = "Runs Bun with arguments: ${args.joinToString(" ")}"

        val bunCommand = if (Os.isFamily(Os.FAMILY_WINDOWS)) "bun.exe" else "${System.getProperty("user.home")}/.bun/bin/bun"

        commandLine(bunCommand, *args)
    }

    val installDependencies by registeringBunTask("install")
    val build by registeringBunTask("run", "build")
}

configurations {
    create("jsConfiguration") {
        isCanBeResolved = false
        isCanBeConsumed = true

        outgoing.artifact(layout.buildDirectory.dir("revenge")) {
            builtBy(tasks.named("build"))
        }
    }
}