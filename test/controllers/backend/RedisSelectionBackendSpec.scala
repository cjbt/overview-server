package controllers.backend

import com.redis.protocol.StringCommands
import java.util.UUID
import org.specs2.mock.Mockito
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import com.overviewdocs.query.Field
import com.overviewdocs.searchindex.SearchWarning
import models.{InMemorySelection,Selection,SelectionRequest,SelectionWarning}
import models.pagination.{Page,PageInfo,PageRequest}

class RedisSelectionBackendSpec extends RedisBackendSpecification with Mockito {
  trait BaseScope extends RedisScope {
    def resultIds: Array[Long] = Array.empty
    def warnings: List[SelectionWarning] = Nil
    def serializedWarnings: Array[Byte] = {
      val stream = new java.io.ByteArrayOutputStream
      new java.io.ObjectOutputStream(stream).writeObject(warnings)
      stream.toByteArray
    }
    val dsBackend = mock[DocumentSelectionBackend]
    dsBackend.createSelection(any[SelectionRequest]) returns Future.successful(InMemorySelection(resultIds, warnings))
    val backend = new RedisSelectionBackend(dsBackend, redisModule)
    val documentSetId = 123L
  }

  trait CreateScopeLike extends BaseScope {
    val request = SelectionRequest(documentSetId, Seq(), Seq(), Seq(), Seq(), None, None)
    def go: Selection
  }

  trait StoreHashExample { self: CreateScopeLike =>
    val selection = go
    val key = s"selection:${documentSetId}:by-user-hash:user@example.org:${request.hash}"
    await(redis.get(key)) must beSome(selection.id.toString)
  }

  trait ExpireHashExample { self: CreateScopeLike =>
    go
    val key = s"selection:${documentSetId}:by-user-hash:user@example.org:${request.hash}"
    await(redis.ttl(key)) must beCloseTo(backend.ExpiresInSeconds.toLong, 1)
  }

  trait StoreDocumentIdsExample { self: CreateScopeLike =>
    override def resultIds = Array(1L, 2L, 3L, 4L, 5L)
    val bytes = Array(
      0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x1,
      0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x2,
      0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x3,
      0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x4,
      0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x5
    ).map(_.toByte)
    val selection = go
    await(redis.get[Array[Byte]](s"selection:${documentSetId}:by-id:${selection.id}:document-ids")) must beSome(bytes)
    await(redis.get(s"selection:${documentSetId}:by-user-hash:user@example.org:${request.hash}")) must beSome(selection.id.toString)
  }

  trait StoreWarningsExample { self: CreateScopeLike =>
    override def warnings = List(
      SelectionWarning.SearchIndexWarning(SearchWarning.TooManyExpansions(Field.Text, "foo", 10)),
      SelectionWarning.SearchIndexWarning(SearchWarning.TooManyExpansions(Field.Title, "bar", 20))
    )

    val selection = go

    def deserialize(bytes: Array[Byte]): Object = {
      import java.io.ByteArrayInputStream
      import java.io.ObjectInputStream
      new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject()
    }

    val bytes = await(redis.get[Array[Byte]](s"selection:${documentSetId}:by-id:${selection.id}:warnings")).getOrElse(Array.empty)
    deserialize(bytes) must beEqualTo(warnings)
  }

  trait ExpireDocumentIdsExample { self: CreateScopeLike =>
    val selection = go
    val key = s"selection:${documentSetId}:by-id:${selection.id}:document-ids"
    await(redis.ttl(key)) must beCloseTo(backend.ExpiresInSeconds.toLong, 1)
  }

  trait ExpireWarningsExample { self: CreateScopeLike =>
    val selection = go
    val key = s"selection:${documentSetId}:by-id:${selection.id}:warnings"
    await(redis.ttl(key)) must beCloseTo(backend.ExpiresInSeconds.toLong, 1)
  }

