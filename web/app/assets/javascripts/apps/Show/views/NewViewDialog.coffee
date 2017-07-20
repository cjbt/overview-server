define [
  'jquery'
  'i18n'
  'bootstrap-modal'
], ($, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.NewViewDialog')

  class NewViewDialog
    constructor: (options) ->
      throw 'Must pass options.success, a function that accepts a { title: ..., url: ... }' if !options?.success

      @url = options.url # if set, we know exactly which URL we want.
      # XXX @url is a bit of a hack. We'll probably want an entirely separate dialog soon

      @_success = options.success
      @$container = $(options.container ? 'body')

      @secure ||= {}
      @statuses ||= {}
      @xhrs ||= {}

      html = _.template("""
        <form method="get" action="#" id="new-view-dialog" class="modal" role="dialog">
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">×</button>
                <h4 class="modal-title"><%- t('title') %></h4>
              </div>
              <div class="modal-body">
                <div class="form-group">
                  <label for="new-view-dialog-title"><%- t('title.label') %></label>
                  <input
                    id="new-view-dialog-title"
                    name="title"
                    placeholder="<%- t('title.placeholder') %>"
                    class="form-control"
                    required="required"
                    />
                </div>
                <div class="form-group <%- url ? 'hide' : '' %>">
                  <label for="new-view-dialog-url"><%- t('url.label') %></label>
                  <!-- We can't use type="url" because "//example.org" isn't an absolute URL.
                       No biggie: we're actually trying to reach the URLs, so they'll be valid. -->
                  <input
                    id="new-view-dialog-url"
                    name="url"
                    type="text"
                    pattern="(https?:)?//.*"
                    placeholder="<%- t('url.placeholder') %>"
                    value="<%- url %>"
                    class="form-control"
                    required="required"
                    />
                  <div class="state">
                    <div class="checking"><%- t('url.checking') %></div>
                    <div class="ok"><%- t('url.ok') %></div>
                    <div class="invalid"><%- t('url.invalid') %></div>
                    <div class="unavailable">
                      <span class="message"></span>
                      <a href="#" class="retry"><%- t('url.unavailable.retry') %></a>
                    </div>
                    <div class="insecure">
                      <%= t('url.insecure_html') %>
                      <a href="#" class="dismiss"><%- t('url.insecure.dismiss') %></a>
                    </div>
                  </div>
                </div>
              </div>
              <div class="modal-footer">
                <input type="reset" class="btn" data-dismiss="modal" value="<%- t('cancel') %>" />
                <input type="submit" class="btn btn-primary" value="<%- t('submit') %>" />
              </div>
            </div>
          </div>
        </form>
      """)(t: t, url: @url)
      @$el = $(html)
      @$container.append(@$el)

      @$el.on('click', 'input[type=reset]', @onReset.bind(@))
      @$el.on('click', 'input[type=submit]', @onSubmit.bind(@))
      @$el.on('click', 'a.retry', @onRetry.bind(@))
      @$el.on('click', 'a.dismiss', @onDismiss.bind(@))
      @$el.on('change input', 'input[name=title]', @onChangeName.bind(@))
      @$el.on('change', 'input[name=url]', @onChangeUrl.bind(@))

      $state = @$el.find('.state')
      @$els =
        title: @$el.find('[name=title]')
        url: @$el.find('[name=url]')
        submit: @$el.find('[type=submit]')

        state:
          all: $state.children()
          checking: $state.children('.checking')
          ok: $state.children('.ok')
          unavailable: $state.children('.unavailable')
          insecure: $state.children('.insecure')
          invalid: $state.children('.invalid')

      @refreshState()
      if @url?
        @setUrlState(@url, 'ok')

      @$el.modal('show')
      @$el.find('input:eq(0)').focus().select()
      @$el.one 'shown.bs.modal', =>
        @$el.find('input:eq(0)').focus().select()
      @$el.on('hidden.bs.modal', => @remove())

    attrs: ->
      title: @$els.title.val()
      url: @$els.url.val()

    refreshState: ->
      @state = ''
      @$els.state.all.hide()

    remove: ->
      @$el.modal('hide')
      @$el.remove()
      @$el.off()
      $(document).off('.bs.modal')

    onReset: -> @remove()

    onSubmit: (e) ->
      e.preventDefault()

      if @validate()
        @_success(@attrs())
        @remove()

    onRetry: (e) ->
      e.preventDefault()
      url = @attrs().url
      delete @statuses[url]
      @checkUrl(url)

    onDismiss: (e) ->
      e.preventDefault()
      url = @attrs().url
      @secure[url] = true
      @checkUrl(url)

    onChangeUrl: ->
      $url = @$els.url
      url = $url.val()
      if $url[0].checkValidity()
        @checkUrl(url)
      else
        @setUrlState(url, 'invalid')

    onChangeName: ->
      @validate()

    setUrlState: (url, state, statusCode) ->
      if url == @attrs().url
        @state = state
        @$els.state.all.hide()
        @$els.state[state].show()

        if state == 'unavailable'
          @$els.state.unavailable.children('.message')
            .html(t('url.unavailable_html', "#{url}/metadata", statusCode))

        @validate()

    validate: ->
      valid = (@state == 'ok' && @$el[0].checkValidity())
      @$els.submit.prop('disabled', !valid)
      valid

    # Checks if the URL is okay, synchronously. If not, runs stuff in the
    # background and calls itself in the background.
    #
    # Effectively: calls setUrlState() once synchronously and more times
    # asynchronously.
    checkUrl: (url) ->
      if url == @url
        @setUrlState(url, 'ok')
      else if !@checkSecure(url)
        @setUrlState(url, 'insecure')
      else
        status = @checkStatus(url)
        if !status?
          @setUrlState(url, 'checking')
        else if status >= 200 && status < 300
          @setUrlState(url, 'ok')
        else
          @setUrlState(url, 'unavailable', status)

    checkSecure: (url) ->
      if /^http:\/\//.test(url)
        @secure[url] || false
      else
        true

    checkStatus: (url) ->
      if url of @statuses
        @statuses[url]
      else
        if url not of @xhrs
          xhr = $.ajax
            url: "#{url}/metadata"
            complete: =>
              delete @xhrs[url]
              @statuses[url] = xhr.status
              @checkUrl(url)
        null
