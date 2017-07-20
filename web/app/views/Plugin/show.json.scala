package views.json.Plugin

import play.api.libs.json.{JsValue,Json}

import com.overviewdocs.models.Plugin

object show {
  def apply(plugin: Plugin): JsValue = Json.obj(
    "id" -> plugin.id,
    "name" -> plugin.name,
    "description" -> plugin.description,
    "url" -> plugin.url,
    "autocreate" -> plugin.autocreate,
    "autocreateOrder" -> plugin.autocreateOrder
  )
}
