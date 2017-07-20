package views.json.api.View

import play.api.libs.json.{JsValue,Json}

import com.overviewdocs.models.View

object index {
  def apply(views: Seq[View]) : JsValue = {
    val jsons: Seq[JsValue] = views.map(show(_))
    Json.toJson(jsons)
  }
}
