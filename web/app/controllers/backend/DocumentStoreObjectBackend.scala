package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.libs.json.{Json,JsObject}
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.DocumentStoreObject
import com.overviewdocs.models.tables.{DocumentStoreObjects,StoreObjects}
import models.Selection

@ImplementedBy(classOf[DbDocumentStoreObjectBackend])
trait DocumentStoreObjectBackend extends Backend {
  /** Fetches a single DocumentStoreObject.
    *
    * Returns `None` if the DocumentStoreObject does not exist.
    */
  def show(documentId: Long, storeObjectId: Long): Future[Option[DocumentStoreObject]]

  /** Shows how many DocumentStoreObjects exist, counted by StoreObject.
    *
    * There are no zero counts.
    *
    * @param storeId Store ID.
    * @param selection Documents to count.
    */
  def countByObject(storeId: Long, selection: Selection): Future[Map[Long,Int]]

  /** Creates a DocumentStoreObject and returns it.
    *
    * Throws `Conflict` or `ParentMissing` when the write fails for sensible
    * reasons.
    */
  def create(documentId: Long, storeObjectId: Long, json: Option[JsObject]): Future[DocumentStoreObject]

  /** Creates several DocumentStoreObjects and returns them.
    *
    * Throws `ParentMissing` on invalid reference. Never throws `Conflict`:
    * any existing (documentId, storeObjectId) rows will be overwritten. If the
    * insert fails, nothing will be inserted.
    *
    * @param storeId skip StoreObjects that aren't in this Store
    * @param entries DocumentStoreObjects to create
    */
  def createMany(storeId: Long, entries: Seq[DocumentStoreObject]): Future[Seq[DocumentStoreObject]]

  /** Modifies a DocumentStoreObject and returns the modified version.
    *
    * Does nothing when the DocumentStoreObject does not exist.
    */
  def update(documentId: Long, storeObjectId: Long, json: Option[JsObject]): Future[Option[DocumentStoreObject]]

  /** Destroys a DocumentStoreObject.
    *
    * Does nothing when the DocumentStoreObject does not exist.
    */
  def destroy(documentId: Long, storeObjectId: Long): Future[Unit]

  /** Destroys several DocumentStoreObjects.
    *
    * @param storeId skip StoreObjects that aren't in this Store
    * @param entries (document ID, object ID) pairs
    */
  def destroyMany(storeId: Long, entries: Seq[(Long,Long)]): Future[Unit]
}

