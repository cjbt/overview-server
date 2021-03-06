package controllers.backend

import scala.collection.immutable

import models.InMemorySelection

class DbDocumentNodeBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbBackendScope {
    val backend = new DbDocumentNodeBackend(database)
  }

  "DbDocumentNodeBackend" should {
    "#indexMany" should {
      trait IndexManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val doc3 = factory.document(documentSetId=documentSet.id) // no joins
        val node1 = factory.node()
        val node2 = factory.node(rootId=node1.id)
        val node3 = factory.node(rootId=node1.id) // no joins
        factory.nodeDocument(nodeId=node1.id, documentId=doc1.id)
        factory.nodeDocument(nodeId=node1.id, documentId=doc2.id)
        factory.nodeDocument(nodeId=node2.id, documentId=doc1.id)

        lazy val result: Map[Long,immutable.Seq[Long]] = await(backend.indexMany(Vector(doc1.id, doc2.id, doc3.id)))
      }

      "return Node IDs for Documents that have them" in new IndexManyScope {
        result(doc1.id) must containTheSameElementsAs(Vector(node1.id, node2.id))
        result(doc2.id) must beEqualTo(Vector(node1.id))
      }

      "return nothing for Documents that have no Nodes" in new IndexManyScope {
        result.isDefinedAt(doc3.id) must beFalse
      }

      "not return Documents that were not requested" in new IndexManyScope {
        val doc4 = factory.document(documentSetId=documentSet.id)
        factory.nodeDocument(nodeId=node1.id, documentId=doc4.id)
        result.isDefinedAt(doc4.id) must beFalse
      }

      "work with an empty set of documents" in new IndexManyScope {
        await(backend.indexMany(Vector())) must beEqualTo(Map())
      }
    }

    "#countByNode" should {
      trait CountByNodeScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val doc3 = factory.document(documentSetId=documentSet.id) // no joins
        val node1 = factory.node()
        val node2 = factory.node(rootId=node1.id)
        val node3 = factory.node(rootId=node1.id) // no joins

        factory.nodeDocument(nodeId=node1.id, documentId=doc1.id)
        factory.nodeDocument(nodeId=node1.id, documentId=doc2.id)
        factory.nodeDocument(nodeId=node2.id, documentId=doc1.id)

        val selection: InMemorySelection = InMemorySelection(Array(doc1.id, doc2.id, doc3.id))
        val requestedNodeIds: Vector[Long] = Vector(node1.id, node2.id, node3.id)

        lazy val result: Map[Long,Int] = await(backend.countByNode(selection, requestedNodeIds))
      }

      "return counts for Nodes that have counts" in new CountByNodeScope {
        result(node1.id) must beEqualTo(2)
        result(node2.id) must beEqualTo(1)
      }

      "skip counts for Nodes with no documents" in new CountByNodeScope {
        result.isDefinedAt(node3.id) must beFalse
      }

      "only include Nodes we request" in new CountByNodeScope {
        override val requestedNodeIds = Vector(node1.id)
        result.isDefinedAt(node1.id) must beTrue
        result.isDefinedAt(node2.id) must beFalse
      }

      "work even when we request bogus node IDs" in new CountByNodeScope {
        override val requestedNodeIds = Vector(-1L)
        result.isEmpty must beTrue
      }

      "filter by the SelectionLike" in new CountByNodeScope {
        override val selection = InMemorySelection(Array(doc2.id))
        result(node1.id) must beEqualTo(1)
        result.isDefinedAt(node2.id) must beFalse
        result.isDefinedAt(node3.id) must beFalse
      }

      "work when documentIds is empty" in new CountByNodeScope {
        override val selection = InMemorySelection(Vector.empty)
        result.isEmpty must beTrue
      }

      "work when nodeIds is empty" in new CountByNodeScope {
        override val requestedNodeIds = Vector()
        result.isEmpty must beTrue
      }

      // Selection will regulate that we restrict ourselves to this docset; no
      // need to test it here. And we don't bother restricting ourselves to one
      // Tree because (as of 2015-01-14) anybody with access to one Tree in a
      // docset has access to them all.
    }
  }
}
