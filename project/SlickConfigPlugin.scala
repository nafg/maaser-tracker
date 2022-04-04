import com.typesafe.config.{Config, ConfigFactory}
import sbt._

object SlickConfigPlugin extends AutoPlugin {
  object autoImport {
    val slickConfig = settingKey[Config]("The slick database config")
  }

  def load(file: File, path: String = "db.slick") = ConfigFactory.load(ConfigFactory.parseFile(file)).getConfig(path)
}
