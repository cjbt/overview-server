package com.overviewdocs.models.tables

import java.util.Date
import play.api.libs.json.{JsObject,Json}
import scala.util.Try

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.{Document,DocumentDisplayMethod,PdfNote,PdfNoteCollection}

object DocumentsImpl {
  implicit val dateColumnType = MappedColumnType.base[Date, java.sql.Timestamp](
    d => new java.sql.Timestamp(d.getTime),
    d => new Date(d.getTime)
  )

  implicit val pdfNoteFormat = Json.format[PdfNote]

  implicit val pdfNotesColumnType = MappedColumnType.base[PdfNoteCollection, String](
    (d: PdfNoteCollection) => Json.toJson(d.pdfNotes).toString,
    { (d: String) =>
      Try(Json.parse(d)).toOption.flatMap(_.asOpt[Seq[PdfNote]]) match {
        case Some(array) => PdfNoteCollection(array.toArray)
        case None => PdfNoteCollection(Array())
      }
    }
  )
}

class DocumentsImpl(tag: Tag) extends Table[Document](tag, "document") {
  import DocumentsImpl._

  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def createdAt = column[Date]("created_at")(dateColumnType)
  def text = column[Option[String]]("text")
  def url = column[Option[String]]("url")
  def suppliedId = column[Option[String]]("supplied_id")
  def documentcloudId = column[Option[String]]("documentcloud_id")
  def title = column[Option[String]]("title")
  def fileId = column[Option[Long]]("file_id")
  def pageId = column[Option[Long]]("page_id")
  def pageNumber = column[Option[Int]]("page_number")
  def displayMethod = column[Option[DocumentDisplayMethod.Value]]("display_method")
  def isFromOcr = column[Option[Boolean]]("is_from_ocr")
  def metadataJson = column[Option[JsObject]]("metadata_json_text") // add DocumentSet.metadataSchema to make a Metadata
  def thumbnailLocation = column[Option[String]]("thumbnail_location")
  def pdfNotes = column[Option[PdfNoteCollection]]("pdf_notes_json_text")(pdfNotesColumnType.optionType)

  /*
   * Unfortunately, our database allows NULL in some places it shouldn't. Slick
   * can only handle this with a column[Option[_]] -- no type mappers allowed.
   * So we have to map the types here.
   */
  def * = (
    id,
    documentSetId,
    url,
    suppliedId,
    documentcloudId,
    title,
    pageNumber,
    createdAt,
    fileId,
    pageId,
    displayMethod,
    isFromOcr,
    metadataJson,
    thumbnailLocation,
    pdfNotes,
    text
  ).<>(
    (t: Tuple16[Long,Long,Option[String],Option[String],Option[String],Option[String],Option[Int],Date,Option[Long],Option[Long],Option[DocumentDisplayMethod.Value],Option[Boolean],Option[JsObject],Option[String],Option[PdfNoteCollection],Option[String]]) => Document.apply(
      t._1,
      t._2,
      t._3,
      t._4.orElse(t._5).getOrElse(""),  // suppliedId || documentcloudId || ""
      t._6.getOrElse(""),               // title
      t._7,
      t._8,
      t._9,
      t._10,
      t._11.getOrElse(DocumentDisplayMethod.auto),
      t._12.getOrElse(false),                      // isFromOcr
      t._13.getOrElse(JsObject(Seq())),            // metadataJson
      t._14,                                       // thumbnail
      t._15.getOrElse(PdfNoteCollection(Array())), // pdfNotes
      t._16.getOrElse("")                          // text
    ),
    { d: Document => Some(
      d.id,
      d.documentSetId,
      d.url,
      Some(d.suppliedId),
      None,
      Some(d.title),
      d.pageNumber,
      d.createdAt,
      d.fileId,
      d.pageId,
      Some(d.displayMethod),
      Some(d.isFromOcr),
      Some(d.metadataJson),
      d.thumbnailLocation,
      Some(d.pdfNotes),
      Some(d.text)
    )}
  )
}

object Documents extends TableQuery(new DocumentsImpl(_))
