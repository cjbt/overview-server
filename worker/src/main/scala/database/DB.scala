/*
 * DB.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package database

import java.sql.Connection
import java.sql.Statement
import java.sql.Savepoint

/**
 * Convenience object for database access. Call DB.connect(datasSource) once at the start
 * of the application, and DB.close() at the end.
 */
object DB {

  private var dataSource: DataSource = _
  
  def connect(source: DataSource) {
    dataSource = source
  }
  
  def close() {
    dataSource.shutdown()
  }
  
  /**
   * @return a connection. Caller is responsible for closing connection.
   */
  def getConnection(): Connection = {
    dataSource.getConnection()
  }
  
 /**
  * Provides a scope with an implicit connection that is automatically closed.
  */
  def withConnection[T](block: Connection => T): T = {
    val connection = new AutoCleanConnection(dataSource.getConnection())
    try {
      block(connection)
    }
    finally {
      connection.close()
    }
  }
  
  /**
   * Provides a scope inside a transaction with an implicit connection .
   * If an error occurs, a rollback is performed. The connection is automatically closed.
   */
  def withTransaction[T](block: Connection => T): T = {
    withConnection { implicit connection =>
      try {
        connection.setAutoCommit(false)
        val result = block(connection)
        connection.commit()
        
        result
      }  
      catch {
        case e => {
          connection.rollback()
          throw e
        }
      }
      finally {
        connection.setAutoCommit(true)
      }
    }
  }
}


/**
 * A connection releasing automatically statements on close
 */
private class AutoCleanConnection(connection: Connection) extends Connection {

  private val statements = scala.collection.mutable.ListBuffer.empty[Statement]

  private def registering[T <: Statement](b: => T) = {
    val statement = b
    statements += statement
    statement
  }

  private def releaseStatements() {
    statements.foreach { statement =>
      statement.close()
    }
    statements.clear()
  }

  def createStatement() = registering(connection.createStatement())
  def createStatement(resultSetType: Int, resultSetConcurrency: Int) = registering(createStatement(resultSetType, resultSetConcurrency))
  def createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int) = registering(createStatement(resultSetType, resultSetConcurrency, resultSetHoldability))
  def prepareStatement(sql: String) = registering(connection.prepareStatement(sql))
  def prepareStatement(sql: String, autoGeneratedKeys: Int) = registering(connection.prepareStatement(sql, autoGeneratedKeys))
  def prepareStatement(sql: String, columnIndexes: scala.Array[Int]) = registering(connection.prepareStatement(sql, columnIndexes))
  def prepareStatement(sql: String, resultSetType: Int, resultSetConcurrency: Int) = registering(connection.prepareStatement(sql, resultSetType, resultSetConcurrency))
  def prepareStatement(sql: String, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int) = registering(connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability))
  def prepareStatement(sql: String, columnNames: scala.Array[String]) = registering(connection.prepareStatement(sql, columnNames))
  def prepareCall(sql: String) = registering(connection.prepareCall(sql))
  def prepareCall(sql: String, resultSetType: Int, resultSetConcurrency: Int) = registering(connection.prepareCall(sql, resultSetType, resultSetConcurrency))
  def prepareCall(sql: String, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int) = registering(connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability))

  def close() {
    releaseStatements()
    connection.close()
  }

  def clearWarnings() { connection.clearWarnings() }
  def commit() { connection.commit() }
  def createArrayOf(typeName: String, elements: scala.Array[AnyRef]) = connection.createArrayOf(typeName, elements)
  def createBlob() = connection.createBlob()
  def createClob() = connection.createClob()
  def createNClob() = connection.createNClob()
  def createSQLXML() = connection.createSQLXML()
  def createStruct(typeName: String, attributes: scala.Array[AnyRef]) = connection.createStruct(typeName, attributes)
  def getAutoCommit() = connection.getAutoCommit()
  def getCatalog() = connection.getCatalog()
  def getClientInfo() = connection.getClientInfo()
  def getClientInfo(name: String) = connection.getClientInfo(name)
  def getHoldability() = connection.getHoldability()
  def getMetaData() = connection.getMetaData()
  def getTransactionIsolation() = connection.getTransactionIsolation()
  def getTypeMap() = connection.getTypeMap()
  def getWarnings() = connection.getWarnings()
  def isClosed() = connection.isClosed()
  def isReadOnly() = connection.isReadOnly()
  def isValid(timeout: Int) = connection.isValid(timeout)
  def nativeSQL(sql: String) = connection.nativeSQL(sql)
  def releaseSavepoint(savepoint: Savepoint) { connection.releaseSavepoint(savepoint) }
  def rollback() { connection.rollback() }
  def rollback(savepoint: Savepoint) { connection.rollback(savepoint) }
  def setAutoCommit(autoCommit: Boolean) { connection.setAutoCommit(autoCommit) }
  def setCatalog(catalog: String) { connection.setCatalog(catalog) }
  def setClientInfo(properties: java.util.Properties) { connection.setClientInfo(properties) }
  def setClientInfo(name: String, value: String) { connection.setClientInfo(name, value) }
  def setHoldability(holdability: Int) { connection.setHoldability(holdability) }
  def setReadOnly(readOnly: Boolean) { connection.setReadOnly(readOnly) }
  def setSavepoint() = connection.setSavepoint()
  def setSavepoint(name: String) = connection.setSavepoint(name)
  def setTransactionIsolation(level: Int) { connection.setTransactionIsolation(level) }
  def setTypeMap(map: java.util.Map[String, Class[_]]) { connection.setTypeMap(map) }
  def isWrapperFor(iface: Class[_]) = connection.isWrapperFor(iface)
  def unwrap[T](iface: Class[T]) = connection.unwrap(iface)

  // JDBC 4.1
  def getSchema() = {
    connection.asInstanceOf[{ def getSchema(): String }].getSchema()
  }

  def setSchema(schema: String) {
    connection.asInstanceOf[{ def setSchema(schema: String): Unit }].setSchema(schema)
  }

  def getNetworkTimeout() = {
    connection.asInstanceOf[{ def getNetworkTimeout(): Int }].getNetworkTimeout()
  }

  def setNetworkTimeout(executor: java.util.concurrent.Executor, milliseconds: Int) {
    connection.asInstanceOf[{ def setNetworkTimeout(executor: java.util.concurrent.Executor, milliseconds: Int): Unit }].setNetworkTimeout(executor, milliseconds)
  }

  def abort(executor: java.util.concurrent.Executor) {
    connection.asInstanceOf[{ def abort(executor: java.util.concurrent.Executor): Unit }].abort(executor)
  }

}
