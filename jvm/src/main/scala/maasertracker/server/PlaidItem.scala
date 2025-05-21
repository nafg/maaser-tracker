package maasertracker.server

import maasertracker.Institution

case class PlaidItem(itemId: String, accessToken: String, institution: Institution) {
  def toShared = maasertracker.PlaidItem(itemId = itemId, institution = institution)
}
