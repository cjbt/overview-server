package org.overviewproject.background.filecleanup



import org.overviewproject.database.Slick.simple._
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession

class DeletedFileFinderSpec extends SlickSpecification {

  "DeletedFileFinder" should {
    
    "find deleted files" in new DeletedFilesScope {
      val fileIds = await(deletedFileFinder.deletedFileIds)
      
      fileIds must containTheSameElementsAs(deletedFiles.map(_.id))
    }
  }

  trait DeletedFilesScope extends DbScope {
    val numberOfDeletedFiles = 10
    val deletedFiles = Seq.fill(numberOfDeletedFiles)(factory.file(referenceCount = 0))
    
    val numberOfFiles = 10
    val existingFiles = Seq.fill(numberOfFiles)(factory.file(referenceCount = 1))
    
    val deletedFileFinder = new TestDeletedFileFinder
  }
  
  class TestDeletedFileFinder(implicit val session: Session) extends DeletedFileFinder with SlickClientInSession
}