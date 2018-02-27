define [
  'jquery'
  'backbone'
  'apps/Show/controllers/ViewAppController'
], ($, Backbone, ViewAppController) ->
  class MockDocumentSet extends Backbone.Model

  class MockState extends Backbone.Model

  class MockView extends Backbone.Model

  class MockDocumentList extends Backbone.Model
    initialize: (params, attributes) ->
      Object.assign(@, params)
      @documents = []
      @set(attributes)

  describe 'apps/Show/controllers/ViewAppController', ->
    beforeEach ->
      @tags = 'tags'

      @jobView = new MockView(type: 'job')
      @treeView = new MockView(type: 'tree')

      @documentSet = new MockDocumentSet()
      @documentSet.tags = @tags

      @globalActions =
        openMetadataSchemaEditor: sinon.spy()

      @aDocument = new Backbone.Model({ id: 123, title: 'aDocument' })
      @anotherDocument = new Backbone.Model({ id: 124, title: 'anotherDocument' })

      @state = new MockState
        documentList: new MockDocumentList({ params: 'documentListParams' })
        document: @aDocument
        view: @jobView
      @state.documentSet = @documentSet
      @state.setViewFilterSelection = sinon.spy()

      @transactionQueue = 'transactionQueue'

      @jobViewApp =
        onDocumentChanged: sinon.spy() # hack! testing the implementation uses ViewAppClient
        remove: sinon.spy()

      @treeViewApp =
        onDocumentChanged: sinon.spy()
        remove: sinon.spy()

      @viewAppConstructors =
        job: sinon.stub().returns(@jobViewApp)
        tree: sinon.stub().returns(@treeViewApp)

      @el = document.createElement('div')
      @main = document.createElement('main')

      @keyboardController = {}

      @init = =>
        @subject = new ViewAppController
          state: @state
          main: @main
          globalActions: @globalActions
          transactionQueue: @transactionQueue
          keyboardController: @keyboardController
          viewAppConstructors: @viewAppConstructors
          el: @el

    afterEach ->
      @subject?.stopListening()

    it 'should give each ViewApp a new el', ->
      @state.set(view: @jobView)
      @init()
      args1 = @viewAppConstructors.job.lastCall.args[0]
      expect(args1.el.parentNode).to.eq(@el)
      @state.set(view: @treeView)
      args2 = @viewAppConstructors.tree.lastCall.args[0]
      expect(args2.el).not.to.eq(args1.el)
      expect(args2.el.parentNode).to.eq(@el)

    describe 'starting with a null view', ->
      it 'should not crash', ->
        @state.set(view: null)
        expect(=> @init()).not.to.throw()

    describe 'starting with a job view', ->
      beforeEach ->
        @state.set(view: @jobView)
        @init()

      it 'should construct a viewApp', -> expect(@viewAppConstructors.job).to.have.been.called
      it 'should set state.viewApp', -> expect(@state.get('viewApp')).to.eq(@jobViewApp)
      it 'should pass view to the viewApp', -> expect(@viewAppConstructors.job.args[0][0].view).to.eq(@jobView)

      # TODO figure out which of these features are necessary.
      # [adam, 2017-11-17] We were passing `document` and `documentList`, which
      # clearly were _not_ necessary since we already pass `state`. I haven't
      # audited any further.
      it 'should pass state variables to the viewApp', ->
        expect(@viewAppConstructors.job).to.have.been.calledWithMatch
          documentSetId: @state.documentSetId

      it 'should pass transactionQueue to the viewApp', ->
        expect(@viewAppConstructors.job).to.have.been.calledWithMatch
          transactionQueue: @transactionQueue

      it 'should pass keyboardController to the viewApp', ->
        expect(@viewAppConstructors.job).to.have.been.calledWithMatch
          keyboardController: @keyboardController

      it 'should pass a State to the viewApp', ->
        # These parameters won't work across iframes. We should deprecate them.
        options = @viewAppConstructors.job.lastCall.args[0]
        expect(options.state).to.eq(@state)

      it 'should pass main to the viewApp', ->
        expect(@viewAppConstructors.job).to.have.been.calledWithMatch
          main: @main

      it 'should use ViewAppClient to notify the viewApp of changes', ->
        document = new Backbone.Model(foo: 'document2')
        @state.set(document: @anotherDocument)
        expect(@jobViewApp.onDocumentChanged).to.have.been.calledWithMatch({
          id: 124,
          title: 'anotherDocument',
        })

      describe 'when a new View is set', ->
        beforeEach -> @state.set(view: @treeView)

        it 'should call .remove() on the old viewApp', -> expect(@jobViewApp.remove).to.have.been.called
        it 'should construct the new viewApp', -> expect(@viewAppConstructors.tree).to.have.been.called
        it 'should set state.viewApp', -> expect(@state.get('viewApp')).to.eq(@treeViewApp)

        it 'should use ViewAppClient to notify the new viewApp of changes', ->
          document = new Backbone.Model(foo: 'document2')
          @state.set(document: @anotherDocument)
          expect(@treeViewApp.onDocumentChanged).to.have.been.calledWithMatch({
            id: 124,
            title: 'anotherDocument',
          })

        it 'should stop notifying the original ViewAppClient of changes', ->
          document = new Backbone.Model(foo: 'document2')
          @state.set(document: @anotherDocument)
          expect(@jobViewApp.onDocumentChanged).not.to.have.been.called

      describe 'when the View changes type', ->
        # This should do the same stuff as "when a new View is set"
        beforeEach -> @jobView.set(type: 'tree')

        it 'should call .remove() on the old viewApp', -> expect(@jobViewApp.remove).to.have.been.called
        it 'should construct the new viewApp', -> expect(@viewAppConstructors.tree).to.have.been.called
        it 'should set state.viewApp', -> expect(@state.get('viewApp')).to.eq(@treeViewApp)

      describe 'when the view changes to null', ->
        beforeEach -> @state.set(view: null)

        it 'should call .remove() on the old viewApp', -> expect(@jobViewApp.remove).to.have.been.called
        it 'should set state.viewApp to null', -> expect(@state.get('viewApp')).to.be.null
