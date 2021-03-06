package com.overviewdocs.jobhandler.documentset

import akka.actor.{Actor,ActorRef,ActorSystem}
import akka.stream.Materializer
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.Database
import com.overviewdocs.messages.Progress
import com.overviewdocs.models.tables.DocumentIdLists
import com.overviewdocs.sort.{SortConfig,Sorter,DocumentSource}
import com.overviewdocs.util.Logger

class SortRunner(
  val database: Database,
  nDocumentsPerFetch: Int,
  sortConfig: SortConfig
) {
  private val documentSource = new DocumentSource(database, nDocumentsPerFetch)
  private val sorter = new Sorter(sortConfig)
  private val logger = Logger.forClass(getClass)

  def run(documentSetId: Long, fieldName: String, asker: ActorRef)(implicit system: ActorSystem): Future[Unit] = {
    val blockingEc = system.dispatchers.lookup("blocking-io-dispatcher")
    import system.dispatcher
    logger.info("Sorting {}:{} for {}", documentSetId, fieldName, asker)

    exists(documentSetId.toInt, fieldName).flatMap(_ match {
      case true => {
        logger.info("Sort skipped: it was already finished")
        asker.tell(Progress.SortDone, Actor.noSender)
        Future.unit
      }
      case false => {
        for {
          recordSource <- documentSource.recordSourceByMetadata(documentSetId, fieldName)
          ids <- sorter.sortIds(recordSource, (p) => asker.tell(Progress.Sorting(p), Actor.noSender))(Materializer.matFromSystem, blockingEc)
          _ <- writeIds(documentSetId.toInt, fieldName, ids)
        } yield {
          logger.info("Sort finished")
          asker.tell(Progress.SortDone, Actor.noSender)
        }
      }
    })
  }

  import database.api._
  private lazy val findExisting = Compiled { (documentSetId: Rep[Int], fieldName: Rep[String]) =>
    DocumentIdLists
      .filter(_.documentSetId === documentSetId)
      .filter(_.fieldName === fieldName)
      .map(_.id)
  }

  private def exists(documentSetId: Int, fieldName: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    database.option(findExisting(documentSetId, fieldName)).map(_.nonEmpty)
  }

  lazy val inserter = DocumentIdLists.map(dil => (dil.documentSetId, dil.fieldName, dil.document32BitIds))
  private def writeIds(documentSetId: Int, fieldName: String, ids: immutable.Seq[Int]): Future[Unit] = {
    database.runUnit(inserter.+=((documentSetId, fieldName, ids.toVector)))
  }
}
