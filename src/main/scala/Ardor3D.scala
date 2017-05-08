import sbt._

import Keys._

object Ardor3D {

  object ardor {
    val version: SettingKey[String] = SettingKey("ardor-version")
  }

  lazy val baseSettings: Seq[Setting[_]] = Seq (
    ardor.version := "0.8-SNAPSHOT",

    resolvers ++= Seq (
      "Ardor3D Maven repo" at 
      "http://ardor3d.com:8081/nexus/content/groups/public/",
      "Ardor3D Third party" at 
      "http://ardor3d.com:8081/nexus/content/repositories/thirdparty",
      "Google Repo" at
      "http://google-maven-repository.googlecode.com/svn/repository/"
    ),

    libraryDependencies += "com.ardor3d" % "ardor3d" % ardor.version.value

  )

  lazy val ardorSettings: Seq[Setting[_]] = 
    LWJGLPlugin.lwjglSettings ++ baseSettings
}
