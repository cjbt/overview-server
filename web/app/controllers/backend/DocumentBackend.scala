package controllers.backend

import akka.stream.scaladsl.Source
import com.google.inject.ImplementedBy
import play.api.libs.json.JsObject
import play.api.Configuration
import javax.inject.Inject
import scala.collection.immutable
import scala.collection.mutable.Buffer
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.{Document,DocumentDisplayMethod,DocumentHeader,PdfNoteCollection}
import com.overviewdocs.models.tables.{DocumentInfos,DocumentInfosImpl,Documents,DocumentsImpl,DocumentTags,Tags}
import com.overviewdocs.query.{Query=>SearchQuery}
import com.overviewdocs.searchindex.SearchResult
import com.overviewdocs.util.Logger
import models.pagination.{Page,PageRequest}
import models.{Selection,SelectionRequest}

@ImplementedBy(classOf[DbDocumentBackend])
trait DocumentBackend {
  /** Lists all Documents for the given parameters. */
  def index(
    selection: Selection,
    pageRequest: PageRequest,
    includeText: Boolean
  ): Future[Page[DocumentHeader]]

  /** Lists all requested Documents, in the requested order. */
  def index(documentSetId: Long, documentIds: Vector[Long]): Future[Vector[Document]]

  /** Streams all requested Documents, in the requested order. */
  def stream(documentSetId: Long, documentIds: Vector[Long]): Source[Document, akka.NotUsed]

  /** Returns a single Document.
    *
    * Will not find the document if it is in a different document set than the
    * one specified. (This is useful both for security and for scaling.)
    */
  def show(documentSetId: Long, documentId: Long): Future[Option[Document]]

  /** Returns a single Document.
    *
    * Avoid this method in favor of show(Long,Long). The extra parameter will
    * help us scale.
    */
  def show(documentId: Long): Future[Option[Document]]

  /** Updates a Document's title.
    *
    * Is a no-op if the Document does not exist or if it exists in a different
    * DocumentSet.
    */
  def updateTitle(documentSetId: Long, documentId: Long, title: String): Future[Unit]

  /** Updates a Document's metadata.
    *
    * Is a no-op if the Document does not exist or if it exists in a different
    * DocumentSet.
    */
  def updateMetadataJson(documentSetId: Long, documentId: Long, metadataJson: JsObject): Future[Unit]

  /** Updates a Document's pdfNotes.
   *
   * Is a no-op if the Document does not exist.
   */
  def updatePdfNotes(documentSetId: Long, documentId: Long, pdfNotes: PdfNoteCollection): Future[Unit]
}

