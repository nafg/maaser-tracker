import java.awt.Desktop

import slick.additions.codegen.{EntityTableModulesCodeGenerator, KeylessModelsCodeGenerator}

import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport

name := "maaser"

inThisBuild(
  Seq(
    version      := "0.1",
    scalaVersion := "2.13.16",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Xlint",
      "-Ymacro-annotations",
      "-Xsource:3",
      "-Wconf:site=maasertracker.generated.tables.*&cat=scala3-migration:warning"
    )
  )
)

val CirceVersion          = "0.14.13"
val SlickAdditionsVersion = "0.12.1"

val migrations = project
  .enablePlugins(SlickFlywayPlugin)
  .settings(
    slickConfig := SlickConfigPlugin.load(file("jvm/src/main/resources"))
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

val Http4sVersion  = "0.23.30"
val LogbackVersion = "1.5.18"

//noinspection ScalaUnusedSymbol,ScalaWeakerAccess
val jvm = project
  .dependsOn(migrations, shared.jvm)
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
      "org.http4s"         %% "http4s-ember-server"        % Http4sVersion,
      "org.http4s"         %% "http4s-ember-client"        % Http4sVersion,
      "org.http4s"         %% "http4s-circe"               % Http4sVersion,
      "org.http4s"         %% "http4s-dsl"                 % Http4sVersion,
      "com.plaid"           % "plaid-java"                 % "32.0.0",
      "org.flywaydb"        % "flyway-database-postgresql" % "11.8.2",
      "io.github.nafg"     %% "slick-additions"            % SlickAdditionsVersion,
      "com.typesafe.slick" %% "slick-hikaricp"             % "3.4.1",
      "org.postgresql"      % "postgresql"                 % "42.7.5",
      "org.scala-lang"      % "scala-reflect"              % scalaVersion.value,
      "ch.qos.logback"      % "logback-classic"            % LogbackVersion
    ),
    slickConfig         := (migrations / slickConfig).value,
    slickMetaGenRules   := new MyGenerationRules("tables", "Tables")("maasertracker.generated.models._"),
    Compile / sourceGenerators += mkSlickGenerator(new EntityTableModulesCodeGenerator)
  )

//noinspection ScalaUnusedSymbol,ScalaWeakerAccess
val js = project
  .enablePlugins(ScalaJSBundlerPlugin)
  .dependsOn(shared.js)
  .settings(
    useYarn                         := false,
    autoImport.webpackDevServerPort := 8081,
    scalaJSLinkerConfig             := scalaJSLinkerConfig.value.withSourceMap(false),
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "dev.optics"                        %%% "monocle-macro"      % "3.2.0",
      "com.github.japgolly.scalajs-react" %%% "extra-ext-monocle3" % "2.1.2",
      "io.github.cquiroz"                 %%% "scala-java-time"    % "2.6.0",
      "com.nrinaudo"                      %%% "kantan.csv"         % "0.8.0",
      "io.github.nafg.antd"               %%% "antd-scalajs-react" % "0.0.1"
    ),
    Compile / npmDependencies ++= Seq(
      "react"     -> "17.0.2",
      "react-dom" -> "17.0.2",
      "csstype"   -> "3.0.11"
    ),
    webpackConfigFile               := Some(baseDirectory.value / "custom.webpack.config.js"),
    webpack / version               := "5.89.0",
    startWebpackDevServer / version := "4.15.1",
    webpackCliVersion               := "4.10.0",
    Test / npmDependencies          := Seq(),
    Compile / npmDevDependencies ++= Seq(
      "css-loader"   -> "6.8.1",
      "less-loader"  -> "11.1.3",
      "style-loader" -> "3.3.3"
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
