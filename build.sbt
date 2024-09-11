ThisBuild / organization := "io.simplifier"
ThisBuild / version := sys.env.get("VERSION").getOrElse("NA")
ThisBuild / scalaVersion := "2.12.15"

ThisBuild / useCoursier := true


lazy val jsonStorePlugin = (project in file("."))
  .settings(
    name := "JSONStorePlugin",
    assembly / assemblyJarName := "jsonStore.jar",
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case "META-INF/native-image/native-image.properties" => MergeStrategy.discard
      case "META-INF/native-image/reflect-config.json" => MergeStrategy.discard
      case "META-INF/native-image/resource-config.json" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    libraryDependencies ++= Seq(
      "com.mysql"                % "mysql-connector-j"       % "8.2.0"       exclude("com.google.protobuf", "protobuf-java"),
      "com.oracle.database.jdbc" % "ojdbc11-production"      % "23.4.0.24.05" pomOnly() exclude("com.oracle.database.xml", "xmlparserv2"),
      "org.mapdb"                % "mapdb"                   % "2.0-beta11"  withSources() withJavadoc(),
      "io.github.simplifier-ag" %% "simplifier-plugin-base"  % "1.0.0"       withSources()
    )
  )



//Security Options for Java >= 18
lazy val requireAddOpensPackages = Seq(
  "java.base/java.lang",
  "java.base/java.util",
  "java.base/java.time",
  "java.base/java.lang.invoke",
  "java.base/sun.security.jca"
)
lazy val requireAddExportsPackages = Seq(
  "java.xml/com.sun.org.apache.xalan.internal.xsltc.trax"
)

assembly / packageOptions +=
  Package.ManifestAttributes(
    "Add-Opens" -> requireAddOpensPackages.mkString(" "),
    "Add-Exports" -> requireAddExportsPackages.mkString(" "),
    "Class-Path" -> (file("lib") * "*.jar").get.mkString(" ")
  )

run / javaOptions ++=
  requireAddOpensPackages.map("--add-opens=" + _ + "=ALL-UNNAMED") ++
    requireAddExportsPackages.map("--add-exports=" + _ + "=ALL-UNNAMED")

run / fork := true