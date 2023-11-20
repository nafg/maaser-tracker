import com.typesafe.config.{Config, ConfigFactory}
import sbt.*

object SlickConfigPlugin extends AutoPlugin {
  object autoImport {
    val slickConfig = settingKey[Config]("The slick database config")
  }

  def load(dir: File, path: String = "db.slick") =
    ConfigFactory.parseFile(new File(dir, "application.conf"))
      .withFallback(ConfigFactory.parseFile(new File(dir, "reference.conf")))
      .resolve()
      .getConfig(path)
}
