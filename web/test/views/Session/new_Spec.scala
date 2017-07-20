package views.html.Session

class new_Spec extends views.ViewSpecification {
  trait OurContext extends HtmlViewSpecificationScope {
    val loginForm = controllers.forms.LoginForm()
    val userForm = controllers.forms.UserForm()

    val view = new _new(new MockMain)

    override def result = view(loginForm, userForm)
  }

  "new_()" should {
    "show two form" in new OurContext {
      $("form").length.must(beEqualTo(2))
    }

    "show a confirm-password field" in new OurContext {
      $("input[type=password].confirm-password").length.must(beEqualTo(1))
    }
  }
}
