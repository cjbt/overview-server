package controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader}
import play.api.test.FakeRequest

import models.pagination.PageRequest

class ControllerHelpersSpec extends Specification with JsonMatchers {
  "jsonError" should {
    "generate a JSON Error object" in {
      trait MyTest {
        self: ControllerHelpers =>

        val err = jsonError("aaa", "foo")
      }

      val controller = new ControllerHelpers with MyTest

      Json.stringify(controller.err) must /("message" -> "foo")
      Json.stringify(controller.err) must /("code" -> "aaa")
    }
  }

  "pageRequest" should {
    trait PageScope extends Scope {
      trait F {
        def f(request: RequestHeader, maxLimit: Int): PageRequest
      }
      val controller = new ControllerHelpers with F {
        // Make it public
        override def f(request: RequestHeader, maxLimit: Int) = pageRequest(request, maxLimit)
      }
    }

    "default to offset 0" in new PageScope {
      controller.f(FakeRequest(), 1000).offset must beEqualTo(0)
    }

    "default to reverse=false" in new PageScope {
      controller.f(FakeRequest(), 1000).reverse must beEqualTo(false)
    }

    "let you specify an offset" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=123"), 1000).offset must beEqualTo(123)
    }

    "ignore offsets that cannot be parsed" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=123foo"), 1000).offset must beEqualTo(0)
    }

    "ignore offsets that overflow" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=999999999999999999"), 1000).offset must beEqualTo(0)
    }

    "ignore negative offsets" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=-123"), 1000).offset must beEqualTo(0)
    }

    "default to a limit of maxLimit" in new PageScope {
      controller.f(FakeRequest(), 1000).limit must beEqualTo(1000)
    }

    "let you specify a limit" in new PageScope {
      controller.f(FakeRequest(), 100).limit must beEqualTo(100)
    }

    "let you set reverse=true" in new PageScope {
      controller.f(FakeRequest("GET", "/?reverse=true"), 1000).reverse must beEqualTo(true)
    }

    "let you set reverse=false" in new PageScope {
      controller.f(FakeRequest("GET", "/?reverse=false"), 1000).reverse must beEqualTo(false)
    }

    "ignore limits that cannot be parsed" in new PageScope {
      controller.f(FakeRequest("GET", "/?limit=10foo"), 1000).limit must beEqualTo(1000)
    }

    "ignore limits that are higher than the maximum" in new PageScope {
      controller.f(FakeRequest("GET", "/?limit=9999"), 1000).limit must beEqualTo(1000)
    }

    "ignore negative limits" in new PageScope {
      controller.f(FakeRequest("GET", "/?limit=-1"), 1000).limit must beEqualTo(1000)
    }

    "change limit 0 to 1" in new PageScope {
      controller.f(FakeRequest("GET", "/?limit=0"), 1000).limit must beEqualTo(1)
    }

    "parse both offset and limit in the same request" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=20&limit=30"), 1000) must beEqualTo(PageRequest(20, 30, false))
    }
  }
}
