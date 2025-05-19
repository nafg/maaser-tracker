package maasertracker.js

import scala.scalajs.*
import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.|

import org.scalajs.dom.HTMLElement
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, ReactMouseEventFrom}
import io.github.nafg.antd.facade.antd.anon.ScrollToFirstRowOnChange
import io.github.nafg.antd.facade.antd.libButtonButtonMod.ButtonType
import io.github.nafg.antd.facade.antd.libCardMod.CardSize
import io.github.nafg.antd.facade.antd.libConfigProviderSizeContextMod.SizeType
import io.github.nafg.antd.facade.antd.libGridColMod.FlexType
import io.github.nafg.antd.facade.antd.libTableInterfaceMod.*
import io.github.nafg.antd.facade.antd.libTooltipMod.TooltipProps
import io.github.nafg.antd.facade.antd.{antdBooleans, antdStrings, components as A}
import io.github.nafg.antd.facade.rcTable
import io.github.nafg.antd.facade.react.mod.{CSSProperties, RefAttributes}
import io.github.nafg.antd.facade.std.Record

object Facades {
  object Ant {
    def Button(buttonType: ButtonType = antdStrings.default,
               danger: Boolean = false,
               onClick: ReactMouseEventFrom[HTMLElement] => Callback = _ => Callback.empty,
               title: String = "")(
        content: TagMod*) =
      A.Button
        .apply(content*)
        .`type`(buttonType)
        .danger(danger)
        .onClick(onClick)
        .title(title)

    def Card(size: CardSize)(content: TagMod*) =
      A.Card
        .apply(content*)
        .size(size)

    def Col(flex: FlexType)(content: TagMod*) =
      A.Col
        .apply(content*)
        .flex(flex)

    object Dropdown {
      sealed trait Child
      case class Item(key: String, danger: Boolean = false, disabled: Boolean = false)(val content: VdomNode)(
          val onClick: Callback)
          extends Child
      //noinspection ScalaWeakerAccess
      case object Divider extends Child

      def apply(triggers: (antdStrings.click | antdStrings.hover | antdStrings.contextMenu)*)(content: TagMod*)(
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
          .trigger(triggers.toJSArray)
          .apply(content*)
    }

    object Layout {
      def apply(style: CSSProperties)(content: TagMod*) =
        A.Layout
          .apply(content*)
          .style(style)

      def Content(content: TagMod*) =
        A.Layout.Content
          .apply(content*)
    }

    def Row(content: TagMod*) =
      A.Row
        .apply(content*)

    def Space(direction: antdStrings.horizontal | antdStrings.vertical = antdStrings.horizontal)(content: TagMod*) =
      A.Space
        .apply(content*)
        .direction(direction)

    object Table {
      case class ScrollConfig(x: js.UndefOr[String] = js.undefined,
                              y: js.UndefOr[String] = js.undefined,
                              scrollToFirstRowOnChange: js.UndefOr[Boolean] = js.undefined)

      def apply[A](dataSource: Seq[A])(
          columns: Seq[ColumnGroupType[A] | ColumnType[A]],
          onChange: (TablePaginationConfig,
                     Record[String, FilterValue | Null],
                     SorterResult[A] | js.Array[SorterResult[A]],
                     TableCurrentDataSource[A]) => Callback,
          pagination: TablePaginationConfig | antdBooleans.`false` = null,
          rowClassName: (A, Int) => String,
          rowKey: A => String,
          scroll: js.UndefOr[ScrollConfig] = js.undefined,
          size: SizeType
      ): VdomElement =
        A.Table[A]()
          .dataSource(dataSource.toJSArray)
          .columns(columns.toJSArray)
          .onChange(onChange)
          .pagination(pagination)
          .rowClassNameFunction3 { case (a, index, _) => rowClassName(a, index.toInt) }
          .rowKeyFunction2 { case (a, _) => rowKey(a) }
          .scroll(
            scroll
              .map { case ScrollConfig(x, y, scrollToFirstRowOnChange) =>
                js.Dynamic.literal(x = x, y = y, scrollToFirstRowOnChange = scrollToFirstRowOnChange)
              }
              .asInstanceOf[js.UndefOr[rcTable.anon.X] & ScrollToFirstRowOnChange]
          )
          .size(size)
    }

    def Tooltip(title: VdomNode)(children: VdomNode) =
      A.Tooltip
        .apply(
          TooltipProps.TooltipPropsWithTitle()
            .setTitle(title.rawNode)
            .setChildren(children)
            .combineWith(RefAttributes[Any]())
        )

  }
}
