define [ 'underscore', 'backbone', 'i18n' ], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.admin.User.index')

  class NewUserView extends Backbone.View
    tagName: 'tfoot'
    className: 'new-user'

    template: _.template("""
      <tr>
        <td colspan="5">
          <form class="form-inline">
            <label for="new-user-email"><%- t('new.title') %></label>
            <input
              name="email"
              id="new-user-email"
              class="form-control input-sm"
              required="required"
              type="email"
              placeholder="<%- t('new.email') %>"
              />
            <input
              name="password"
              class="form-control input-sm"
              required="required"
              type="password"
              placeholder="<%- t('new.password') %>"
              />
            <input
              type="submit"
              class="btn btn-primary"
              value="<%- t('new.submit') %>"
              />
          </form>
        </td>
      </tr>
      """)

    events:
      'submit form': '_onSubmit'

    render: ->
      html = @template(t: t)
      @$el.html(html)

    _onSubmit: (e) ->
      e.preventDefault()
      email = @$('[name=email]').val()
      password = @$('[name=password]').val()
      @$('form').get(0).reset()
      @trigger('create', email: email, password: password)
      @$('[name=email]').focus()
