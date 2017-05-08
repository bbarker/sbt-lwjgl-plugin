import sbt._

import Keys._
import sbt.plugins.JvmPlugin

import scala.util.Properties
import scala.language.postfixOps

object LWJGLPlugin extends AutoPlugin {


  override def requires = JvmPlugin
  override def trigger = AllRequirements

  // TODO: autoImport object?

  object lwjgl {
    /** LWJGL Settings */
    val version = SettingKey[String]("lwjgl-version")

    val copyDir = SettingKey[File]("lwjgl-copy-directory", 
      "This is where lwjgl resources will be copied")

    val nativesDir = SettingKey[File]("lwjgl-natives-directory", 
      "This is the location where the lwjgl-natives will bomb to") 

    val os = SettingKey[(String, String)]("lwjgl-os", 
      "This is the targeted OS for the build. Defaults to the running OS.")

    val org = SettingKey[String]("lwjgl-org",
      "Custom lwjgl maven organization.")

    val nativesName = SettingKey[String]("lwjgl-natives-name",
      "Name of the natives artifact to extract.")

    val nativesJarName = SettingKey[String]("lwjgl-natives-jar-name",
      "This name will be used to pull the specific jar for loading the runtime.")

    val utilsName = SettingKey[String]("lwjgl-utils-name",
      "Name of the utils artifact.")

    val includePlatform = SettingKey[Boolean]("lwjgl-include-platform",
      "Include the platform dependency... Do not if using the old method")

    /** LWJGL Tasks */ 
    val copyNatives = TaskKey[Seq[File]]("lwjgl-copy-natives", 
      "Copies the lwjgl library from natives jar to managed resources")

    val manifestNatives = TaskKey[Unit]("lwjgl-manifest-natives", 
      "Copy LWJGL resources to output directory")
  }

  import lwjgl._


  // Define Tasks
  private def lwjglCopyTask: Def.Initialize[Task[Seq[File]]] = Def.task  {
    val s = streams.value
    val dir = copyDir.value
    val jarName = nativesJarName.value
    val dos = os.value
    val ivys = ivyPaths.value
    val (tos, ext) = dos
    val endness = Properties
      .propOrNone("os.arch")
      .filter(_.contains("64"))
      .map(_ => "64")
      .getOrElse("")

    s.log.info("Copying files for %s%s" format(tos, endness))

    val target = dir / tos
    s.log.debug("Target directory: %s" format target)


    if (target.exists) {
      s.log.info("Skipping because of existence: %s" format(target))
      Nil
    } else {
      val nativeLocation = pullNativeJar(org.value, nativesName.value, jarName, ivys.ivyHome)

      if (nativeLocation.exists) {
        s.log.debug("Natives found at %s" format nativeLocation)
        val filter = new SimpleFilter(_.endsWith(ext))
        s.log.debug("Unzipping files ending with %s" format ext)

        IO.unzip(nativeLocation, target.asFile, filter)

        // House keeping - to be used in old method
        (target / tos * "*").get foreach { f =>
          IO.copyFile(f, target / f.name)
        }

        // Return the managed LWJGL resources
        target * "*" get
      } else {
        s.log.warn("""|You do not have the LWJGL natives installed %s.
                      |Consider requiring LWJGL through LWJGLPlugin.lwjglSettings and running
                      |again.""".stripMargin.format(nativeLocation))
        Nil
      }
    }
  }

  private def lwjglNativesTask = Def.task {
    val s = streams.value
    val jarName = nativesJarName.value
    val ivys = ivyPaths.value
    val outDir = nativesDir.value
    val unzipTo = file(".") / "natives-cache"
    val lwjglN = pullNativeJar(org.value, nativesName.value, jarName, ivys.ivyHome)

    s.log.info("Unzipping the native jar")
    IO.unzip(lwjglN, unzipTo)

    val allFiles = unzipTo ** "*.*"

    allFiles.get foreach { f =>
      IO.copyFile(f, outDir / f.name)
    }
    // Delete cache
    s.log.info("Removing cache")
    IO.delete(unzipTo.asFile)
  }

  // Helper methods 
  def defineOs = System.getProperty("os.name").toLowerCase.take(3).toString match {
    case "lin" => ("linux", "so")
    case "mac" | "dar" => ("osx", "lib")
    case "win" => ("windows", "dll")
    case "sun" => ("solaris", "so")
    case _ => ("unknown", "")
  }

  private def pullNativeJar(org: String, name: String, jarName: String, ivyHome: Option[File]) = { 
    val correct = (f: File) =>
      f.getName == "%s.jar".format(jarName)

    val base = ivyHome.getOrElse(Path.userHome / ".ivy2")

    val jarBase = base / "cache" / org / name / "jars"
    val jars = jarBase * "*.jar"

    jars.get.filter(correct).headOption.getOrElse {
      throw new java.io.FileNotFoundException(
        "No Natives found in: %s" format(jarBase)
      )
    }
  }

  private def major(v: String): Int = v.split("\\.")(0) toInt

  lazy val lwjglSettings: Seq[sbt.Def.Setting[_]] = baseSettings ++ runSettings

  lazy val baseSettings: Seq[sbt.Def.Setting[_]] = Seq (
    lwjgl.includePlatform := true,

    // The group ID changed at version 3
    lwjgl.org := {
      val v = lwjgl.version.value
      if (major(v) <= 2) "org.lwjgl.lwjgl" else "org.lwjgl"
    },

    lwjgl.utilsName := "lwjgl_util",

    nativesDir := (target.value / "lwjgl-natives"),

    manifestNatives := lwjglNativesTask,
    manifestNatives := manifestNatives dependsOn update,

    libraryDependencies ++= {
      val v = lwjgl.version.value
      val org = lwjgl.org.value
      val utils = lwjgl.utilsName.value
      val os = lwjgl.os.value
      val isNew = lwjgl.includePlatform.value
      val deps = Seq(org % "lwjgl" % v)

      // Version 2 includes a util artifact.
      val utilsDeps = if (major(v) <= 2)
        Seq(org % utils % v)
      else
        Nil

      val nativeDeps = if (isNew)
        Seq(org % "lwjgl-platform" % v classifier "natives-" + os._1)
      else
        Nil

      deps ++ utilsDeps ++ nativeDeps
    }
  )

  lazy val runSettings: Seq[sbt.Def.Setting[_]] = Seq (
    lwjgl.version := "2.9.3",

    lwjgl.nativesName := "lwjgl-platform",

    lwjgl.nativesJarName :=
      lwjgl.nativesName.value + "-" + lwjgl.version.value + "-natives-" + lwjgl.os.value._1,

    lwjgl.os := defineOs,
    copyDir := ((resourceManaged in Compile).value  / "lwjgl-resources"),

    copyNatives := lwjglCopyTask.value,
    resourceGenerators in Compile += copyNatives,

    cleanFiles += copyDir.value,

    fork := true,
    javaOptions +=
      "-Dorg.lwjgl.librarypath=%s".format(copyDir.value / lwjgl.os.value._1)
  )

  lazy val oldLwjglSettings: Seq[Setting[_]] = lwjglSettings ++ Seq (
    resolvers += "Diablo-D3" at "http://adterrasperaspera.com/lwjgl",

    lwjgl.nativesName := "lwjgl-native",

    lwjgl.nativesJarName := lwjgl.nativesName + "-" + lwjgl.version,

    lwjgl.version := "2.7.1",

    lwjgl.org := "org.lwjgl",

    lwjgl.utilsName := "lwjgl-util",

    lwjgl.includePlatform := false
  )

}
