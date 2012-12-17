package models

import org.overviewproject.tree.orm.{ Document, DocumentSetCreationJob }
import models.orm.DocumentSet
import models.upload.OverviewUploadedFile

sealed trait OverviewDocumentSet {
  /** database ID */
  val id: Long

  /*
   * XXX we don't have a "list documents" method. To include one, we'd need
   * an API for filtering and pagination--wrappers around Squeryl features.
   */

  /** Creation job, if this DocumentSet isn't complete yet.
    * FIXME: should be models.OverviewDocumentSetCreationJob, but we don't have one
    */
  def creationJob: Option[OverviewDocumentSetCreationJob]

  /** Number of documents.
    *
    * If the DocumentSet hasn't finished being generated, this number may be
    * less than its final value.
    */
  def documentCount: Int

  /** Title of the document set. (Empty string is allowed.) */
  val title: String

  /** Creation date (without milliseconds) */
  val createdAt: java.util.Date
  
  /** The user owning the document set */
  val user: OverviewUser
  
  /** FIXME: Only here because admin page expects it of all jobs */
  val query: String
}

object OverviewDocumentSet {
  trait OverviewDocumentSetImpl extends OverviewDocumentSet {
    protected val ormDocumentSet: DocumentSet

    override val id = ormDocumentSet.id
    override lazy val creationJob = OverviewDocumentSetCreationJob.findByDocumentSetId(id)
    override lazy val documentCount = ormDocumentSet.documentCount.toInt
    override val title = ormDocumentSet.title
    override val createdAt = ormDocumentSet.createdAt
    override lazy val user = {
      OverviewUser.findById(ormDocumentSet.users.single.id).get 
    }
    override lazy val query = ""
  }

  case class CsvImportDocumentSet(protected val ormDocumentSet: DocumentSet) extends OverviewDocumentSetImpl {
    lazy val uploadedFile: Option[OverviewUploadedFile] =
      ormDocumentSet.uploadedFile.map(OverviewUploadedFile.apply)
  }

  case class DocumentCloudDocumentSet(protected val ormDocumentSet: DocumentSet) extends OverviewDocumentSetImpl {
    private def throwOnNull = throw new Exception("DocumentCloudDocumentSet has NULL values it should not have")

    override lazy val query : String = ormDocumentSet.query.getOrElse(throwOnNull)
  }

  /** Factory method */
  def apply(ormDocumentSet: DocumentSet) : OverviewDocumentSet = {
    ormDocumentSet.documentSetType.value match {
      case "CsvImportDocumentSet" => CsvImportDocumentSet(ormDocumentSet)
      case "DocumentCloudDocumentSet" => DocumentCloudDocumentSet(ormDocumentSet)
      case _ => throw new Exception("Impossible document-set type " + ormDocumentSet.documentSetType.value)
    }
  }

  /** Database lookup */
  def findById(id: Long) : Option[OverviewDocumentSet] = {
    DocumentSet.findById(id).map({ ormDocumentSet =>
      OverviewDocumentSet(ormDocumentSet.withUploadedFile.withCreationJob)
    })
  }
}
