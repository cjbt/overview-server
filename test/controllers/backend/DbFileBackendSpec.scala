package controllers.backend

import com.overviewdocs.models.File

class DbFileBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbBackendScope {
    val backend = new DbFileBackend(injectedDatabase)
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val file = factory.file()
    }

    "show a File" in new ShowScope {
      val ret = await(backend.show(file.id))
      ret must beSome
      ret.map(_.id) must beSome(file.id)
      ret.map(_.name) must beSome(file.name)
      // etc
    }

    "not show a non-File" in new ShowScope {
      await(backend.show(file.id + 1)) must beNone
    }
  }
}
