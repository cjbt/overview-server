Backbone = require('backbone')
QueryView = require('apps/DocumentCloudImportForm/views/DocumentCloudQuery')
i18n = require('i18n')

describe 'apps/DocumentCloudImportForm/views/DocumentCloudQuery', ->
  model = undefined
  view = undefined

  beforeEach ->
    model = new Backbone.Model({
      query: new Backbone.Model({
        id: 123
        title: ''
        description: null
        document_count: 0
      }),
      credentials: undefined
      status: 'unknown'
    })
    i18n.reset_messages({
      'views.DocumentCloudImportJob.new.query.preamble': 'preamble'
      'views.DocumentCloudImportJob.new.query.document_count': 'document_count,{0}'
    })
    view = new QueryView({ model: model })

  it 'should not render anything when status is not fetched', ->
    model.set('status', 'unknown')
    expect(view.$el.html()).to.eq('')
    model.set('status', 'fetching')
    expect(view.$el.html()).to.eq('')
    model.set('status', 'error')
    expect(view.$el.html()).to.eq('')

  it 'should render the title', ->
    model.get('query').set('title', 'title')
    model.set('status', 'fetched')
    expect(view.$('h3').text()).to.eq('title')

  it 'should render the description', ->
    model.get('query').set('description', 'description')
    model.set('status', 'fetched')
    expect(view.$('.description').text()).to.eq('description')

  it 'should not render an empty description', ->
    model.get('query').set('description', null)
    model.set('status', 'fetched')
    expect(view.$('.description').length).to.eq(0)

  it 'should render the number of documents', ->
    model.get('query').set('document_count', 1234)
    model.set('status', 'fetched')
    expect(view.$('.document-count').text()).to.contain('document_count,1234')
