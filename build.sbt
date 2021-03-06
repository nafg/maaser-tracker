import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport

import java.awt.Desktop

name := "maaser"

inThisBuild(
  Seq(
    version := "0.1",
    scalaVersion := "2.13.4",
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ymacro-annotations")
  )
)

libraryDependencies += "com.nrinaudo" %% "kantan.csv-java8" % "0.6.1"

val CirceVersion = "0.13.0"

val shared = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(
    libraryDependencies += "io.circe" %%% "circe-generic" % CirceVersion,
    libraryDependencies += "io.circe" %%% "circe-parser"  % CirceVersion
  )

commands += Command.command("dev")("js/start; ~all jvm/reStart js/fastOptJS::webpack" :: _)

lazy val start = TaskKey[Unit]("start")

val Http4sVersion  = "0.21.19"
val LogbackVersion = "1.2.3"

val jvm = project
  .dependsOn(shared.jvm)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi"                  %% "cask"                 % "0.7.8",
      "com.lihaoyi"                  %% "scalatags"            % "0.9.3",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.11.4",
      "com.plaid"                     % "plaid-java"           % "8.1.0",
      "org.dizitart"                  % "nitrite"              % "3.4.3"
    ),
    reForkOptions := reForkOptions.value.withWorkingDirectory(Option((ThisBuild / baseDirectory).value)),
    start := Def.taskDyn {
      val re       = reStart.toTask("").taskValue
      val modified = (Compile / compileIncremental).value.hasModified
      if (modified) Def.task(re.value).map(_ => ()) else Def.task(())
    }.value,
    Compile / mainClass := Some("maasertracker.server.PlaidHttp4sServer"),
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"    %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"    %% "http4s-circe"        % Http4sVersion,
      "org.http4s"    %% "http4s-dsl"          % Http4sVersion,
      "ch.qos.logback" % "logback-classic"     % LogbackVersion
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
  )

val js = project
  .enablePlugins(ScalablyTypedConverterPlugin).dependsOn(shared.js)
  .settings(
    stFlavour := Flavour.Japgolly,
    useYarn := true,
    autoImport.webpackDevServerPort := 8081,
    scalaJSLinkerConfig := scalaJSLinkerConfig.value.withSourceMap(false),
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "extra"           % "1.7.7",
      "io.github.cquiroz"                 %%% "scala-java-time" % "2.1.0"
    ),
    Compile / npmDependencies ++= Seq(
      "react"             -> "17.0.1",
      "react-dom"         -> "17.0.1",
      "@types/react"      -> "17.0.2",
      "@types/react-dom"  -> "17.0.1",
      "csstype"           -> "3.0.6",
      "@types/prop-types" -> "15.7.3",
      "antd"              -> "4.12.3"
    ),
    webpackConfigFile := Some(baseDirectory.value / "custom.webpack.config.js"),
    Compile / npmDevDependencies ++= Seq(
      "webpack-merge" -> "4.2.2",
      "css-loader"    -> "3.4.2",
      "less-loader"   -> "6.2.0",
      "style-loader"  -> "1.1.3",
      "file-loader"   -> "5.1.0",
      "url-loader"    -> "3.0.0"
    ),
    start := {
      (Compile / fastOptJS / startWebpackDevServer).value
      Desktop.getDesktop.browse(uri(s"http://localhost:${webpackDevServerPort.value}"))
    },
    Compile / fastOptJS / webpackExtraArgs += "--mode=development",
    Compile / fullOptJS / webpackExtraArgs += "--mode=production",
    Compile / fastOptJS / webpackDevServerExtraArgs += "--mode=development",
    Compile / fullOptJS / webpackDevServerExtraArgs += "--mode=production"
  )
