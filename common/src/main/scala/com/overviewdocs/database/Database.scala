package com.overviewdocs.database

import java.sql.SQLException
import javax.sql.DataSource
import scala.concurrent.Future
import scala.language.higherKinds
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.{Database=>DatabaseFactory}
import slick.lifted.RunnableCompiled

import com.overviewdocs.database.Slick.api._

/** Runs queries on a Postgres database.
  *
  * These methods act upon Slick DBIOActions. All are asynchronous: they run in
  * the Slick executor.
  *
  * Use it like this:
  *
  * ```
  * val db = new Database(dataSource)
  * import db.api._
  * db.seq(Documents.filter(_.documentSetId === documentSetId))
  * ```
  *
  * Most of the time, you'll want to extend the HasDatabase trait instead of
  * invoking this class directly.
  * The constructor is private to force use of the singleton object returned by
  * `Database()`.
  */
class Database(val dataSource: DataSource, val maxConnections: Int) {
  /** Exposes the Slick Database. */
  val slickDatabase = DatabaseFactory.forDataSource(dataSource, Some(maxConnections))

  /** Exposes the Slick Database API.
    *
    * This saves typing over "com.overviewdocs.database.Slick.api".
    */
  val api = com.overviewdocs.database.Slick.api

  /** Exposes a "standard" execution context for flatMap operations.
    *
    * Again, this is to save typing. You'll need an execution context when
    * running code like this:
    *
    * ```
    * import database.executionContext
    * val x = database.run(for {
    *   foo &lt;- dbio1
    *   bar &lt;- dbio2
    * } yield bar)
    * ```
    *
    * The database operations occur in Slick's execution context. The flatMap
    * logic occurs in this one.
    */
  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  /** Provides access to large objects.
    *
    * @see LargeObjectManager
    */
  val largeObjectManager: LargeObjectManager = new LargeObjectManager(this)

  /** Runs a DBIO (Action) and returns the result.
    *
    * This is the lowest-level method in the class.
    */
  def run[T](action: DBIO[T]): Future[T] = wrapExceptions(slickDatabase.run(action))

  /** Like `run()`, but ignores the return value. */
  def runUnit[T](action: DBIO[T]): Future[Unit] = run(action).map(_ => ())(slickDatabase.ioExecutionContext)

  /** Returns all the results from a query as a Seq.
    *
    * Usage:
    *
    * ```
    * import com.overviewdocs.database.Slick.api._
    * database.seq(Documents.filter(_.documentSetId === documentSetId))
    * ```
    */
  def seq[T](query: Rep[Seq[T]]): Future[Seq[T]] = run(query.result)

  /** Return all the results from a compiled query as a Seq.
    *
    * Usage:
    *
    * ```
    * import com.overviewdocs.database.Slick.api._
    * lazy val compiledQuery = Compiled { (documentSetId: Rep[Long]) =&gt;
    *   Documents.filter(_.documentSetId === documentSetId)
    * }
    * database.seq(compiledQuery(documentSetId))
    * ```
    */
  def seq[T](query: RunnableCompiled[_, Seq[T]]): Future[Seq[T]] = run(query.result)

  /** Returns an Option with the first row from the query, if there is one.
    *
    * Usage:
    *
    * ```
    * database.option(sql"SELECT id FROM document".as[Long])
    */
  def option[T](action: DBIO[Seq[T]]): Future[Option[T]] = {
    run(action).map(_.headOption)(slickDatabase.ioExecutionContext)
  }

  /** Returns an Option with the first row from the query, if there is one.
    *
    * Usage:
    *
    * ```
    * database.option(Documents.filter(_.id === id))
    * ```
    */
  def option[T](query: Query[_, T, Seq]): Future[Option[T]] = run(query.result.headOption)

  /** Returns an Option with the first row from the query, if there is one.
    *
    * Usage:
    *
    * ```
    * lazy val compiledQuery = Compiled { (id: Rep[Long]) =&gt;
    *   Documents.filter(_.id === id)
    * }
    * database.option(compiledQuery(documentId))
    * ```
    */
  def option[T](query: RunnableCompiled[_, Seq[T]]): Future[Option[T]] = option(query.result)

  /** Returns the result of a COUNT(*) query.
    *
    * Usage:
    *
    * ```
    * database.length(Documents.filter(_.documentSetId === documentSetId))
    * ```
    */
  def length(query: Query[_, _, Seq]): Future[Int] = run(query.length.result)

  /** Destroys all rows the query would return.
    *
    * Usage:
    *
    * ```
    * database.delete(Documents.filter(_.documentSetId === documentSetId))
    * ```
    *
    * There is no return value, because `Future[Unit]` is most often the result
    * you want to return. If you want to check how many rows were deleted, drop
    * the layer of abstraction:
    *
    * ```
    * database.run(Documents.filter(_.documentSetId === documentSetId).delete)
    * ```
    */
  def delete(query: Query[_ <: Table[_], _, Seq]): Future[Unit] = runUnit(query.delete)

  /** Destroys all rows the query would return.
    *
    * Usage:
    *
    * ```
    * lazy val documentsByDocumentSetId = Compiled { (documentSetId: Rep[Long]) =gt;
    *   Documents.filter(_.documentSetId === documentSetId)
    * }
    * database.delete(documentsByDocumentSetId(documentSetId))
    * ```
    *
    * There is no return value, because `Future[Unit]` is most often the result
    * you want to return. If you want to check how many rows were deleted, drop
    * the layer of abstraction:
    *
    * ```
    * database.run(Documents.filter(_.documentSetId === documentSetId).delete)
    * ```
    */
  def delete[RU, C[_]](query: RunnableCompiled[_ <: Query[_, _, C], C[RU]]): Future[Unit] = runUnit(query.delete)

  def wrapException(t: Throwable): Throwable = t match {
    case e: SQLException => e.getSQLState() match {
      case "23505" => new exceptions.Conflict(e)
      case "23503" => new exceptions.ParentMissing(e)
      case _ => e
    }
    case _ => t
  }

  /** Re-casts Future failures to Conflict or ParentMissing when appropriate.
    */
  def wrapExceptions[T](future: Future[T]): Future[T] = {
    future.transform(identity, wrapException)(slickDatabase.ioExecutionContext)
  }
}


object Database {
  private lazy val database = new Database(DB.dataSource, DB.hikariConfig.getMaximumPoolSize)
  
  def apply(): Database = database
}
