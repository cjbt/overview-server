define [
  'jquery'
  'underscore'
  'backbone'
  '../views/TagList'
  'i18n'
], ($, _, Backbone, TagListView, i18n) ->
  t = i18n.namespaced('views.Tree.show.tag_list')

  template = _.template("""
    <div class="modal fade">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
            <h4 class="modal-title"><%- t('header') %></h3>
          </div>
          <div class="modal-body"></div>
          <div class="modal-footer">
            <a href="#" class="btn" data-dismiss="modal">Close</a>
          </div>
        </div>
      </div>
    </div>
  """)

  # Opens a dialog showing the tags in the tag store.
  #
  # This dialog allows edits. It will be closed when the user clicks "close".
  #
  # Usage:
  #
  #   new TagDialogController(tags: tags, state: state)
  #
  # Test usage:
  #
  #   controller = new TagDialogController
  #     tags: mockTags
  #     view: new Backbone.View
  #     state: new Backbone.Model
  class TagDialogController
    constructor: (@options) ->
      throw 'Must set options.tags, a Tags' if !@options.tags?
      throw 'Must set options.state, a State' if !@options.state?

      @tags = @options.tags
      @state = @options.state

      @_createListView()
      @_attachListView()

    _createListView: ->
      # Create a view, unless one was supplied in the constructor (for testing)
      @listView = if @options.view?
        @options.view
      else
        new TagListView
          collection: @tags
          exportUrl: "#{@tags.url}.csv" # TODO routing in JS

    _attachListView: ->
      @listenTo @listView, 'add', (attrs) =>
        tag = @tags.create(attrs)

      @listenTo @listView, 'update', (tag, attrs) ->
        tag.save(attrs)

      state = @state

      @listenTo @listView, 'remove', (tag) ->
        if tag.id in (state.get('documentList')?.params?.tags?.ids || [])
          state.refineDocumentListParams(tags: null)
        tag.destroy()

      @$dialog = $dialog = $(template({ t: t }))
      $dialog.find('.modal-body').append(@listView.el)

      $dialog
        .appendTo('body')
        .modal()
        .on 'hidden.bs.modal', =>
          @listView.remove()
          @listView = undefined
          $dialog.remove()
          @$dialog = undefined
          @stopListening()

      Backbone.ajax
        url: @tags.url
        success: (tags) =>
          counts = _.indexBy(tags, 'id')
          @listView.renderCounts(counts)

    _.extend(@::, Backbone.Events)
