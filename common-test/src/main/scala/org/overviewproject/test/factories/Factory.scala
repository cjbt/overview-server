package org.overviewproject.test.factories

import java.sql.Timestamp
import java.util.UUID
import play.api.libs.json.JsObject
import org.overviewproject.models.{ ApiToken, Document, DocumentInfo, DocumentSet, DocumentSetUser, DocumentTag, DocumentStoreObject, Node, NodeDocument, Plugin, Store, StoreObject, Tree, View }
import org.overviewproject.models.{ File, Page }
import org.overviewproject.tree.orm.{ Document => DeprecatedDocument, DocumentSearchResult, SearchResult, SearchResultState, Tag }
import org.overviewproject.util.DocumentSetVersion

/**
 * Creates models simply.
 *
 * Usage:
 *
 *   val factory = new org.overviewproject.test.factories.Factory
 *   val documentSet = factory.documentSet()
 *   val document = factory.document(documentSetId=documentSet.id)
 *   val tag = factory.tag(documentSetId=documentSet.id)
 *   val documentTag = factory.documentTag(documentId=document.id, tagId=tag.id)
 *   ...
 *
 * Use PodoFactory for simple tests. Use DbFactory to insert rows in the
 * database while building objects, for more thorough (and slower) tests.
 */
trait Factory {
  /** Creates an ApiToken with the given parameters. */
  def apiToken(
    token: String = "token",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    createdBy: String = "user@example.org",
    description: String = "description",
    documentSetId: Long = 0L): ApiToken

  /** Creates a new Document with the given parameters. */
  def document(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: Option[String] = None,
    suppliedId: String = "",
    title: String = "",
    keywords: Seq[String] = Seq(),
    pageNumber: Option[Int] = None,
    fileId: Option[Long] = None,
    pageId: Option[Long] = None,
    text: String = ""): Document

  /** Creates a new DeprecatedDocument with the given parameters. */
  def deprecatedDocument(
    id: Long = 0L,
    documentSetId: Long = 0L,
    description: String = "",
    title: Option[String] = None,
    suppliedId: Option[String] = None,
    text: Option[String] = None,
    url: Option[String] = None,
    documentcloudId: Option[String] = None,
    fileId: Option[Long] = None,
    pageId: Option[Long] = None,
    pageNumber: Option[Int] = None): DeprecatedDocument

  /** Creates a new DocumentSet with the given parameters. */
  def documentSet(
    id: Long = 0L,
    title: String = "",
    query: Option[String] = None,
    isPublic: Boolean = false,
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime),
    documentCount: Int = 4,
    documentProcessingErrorCount: Int = 3,
    importOverflowCount: Int = 2,
    uploadedFileId: Option[Long] = None,
    version: Int = DocumentSetVersion.current,
    deleted: Boolean = false): DocumentSet

  def documentSetUser(
    documentSetId: Long = 0L,
    userEmail: String = "user@example.com",
    role: DocumentSetUser.Role = DocumentSetUser.Role(1)  
  ): DocumentSetUser
  
  def documentSearchResult(
    documentId: Long,
    searchResultId: Long): DocumentSearchResult

  def documentTag(documentId: Long, tagId: Long): DocumentTag

  def documentStoreObject(
    documentId: Long = 0L,
    storeObjectId: Long = 0L,
    json: Option[JsObject] = None): DocumentStoreObject

  def node(
    id: Long = 0L,
    rootId: Long = 0L,
    parentId: Option[Long] = None,
    description: String = "",
    cachedSize: Int = 0,
    isLeaf: Boolean = true): Node

  def nodeDocument(nodeId: Long, documentId: Long): NodeDocument

  def plugin(
    id: UUID = new UUID(0L, 0L),
    name: String = "name",
    description: String = "description",
    url: String = "http://example.org"): Plugin

  def searchResult(
    id: Long = 0L,
    state: SearchResultState.Value = SearchResultState.Complete,
    documentSetId: Long = 0L,
    query: String = "query",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)): SearchResult

  def tag(
    id: Long = 0L,
    documentSetId: Long = 0L,
    name: String = "a tag",
    color: String = "abcdef"): Tag

  def tree(
    id: Long = 0L,
    documentSetId: Long = 0L,
    rootNodeId: Long = 0L,
    jobId: Long = 0L,
    title: String = "title",
    documentCount: Int = 10,
    lang: String = "en",
    description: String = "description",
    suppliedStopWords: String = "supplied stop words",
    importantWords: String = "important words",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)): Tree

  def view(
    id: Long = 0L,
    documentSetId: Long = 0L,
    url: String = "http://example.org",
    apiToken: String = "api-token",
    title: String = "title",
    createdAt: Timestamp = new Timestamp(scala.compat.Platform.currentTime)): View

  def store(
    id: Long = 0L,
    apiToken: String = "token",
    json: JsObject = JsObject(Seq())): Store

  def storeObject(
    id: Long = 0L,
    storeId: Long = 0L,
    indexedLong: Option[Long] = None,
    indexedString: Option[String] = None,
    json: JsObject = JsObject(Seq())): StoreObject

  def page(
    id: Long = 0L,
    fileId: Long = 0L,
    pageNumber: Int = 1,
    referenceCount: Int = 1,
    dataLocation: String = "pagebytea:123",
    dataSize: Long = 9L,
    data: Option[Array[Byte]] = Some("page text".getBytes("utf-8")),
    text: Option[String] = Some("page text"),
    dataErrorMessage: Option[String] = None,
    textErrorMessage: Option[String] = None): Page

  def file(
    id: Long = 0L,
    referenceCount: Int = 1,
    contentsOid: Long = 1,
    viewOid: Long = 1,
    name: String = "filename",
    contentsSize: Long = 1L,
    viewSize: Long = 1L): File

}
