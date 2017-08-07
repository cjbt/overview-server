define [ 'i18n' ], (i18n) ->
  describe 'i18n', ->
    messages = {
      'messages.simple': 'a simple message',
      'messages.single-quote': "doesn''t",
      'messages.quoted-string': "a message with 'quotes'",
      'messages.quoted-brace': "a message with a '{0}' literal",
      'messages.interpolation.string': 'a message with {0} as string',
      'messages.interpolation.integer': 'a message with {0,number,integer} as integer',
      'messages.interpolation.float': 'a message with {0,number,0.00} as float', # we only support 0.00 format now
      'messages.interpolation.ordering': 'a message with {1} coming before {0}',
      'messages.choice.simple': 'choice of {0,choice,-1#negative values|0#zero (or fraction) values|1#one value|1<{0,number,integer} values}',
      'messages.choice.nested': 'choice of {0,choice,0#zero|0<{1,choice,0#one then zero|0<all non-zero}} values',
      'messages.date.medium': 'foo {0,date,medium} bar'
      'messages.time.short': 'foo {0,time,short} bar'
    }

    tests = [
      [ 'simple', [], 'a simple message' ]
      [ 'single-quote', [], "doesn't" ]
      [ 'quoted-string', [], "a message with quotes" ]
      [ 'quoted-brace', ['not-seen'], 'a message with a {0} literal' ]
      [ 'interpolation.string', ['string'], 'a message with string as string' ]
      [ 'interpolation.integer', [5], 'a message with 5 as integer' ]
      [ 'interpolation.integer', [12345], 'a message with 12,345 as integer' ]
      [ 'interpolation.float', [4.126123], 'a message with 4.13 as float' ]
      [ 'interpolation.float', [1234.126123], 'a message with 1,234.13 as float' ]
      [ 'interpolation.ordering', ['1', '0'], 'a message with 0 coming before 1' ]
      [ 'choice.simple', [0], 'choice of zero (or fraction) values' ]
      [ 'choice.simple', [1], 'choice of one value' ]
      [ 'choice.simple', [4], 'choice of 4 values' ]
      [ 'choice.simple', [-1], 'choice of negative values' ]
      [ 'choice.nested', [0, 3], 'choice of zero values' ]
      [ 'choice.nested', [1, 0], 'choice of one then zero values' ]
      [ 'choice.nested', [1, 1], 'choice of all non-zero values' ]

      # FIXME date.medium and time.short are _not_ like their Java equivalents!
      [ 'date.medium', [new Date(2014, 1, 18)], 'foo 2014-02-18 bar' ]
      [ 'date.medium', [null], 'foo  bar' ]
      [ 'time.short', [new Date(2014, 1, 18, 13, 1, 24)], 'foo 13:01 bar' ]
      [ 'time.short', [null], 'foo  bar' ]
    ]

    make_test = (subkey, args, expected) =>
      key = "messages.#{subkey}"
      message = messages[key]
      it "should translate \"#{message}\" with #{JSON.stringify(args)} to \"#{expected}\"", ->
        i18n.reset_messages(messages)
        all_args = [ key ].concat(args)
        result = i18n.apply({}, all_args)
        expect(result).to.eq(expected)

    make_test(subkey, args, expected) for [ subkey, args, expected] in tests

    describe '.namespaced', ->
      it 'should translate within a namespace', ->
        i18n.reset_messages({ 'name.space.foo': 'bar' })
        t = i18n.namespaced('name.space')
        expect(t('foo')).to.eq('bar')

      it 'should pass arguments', ->
        i18n.reset_messages({ 'name.space.foo': 'bar {0} {1}' })
        t = i18n.namespaced('name.space')
        expect(t('foo', 1, 2)).to.eq('bar 1 2')
