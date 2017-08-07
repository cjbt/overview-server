define [
  'apps/ImportOptions/models/Options'
], (Options) ->
  describe 'apps/ImportOptions/models/Options', ->
    subject = undefined

    it 'should throw when trying to include a non-option', ->
      expect(->
        new Options {},
          supportedLanguages: [{code:'en',name:'English'}]
          defaultLanguageCode: 'en'
          onlyOptions: [ 'splitdocuments' ]
      ).to.throw()

    it 'should add a `documentSet` from initialize()', ->
      documentSet = new Backbone.Model(id: 1)
      subject = new Options {},
        defaultLanguageCode: 'en'
        supportedLanguages: [{code:'en',name:'English'}]
        onlyOptions: [ 'metadata_json' ]
        documentSet: documentSet
      expect(subject.documentSet).to.eq(documentSet)

    describe 'with onlyOptions', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'
        onlyOptions: [ 'split_documents' ]

      beforeEach -> subject = new Options({}, ctorOptions)
      it 'should define an included option', -> expect(subject.has('split_documents')).to.be.true
      it 'should not define an excluded option', -> expect(subject.has('lang')).to.be.false

    describe 'with all options', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'
        onlyOptions: [ 'name', 'split_documents', 'ocr', 'lang', 'metadata_json' ]

      beforeEach ->
        subject = new Options({}, ctorOptions)

      describe 'supportedLanguages', ->
        it 'should be the list of supported languages', -> expect(subject.supportedLanguages).to.eq(ctorOptions.supportedLanguages)

      describe 'name', ->
        it 'should begin empty', -> expect(subject.get('name')).to.eq('')

      describe 'split_documents', ->
        it 'should begin false', -> expect(subject.get('split_documents')).to.be.false

      describe 'ocr', ->
        it 'should begin true', -> expect(subject.get('ocr')).to.be.true

      describe 'lang', ->
        it 'should begin as defaultLanguageCode', -> expect(subject.get('lang')).to.eq('en')

      describe 'metadata_json', ->
        it 'should begin as empty', -> expect(subject.get('metadata_json')).to.eq('{}')
