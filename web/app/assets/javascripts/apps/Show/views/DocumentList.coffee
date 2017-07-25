define [
  'underscore'
  'backbone'
  '../helpers/DocumentHelper'
  'i18n'
], (_, Backbone, DocumentHelper, i18n) ->
  t = i18n.namespaced('views.Tree.show.DocumentList')

  templates =
    # We put < and > at the exact beginning and end of screen so we can iterate
    # over DOM children without bothering with text nodes
    model: _.template("""<li <%= liAttributes %>
      class="document <%= attrs.thumbnailUrl ?  'with-thumbnail' : '' %>" data-cid="<%- model.cid %>" data-docid="<%- model.id %>">
        <% if (attrs.thumbnailUrl) { %>
          <div class="thumbnail"><img src="<%= attrs.thumbnailUrl %>" alt="" /></div>
        <% } %>
        <div class="details">
          <h3><%- title %></h3>
          <% if (sortKey) { %>
            <p class="sort-key"><%- t('sortKey', sortKey, attrs.metadata[sortKey]) %></p>
          <% } %>
          <% if (attrs.snippet) { %>
            <p class="snippets"><%= attrs.snippet %></p>
          <% }%>
          <ul class="tags">
            <% _.each(tags, function(tag) { %>
              <li class="tag" data-cid="<%- tag.cid %>">
                <div class="<%= tag.getClass() %>" style="<%= tag.getStyle() %>">
                  <span class="name"><%- tag.get('name') %></span>
                </div>
              </li>
            <% }); %>
          </ul>
        </div>
      </li
    >""")

    collection: _.template("""
      <ul class="warnings"><%= _.map(warnings, renderWarning).join('') %></ul>
      <ul class="documents" <%= ulAttributes %>><%= _.map(collection.models, renderModel).join('') %></ul>
      <div class="loading"><%- t('loading') %></div>
    """)

    error: _.template("""
      <div class="error"><%- error %></div>
    """)

  # Shows a list of Document models.
  #
  # Each document has a list of tags.
  #
  # The following events can be fired:
  #
  # * click-document(document, index, { shift: boolean, meta: boolean }): user clicked a document
  #
  # The following options must be passed:
  #
  # * model (a DocumentList)
  # * tags
  # * selection (a Backbone.Model with 'cursorIndex' and 'selectedIndices')
  #
  # The following options are, well, optional:
  #
  # * liAttributes: string of HTML attributes for document li tags. This is only
  #   needed for testing, as the browser (Chrome 26.0.1410.63 on Ubuntu)
  #   doesn't seem to repaint after we change elements' heights and before
  #   we run calculations with the new heights.
  # * ulAttributes: ditto, but for the outer ul
  Backbone.View.extend
    id: 'document-list'

    events:
      'mousedown': '_onMousedownCancelSelect'
      'mouseenter .thumbnail img': '_onMouseenterThumbnail'
      'mouseleave .thumbnail img': '_onMouseleaveThumbnail'
      'click .document': '_onClickDocument'

    initialize: ->
      throw 'Do not pass options.collection' if @collection?
      throw 'Must pass options.model, a DocumentList' if !@model? || !@model.documents?
      throw 'Must pass options.tags, a Collection of Backbone.Tags' if !@options.tags
      throw 'Must pass options.selection, a Backbone.Model with cursorIndex and selectedIndices' if !@options.selection

      @listenTo(@options.tags, 'change', @_renderTag)
      @listenTo(@options.selection, 'change:selectedIndices', (model, value) => @_renderSelectedIndices(value))
      @listenTo(@options.selection, 'change:cursorIndex', (model, value) => @_renderCursorIndex(value))
      @$el.on('scroll', => @_updateMaxViewedIndex())

      @hoverThumbnail = null
      @setModel(@model) # calls @render()
      @_renderSelectedIndices(@options.selection.get('selectedIndices'))
      @_renderCursorIndex(@options.selection.get('cursorIndex'))

    remove: ->
      document.body.remove(@hoverThumbnail) if @hoverThumbnail
      Backbone.View.prototype.remove.call(@)

    _listenToModel: ->
      @listenTo @model,
        'change:warnings': @render
        'change:error': @render
        'change:loading': @_renderLoading

      @listenTo @collection,
        'reset': @render
        'change': @_renderModel
        'document-tagged': @_renderModel
        'document-untagged': @_renderModel
        'add': @_addModel

    setModel: (model) ->
      @stopListening(@model) if @model?
      @stopListening(@collection) if @collection?
      @model = model
      @collection = model.documents
      @_listenToModel()
      @render()

    _renderModelHtml: (model) ->
      tags = @options.tags.filter((x) -> model.hasTag(x))
      templates.model
        title: DocumentHelper.title(model.attributes)
        model: model
        attrs: model.attributes
        sortKey: @model.params.sortByMetadataField
        tags: tags
        t: t
        liAttributes: @options.liAttributes || ''

    _renderWarningHtml: (warning) ->
      i18nKey = "warning.#{warning.type}"
      switch warning.type
        when 'TooManyExpansions' then "<li>#{t(i18nKey, warning.field, warning.term, warning.nExpansions)}</li>"
        when 'TooMuchFuzz' then "<li>#{t(i18nKey, warning.field, warning.term, warning.allowedFuzz)}</li>"
        when 'IndexDoesNotExist' then "<li>#{t(i18nKey)}</li>"
        when 'MissingField' then "<li>#{t(i18nKey, warning.field)}<ul class='validFieldNames'><li>text</li><li>title</li><li>notes</li>#{warning.validFieldNames.map((s) => "<li>#{_.escape(s)}</li>").join('')}</ul></li>"
        else
          console.warn("Unhandled warning: #{JSON.stringify(warning)}")
          ""

    render: ->
      html = if @model.get('error')
        templates.error(error: @model.get('error'), t: t)
      else
        templates.collection({
          collection: @collection
          warnings: @model.get('warnings') || []
          renderWarning: (warning) => @_renderWarningHtml(warning)
          t: t
          renderModel: (model) => @_renderModelHtml(model)
          ulAttributes: @options.ulAttributes || ''
        })

      @$el.html(html)

      @_$els =
        ul: @$('ul.documents')

      delete @maxViewedIndex
      @_updateMaxViewedIndex()
      @_renderLoading()

    _renderLoading: ->
      loading = @model.get('loading')
      @$el.toggleClass('loading', loading)

    # Returns the maximum index of a ul.documents>li that is presently visible.
    #
    # Assumes the ul.documents is entirely on-screen.
    _findMaxViewedIndex: (startAt) ->
      ul = @_$els.ul[0]

      return undefined if !ul?

      elBottom = (el) -> el.getBoundingClientRect().bottom

      containerBottom = elBottom(@el)

      lis = ul.childNodes

      return -1 if lis.length == 0

      # Binary search
      low = startAt || 0 # invariant: low is always fully visible
      high = lis.length - 1 # invariant: high+1 is always invisible, at least partially

      while low < high
        mid = (low + high + 1) >>> 1 # low < mid <= high
        if elBottom(lis[mid]) < containerBottom
          low = mid
        else
          high = mid - 1

      # low=high, so low is visible and low+1 is partially invisible

      Math.min(low + 1, lis.length - 1)

    _updateMaxViewedIndex: ->
      maxViewedIndex = @_findMaxViewedIndex(@maxViewedIndex || 0)
      if !@maxViewedIndex? || maxViewedIndex > @maxViewedIndex
        @maxViewedIndex = maxViewedIndex
        @trigger('change:maxViewedIndex', this, maxViewedIndex)

    _addModel: (model) ->
      if @_$els.ul.length == 0
        @render()
      else
        html = @_renderModelHtml(model)
        @_$els.ul.append(html)

    _renderModel: (model) ->
      $li = @$("li.document[data-cid=#{model.cid}]")

      html = @_renderModelHtml(model)
      $newLi = $(html)
      $newLi.addClass('selected') if $li.hasClass('selected')
      $newLi.addClass('cursor') if $li.hasClass('cursor')

      $li.replaceWith($newLi)

    _renderTag: (tag) ->
      if 'name' of tag.changed
        # Tags might be reordered. Rewrite all models.
        $documents = @$(".tag[data-cid=#{tag.cid}]").closest('li.document')
        for documentEl in $documents
          documentCid = documentEl.getAttribute('data-cid')
          document = @collection.get(documentCid)
          @_renderModel(document)
      else
        $tags = @$(".tag[data-cid=#{tag.cid}]>div")
        $tags.attr
          class: tag.getClass()
          style: tag.getStyle()

    _renderSelectedIndices: (selectedIndices) ->
      @$('ul.documents>li.selected').removeClass('selected')
      if selectedIndices?.length
        $lis = @$('ul.documents>li')
        for index in selectedIndices
          $lis.eq(index).addClass('selected')

      if selectedIndices?.length == 1
        @$el.addClass('document-selected')
      else
        @$el.removeClass('document-selected')

    _renderCursorIndex: (index) ->
      @$('ul.documents>li.cursor').removeClass('cursor')
      if index?
        $li = @$("li.document:eq(#{index})")

        margin = 20 # always scroll this number of pixels past what we strictly need

        height = @$el.height()
        currentTop = @$el.scrollTop()
        currentBottom = currentTop + height
        neededTop = Math.max(0, ($li.position().top - $li.parent().position().top) - margin)
        neededBottom = Math.max(height, neededTop + $li.outerHeight() + margin + margin)

        if currentTop > neededTop
          # scroll up
          @$el.scrollTop(neededTop)
        else if currentBottom < neededBottom
          # scroll down
          @$el.scrollTop(neededBottom - height) # neededBottom >= height

        $li.addClass('cursor')


    _onMousedownCancelSelect: (e) ->
      e.preventDefault() if e.ctrlKey || e.metaKey || e.shiftKey

    _onMouseenterThumbnail: (ev) ->
      document.body.removeChild(@hoverThumbnail) if @hoverThumbnail

      img = @hoverThumbnail = document.createElement('img')
      img.id = 'document-list-thumbnail-tooltip'
      document.body.appendChild(img)

      anchor = ev.target.getBoundingClientRect()
      img.style.top = anchor.top + 'px'
      img.style.right = (window.innerWidth - anchor.left) + 'px'

      img.onload = ->
        pos1 = img.getBoundingClientRect()
        if pos1.bottom > window.innerHeight - 10
          img.style.top = (window.innerHeight - 10 - pos1.height) + 'px'
        img.style.opacity = 1

      img.src = ev.target.src

    _onMouseleaveThumbnail: (ev) ->
      document.body.removeChild(@hoverThumbnail) if @hoverThumbnail
      @hoverThumbnail = null

    _onClickDocument: (e) ->
      e.preventDefault()
      $document = $(e.target).closest('.document[data-cid]')
      document = @collection.get($document.attr('data-cid'))
      index = $document.prevAll().length
      @trigger('click-document', document, index, {
        meta: e.ctrlKey || e.metaKey || false
        shift: e.shiftKey || false
      })
