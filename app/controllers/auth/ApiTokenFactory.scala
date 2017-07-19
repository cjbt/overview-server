package controllers.auth

import com.google.inject.ImplementedBy
import java.nio.charset.Charset
import javax.inject.Inject
import javax.xml.bind.DatatypeConverter
import play.api.mvc.{RequestHeader,Result,Results}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.Exception.catching

import controllers.backend.ApiTokenBackend
import com.overviewdocs.models.ApiToken

@ImplementedBy(classOf[DefaultApiTokenFactory])
trait ApiTokenFactory {
  def loadAuthorizedApiToken(request: RequestHeader, authority: Authority) : Future[Either[Result,ApiToken]]
}

/** Authorizes API tokens.
  *
  * This involves a few tasks:
  *
  * 1. Fetch the ApiToken from the database (not there? authentication failed)
  * 2. Check if the user is authorized for the task at hand (no? authorization failed)
  */
class DefaultApiTokenFactory @Inject() (backend: ApiTokenBackend) extends ApiTokenFactory {
  private def unauthenticated: Result = {
    Results.Unauthorized(views.json.api.auth.unauthenticated())
  }

  private def forbidden: Result = {
    Results.Forbidden(views.json.api.auth.forbidden())
  }

  private val ascii = Charset.forName("US-ASCII")

  private def getEncodedUsernameAndPassword(s: String) : Option[String] = {
    if (s.startsWith("Basic ")) Some(s.substring(6)) else None
  }

  private def decode64(s: String) : Option[String] = {
    catching(classOf[IllegalArgumentException]).opt(DatatypeConverter.parseBase64Binary(s))
      .map((b: Array[Byte]) => new String(b, ascii))
  }

  private def getTokenString(usernameAndPassword: String) : Option[String] = {
    if (usernameAndPassword.contains(':')) {
      val parts = usernameAndPassword.split(":", 2)
      if (parts(1).equals("x-auth-token")) {
        Some(parts(0))
      } else {
        None
      }
    } else {
      None
    }
  }

  private def authorizationHeaderToToken(s: String) : Option[String] = {
    val ret = getEncodedUsernameAndPassword(s)
      .flatMap(decode64(_))
      .flatMap(getTokenString(_))
    ret
  }

  /** Returns either a Result (no access) or an ApiToken (access). */
  def loadAuthorizedApiToken(request: RequestHeader, authority: Authority) : Future[Either[Result,ApiToken]] = {
    request.headers.get("Authorization").toRight(unauthenticated)
      .right.flatMap(authorizationHeaderToToken(_).toRight(unauthenticated))
      match {
        case Left(result) => Future(Left(result))
        case Right(tokenString) => {
          backend.show(tokenString).flatMap(_ match {
            case None => Future(Left(unauthenticated))
            case Some(apiToken) => {
              authority(apiToken).map((allowed: Boolean) => allowed match {
                case true => Right(apiToken)
                case false => Left(forbidden)
              })
            }
          })
        }
      }
  }
}
