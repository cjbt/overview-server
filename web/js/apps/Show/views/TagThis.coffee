define [
  'underscore'
  'jquery'
  'backbone'
  'i18n'
  'typeahead'
], (_, $, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.TagThis')

  fuzzyEquals = (text1, text2) ->
    text1.toLowerCase() == text2.toLowerCase()

  fuzzyContains = (full, partial) ->
    full = full.toLowerCase().trim()
    partial = partial.toLowerCase().trim()
    full.indexOf(partial) == 0

  # Tagging interface for the current document list
  #
  # Calls the following:
  #
  # * tags.create(name: tagName)
  # * state.getCurrentTaggable().tag(tag)
  # * state.getCurrentTaggable().untag(tag)
  #
  # Triggers the following events:
  # * organize-clicked()
  class TagThis extends Backbone.View
    className: 'tag-this'

    templates:
      button: _.template('''
        <button class="prompt btn btn-default"><i class="icon icon-tag"></i> <%- t('prompt') %> <i class="icon icon-caret-down"></i></button>
        <div class="tag-this-main">
        </div>
      ''')

      ulContents: _.template('''
        <% tags.forEach(function(tag) { %>
          <li data-cid="<%- tag.cid %>" class="unknown">
            <i class="status"></i>
            <span class="<%- tag.getClass() %>" style="<%- tag.getStyle() %>"></span>
            <span class="name">
              <% if (highlight) { %>
                <u><%- tag.get('name').substring(0, highlight.length) %></u
              ><% } %><%- tag.get('name').substring(highlight.length) %>
            </span>
          </li>
        <% }); %>
        <% if (newTagName) { %>
          <li class="create" data-name="<%- newTagName %>"><%= t('create_html', _.escape(newTagName)) %></li>
        <% } %>
      ''')

      main: _.template('''
        <div class="search">
          <label for="tag-this-name"><%- t('label', nDocuments) %></label>
          <div class="search-input">
            <input
              id="tag-this-name"
              autocomplete="off"
              type="text"
              class="form-control input-sm"
              name="name"
              placeholder="<%- t('placeholder') %>"
            />
          </div>
        </div>
        <div class="existing-tags">
          <ul></ul>
        </div>
        <div class="actions">
          <div class="organize"><a href="#" class="organize"><%- t('organize') %></a></div>
        </div>
      ''')

    events:
      'click': ((e) -> e.stopPropagation()) # don't clear()
      'click .prompt': 'toggle'
      'click div.organize': '_onClickOrganize'
      'click li[data-cid]': '_onClickTag'
      'click li.create': '_onClickCreate'
      'mouseenter li': '_onMouseenterLi'
      'input input[name=name]': '_onInput'
      'keydown input[name=name]': '_onKeyDown'

    initialize: (options) ->
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.tags, a Tags' if !options.tags

      @tags = options.tags
      @state = options.state
      @documentList = null
      @keyboardController = options.keyboardController # optional

      if @keyboardController?
        @keyBindings =
          T: => @toggle()
        @keyboardController.register(@keyBindings)

      @_clear = @clear.bind(@)
      $(document).on('click.tag-this', @_clear)

      @render()

      @listenTo(@state, 'change:documentList', @close)

    remove: ->
      @keyboardController?.unregister(@keyBindings)
      $(document).off('click.tag-this', @_clear)
      super()

    render: ->
      @$el.html(@templates.button(t: t))

      @ui =
        button: @$('button')
        main: @$('.tag-this-main')

    toggle: ->
      if @$el.hasClass('open')
        @clear()
      else
        @show()

    # Prevents pointer events from hitting iframes.
    #
    # This solves the problem:
    #
    # 1. User opens popup
    # 2. User clicks on iframe behind the popup
    # 3. Iframe eats the click event, so popup stays open
    #
    # It's better (still icky, but better) to make the click do nothing. That
    # nullifies mousewheel events and on-hover stuff in the iframe, but at
    # least it doesn't eat the click.
    _muteIframes: ->
      @_unmuteIframes()
      @_iframesWithPointerEvents = for iframe in $('iframe, object')
        $iframe = $(iframe)
        value = $iframe.css('pointerEvents')
        $iframe.css(pointerEvents: 'none')
        [ $iframe, value ]
      undefined

    _unmuteIframes: ->
      for [ $iframe, oldValue ] in (@_iframesWithPointerEvents || [])
        $iframe.css(pointerEvents: oldValue)
      @_iframesWithPointerEvents = []
      undefined

    clear: ->
      return if !@$el.hasClass('open')
      @$el.removeClass('open')
      @ui.button.removeClass('active btn-primary')
      @_unmuteIframes()

      @stopListening(@documentList)
      @documentList = null

      @ui.main.empty()
      @ui.main.css(minWidth: 'auto')

      _.extend @ui,
        search: null
        ul: null
        create: null
        actions: null
        tagStatuses: {}

      @ui.button.blur()

    show: ->
      return if @$el.hasClass('open')
      @$el.addClass('open')
      @ui.button.addClass('active btn-primary')
      @_muteIframes()

      taggable = @state.getCurrentTaggable()
      nDocuments = if taggable?
        if taggable.params? # a DocumentList
          taggable.get('length') || 0
        else # a Document
          1
      else
        0
      html = @templates.main(t: t, nDocuments: nDocuments)
      @ui.main.html(html)

      _.extend @ui,
        search: @$('input[name=name]')
        ul: @$('ul')
        create: @$('.create')
        actions: @$('.actions')

      @ui.search.focus()

      @documentList = @state.get('documentList')

      @_refreshUl()

      # Prevent reflow when autocompleting. +1 in case $.fn.width rounds down
      @ui.main.css(minWidth: @ui.main.width() + 1)

      @listenTo(@documentList, 'tag-counts-changed', @_renderTagStatuses)
      @_renderTagStatuses()

    _onInput: ->
      text = @ui.search.val().trim()

      @ui.actions.toggleClass('hidden', !!text)
      @_refreshUl(text)

      if text
        @_highlight(0)
      else
        @_highlight(null)

    _refreshUl: (search) ->
      search ||= ''

      tags = @tags
        .filter((tag) -> fuzzyContains(tag.get('name'), search))
        .sort((a, b) -> a.get('name').localeCompare(b.get('name')))

      newTagName = if search && tags.some((tag) -> fuzzyEquals(tag.get('name'), search))
        ''
      else
        search

      html = @templates.ulContents(t: t, tags: tags, newTagName: newTagName, highlight: search)
      @ui.ul.html(html)

      @ui.tagStatuses = {}
      for li in @ui.ul.children('li[data-cid]')
        cid = li.getAttribute('data-cid')
        @ui.tagStatuses[cid] = { li: li, status: 'unknown' }

      @_renderTagStatuses()

      @_highlightedIndex = null
      @_nLis = @ui.ul.children().length

    _renderTagStatuses: ->
      for cid, __ of (@ui.tagStatuses || {})
        @_renderTagStatus(@tags.get(cid))
      undefined

    _getTagStatus: (tag) ->
      taggable = @state.getCurrentTaggable()

      if taggable.getTagCount? # it's a DocumentList
        count = @documentList.getTagCount(tag)
        total = @documentList.get('length')
        if count.n == @documentList.get('length') # even null
          # This is 'all', but it's less confusing if we say 'none' when count=0
          count.n == 0 && 'none' || 'all'
        else if count.n > 0 || count.howSure == 'atLeast'
          'some'
        else # exactly 0
          'none'
      else
        taggable.hasTag(tag) && 'all' || 'none'

    _renderTagStatus: (tag) ->
      obj = @ui.tagStatuses[tag.cid]
      oldStatus = obj.status
      obj.status = @_getTagStatus(tag)

      if obj.status != oldStatus
        obj.li.className = obj.status

    # Marks one <li> as the active one. Affects Enter, Up, Down.
    _highlight: (index) ->
      # Wrap
      if index?
        index = @_nLis - 1 if index < 0
        index = 0 if index >= @_nLis

      $(@ui.ul.children()[@_highlightedIndex]).removeClass('active') if @_highlightedIndex?
      @_highlightedIndex = index
      $(@ui.ul.children()[@_highlightedIndex]).addClass('active') if @_highlightedIndex?

    _onKeyDown: (e) ->
      switch e.keyCode
        when 27 # Escape
          @clear()
        when 38 # Up
          @_highlight((@_highlightedIndex ? 0) - 1)
        when 40 # Down
          @_highlight((@_highlightedIndex ? -1) + 1)
        when 13 # Enter
          @_onPressEnter(e)
        when 9 # Tab
          undefined

    _onMouseenterLi: (e) ->
      index = $(e.currentTarget).prevAll().length
      @_highlight(index)

    _onClickOrganize: (e) ->
      e.preventDefault()
      @trigger('organize-clicked')
      @clear()

    _onClickCreate: (e) ->
      e.preventDefault()
      @_actOnLi(e.currentTarget)

    _onClickTag: (e) ->
      e.preventDefault()
      @_actOnLi(e.currentTarget)

    _onPressEnter: (e) ->
      e.preventDefault()
      if @_highlightedIndex?
        li = @ui.ul.children()[@_highlightedIndex]
        @_actOnLi(li)

      # This @clear() is to speed up the workflow of a keyboard-shortcut power
      # user who is only tagging and not untagging. It speeds up this tight
      # loop:
      #
      # 1. Press 'j' to select a new document
      # 2. Press 't' to open tagging interface
      # 3. Auto-complete tag name
      # 4. Press Enter to apply/unapply tag
      # 5. Press Escape
      # 6. Repeat
      #
      # It saves the user from step 5. In the process, it makes the UX
      # inconsistent, and it makes untagging slower. (1/7 of tagging operations
      # are untaggings.)
      #
      # [adam] I think it doesn't belong, but I've been out-voted.
      @clear()

    _actOnLi: (li) ->
      $li = $(li)

      if $li.hasClass('create')
        tag = @tags.create(name: $li.attr('data-name'))
        @state.getCurrentTaggable()?.tag(tag)
        @clear()
        @show()
      else
        cid = $li.attr('data-cid')
        tag = @tags.get(cid) || null
        if !tag?
          console.warn("Could not find tag with cid #{cid} in li", li)
          return

        if $li.hasClass('all')
          @state.getCurrentTaggable()?.untag(tag)
          $li.attr(class: 'active none')
        else
          @state.getCurrentTaggable()?.tag(tag)
          $li.attr(class: 'active all')

      @ui.search.focus().select()
