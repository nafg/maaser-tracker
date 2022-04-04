import SlickConfigPlugin.autoImport.slickConfig
import sbt.Keys._
import sbt._
import slick.additions.codegen.{BaseCodeGenerator, GenerationRules}

import scala.concurrent.ExecutionContext.Implicits.global

object SlickAdditionsCodegenPlugin extends AutoPlugin {
  override def requires = SlickConfigPlugin

  object autoImport {
    val slickProfileClassName = settingKey[String]("The slick profile class")
    val slickMetaGenRules     = settingKey[GenerationRules]("The GenerationRules instance to use")

    def mkSlickGenerator(generator: BaseCodeGenerator) =
      Def.task {
        val rules   = slickMetaGenRules.value
        val baseDir = (Compile / sourceManaged).value
        val config  = slickConfig.value
        val file    = generator.doWriteToFile(baseDir.toPath, config, rules).toFile
        Seq(file)
      }
  }
}
