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
import io.github.nafg.antd.facade.antd.libConfigProviderSizeContextMod._SizeType
import io.github.nafg.antd.facade.antd.libGridColMod.FlexType
import io.github.nafg.antd.facade.antd.libTableInterfaceMod.*
import io.github.nafg.antd.facade.antd.libTooltipMod.TooltipProps
import io.github.nafg.antd.facade.antd.{antdBooleans, antdStrings, components as A}
import io.github.nafg.antd.facade.rcTable
import io.github.nafg.antd.facade.react.mod.{CSSProperties, RefAttributes}
import io.github.nafg.antd.facade.std.Record

object Facades {
  object Ant {
    object Button {
      sealed abstract class Type(val repr: ButtonType)
      object Type {
        case object Default extends Type(antdStrings.default)
        //noinspection ScalaUnusedSymbol
        case object Dashed  extends Type(antdStrings.dashed)
        //noinspection ScalaUnusedSymbol
        case object Ghost   extends Type(antdStrings.ghost)
        //noinspection ScalaUnusedSymbol
        case object Link    extends Type(antdStrings.link)
        //noinspection ScalaUnusedSymbol
        case object Primary extends Type(antdStrings.primary)
        //noinspection ScalaUnusedSymbol
        case object Text    extends Type(antdStrings.text_)
      }
      def apply(buttonType: Type = Type.Default,
                danger: Boolean = false,
                onClick: ReactMouseEventFrom[HTMLElement] => Callback = _ => Callback.empty,
                title: String = "")(
          content: TagMod*) =
        A.Button
          .apply(content*)
          .`type`(buttonType.repr)
          .danger(danger)
          .onClick(onClick)
          .title(title)
    }
    object Card   {
      sealed abstract class Size(val repr: CardSize)
      object Size {
        case object Default extends Size(antdStrings.default)
        case object Small   extends Size(antdStrings.small)
      }

      def apply(size: Size = Size.Default)(content: TagMod*) =
        A.Card
          .apply(content*)
          .size(size.repr)
    }

    object Col      {
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

    object Layout {
      def apply(style: js.UndefOr[CSSProperties] = js.undefined)(content: TagMod*) = {
        val step1 = A.Layout()
        style.fold(step1)(step1.style)
          .apply(content*)
      }

      def Content(content: TagMod*) =
        A.Layout.Content
          .apply(content*)
    }

    def Row(content: TagMod*) =
      A.Row
        .apply(content*)

    object Space {
      sealed abstract class Direction(val repr: antdStrings.horizontal | antdStrings.vertical)
      object Direction {
        case object Horizontal extends Direction(antdStrings.horizontal)
        case object Vertical   extends Direction(antdStrings.vertical)
      }

      def apply(direction: Direction = Direction.Horizontal)(content: TagMod*) =
        A.Space
          .apply(content*)
          .direction(direction.repr)
    }
    object Table {
      case class ScrollConfig(x: js.UndefOr[String] = js.undefined,
                              y: js.UndefOr[String] = js.undefined,
                              scrollToFirstRowOnChange: js.UndefOr[Boolean] = js.undefined)

      sealed abstract class Size(val repr: _SizeType)
      object Size {
        case object Small  extends Size(antdStrings.small)
        //noinspection ScalaUnusedSymbol
        case object Middle extends Size(antdStrings.middle)
        //noinspection ScalaUnusedSymbol
        case object Large  extends Size(antdStrings.large)
      }

      sealed abstract class Pagination(val repr: TablePaginationConfig | antdBooleans.`false`)
      object Pagination {
        case object False                                extends Pagination(antdBooleans.`false`)
        //noinspection ScalaUnusedSymbol
        case class Custom(config: TablePaginationConfig) extends Pagination(config)
      }

      def apply[A](dataSource: Seq[A])(
          columns: Seq[ColumnGroupType[A] | ColumnType[A]],
          onChange: (TablePaginationConfig,
                     Record[String, FilterValue | Null],
                     SorterResult[A] | js.Array[SorterResult[A]],
                     TableCurrentDataSource[A]) => Callback,
          pagination: js.UndefOr[Pagination] = js.undefined,
          rowClassName: (A, Int) => String,
          rowKey: A => String,
          scroll: js.UndefOr[ScrollConfig] = js.undefined,
          size: js.UndefOr[Size] = js.undefined
      ): VdomElement = {
        val step1 =
          A.Table[A]()
            .dataSource(dataSource.toJSArray)
            .columns(columns.toJSArray)
            .onChange(onChange)
            .rowClassNameFunction3 { case (a, index, _) => rowClassName(a, index.toInt) }
            .rowKeyFunction2 { case (a, _) => rowKey(a) }
            .scroll(
              scroll
                .map { case ScrollConfig(x, y, scrollToFirstRowOnChange) =>
                  js.Dynamic.literal(x = x, y = y, scrollToFirstRowOnChange = scrollToFirstRowOnChange)
                }
                .asInstanceOf[js.UndefOr[rcTable.anon.X] & ScrollToFirstRowOnChange]
            )
            .size(size.map(_.repr))

        pagination.fold(step1)(p => step1.pagination(p.repr))
      }
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
