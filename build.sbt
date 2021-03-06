def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // If we're building with Scala > 2.11, enable the compile option
    //  sw_default to support our anonymous Bundle definitions:
    //  https://github.com/scala/bug/issues/10047
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
      case _ => Seq("-Xsource:2.11")
    }
  }
}

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    //  Java 7 compatible code for Scala 2.11
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

name := "dsa-cgra-gen"

mainClass in (Compile, run) := Some("CgraGen")

version := "0.0.1"

scalaVersion := "2.12.8"

mainClass in assembly := Some("cgra.driver.CgraGen")

crossScalaVersions := Seq("2.11.12", "2.12.4","2.12.8")

// Not using online package
updateOptions := updateOptions.value.withLatestSnapshots(false)

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("master")
)

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel3" -> "3.3-SNAPSHOT",
  "chisel-iotesters" -> "1.4-SNAPSHOT",
  "firrtl" -> "1.3-SNAPSHOT"
)

libraryDependencies ++= Seq("chisel3","chisel-iotesters")
  .map{dep: String =>"edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))}

libraryDependencies += "org.yaml" % "snakeyaml" % "1.8"

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % "2.1.1",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.1.1",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.1.1",
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.1.1"
)



scalacOptions ++= scalacOptionsVersion(scalaVersion.value)

javacOptions ++= javacOptionsVersion(scalaVersion.value)
