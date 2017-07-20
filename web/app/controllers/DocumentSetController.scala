package controllers

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsError,JsObject,JsResult,JsSuccess}
import play.api.mvc.BodyParsers.parse
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{DocumentSet,ImportJob,Tag,Tree}
import com.overviewdocs.models.tables.{DocumentSets,Tags,Trees}
import controllers.auth.{AuthorizedAction,Authorities}
import controllers.backend.{DocumentSetBackend,ImportJobBackend,ViewBackend}
import controllers.forms.DocumentSetUpdateForm
import controllers.util.JobQueueSender
import models.pagination.{Page,PageRequest}

class DocumentSetController @Inject() (
  backend: DocumentSetBackend,
  storage: DocumentSetController.Storage,
  jobQueue: JobQueueSender,
  importJobBackend: ImportJobBackend,
  viewBackend: ViewBackend,
  val controllerComponents: ControllerComponents,
  val indexHtml: views.html.DocumentSet.index,
  val showHtml: views.html.DocumentSet.show,
  val showProgressHtml: views.html.DocumentSet.showProgress
) extends BaseController {
  import Authorities._

  protected val indexPageSize = 10

  def index(page: Int) = authorizedAction(anyUser).async { implicit request =>
    val requestedPage: Int = RequestData(request).getInt("page").getOrElse(0)
    val realPage = if (requestedPage <= 0) 1 else requestedPage
    val pageRequest = PageRequest((realPage - 1) * indexPageSize, indexPageSize, false)

    for {
      jobs: Seq[ImportJob] <- importJobBackend.indexByUser(request.user.email)
      documentSets: Page[DocumentSet] <- backend.indexPageByOwner(request.user.email, pageRequest)
    } yield {
      if (documentSets.pageInfo.total == 0) {
        Redirect(routes.PublicDocumentSetController.index).flashing(request.flash)
      } else {
        val jobsById: Map[Long,Iterable[ImportJob]] = jobs.groupBy(_.documentSetId)
        val nViewsById: Map[Long,Int] = storage.findNViewsByDocumentSets(documentSets.items.map(_.id))
        val detailedDocumentSets: Page[(DocumentSet,Iterable[ImportJob],Int)] = documentSets.map { documentSet =>
          (
            documentSet,
            jobsById.getOrElse(documentSet.id, Iterable()),
            nViewsById.getOrElse(documentSet.id, 0)
          )
        }
        Ok(indexHtml(request.user, detailedDocumentSets))
      }
    }
  }

  /** Shows a DocumentSet to the user.
    *
    * This is a shell of a page with a giant JavaScript application inside it.
    *
    * Ignore the silly "jsParams" argument. It's so users can bookmark URLs.
    *
    * * GET /documentsets/123 -&gt; load the first View
    * * GET /documentsets/123/trees/456 -&gt; load a specific View
    *
    * JavaScript will parse the rest of the URL.
    */
  def show(id: Long) = authorizedAction(userViewingDocumentSet(id)).async { implicit request =>
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        importJobBackend.indexByDocumentSet(id).map(_ match {
          case Seq() => Ok(showHtml(request.user, documentSet))
          case importJobs: Seq[ImportJob] => Ok(showProgressHtml(request.user, documentSet, importJobs))
        })
      }
    })
  }

  def showWithJsParams(id: Long, jsParams: String) = show(id)

  def showHtmlInJson(id: Long) = authorizedAction(userViewingDocumentSet(id)).async { implicit request =>
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        for {
          jobs: Iterable[ImportJob] <- importJobBackend.indexByDocumentSet(documentSet.id)
          nViews: Int <- Future.successful(storage.findNViewsByDocumentSets(Seq(id)).get(id).getOrElse(0))
        } yield Ok(views.json.DocumentSet.showHtml(documentSet, jobs, nViews))
      }
    })
  }

  def showJson(id: Long) = authorizedAction(userViewingDocumentSet(id)).async { implicit request =>
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        val trees = storage.findTrees(id).map(_.copy()).toArray
        val tags = storage.findTags(id).map(_.copy()).toArray

        for {
          _views <- viewBackend.index(id)
        } yield Ok(views.json.DocumentSet.show(
          documentSet,
          trees,
          _views,
          tags
        ))
      }
    })
  }

  def delete(id: Long) = authorizedAction(userOwningDocumentSet(id)) { implicit request =>
    storage.deleteDocumentSet(id)
    jobQueue.send(DocumentSetCommands.DeleteDocumentSet(id))
    Accepted.flashing("event" -> "document-set-delete")
  }

  def update(id: Long) = authorizedAction(adminUser).async { implicit request =>
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        DocumentSetUpdateForm(documentSet).bindFromRequest().fold(
          f => Future.successful(BadRequest),
          hackyDocumentSet => backend.updatePublic(id, hackyDocumentSet.public).map(_ => NoContent)
        )
      }
    })
  }

  def updateJson(id: Long) = authorizedAction(userOwningDocumentSet(id)).async { implicit request =>
    // The interface is complicated enough that we should create error messages
    // ourselves.
    //
    // One error if metadataSchema is not *set*; another error if it is not
    // *valid*.
    val maybeMetadataSchema: Option[JsResult[MetadataSchema]] = for {
      jsonBody <- request.body.asJson
      jsonBodyAsObject <- jsonBody.asOpt[JsObject]
      metadataSchemaJson <- jsonBodyAsObject.value.get("metadataSchema")
    } yield metadataSchemaJson.validate[MetadataSchema](MetadataSchema.Json.reads)

    def err(code: String, message: String) = Future.successful(BadRequest(jsonError(code, message)))

    maybeMetadataSchema match {
      case None => err("illegal-arguments", "You must specify a metadataSchema property in the JSON body")
      case Some(JsError(_)) => err(
        "illegal-arguments",
        """metadataSchema should look like { "version": 1, "fields": [ { "name": "foo", "type": "String" } ] }"""
      )
      case Some(JsSuccess(metadataSchema, _)) => backend.updateMetadataSchema(id, metadataSchema).map(_ => NoContent)
    }
  }
}

