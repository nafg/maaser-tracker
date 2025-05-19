package maasertracker.js.facades.ant

import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.|

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.anon.ScrollToFirstRowOnChange
import io.github.nafg.antd.facade.antd.libConfigProviderSizeContextMod._SizeType
import io.github.nafg.antd.facade.antd.libTableInterfaceMod.*
import io.github.nafg.antd.facade.antd.{antdBooleans, antdStrings, components as A}
import io.github.nafg.antd.facade.rcTable
import io.github.nafg.antd.facade.std.Record

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
