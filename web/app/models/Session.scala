package models

import java.sql.Timestamp
import java.util.{Date,UUID}

import com.github.tminglei.slickpg.InetString

/** A user's session in the database.
  *
  * Create a new one like this: Session(userId, userIpAddressString)
  *
  * The Session is stored in the database, and its ID is (hopefully) stored
  * in a client-side cookie. The next time the client wants to log in, he
  * or she supplies the session ID and we re-load the session, modify updatedAt
  * and save. When the user logs out, we delete the session from the database
  * and delete the cookie; any new person who comes along with the same session
  * ID won't be able to log in.
  *
  * Note the hack for isPersisted. The above-mentioned constructor creates
  * a Session in which createdAt and updatedAt are the same object, and that
  * makes isPersisted=true. When you call .update() to modify updatedAt, the
  * resulting object has isPersisted=false.
  */
case class Session(
  val id: UUID,
  val userId: Long,
  val ip: InetString,
  val createdAt: Timestamp,
  val updatedAt: Timestamp
) {
  def update(ip: InetString): Session = {
    copy(
      ip=ip,
      updatedAt=new Timestamp((new Date()).getTime())
    )
  }

  def update(ip: String): Session = update(InetString(ip))

  def update(attributes: Session.UpdateAttributes): Session = copy(
    ip=InetString(attributes.ip),
    updatedAt=new Timestamp(attributes.updatedAt.getTime())
  )
}

object Session {
  case class UpdateAttributes(ip: String, updatedAt: Date)

  /** Constructor with default id and updatedAt */
  def apply(userId: Long, ip: String, createdAt: Timestamp) : Session = {
    apply(
      id=UUID.randomUUID(),
      userId=userId,
      ip=InetString(ip),
      createdAt=createdAt,
      updatedAt=createdAt
    )
  }

  /** Constructor with default id, updatedAt and createdAt */
  def apply(userId: Long, ip: String) : Session = {
    apply(userId, ip, new Timestamp((new Date()).getTime))
  }
}
