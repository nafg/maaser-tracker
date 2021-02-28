package maasertracker

import com.plaid.client.PlaidClient

package object server {
  val configRepo = new JsonRepo[Config]("config")

  lazy val plaidService = {
    val config = configRepo()
    val plaid  =
      PlaidClient
        .newBuilder()
        .clientIdAndSecret(config.clientId, config.clientSecret)
        .developmentBaseUrl()
        .build()
    plaid.service()
  }

  val itemsRepo = new JsonRepo[Seq[PlaidItem]]("items")
}
