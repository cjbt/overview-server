package views.json.api.ApiToken

import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.{JsValue, Json}

import com.overviewdocs.models.ApiToken

object show {
  private val iso8601Format = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC)
  private def formatDate(date: java.util.Date) = iso8601Format.print(date.getTime())

  def apply(token: ApiToken) : JsValue = {
    Json.obj(
      "token" -> token.token,
      "description" -> token.description,
      "createdAt" -> formatDate(token.createdAt),
      "userEmail" -> token.createdBy,
      "documentSetId" -> token.documentSetId
    )
  }
}