class DbDocumentBackend @Inject() (
  val database: Database,
  val searchBackend: SearchBackend, // TODO make writes go through worker, then NIX THIS! WHEE!
  val configuration: Configuration
) extends DocumentBackend with DbBackend {
  val nDocumentsPerStreamPacket = configuration.get[Int]("overview.n_documents_per_stream_packet")

  import database.api._

  private val NullDocumentHeader = new DocumentHeader {
    override val id = 0L
    override val documentSetId = 0L
    override val url = None
    override val suppliedId = ""
    override val title = ""
    override val pageNumber = None
    override val createdAt = new java.util.Date(0L)
    override val displayMethod = DocumentDisplayMethod.auto
    override val file2Id = None
    override val isFromOcr = false
    override val metadataJson = JsObject(Seq())
    override val text = ""
    override val pdfNotes = PdfNoteCollection(Array())
    override val thumbnailLocation = None
  }

  override def index(selection: Selection, pageRequest: PageRequest, includeText: Boolean): Future[Page[DocumentHeader]] = {
    selection.getDocumentIds(pageRequest)
      .flatMap { (page: Page[Long]) =>
        if (page.pageInfo.total == 0) {
          emptyPage[DocumentHeader](pageRequest)
        } else {
          val documentsFuture: Future[Vector[DocumentHeader]] = includeText match {
            case false => database.seq(InfosByIds.page(page.items))
            case true => database.seq(DocumentsByIds.page(page.items))
          }

          documentsFuture.map { documents: Vector[DocumentHeader] =>
            val documentsById: Map[Long,DocumentHeader] = documents
              .map(document => (document.id -> document))
              .toMap

            page.map(id => documentsById.getOrElse(id, NullDocumentHeader))
          }
        }
      }
  }

  override def index(documentSetId: Long, documentIds: Vector[Long]) = {
    database.seq(byDocumentSetIdAndIds(documentSetId, documentIds)).map { documents =>
      val map: Map[Long,Document] = documents.map((d) => (d.id -> d)).toMap
      documentIds.collect(map)
    }
  }

  override def stream(documentSetId: Long, documentIds: Vector[Long]) = {
    Source(documentIds)
      .grouped(nDocumentsPerStreamPacket)
      .mapAsync(1) { someIds: immutable.Seq[Long] => index(documentSetId, someIds.toVector) }
      .mapConcat(identity)
  }

  override def show(documentSetId: Long, documentId: Long) = {
    database.option(byDocumentSetIdAndId(documentSetId, documentId))
  }

  override def show(documentId: Long) = {
    database.option(byId(documentId))
  }

  override def updateTitle(documentSetId: Long, documentId: Long, title: String) = {
    for {
      _ <- database.runUnit(updateTitleCompiled(documentSetId, documentId).update(Some(title)))
      _ <- searchBackend.refreshDocument(documentSetId, documentId)
    } yield ()
  }

  override def updateMetadataJson(documentSetId: Long, documentId: Long, metadataJson: JsObject) = {
    for {
      _ <- database.runUnit(updateMetadataJsonCompiled(documentSetId, documentId).update(Some(metadataJson)))
      _ <- searchBackend.refreshDocument(documentSetId, documentId)
    } yield ()
  }

  override def updatePdfNotes(documentSetId: Long, documentId: Long, pdfNotes: PdfNoteCollection) = {
    // Assume documentSetId is the top 32 bits of documentId.
    val documentSetId = documentId >> 32
    for {
      _ <- database.runUnit(updatePdfNotesCompiled(documentSetId, documentId).update(Some(pdfNotes)))
      _ <- searchBackend.refreshDocument(documentSetId, documentId)
    } yield ()
  }

  protected object InfosByIds {
    private def q(ids: Vector[Long]) = DocumentInfos.filter(_.id inSet ids)

    def ids(ids: Vector[Long]) = q(ids).map(_.id)

    def page(ids: Vector[Long]) = {
      // We call this one when we're paginating.
      DocumentInfos.filter(_.id inSet ids) // we know we don't have 10M IDs here
    }
  }

  protected object DocumentsByIds {
    private def q(ids: Vector[Long]) = Documents.filter(_.id inSet ids)

    def page(ids: Vector[Long]) = {
      // We call this one when we're paginating.
      Documents
        .filter(_.id inSet ids) // bind: we know we don't have 10M IDs here
    }
  }

  private lazy val byDocumentSetIdAndId = Compiled { (documentSetId: Rep[Long], documentId: Rep[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
  }

  private def byDocumentSetIdAndIds(documentSetId: Long, documentIds: Vector[Long]) = {
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id inSet documentIds)
  }

  private lazy val updateTitleCompiled = Compiled { (documentSetId: Rep[Long], documentId: Rep[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
      .map(_.title)
  }

  private lazy val updateMetadataJsonCompiled = Compiled { (documentSetId: Rep[Long], documentId: Rep[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
      .map(_.metadataJson)
  }

  private lazy val updatePdfNotesCompiled = Compiled { (documentSetId: Rep[Long], documentId: Rep[Long]) =>
    import DocumentsImpl._

    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
      .map(_.pdfNotes)
  }

  private lazy val byId = Compiled { (documentId: Rep[Long]) =>
    Documents.filter(_.id === documentId)
  }
}
