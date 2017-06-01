package controllers.backend

import java.util.Date
import org.specs2.mock.Mockito
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import models.{Selection,SelectionRequest}

class NullSelectionBackendSpec extends NullBackendSpecification with Mockito {
  trait BaseScope extends NullScope {
    def resultIds: Seq[Long] = Seq()
    val documentBackend = mock[DocumentBackend]
    val backend = new NullSelectionBackend(documentBackend)
    documentBackend.indexIds(any[SelectionRequest]) returns Future.successful(resultIds)

    val userEmail: String = "user@example.org"
    val documentSetId: Long = 1L
  }

  "NullSelectionBackend" should {
    "#create" should {
      trait CreateScope extends BaseScope {
        lazy val request = SelectionRequest(documentSetId, Seq(), Seq(), Seq(), Seq(), None, None)
        def create = await(backend.create(userEmail, request))
        lazy val result = create
      }

      "return a Selection with the returned document IDs" in new CreateScope {
        override def resultIds = Seq(1L, 2L, 3L)
        await(result.getAllDocumentIds) must beEqualTo(Seq(1L, 2L, 3L))
      }

      "return a different Selection each time" in new CreateScope {
        create.id must not(beEqualTo(create.id))
      }

      "pass the SelectionRequest to the documentBackend" in new CreateScope {
        create
        there was one(documentBackend).indexIds(request)
      }

      "pass a failure back" in new CreateScope {
        val t = new Throwable("moo")
        documentBackend.indexIds(any[SelectionRequest]) returns Future.failed(t)
        create must throwA[Throwable](message="moo")
      }
    }
  }
}