class DbDocumentStoreObjectBackend @Inject() (
  val database: Database
) extends DocumentStoreObjectBackend with DbBackend {
  import database.api._
  import database.executionContext

  private lazy val byIdsCompiled = Compiled { (documentId: Rep[Long], storeObjectId: Rep[Long]) =>
    DocumentStoreObjects
      .filter(_.documentId === documentId)
      .filter(_.storeObjectId === storeObjectId)
  }

  private lazy val attributesByIdsCompiled = Compiled { (documentId: Rep[Long], storeObjectId: Rep[Long]) =>
    DocumentStoreObjects
      .filter(_.documentId === documentId)
      .filter(_.storeObjectId === storeObjectId)
      .map(_.json)
  }

  private lazy val countByObjectCompiled = Compiled { (storeId: Rep[Long]) =>
    val storeObjectIds = StoreObjects
      .filter(_.storeId === storeId)
      .map(_.id)

    DocumentStoreObjects
      .filter(_.storeObjectId in storeObjectIds)
      .groupBy(_.storeObjectId)
      .map { case (storeObjectId, group) => (storeObjectId, group.length) }
  }

  private def countByObjectAndDocumentIds(storeId: Long, documentIds: Seq[Long]) = {
    val storeObjectIds = StoreObjects
      .filter(_.storeId === storeId)
      .map(_.id)

    DocumentStoreObjects
      .filter(_.storeObjectId in storeObjectIds)
      .filter(_.documentId inSet documentIds)
      .groupBy(_.storeObjectId)
      .map { case (storeObjectId, group) => (storeObjectId, group.length) }
  }

  lazy val inserter = (DocumentStoreObjects returning DocumentStoreObjects)

  override def show(documentId: Long, storeObjectId: Long) = {
    database.option(byIdsCompiled(documentId, storeObjectId))
  }

  override def countByObject(storeId: Long, selection: Selection) = {
    selection.getAllDocumentIds
      .flatMap(ids => database.seq(countByObjectAndDocumentIds(storeId, ids)))
      .map(_.toMap)
  }

  override def create(documentId: Long, storeObjectId: Long, json: Option[JsObject]) = {
    database.run(inserter.+=(DocumentStoreObject(documentId, storeObjectId, json)))
  }

  implicit lazy val getDsoResult = new slick.jdbc.GetResult[DocumentStoreObject] {
    override def apply(v1: slick.jdbc.PositionedResult): DocumentStoreObject = {
      DocumentStoreObject(
        v1.nextLong,
        v1.nextLong,
        v1.nextStringOption.map(Json.parse(_).as[JsObject])
      )
    }
  }

  override def createMany(storeId: Long, entries: Seq[DocumentStoreObject]) = {
    /*
     * We run both DELETE and INSERT in one query, to save bandwidth and let
     * Postgres handle atomicity. (This doesn't avoid the race between DELETE
     * and INSERT, but it does make Postgres roll back on error.)
     *
     * Do not omit `WHERE (SELECT COUNT(*) FROM deleted) IS NOT NULL`: that
     * actually executes the DELETE.
     */
    def jsonToSql(json: JsObject) = s"'${json.toString.replaceAll("'", "''")}'"
    val dsosAsSqlTuples: Seq[String] = entries
      .map((dso: DocumentStoreObject) => "(" + dso.documentId + "," + dso.storeObjectId + "," + dso.json.map(jsonToSql _).getOrElse("NULL") + ")")

    database.run(sql"""
      WITH request AS (
        SELECT *
        FROM (VALUES #${dsosAsSqlTuples.mkString(",")})
          AS t(document_id, store_object_id, json_text)
        WHERE document_id IN (
            SELECT id
            FROM document
            WHERE document_set_id IN (
              SELECT document_set_id
              FROM api_token
              WHERE token IN (SELECT api_token FROM store WHERE id = $storeId)
            )
          )
          AND store_object_id IN (SELECT id FROM store_object WHERE store_id = $storeId)
      ),
      deleted AS (
        DELETE FROM document_store_object
        WHERE (document_id, store_object_id) IN (SELECT document_id, store_object_id FROM request)
        RETURNING 1
      )
      INSERT INTO document_store_object (document_id, store_object_id, json_text)
      SELECT document_id, store_object_id, json_text FROM request
      WHERE (SELECT COUNT(*) FROM deleted) IS NOT NULL
      RETURNING document_id, store_object_id, json_text
    """.as[DocumentStoreObject])
  }

  override def update(documentId: Long, storeObjectId: Long, json: Option[JsObject]) = {
    database.run(attributesByIdsCompiled(documentId, storeObjectId).update(json))
      .map(_ match {
        case 0 => None
        case _ => Some(DocumentStoreObject(documentId, storeObjectId, json))
      })
  }

  override def destroy(documentId: Long, storeObjectId: Long) = {
    database.delete(byIdsCompiled(documentId, storeObjectId))
  }

  override def destroyMany(storeId: Long, entries: Seq[(Long,Long)]) = {
    val tuplesAsSql: Seq[String] = entries
      .map((t: Tuple2[Long,Long]) => "(" + t._1 + "," + t._2 + ")")

    database.runUnit(sqlu"""
      DELETE FROM document_store_object
      WHERE (document_id, store_object_id) IN (VALUES #${tuplesAsSql.mkString(",")})
        AND store_object_id IN (SELECT id FROM store_object WHERE store_id = $storeId)
    """)
  }
}
