package models.pagination

case class Page[+A](
  items: Seq[A],
  pageInfo: PageInfo
) {
  def map[B](f: A => B): Page[B] = Page(items.map(f), pageInfo)
}

object Page {
  /** Makes a Page with offset 0, limit length, total length */
  def apply[A](items: Seq[A]): Page[A] = {
    val len = items.length
    Page(items, PageInfo(PageRequest(0, len, false), len))
  }
}
