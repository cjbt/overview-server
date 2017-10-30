package controllers.forms

import play.api.data.{Form,Forms}

import com.overviewdocs.models.Plugin

object PluginCreateForm {
  def apply(): Form[Plugin.CreateAttributes] = {
    Form(
      Forms.mapping(
        "name" -> Forms.nonEmptyText,
        "description" -> Forms.nonEmptyText,
        "url" -> Forms.nonEmptyText,
        "serverUrlFromPlugin" -> Forms.optional(Forms.nonEmptyText),
        "autocreate" -> Forms.default(Forms.boolean, false),
        "autocreateOrder" -> Forms.default(Forms.number, 0)
      )(Plugin.CreateAttributes.apply)(Plugin.CreateAttributes.unapply)
    )
  }
}
