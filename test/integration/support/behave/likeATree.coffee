testMethods = require('../testMethods')
wd = require('wd')

module.exports = (opts) ->
  testMethods.usingPromiseChainMethods
    waitForDocumentListToLoad: ->
      # It's loaded when "#document-list-title.loading" changes to
      # "#document-list:not(.loading)"
      @waitForElementByCss('#document-list:not(.loading)', 5000)

  before ->
    @likeATree = {}
    @userBrowser
      .url()
      .then((url) => @likeATree.url = url)

  beforeEach ->
    # Pick up new promise chain methods
    @userBrowser = @userBrowser.noop()

  afterEach ->
    @userBrowser
      .elementByCss('#tree-app-tree button.refresh').click()

  it 'should show a document list title', ->
    @userBrowser
      .waitForDocumentListToLoad()
      .elementById('document-list-title').text().should.eventually.match(/\d+ document/)

  opts.documents?.forEach (document) ->
    it "should show a #{document.type} document with title #{document.title}", ->
      extra = =>
        switch document.type
          when 'text'
            @userBrowser
              .waitForElementBy({ tag: 'pre', contains: document.contains }, 10000).should.eventually.exist
          when 'pdf'
            @userBrowser
              .waitForElementBy({ tag: 'object', type: 'application/pdf'}, 10000).should.eventually.exists

      @userBrowser
        .waitForDocumentListToLoad()
        .elementBy(tag: 'h3', contains: document.title).should.eventually.exist
        .elementBy(tag: 'h3', contains: document.title).click()
        .waitForElementBy(tag: 'h2', contains: document.title, visible: true) # animate
        .elementBy(tag: 'div', class: 'keywords', contains: 'Key words:').should.eventually.exist
        .then(extra)

  opts.searches?.forEach (search) ->
    it "should search for #{search.query}", ->
      @userBrowser
        .elementByCss('#document-list-params .search input[name=query]').type(search.query)
        .listenForJqueryAjaxComplete()
        .elementByCss('#document-list-params .search button').click()
        .waitForJqueryAjaxComplete() # wait for UI to clear previous search results
        .waitForElementBy({ tag: 'h3', contains: "#{search.nResults} document" }, 20000).should.eventually.exist

  opts.ignoredWords?.forEach (word) ->
    it "should show ignored word #{word}", ->
      @userBrowser
        .elementByCss("li.active a .toggle-popover").click()
        .elementBy(tag: 'dd', contains: word).should.eventually.exist
        .elementByCss("li.active a .toggle-popover").click()

  opts.importantWords?.forEach (word) ->
    it "should show important word #{word}", ->
      @userBrowser
        .elementByCss("li.active a .toggle-popover").click()
        .elementBy(tag: 'dd', contains: word).should.eventually.exist
        .elementByCss("li.active a .toggle-popover").click()
