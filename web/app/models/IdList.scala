package models

import scala.collection.immutable
import scala.util.control.Exception.catching
import scala.language.implicitConversions

/** A list of comma-separated IDs in a String.
  *
  * There is one public `ids`, a Seq[T].
  */
class IdList[T](string: String, read: (String => T)) {
  lazy val ids : immutable.Seq[T] = {
    """\d+""".r.findAllIn(string) // Iterable[String]
      .map(parse)                 // Iterable[Option[T]]
      .flatten                    // Iterable[T]
      .toVector
  }

  private def parse(s: String) = catching(classOf[NumberFormatException]).opt(read(s))
}

object IdList {
  def longs(s: String) = new IdList[Long](s, (s: String) => new immutable.StringOps(s).toLong)
}
