package com.overviewdocs.metadata

import com.fasterxml.jackson.core.JsonProcessingException
import play.api.libs.json.{JsArray,JsNumber,JsObject,JsString,JsValue,Json}
import scala.util.{Failure,Success,Try}

/** Schema that describes a Document's metadata.
  *
  * Document metadata is encoded as JSON. See Metadata for more details.
  *
  * The `version` must always be 1: anything else is an error.
  */
case class MetadataSchema(version: Int, fields: Seq[MetadataField]) {
  def toJson: JsValue = JsObject(Seq(
    "version" -> JsNumber(1),
    "fields" -> JsArray(fields.map { field =>
      Json.obj(
        "name" -> field.name,
        "type" -> (field.fieldType match {
          case MetadataFieldType.String => "String"
        })
      )
    })
  ))
}

object MetadataSchema {
  def fromJson(json: JsValue): MetadataSchema = MetadataSchema.Json.parse(json)

  /** Given a JSON String, returns a MetadataSchema that will absorb as much
    * data as possible from that JSON string.
    *
    * If the JSON is invalid or not an Object, returns an empty schema.
    *
    * This method should be a last resort: it works with Strings, but when we
    * get new types beyond standard JSON ones, it won't work.
    */
  def inferFromMetadataJson(jsObject: JsObject): MetadataSchema = {
    val fields = jsObject.keys.map(key => MetadataField(key, MetadataFieldType.String))
    MetadataSchema(1, fields.toSeq)
  }

  def empty: MetadataSchema = MetadataSchema(1, Seq())

  object Json {
    import play.api.libs.json.{Reads,JsPath,JsResultException,JsonValidationError}
    import play.api.libs.json.Reads._
    import play.api.libs.functional.syntax._

    private val badTypeError = JsonValidationError("Invalid \"type\" value")

    private implicit val metadataFieldTypeReads: Reads[MetadataFieldType] = StringReads.collect(badTypeError) {
      case "String" => MetadataFieldType.String
    }

    private implicit val metadataFieldReads: Reads[MetadataField] = (
      (JsPath \ "name").read[String] and
      (JsPath \ "type").read[MetadataFieldType]
    )(MetadataField.apply _)

    implicit val reads: Reads[MetadataSchema] = (
      (JsPath \ "version").read[Int](min(1) keepAnd max(1)) and
      (JsPath \ "fields").read[Seq[MetadataField]]
    )(MetadataSchema.apply _)

    def parse(json: JsValue): MetadataSchema = try {
      json.as[MetadataSchema]
    } catch {
      case e: JsResultException => throw new IllegalArgumentException(e)
    }
  }
}
