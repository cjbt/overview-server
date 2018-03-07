package models.upload

import java.sql.Timestamp
import java.util.UUID

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.{Upload,UploadedFile}
import com.overviewdocs.models.tables.{UploadedFiles,Uploads}

/**
 * Wrapper around models.orm.Upload hiding details of storage and managing
 * timestamp information.
 * Currently the Large Object referenced by oid is managed separately, and
 * client code needs to make sure that OverviewUpload.bytesUploaded is
 * consistent.
 */
trait OverviewUpload {
  val id: Long
  val userId: Long
  val lastActivity: Timestamp
  val size: Long
  val uploadedFile: OverviewUploadedFile
  val contentsOid: Long

  /** @return a copy with bytesUploaded updated to the new value */
  def withUploadedBytes(bytesUploaded: Long): OverviewUpload

  /** Store the current state in the database */
  def save: OverviewUpload

  /** Set bytesUploaded to 0 */
  def truncate: OverviewUpload

  /** Delete upload info */
  def delete

  def underlying: Upload
}

object OverviewUpload extends HasBlockingDatabase {
  import database.api._

  lazy val inserter = (Uploads.map(_.createAttributes) returning Uploads)
  lazy val updater = Compiled { (id: Rep[Long]) => Uploads.filter(_.id === id).map(_.updateAttributes) }

  def apply(upload: Upload, uploadedFile: UploadedFile): OverviewUpload = {
    new OverviewUploadImpl(upload, OverviewUploadedFile(uploadedFile))
  }

  /** Create a new instance and write it to the database. ICK ICK ICK. */
  def apply(userId: Long, guid: UUID, contentDisposition: String, contentType: String, totalSize: Long, oid: Long): OverviewUpload = {
    val uploadedFile = OverviewUploadedFile(oid, contentDisposition, contentType).save
    val attributes = Upload.CreateAttributes(
      userId,
      guid,
      oid,
      uploadedFileId=uploadedFile.id,
      lastActivity=now,
      totalSize=totalSize
    )
    val upload = blockingDatabase.run(inserter.+=(attributes))
    new OverviewUploadImpl(upload, uploadedFile)
  }

  /** Find currently existing instance */
  def find(userId: Long, guid: UUID): Option[OverviewUpload] = {
    val q = for {
      upload <- Uploads.filter(_.userId === userId).filter(_.guid === guid)
      uploadedFile <- UploadedFiles.filter(_.id === upload.uploadedFileId)
    } yield (upload, uploadedFile)
    blockingDatabase.option(q)
      .map({ t: (Upload,UploadedFile) => new OverviewUploadImpl(t._1, OverviewUploadedFile(t._2)) })
  }

  private class OverviewUploadImpl(upload: Upload, val uploadedFile: OverviewUploadedFile) extends OverviewUpload {
    val id = upload.id
    val userId = upload.userId
    val lastActivity = upload.lastActivity
    val size = upload.totalSize
    val contentsOid = upload.contentsOid
 
    def withUploadedBytes(bytesUploaded: Long): OverviewUpload =
      new OverviewUploadImpl(upload.copy(lastActivity = now), uploadedFile.withSize(bytesUploaded))

    def save: OverviewUpload = {
      uploadedFile.save
      val q = updater(upload.id).update(Upload.UpdateAttributes(now, size))
      blockingDatabase.runUnit(q)
      this
    }

    def truncate: OverviewUpload = new OverviewUploadImpl(upload.copy(lastActivity = now), uploadedFile.withSize(0l))

    def delete { blockingDatabase.delete(Uploads.filter(_.id === upload.id)) }

    override def toString: String = s"OverviewUploadImpl(${upload},${uploadedFile})"

    override def underlying = upload
  }

  private def now: Timestamp = new Timestamp(System.currentTimeMillis)
}
