package maasertracker.js.facades.ant

import scala.scalajs.js.JSConverters.iterableOnceConvertible2JSRichIterableOnce
import scala.scalajs.js.|

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

object Dropdown {
  sealed trait Child
  case class Item(key: String, danger: Boolean = false, disabled: Boolean = false)(val content: VdomNode)(
      val onClick: Callback)
      extends Child
  //noinspection ScalaWeakerAccess
  case object Divider extends Child

  sealed abstract class Trigger(val repr: antdStrings.click | antdStrings.hover | antdStrings.contextMenu)
  object Trigger {
    case object Click       extends Trigger(antdStrings.click)
    //noinspection ScalaUnusedSymbol
    case object Hover       extends Trigger(antdStrings.hover)
    //noinspection ScalaUnusedSymbol
    case object ContextMenu extends Trigger(antdStrings.contextMenu)
  }

  def apply(triggers: Trigger*)(content: TagMod*)(
      menu: Seq[Child]) =
    A.Dropdown
      .apply(overlay =
        A.Menu
          .apply(
            menu.toReactFragment {
              case Divider    => A.Menu.Divider()
              case item: Item =>
                A.Menu.Item
                  .withKey(item.key)
                  .apply(item.content)
                  .danger(item.danger)
                  .disabled(item.disabled)
                  .onClick(_ => item.onClick)
            }
          )
          .rawElement
      )
      .trigger(triggers.toJSArray.map(_.repr))
      .apply(content*)
}
