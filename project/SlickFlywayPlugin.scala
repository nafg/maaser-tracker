import io.github.davidmweber.FlywayPlugin
import sbt.AutoPlugin

object SlickFlywayPlugin extends AutoPlugin {
  override def requires = SlickConfigPlugin && FlywayPlugin

  import FlywayPlugin.autoImport.{flywayPassword, flywayUrl, flywayUser}
  import SlickConfigPlugin.autoImport.slickConfig

  override def projectSettings =
    Seq(
      flywayUrl      := slickConfig.value.getString("url"),
      flywayUser     := slickConfig.value.getString("user"),
      flywayPassword := slickConfig.value.getString("password")
    )
}
