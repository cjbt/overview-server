package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ ActionBuilder, Request, Result }
import play.api.Play
import scala.concurrent.Future

import models.OverviewDatabase

trait OptionallyAuthorizedAction {
  protected val sessionFactory: SessionFactory

  def apply(authority: Authority): ActionBuilder[OptionallyAuthorizedRequest] = {
    new ActionBuilder[OptionallyAuthorizedRequest] {
      override def invokeBlock[A](request: Request[A], block: (OptionallyAuthorizedRequest[A]) => Future[Result]) : Future[Result] = {
        /*
         * We special-case OptionallyAuthorizedRequest[A] to short-circuit
         * auth, so we can write tests that don't hit UserFactory.
         *
         * We can't use overloading (because Request is a trait) or matching
         * (because of type erasure), but we can prove this is type-safe.
         */
        if (request.isInstanceOf[OptionallyAuthorizedRequest[_]]) {
          block(request.asInstanceOf[OptionallyAuthorizedRequest[A]])
        } else {
          sessionFactory
            .loadAuthorizedSession(request, authority) // Future[Either[Result,(Session,User)]]
            .map(_.right.toOption) // Future[Option[(Session,User)]]
            .map(new OptionallyAuthorizedRequest(request, _)) // Future[RequestHeader]
            .flatMap(block(_)) // Future[Result]
        }
      }
    }
  }

  def inTransaction(authority: Authority): ActionBuilder[OptionallyAuthorizedRequest] = {
    new ActionBuilder[OptionallyAuthorizedRequest] {
      override def invokeBlock[A](request: Request[A], block: (OptionallyAuthorizedRequest[A]) => Future[Result]) : Future[Result] = {
        /*
         * We special-case OptionallyAuthorizedRequest[A] to short-circuit
         * auth, so we can write tests that don't hit UserFactory.
         *
         * We can't use overloading (because Request is a trait) or matching
         * (because of type erasure), but we can prove this is type-safe.
         */
        if (request.isInstanceOf[OptionallyAuthorizedRequest[_]]) {
          block(request.asInstanceOf[OptionallyAuthorizedRequest[A]])
        } else {
          sessionFactory
            .loadAuthorizedSession(request, authority) // Future[Either[Result,(Session,User)]]
            .map(_.right.toOption) // Future[Option[(Session,User)]]
            .map(new OptionallyAuthorizedRequest(request, _)) // Future[RequestHeader]
            .flatMap(request => OverviewDatabase.inTransaction(block(request))) // Future[Result]
        }
      }
    }
  }
}

object OptionallyAuthorizedAction extends OptionallyAuthorizedAction {
  private val isMultiUser = Play.current.configuration.getBoolean("overview.multi_user").getOrElse(true)

  override val sessionFactory = {
    if (isMultiUser) {
      SessionFactory
    } else {
      SingleUserSessionFactory
    }
  }
}
