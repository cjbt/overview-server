package views.json.api.StoreObject

import play.api.libs.json.{JsValue,Json}

import com.overviewdocs.models.StoreObject

object show {
  def apply(storeObject: StoreObject): JsValue = Json.obj(
    "id" -> storeObject.id,
    "indexedLong" -> storeObject.indexedLong,
    "indexedString" -> storeObject.indexedString,
    "json" -> storeObject.json
  )
}
