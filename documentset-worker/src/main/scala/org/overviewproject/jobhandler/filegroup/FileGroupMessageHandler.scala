package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.jobhandler.filegroup.TextExtractorProtocol.ExtractText
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.MessageHandlerProtocol._

trait TextExtractorComponent {
  val actorCreator: ActorCreator
  
  trait ActorCreator {
    def produceTextExtractor: Props
  }
}


object FileGroupMessageHandlerProtocol {
  sealed trait Command
  case class ProcessFileCommand(fileGroupId: Long, uploadedFileId: Long) extends Command
}

object FileGroupMessageHandlerFSM {
  sealed trait State
  case object Idle extends State
  case object Working extends State
  
  sealed trait Data
  case object NoData extends Data

}

import FileGroupMessageHandlerFSM._


/**
 * Spawns actors to process files.
 */
class FileGroupMessageHandler(jobMonitor: ActorRef) extends Actor with FSM[State, Data] {
  this: TextExtractorComponent =>

  import FileGroupMessageHandlerProtocol._

  startWith(Idle, NoData)
  
  when (Idle) {
    case Event(ProcessFileCommand(fileGroupId, uploadedFileId), _) => {
      val fileHandler = context.actorOf(actorCreator.produceTextExtractor)
      fileHandler ! ExtractText(fileGroupId, uploadedFileId)

      goto(Working) 
    }
  }
  
  when (Working) {
    case Event(JobDone(fileGroupId), _) => {
      jobMonitor ! JobDone(fileGroupId)
       
       goto(Idle)
    }
  }
  
  initialize
}

trait TextExtractorComponentImpl extends TextExtractorComponent {

  class ActorCreatorImpl extends ActorCreator {
    override def produceTextExtractor: Props = Props[TextExtractorImpl]
  }
 override val actorCreator = new ActorCreatorImpl

}

object FileGroupMessageHandler {
  class FileGroupMessageHandlerImpl(jobMonitor: ActorRef) extends FileGroupMessageHandler(jobMonitor) with TextExtractorComponentImpl 
  
  def apply(jobMonitor: ActorRef): Props = Props(new FileGroupMessageHandlerImpl(jobMonitor))
}
