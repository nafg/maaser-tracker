package maasertracker.js.facades.ant

import scala.scalajs.js
import scala.scalajs.js.|

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.libGridColMod.FlexType
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

object Col {
  sealed abstract class Flex(val repr: FlexType)
  object Flex {
    case object Auto                 extends Flex(antdStrings.auto)
    case object None                 extends Flex(antdStrings.none)
    implicit class Num(self: Double) extends Flex(self)
    implicit class Str(self: String) extends Flex(|.from(self))
  }

  def apply(flex: js.UndefOr[Flex] = js.undefined)(content: TagMod*) = {
    val step1 =
      A.Col
        .apply(content*)

    flex.fold(step1)(f => step1.flex(f.repr))
  }
}
