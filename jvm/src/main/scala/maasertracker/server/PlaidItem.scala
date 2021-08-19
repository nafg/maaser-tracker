package maasertracker.server

import io.circe.generic.JsonCodec
import maasertracker.Institution

@JsonCodec
case class PlaidItem(itemId: String, accessToken: String, institution: Institution) {
  def toShared = maasertracker.PlaidItem(itemId = itemId, institution = institution)
}
