package maasertracker.js

import scala.annotation.unused
import scala.scalajs.js.|

import io.github.nafg.antd.facade.antd.libTableInterfaceMod.FilterValue
import io.github.nafg.antd.facade.std.Record

import monocle.Lens

case class FilterSpec[P, A](key: String,
                            lens: Lens[P, Set[FilterItem[A]]],
                            filterItemsFunc: Main.State => FilterItems[A]) {
  def applyFilters(state: Main.State, filters: Record[String, FilterValue | Null]): P => P = {
    val filterItems    = filterItemsFunc(state)
    val decodedFilters =
      filters.get(key)
        .flatMap(nullableToOption)
        .map(filterValue => filterValue.map(key => filterItems.fromKey(key.toString)).toSet)
        .getOrElse(Set.empty)

    lens.replace(decodedFilters)
  }

  def pageParamsToKeys(state: Main.State, pageParams: P) = {
    val filterItems = filterItemsFunc(state)
    lens.get(pageParams).map(filterItems.toKey)
  }

  def keysToPageParams(state: Main.State, keys: Iterable[String]) = {
    val filterItems = filterItemsFunc(state)
    lens.replace(keys.flatMap(filterItems.fromKey.get).toSet)
  }
}
object FilterSpec                                                          {
  def apply[P, A](key: String, lens: Lens[P, Set[FilterItem[A]]])(
      filterItems: Main.State => Iterable[FilterItem[A]],
      @unused dummy: Null = null): FilterSpec[P, A] =
    new FilterSpec(key, lens, state => FilterItems(filterItems(state)))

  def applyFilters[P](state: Main.State,
                      filters: Record[String, FilterValue | Null],
                      filterSpecs: Seq[FilterSpec[P, ?]]): P => P =
    pageParams =>
      filterSpecs.foldRight(pageParams)(_.applyFilters(state, filters)(_))
}
