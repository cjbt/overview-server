define [
  'jquery'
  'underscore'
  'backbone'
  '../models/ViewAppClient'
], ($, _, Backbone, ViewAppClient) ->
  class ViewAppController
    _.extend(@::, Backbone.Events)

    constructor: (options) ->
      throw 'Must pass options.el, an HTMLElement' if !options.el
      throw 'Must pass options.state, a State' if !options.state
      throw 'Must pass options.keyboardController, a KeyboardController' if !options.keyboardController
      throw 'Must pass options.transactionQueue, a TransactionQueue' if !options.transactionQueue
      throw 'Must pass options.viewAppConstructors, an Object mapping view type to an App' if !options.viewAppConstructors
      throw 'Must pass options.globalActions, an Object full of callbacks' if !options.globalActions

      @el = options.el
      @$el = $(@el)
      @state = options.state
      @keyboardController = options.keyboardController
      @transactionQueue = options.transactionQueue
      @viewAppConstructors = options.viewAppConstructors
      @globalActions = options.globalActions

      @_setView(@state.get('view'))
      @listenTo(@state, 'change:view', (__, view) => @_setView(view))

    _setView: (view) ->
      if @viewAppClient?
        @stopListening(@view)
        @viewAppClient.remove()
        @viewAppClient = null

      viewApp = null

      @$el.empty()

      if view?
        type = view.get('type')

        el = $('<div class="view"></div>').appendTo(@el)[0]

        viewApp = new @viewAppConstructors[type]
          documentSetId: @state.documentSetId
          view: view
          transactionQueue: @transactionQueue
          keyboardController: @keyboardController
          document: @state.attributes.document
          documentListParams: @state.attributes.documentList?.params
          el: el
          state: @state

        # EVIL HACK
        # DocumentListParams can include "nodes" and we need to resolve those
        # nodes in the Show app for now because Show and Tree are too
        # intertwined. So let's expose view.onDemandTree.
        view.onDemandTree = viewApp.onDemandTree if viewApp.onDemandTree?

        @viewAppClient = new ViewAppClient
          viewApp: viewApp
          globalActions: @globalActions
          state: @state

        @view = view
        # When changing from "job" to "tree", reset everything
        @listenTo(@view, 'change:type', @_setView)

      @state.set('viewApp', viewApp)
