package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection
import play.api.Play.current
import play.api.db.DB


/**
 * Loads data from the database about subTrees
 */
class SubTreeLoader(documentSetId: Long,
					loader: SubTreeDataLoader = new SubTreeDataLoader(),
					parser: SubTreeDataParser = new SubTreeDataParser()) {
  
  /**
   * @return a list of all the Nodes in the subTree with root at nodeId
   */
  def load(nodeId: Long, depth: Int)(implicit connection : Connection) : Seq[core.Node] = {
	
    val nodeData = loader.loadNodeData(documentSetId, nodeId, depth)
    val nodeIds = nodeData.map(_._1).distinct

    val documentData = loader.loadDocumentIds(nodeIds)
    val nodeTagCountData = loader.loadNodeTagCounts(nodeIds)
    
    parser.createNodes(nodeData, documentData, nodeTagCountData)
  }

  /**
   * @return Some(rootId) if the documentSet has a root node, None otherwise.
   */
  def loadRootId()(implicit connection: Connection): Option[Long] = {
    loader.loadRoot(documentSetId)
  }
  
  /**
   * @return a list of Documents whose ids are referenced by the passed in nodes and tags.
   * The list is sorted by document IDs and all the elements are distinct, even if documentIds 
   * referenced multiple times.
   */
  def loadDocuments(nodes: Seq[core.Node], tags: Seq[core.Tag])(implicit connection : Connection) : Seq[core.Document] = {
    val nodeDocumentIds = nodes.flatMap(_.documentIds.firstIds)
    val tagDocumentIds = tags.flatMap(_.documentIds.firstIds)
    val documentIds = nodeDocumentIds ++ tagDocumentIds
    
    val documentData = loader.loadDocuments(documentIds.distinct.sorted)
    val documentTagData = loader.loadDocumentTags(documentIds)
    
    parser.createDocuments(documentData, documentTagData)
  }
  
  def loadTags(documentSetId: Long)(implicit connection: Connection) : Seq[core.Tag] = {
    val tagData = loader.loadTags(documentSetId)
    parser.createTags(tagData)
  }
}
