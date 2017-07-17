package controllers.backend

import models.pagination.PageRequest
import com.overviewdocs.metadata.{MetadataField,MetadataFieldType,MetadataSchema}
import com.overviewdocs.models.tables.{ApiTokens,DocumentSetUsers,DocumentSets,Views}
import com.overviewdocs.models.{ApiToken,DocumentSet,DocumentSetUser,View}

class DbDocumentSetBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbBackendScope {
    import database.api._

    val backend = new DbDocumentSetBackend(database)

    def findDocumentSet(id: Long): Option[DocumentSet] = {
      blockingDatabase.option(DocumentSets.filter(_.id === id))
    }

    def findDocumentSetUser(documentSetId: Long): Option[DocumentSetUser] = {
      blockingDatabase.option(DocumentSetUsers.filter(_.documentSetId === documentSetId))
    }

    def findViews(documentSetId: Long): Seq[View] = {
      val tokens = ApiTokens.filter(_.documentSetId === documentSetId).map(_.token)
      blockingDatabase.seq(Views.filter(_.apiToken in tokens).sortBy(_.id))
    }
  }

  "#create" should {
    trait CreateScope extends BaseScope

    "create a document set with a title" in new CreateScope {
      val ret = await(backend.create(DocumentSet.CreateAttributes("our-title"), "foo@bar.com"))
      val dbRet = findDocumentSet(ret.id)
      dbRet must beSome(ret)
      ret.title must beEqualTo("our-title")
    }

    "create a DocumentSetUser owner" in new CreateScope {
      val documentSet = await(backend.create(DocumentSet.CreateAttributes("our-title"), "foo@bar.com"))
      val ret = findDocumentSetUser(documentSet.id)
      ret must beSome(DocumentSetUser(documentSet.id, "foo@bar.com", DocumentSetUser.Role(true)))
    }

    "create an ApiToken and View when a Plugin is autocreate" in new CreateScope {
      factory.plugin(name="plugin name", url="http://plugin.url", autocreate=true)
      val documentSet = await(backend.create(DocumentSet.CreateAttributes("title"), "foo@bar.com"))
      val ret = findViews(documentSet.id)
      ret.length must beEqualTo(1)
      ret.headOption must beSome { view: View =>
        view.documentSetId must beEqualTo(documentSet.id)
        view.title must beEqualTo("plugin name")
        view.url must beEqualTo("http://plugin.url")
      }
    }

    "not create an ApiToken and View when a Plugin is not autocreate" in new CreateScope {
      factory.plugin(name="plugin name", url="http://plugin.url", autocreate=false)
      val documentSet = await(backend.create(DocumentSet.CreateAttributes("title"), "foo@bar.com"))
      findViews(documentSet.id).length must beEqualTo(0)
    }

    "create Views according to autocreateOrder" in new CreateScope {
      factory.plugin(name="plugin1", url="http://plugin1.url", autocreate=true, autocreateOrder=2)
      factory.plugin(name="plugin2", url="http://plugin2.url", autocreate=true, autocreateOrder=1)
      val documentSet = await(backend.create(DocumentSet.CreateAttributes("title"), "foo@bar.com"))
      findViews(documentSet.id).map(_.title) must beEqualTo(Seq("plugin2", "plugin1"))
    }
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val documentSet = factory.documentSet()
      val documentSetId = documentSet.id
      lazy val ret = await(backend.show(documentSetId))
    }

    "show a DocumentSet" in new ShowScope {
      ret must beSome { ds: DocumentSet =>
        ds.id must beEqualTo(documentSet.id)
        ds.title must beEqualTo(documentSet.title)
        // etc
      }
    }

    "not show a DocumentSet that does not exist" in new ShowScope {
      override val documentSetId = documentSet.id + 1L
      ret must beNone
    }
  }

  "#indexPageByOwner" should {
    trait IndexPageByOwnerScope extends BaseScope {
      val pageRequest = PageRequest(1, 2, false)
      val email = "user@example.org"
    }

    "not find another User's DocumentSets" in new IndexPageByOwnerScope {
      val badDocumentSet = factory.documentSet()
      factory.documentSetUser(badDocumentSet.id, "bad@example.org")
      await(backend.indexPageByOwner(email, pageRequest)).pageInfo.total must beEqualTo(0)
    }

    "not find a DocumentSet for a viewer" in new IndexPageByOwnerScope {
      val badDocumentSet = factory.documentSet()
      factory.documentSetUser(badDocumentSet.id, email, DocumentSetUser.Role(false))
      await(backend.indexPageByOwner(email, pageRequest)).pageInfo.total must beEqualTo(0)
    }

    "sort DocumentSets by createdAt" in new IndexPageByOwnerScope {
      val dsu1 = factory.documentSetUser(factory.documentSet(createdAt=new java.sql.Timestamp(4000)).id, email)
      val dsu2 = factory.documentSetUser(factory.documentSet(createdAt=new java.sql.Timestamp(1000)).id, email)
      val dsu3 = factory.documentSetUser(factory.documentSet(createdAt=new java.sql.Timestamp(2000)).id, email)
      val dsu4 = factory.documentSetUser(factory.documentSet(createdAt=new java.sql.Timestamp(3000)).id, email)
      val result = await(backend.indexPageByOwner(email, pageRequest))

      result.pageInfo.total must beEqualTo(4)
      result.items.map(_.id) must beEqualTo(Seq(dsu4.documentSetId, dsu3.documentSetId))
    }

    "ignore a deleted DocumentSet" in new IndexPageByOwnerScope {
      val documentSet = factory.documentSet(deleted=true)
      factory.documentSetUser(documentSet.id, email)
      await(backend.indexPageByOwner(email, pageRequest)).pageInfo.total must beEqualTo(0)
    }
  }

  "#indexByViewerEmail" should {
    trait IndexByViewerEmailScope extends BaseScope

    "not find another User's DocumentSets" in new IndexByViewerEmailScope {
      val documentSet = factory.documentSet()
      factory.documentSetUser(documentSet.id, "user2@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet.id, "user3@example.org", DocumentSetUser.Role(true))
      await(backend.indexByViewerEmail("user@example.org")) must beEqualTo(Seq())
    }

    "not find an owned DocumentSet" in new IndexByViewerEmailScope {
      val documentSet = factory.documentSet()
      factory.documentSetUser(documentSet.id, "user@example.org", DocumentSetUser.Role(true))
      await(backend.indexByViewerEmail("user@example.org")) must beEqualTo(Seq())
    }

    "not find a DocumentSet with no owner" in new IndexByViewerEmailScope {
      val documentSet = factory.documentSet()
      factory.documentSetUser(documentSet.id, "user@example.org", DocumentSetUser.Role(false))
      await(backend.indexByViewerEmail("user@example.org")) must beEqualTo(Seq())
    }

    "find a DocumentSet and its owner" in new IndexByViewerEmailScope {
      val documentSet = factory.documentSet()
      factory.documentSetUser(documentSet.id, "user@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet.id, "owner@example.org", DocumentSetUser.Role(true))
      await(backend.indexByViewerEmail("user@example.org")) must beEqualTo(Seq((documentSet, "owner@example.org")))
    }

    "work with multiple results" in new IndexByViewerEmailScope {
      val documentSet1 = factory.documentSet()
      factory.documentSetUser(documentSet1.id, "user@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet1.id, "owner1@example.org", DocumentSetUser.Role(true))
      val documentSet2 = factory.documentSet()
      factory.documentSetUser(documentSet2.id, "user@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet2.id, "owner2@example.org", DocumentSetUser.Role(true))
      await(backend.indexByViewerEmail("user@example.org")) must containTheSameElementsAs(Seq(
        (documentSet1, "owner1@example.org"),
        (documentSet2, "owner2@example.org")
      ))
    }

    "sort by createdAt" in new IndexByViewerEmailScope {
      val documentSet1 = factory.documentSet(createdAt=new java.sql.Timestamp(12345L))
      factory.documentSetUser(documentSet1.id, "user@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet1.id, "owner1@example.org", DocumentSetUser.Role(true))
      val documentSet2 = factory.documentSet(createdAt=new java.sql.Timestamp(23456L))
      factory.documentSetUser(documentSet2.id, "user@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet2.id, "owner2@example.org", DocumentSetUser.Role(true))
      await(backend.indexByViewerEmail("user@example.org")).map(_._1.id) must beEqualTo(Seq(documentSet2.id, documentSet1.id))
    }
  }

  "#indexPublic" should {
    trait IndexPublicScope extends BaseScope

    "not find a non-public DocumentSet" in new IndexPublicScope {
      val documentSet = factory.documentSet(isPublic=false)
      factory.documentSetUser(documentSet.id, "owner@example.org", DocumentSetUser.Role(true))
      await(backend.indexPublic) must beEqualTo(Seq())
    }

    "not find a DocumentSet with no owner" in new IndexPublicScope {
      val documentSet = factory.documentSet(isPublic=true)
      factory.documentSetUser(documentSet.id, "viewer@example.org", DocumentSetUser.Role(false))
      await(backend.indexPublic) must beEqualTo(Seq())
    }

    "find a DocumentSet and its owner" in new IndexPublicScope {
      val documentSet = factory.documentSet(isPublic=true)
      factory.documentSetUser(documentSet.id, "user@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet.id, "owner@example.org", DocumentSetUser.Role(true))
      await(backend.indexPublic) must beEqualTo(Seq((documentSet, "owner@example.org")))
    }

    "work with multiple results" in new IndexPublicScope {
      val documentSet1 = factory.documentSet(isPublic=true)
      factory.documentSetUser(documentSet1.id, "user@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet1.id, "owner1@example.org", DocumentSetUser.Role(true))
      val documentSet2 = factory.documentSet(isPublic=true)
      factory.documentSetUser(documentSet2.id, "user@example.org", DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet2.id, "owner2@example.org", DocumentSetUser.Role(true))
      await(backend.indexPublic) must containTheSameElementsAs(Seq(
        (documentSet1, "owner1@example.org"),
        (documentSet2, "owner2@example.org")
      ))
    }

    "sort by createdAt" in new IndexPublicScope {
      val documentSet1 = factory.documentSet(isPublic=true, createdAt=new java.sql.Timestamp(12345L))
      factory.documentSetUser(documentSet1.id, "owner1@example.org", DocumentSetUser.Role(true))
      val documentSet2 = factory.documentSet(isPublic=true, createdAt=new java.sql.Timestamp(23456L))
      factory.documentSetUser(documentSet2.id, "owner2@example.org", DocumentSetUser.Role(true))
      await(backend.indexPublic).map(_._1.id) must beEqualTo(Seq(documentSet2.id, documentSet1.id))
    }
  }

  "#updatePublic" should {
    trait UpdatePublicScope extends BaseScope

    "set public to true" in new UpdatePublicScope {
      val documentSet = factory.documentSet(isPublic=false)
      await(backend.updatePublic(documentSet.id, true))
      findDocumentSet(documentSet.id).map(_.public) must beSome(true)
    }

    "set public to false" in new UpdatePublicScope {
      val documentSet = factory.documentSet(isPublic=true)
      await(backend.updatePublic(documentSet.id, false))
      findDocumentSet(documentSet.id).map(_.public) must beSome(false)
    }

    "not update another DocumentSet" in new UpdatePublicScope {
      val documentSet = factory.documentSet()
      val otherDocumentSet = factory.documentSet(isPublic=false)
      await(backend.updatePublic(documentSet.id, true))
      findDocumentSet(otherDocumentSet.id).map(_.public) must beSome(false)
    }

    "ignore a missing DocumentSet" in new UpdatePublicScope {
      await(backend.updatePublic(123L, false)) must not(throwA[Exception])
    }
  }

  "#updateDeleted" should {
    trait UpdateDeletedScope extends BaseScope

    "set deleted to true" in new UpdateDeletedScope {
      val documentSet = factory.documentSet(deleted=false)
      await(backend.updateDeleted(documentSet.id, true))
      findDocumentSet(documentSet.id).map(_.deleted) must beSome(true)
    }

    "set deleted to false" in new UpdateDeletedScope {
      val documentSet = factory.documentSet(deleted=true)
      await(backend.updateDeleted(documentSet.id, false))
      findDocumentSet(documentSet.id).map(_.deleted) must beSome(false)
    }

    "not update another DocumentSet" in new UpdateDeletedScope {
      val documentSet = factory.documentSet()
      val otherDocumentSet = factory.documentSet(deleted=false)
      await(backend.updateDeleted(documentSet.id, true))
      findDocumentSet(otherDocumentSet.id).map(_.deleted) must beSome(false)
    }

    "ignore a missing DocumentSet" in new UpdateDeletedScope {
      await(backend.updateDeleted(123L, false)) must not(throwA[Exception])
    }
  }

  "#updateMetadataSchema" should {
    trait UpdateMetadataSchemaScope extends BaseScope {
      val sampleMetadataSchema = MetadataSchema(1, Seq(
        MetadataField("foo", MetadataFieldType.String),
        MetadataField("bar", MetadataFieldType.String)
      ))
    }

    "set the metadata schema" in new UpdateMetadataSchemaScope {
      val documentSet = factory.documentSet()
      await(backend.updateMetadataSchema(documentSet.id, sampleMetadataSchema))
      findDocumentSet(documentSet.id).map(_.metadataSchema) must beSome(sampleMetadataSchema)
    }
  }

  "#countByOwnerEmail" should {
    trait CountByOwnerEmailScope extends BaseScope {
      val userEmail = "user1@example.org"
      lazy val ret = await(backend.countByOwnerEmail(userEmail))
    }

    "not count other users' DocumentSets" in new CountByOwnerEmailScope {
      val documentSet = factory.documentSet()
      factory.documentSetUser(documentSet.id, "user2@example.org")
      ret must beEqualTo(0)
    }

    "count the user's DocumentSets" in new CountByOwnerEmailScope {
      val documentSet1 = factory.documentSet()
      val documentSet2 = factory.documentSet()
      factory.documentSetUser(documentSet1.id, userEmail)
      factory.documentSetUser(documentSet2.id, userEmail)
      ret must beEqualTo(2)
    }

    "not count viewed DocumentSets" in new CountByOwnerEmailScope {
      val documentSet1 = factory.documentSet()
      val documentSet2 = factory.documentSet()
      factory.documentSetUser(documentSet1.id, userEmail, DocumentSetUser.Role(false))
      factory.documentSetUser(documentSet2.id, userEmail, DocumentSetUser.Role(true))
      ret must beEqualTo(1)
    }

    "ignore a deleted DocumentSet" in new CountByOwnerEmailScope {
      val documentSet = factory.documentSet(deleted=true)
      factory.documentSetUser(documentSet.id, userEmail)
      ret must beEqualTo(0)
    }
  }
}
