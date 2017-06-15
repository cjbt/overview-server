package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.ApiToken
import com.overviewdocs.models.tables.ApiTokens

@ImplementedBy(classOf[DbApiTokenBackend])
trait ApiTokenBackend {
  /** Returns all ApiTokens with the given access.
    */
  def index(createdBy: String, documentSetId: Option[Long]): Future[Seq[ApiToken]]

  /** Returns one ApiToken.
    *
    * Neat point about authentication: if a user can supply an ApiToken, the
    * user has access to the ApiToken, because it's a password.
    */
  def show(token: String): Future[Option[ApiToken]]

  /** Creates an ApiToken with a secure, unique token.
    *
    * Returns an error if the database write fails.
    */
  def create(documentSetId: Option[Long], attributes: ApiToken.CreateAttributes): Future[ApiToken]

  /** Destroys an ApiToken.
    *
    * Throws MissingParent when a Store or View depends on this ApiToken.
    */
  def destroy(token: String): Future[Unit]
}

class DbApiTokenBackend @Inject() (val database: Database) extends ApiTokenBackend with DbBackend {
  import database.api._

  lazy val byCreatedByAndDocumentSetIdCompiled = Compiled { (createdBy: Rep[String], documentSetId: Rep[Option[Long]]) =>
    ApiTokens
      .filter(row => row.documentSetId === documentSetId || (row.documentSetId.isEmpty && documentSetId.isEmpty))
      .filter(_.createdBy === createdBy)
  }

  lazy val byTokenCompiled = Compiled { token: Rep[String] =>
    ApiTokens.filter(_.token === token)
  }

  lazy val inserter = (ApiTokens returning ApiTokens)

  override def index(createdBy: String, documentSetId: Option[Long]) = {
    database.seq(byCreatedByAndDocumentSetIdCompiled(createdBy, documentSetId))
  }

  override def show(token: String) = database.option(byTokenCompiled(token))

  override def create(documentSetId: Option[Long], attributes: ApiToken.CreateAttributes) = database.run {
    val apiToken = ApiToken.build(documentSetId, attributes)
    inserter.+=(apiToken)
  }

  override def destroy(token: String) = database.delete(byTokenCompiled(token))
}