object DocumentSetController {
  @ImplementedBy(classOf[DocumentSetController.DatabaseStorage])
  trait Storage {
    /** Returns a mapping from DocumentSet ID to nViews+nTrees.
      */
    def findNViewsByDocumentSets(documentSetIds: Seq[Long]): Map[Long,Int]

    def deleteDocumentSet(documentSetId: Long): Unit

    /** All Views for the document set. */
    def findTrees(documentSetId: Long) : Iterable[Tree]

    /** All Tags for the document set. */
    def findTags(documentSetId: Long) : Iterable[Tag]
  }

  class DatabaseStorage @Inject() extends Storage with HasBlockingDatabase {
    import database.api._

    override def deleteDocumentSet(documentSetId: Long) = {
      blockingDatabase.runUnit(
        DocumentSets
          .filter(_.id === documentSetId)
          .map(_.deleted).update(true)
      )
    }

    override def findNViewsByDocumentSets(documentSetIds: Seq[Long]) = {
      if (documentSetIds.isEmpty) {
        Map()
      } else {
        import database.api._
        import slick.jdbc.GetResult

        // TODO get rid of Trees. Then Slick queries
        // would make more sense than straight SQL.
        blockingDatabase.run(sql"""
          WITH ids AS (
            SELECT *
            FROM (VALUES #${documentSetIds.map("(" + _ + ")").mkString(",")}) AS t(id)
          ), counts1 AS (
            SELECT document_set_id, COUNT(*) AS c
            FROM tree
            WHERE document_set_id IN (SELECT id FROM ids)
            GROUP BY document_set_id
          ), counts2 AS (
            SELECT document_set_id, COUNT(*) AS c
            FROM "view"
            WHERE document_set_id IN (SELECT id FROM ids)
            GROUP BY document_set_id
          ), all_counts AS (
            SELECT * FROM counts1
            UNION
            SELECT * FROM counts2
          )
          SELECT document_set_id, SUM(c)
          FROM all_counts
          GROUP BY document_set_id
        """.as[(Long,Int)])
          .toMap
      }
    }

    override def findTrees(documentSetId: Long) = {
      blockingDatabase.seq(Trees.filter(_.documentSetId === documentSetId))
    }

    override def findTags(documentSetId: Long) = {
      blockingDatabase.seq(Tags.filter(_.documentSetId === documentSetId))
    }
  }
}
