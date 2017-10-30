package com.overviewdocs.models

import java.util.UUID

/** A plugin: a website that helps us interact with documents.
  *
  * Plugins aren't actually stored in Overview: they're stored elsewhere on the
  * Internet. But Overview maintains a _registry_ that points to the actual
  * plugins. Plugin objects within Overview are retistry entries.
  */
case class Plugin(
  id: UUID,
  name: String,
  description: String,
  url: String,                         /// URL browser uses to see plugin (in iframe)
  serverUrlFromPlugin: Option[String], /// URL plugin server uses to query Overview API
  autocreate: Boolean,
  autocreateOrder: Int
)

object Plugin {
  case class CreateAttributes(
    name: String,
    description: String,
    url: String,
    serverUrlFromPlugin: Option[String],
    autocreate: Boolean,
    autocreateOrder: Int
  )

  case class UpdateAttributes(
    name: String,
    description: String,
    url: String,
    serverUrlFromPlugin: Option[String],
    autocreate: Boolean,
    autocreateOrder: Int
  )

  def build(attributes: CreateAttributes) = apply(
    id=UUID.randomUUID(),
    name=attributes.name,
    description=attributes.description,
    url=attributes.url,
    serverUrlFromPlugin=attributes.serverUrlFromPlugin,
    autocreate=attributes.autocreate,
    autocreateOrder=attributes.autocreateOrder
  )
}