  "RedisSelectionBackend" should {
    "#create" should {
      trait CreateScope extends CreateScopeLike {
        def create = await(backend.create("user@example.org", request))
        override def go = create
      }

      "store the SelectionRequest hash in Redis" in new CreateScope with StoreHashExample
      "expire the SelectionRequest" in new CreateScope with ExpireHashExample
      "store the document IDs in Redis" in new CreateScope with StoreDocumentIdsExample
      "store the warnings in Redis" in new CreateScope with StoreWarningsExample
      "expire the document IDs" in new CreateScope with ExpireDocumentIdsExample
      "expire the warnings" in new CreateScope with ExpireWarningsExample

      "return the Selection" in new CreateScope {
        override def resultIds = Array(1L, 2L, 3L)
        val selection = create
        await(selection.getAllDocumentIds) must beEqualTo(resultIds)
      }
    }

    "#find" should {
      trait FindScope extends BaseScope {
        val selectionId = "cf2f2f74-a009-48fa-986c-f1f8e5873345"
        val byIdKey = s"selection:${documentSetId}:by-id:cf2f2f74-a009-48fa-986c-f1f8e5873345:document-ids"
        val byIdWarningsKey = s"selection:${documentSetId}:by-id:cf2f2f74-a009-48fa-986c-f1f8e5873345:warnings"
        def go: Option[Selection] = await(backend.find(123L, UUID.fromString(selectionId)))
      }

      "when Selection is not present" should {
        "return None" in new FindScope {
          go must beNone
          there was no(dsBackend).createSelection(any)
        }
      }

      "when IDs are present but warnings are not" should {
        "return None" in new FindScope {
          await(redis.set(byIdKey,
            Array(
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x1,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x2,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x3,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x4,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x5
            ).map(_.toByte),
            StringCommands.EX(10)
          ))
          go must beNone
        }
      }

      "when warnings are present but IDs are not" should {
        "return None" in new FindScope {
          await(redis.set(byIdWarningsKey, serializedWarnings, StringCommands.EX(10)))
          go must beNone
          there was no(dsBackend).createSelection(any)
        }
      }

      "when Selection is present" should {
        trait FindExistsScope extends FindScope {
          await(redis.set(byIdKey,
            Array(
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x1,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x2,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x3,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x4,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x5
            ).map(_.toByte),
            StringCommands.EX(10)
          ))
          await(redis.set(byIdWarningsKey, serializedWarnings, StringCommands.EX(10)))
        }

        "expire the selection" in new FindExistsScope {
          go
          await(redis.ttl(byIdKey)) must beCloseTo(backend.ExpiresInSeconds.toLong, 1)
        }

        "return the Selection" in new FindExistsScope {
          val maybeSelection = go
          maybeSelection must beSome
          maybeSelection match {
            case Some(selection) => {
              await(selection.getAllDocumentIds) must beEqualTo(Seq(1L, 2L, 3L, 4L, 5L))
              there was no(dsBackend).createSelection(any)
            }
            case _ =>
          }
        }
      }
    }

    "#findOrCreate" should {
      trait FindOrCreateScope extends CreateScopeLike {
        val maybeSelectionId: Option[UUID] = None
        def findOrCreate = await(backend.findOrCreate("user@example.org", request, maybeSelectionId))
        override def go = findOrCreate
      }

      "when selection is not present" should {
        "store the SelectionRequest hash in Redis" in new FindOrCreateScope with StoreHashExample
        "expire the SelectionRequest" in new FindOrCreateScope with ExpireHashExample
        "store the document IDs in Redis" in new FindOrCreateScope with StoreDocumentIdsExample
        "store the warnings in Redis" in new FindOrCreateScope with StoreWarningsExample
        "expire the document IDs" in new FindOrCreateScope with ExpireDocumentIdsExample
        "expire the warnings" in new FindOrCreateScope with ExpireWarningsExample

        "return the Selection" in new FindOrCreateScope {
          override def resultIds = Array(1L, 2L, 3L)
          val selection = go
          await(selection.getAllDocumentIds) must beEqualTo(resultIds)
          there was one(dsBackend).createSelection(request)
        }

        "return a slice of the Selection" in new FindOrCreateScope {
          override def resultIds = Array(1L, 2L, 3L, 4L, 5L)
          val selection = go
          val documentIds = await(selection.getDocumentIds(PageRequest(1, 3)))
          there was one(dsBackend).createSelection(request)
          documentIds must beEqualTo(Page(Seq(2L, 3L, 4L), PageInfo(PageRequest(1, 3), 5)))
        }
      }

      "when Selection exists already (by ID)" should {
        trait SelectionExistsScope extends FindOrCreateScope {
          val selectionId = "cf2f2f74-a009-48fa-986c-f1f8e5873345"
          override val maybeSelectionId = Some(UUID.fromString(selectionId))
          val byIdKey = s"selection:${documentSetId}:by-id:cf2f2f74-a009-48fa-986c-f1f8e5873345:document-ids"
          val byIdWarningsKey = s"selection:${documentSetId}:by-id:cf2f2f74-a009-48fa-986c-f1f8e5873345:warnings"
          await(redis.set(byIdKey,
            Array(
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x1,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x2,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x3,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x4,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x5
            ).map(_.toByte),
            StringCommands.EX(10)
          ))
          await(redis.set(byIdWarningsKey, serializedWarnings, StringCommands.EX(10)))
        }

        "expire the document IDs" in new SelectionExistsScope with ExpireDocumentIdsExample
        "expire the warnings" in new SelectionExistsScope with ExpireWarningsExample
        "return the Selection" in new SelectionExistsScope {
          val selection = go
          await(selection.getAllDocumentIds) must beEqualTo(Seq(1L, 2L, 3L, 4L, 5L))
          there was no(dsBackend).createSelection(any)
        }
      }

      "when Selection exists already (by hash)" should {
        trait SelectionExistsScope extends FindOrCreateScope {
          val selectionId = "cf2f2f74-a009-48fa-986c-f1f8e5873345"
          val byUserHashKey = s"selection:${documentSetId}:by-user-hash:user@example.org:${request.hash}"
          val byIdKey = s"selection:${documentSetId}:by-id:cf2f2f74-a009-48fa-986c-f1f8e5873345:document-ids"
          val byIdWarningsKey = s"selection:${documentSetId}:by-id:cf2f2f74-a009-48fa-986c-f1f8e5873345:warnings"
          await(redis.set(byUserHashKey, selectionId, StringCommands.EX(10)))
          await(redis.set(byIdKey,
            Array(
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x1,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x2,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x3,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x4,
              0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x5
            ).map(_.toByte),
            StringCommands.EX(10)
          ))
          await(redis.set(byIdWarningsKey, serializedWarnings, StringCommands.EX(10)))
        }

        "store the SelectionRequest hash in Redis" in new SelectionExistsScope with StoreHashExample
        "expire the SelectionRequest" in new SelectionExistsScope with ExpireHashExample
        "store the document IDs in Redis" in new SelectionExistsScope with StoreDocumentIdsExample
        "store the warnings in Redis" in new SelectionExistsScope with StoreWarningsExample
        "expire the document IDs" in new SelectionExistsScope with ExpireDocumentIdsExample
        "expire the warnings" in new SelectionExistsScope with ExpireWarningsExample

        "return the Selection" in new SelectionExistsScope {
          val selection = go
          await(selection.getAllDocumentIds) must beEqualTo(Seq(1L, 2L, 3L, 4L, 5L))
          there was no(dsBackend).createSelection(any)
        }

        "return a slice of the Selection" in new SelectionExistsScope {
          val selection = go
          val documentIds = await(selection.getDocumentIds(PageRequest(1, 3)))
          there was no(dsBackend).createSelection(any)
          documentIds must beEqualTo(Page(Seq(2L, 3L, 4L), PageInfo(PageRequest(1, 3), 5)))
        }
      }

      "when Selection Hash is in Redis but not the Selection" should {
        // Redis expires keys probabilistically. Even though the document list
        // expiry time is always later than the hash's, it may expire sooner.
        trait SelectionHashExistsScope extends FindOrCreateScope {
          val selectionId = "cf2f2f74-a009-48fa-986c-f1f8e5873345"
          await(redis.set(s"selection:${documentSetId}:by-user-hash:user@example.org:${request.hash}", selectionId, StringCommands.EX(10)))
        }

        "store the SelectionRequest hash in Redis" in new SelectionHashExistsScope with StoreHashExample
        "expire the SelectionRequest" in new SelectionHashExistsScope with ExpireHashExample
        "store the document IDs in Redis" in new SelectionHashExistsScope with StoreDocumentIdsExample
        "store the warnings in Redis" in new SelectionHashExistsScope with StoreWarningsExample
        "expire the document IDs" in new SelectionHashExistsScope with ExpireDocumentIdsExample
        "expire the warnings" in new SelectionHashExistsScope with ExpireWarningsExample

        "return the Selection" in new SelectionHashExistsScope {
          override def resultIds = Array(1L, 2L, 3L)
          val selection = go
          await(selection.getAllDocumentIds) must beEqualTo(resultIds)
          there was one(dsBackend).createSelection(request)
        }
      }
    }
  }
}
