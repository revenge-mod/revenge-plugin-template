import org.apache.tools.ant.taskdefs.condition.Os
import kotlin.String

group = "io.github.revenge.plugin"


tasks {
    fun registeringNpmTask(vararg args: String) = registering(Exec::class) {
        group = "build"
        description = "Runs npm command with arguments: ${args.joinToString(" ")}"

        val npmCommand = if (Os.isFamily(Os.FAMILY_WINDOWS)) "npm.cmd" else "/usr/local/bin/npm"

        workingDir("src")
        commandLine(npmCommand, *args)
    }

    val installDependencies by registeringNpmTask("install")
    val build by registeringNpmTask("run", "build")
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