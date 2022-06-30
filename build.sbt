import java.awt.Desktop

import slick.additions.codegen.{EntityTableModulesCodeGenerator, KeylessModelsCodeGenerator}

import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport

name := "maaser"

inThisBuild(
  Seq(
    version      := "0.1",
    scalaVersion := "2.13.8",
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ymacro-annotations", "-Xsource:3")
  )
)

val CirceVersion          = "0.14.2"
val SlickAdditionsVersion = "0.12.0-M1"

val migrations = project
  .enablePlugins(SlickFlywayPlugin)
  .settings(
    slickConfig := SlickConfigPlugin.load(file("jvm/src/main/resources/reference.conf"))
  )

val shared = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .enablePlugins(SlickAdditionsCodegenPlugin)
  .settings(
    libraryDependencies += "io.circe"       %%% "circe-generic"          % CirceVersion,
    libraryDependencies += "io.circe"       %%% "circe-parser"           % CirceVersion,
    libraryDependencies += "io.github.nafg" %%% "slick-additions-entity" % SlickAdditionsVersion,
    slickConfig                              := (migrations / slickConfig).value,
    slickMetaGenRules                        := new MyGenerationRules("models", "models")("maasertracker.Codecs._"),
    Compile / sourceGenerators += mkSlickGenerator(new KeylessModelsCodeGenerator)
  )

commands += Command.command("dev")("js/start; ~all jvm/reStart js/fastOptJS::webpack" :: _)

lazy val start = TaskKey[Unit]("start")

val Http4sVersion  = "0.23.12"
val LogbackVersion = "1.2.11"

val jvm = project
  .dependsOn(shared.jvm)
  .enablePlugins(SlickAdditionsCodegenPlugin)
  .settings(
    reForkOptions       := reForkOptions.value.withWorkingDirectory(Option((ThisBuild / baseDirectory).value)),
    start               := Def.taskDyn {
      val re       = reStart.toTask("").taskValue
      val modified = (Compile / compileIncremental).value.hasModified
      if (modified) Def.task(re.value).map(_ => ()) else Def.task(())
    }.value,
    Compile / mainClass := Some("maasertracker.server.PlaidHttp4sServer"),
    libraryDependencies ++= Seq(
      "org.http4s"         %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"         %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s"         %% "http4s-circe"        % Http4sVersion,
      "org.http4s"         %% "http4s-dsl"          % Http4sVersion,
      "com.plaid"           % "plaid-java"          % "11.6.0",
      "org.flywaydb"        % "flyway-core"         % "8.5.13",
      "io.github.nafg"     %% "slick-additions"     % SlickAdditionsVersion,
      "com.typesafe.slick" %% "slick-hikaricp"      % "3.4.0-M1",
      "org.postgresql"      % "postgresql"          % "42.4.0",
      "org.scala-lang"      % "scala-reflect"       % scalaVersion.value,
      "ch.qos.logback"      % "logback-classic"     % LogbackVersion
    ),
    slickConfig         := (migrations / slickConfig).value,
    slickMetaGenRules   := new MyGenerationRules("tables", "Tables")("maasertracker.generated.models._"),
    Compile / sourceGenerators += mkSlickGenerator(new EntityTableModulesCodeGenerator)
  )

val js = project
  .enablePlugins(ScalablyTypedConverterPlugin).dependsOn(shared.js)
  .settings(
    stFlavour                       := Flavour.ScalajsReact,
    useYarn                         := false,
    autoImport.webpackDevServerPort := 8081,
    scalaJSLinkerConfig             := scalaJSLinkerConfig.value.withSourceMap(false),
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "dev.optics"                        %%% "monocle-macro"      % "3.1.0",
      "com.github.japgolly.scalajs-react" %%% "extra-ext-monocle3" % "2.1.1",
      "io.github.cquiroz"                 %%% "scala-java-time"    % "2.4.0",
      "com.nrinaudo"                      %%% "kantan.csv"         % "0.6.2"
    ),
    Compile / npmDependencies ++= Seq(
      "react"             -> "17.0.2",
      "react-dom"         -> "17.0.2",
      "@types/react"      -> "17.0.43",
      "@types/react-dom"  -> "17.0.14",
      "csstype"           -> "3.0.11",
      "@types/prop-types" -> "15.7.4",
      "antd"              -> "4.17.3"
    ),
    webpackConfigFile               := Some(baseDirectory.value / "custom.webpack.config.js"),
    Compile / npmDevDependencies ++= Seq(
      "webpack-merge" -> "5.8.0",
      "css-loader"    -> "5.2.7",
      "less-loader"   -> "7.3.0",
      "less"          -> "4.1.2",
      "style-loader"  -> "2.0.0",
      "file-loader"   -> "6.2.0",
      "url-loader"    -> "4.1.1"
    ),
    start                           := {
      (Compile / fastOptJS / startWebpackDevServer).value
      Desktop.getDesktop.browse(uri(s"http://localhost:${webpackDevServerPort.value}"))
    },
    Compile / fastOptJS / webpackExtraArgs += "--mode=development",
    Compile / fullOptJS / webpackExtraArgs += "--mode=production",
    Compile / fastOptJS / webpackDevServerExtraArgs += "--mode=development",
    Compile / fullOptJS / webpackDevServerExtraArgs += "--mode=production"
  )
