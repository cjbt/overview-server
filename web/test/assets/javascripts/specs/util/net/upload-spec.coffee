define [
  'util/net/upload'
  'md5'
], (Upload, md5) ->
  describe 'util/net/Upload', ->
    upload = undefined

    makeUUID = (inputString) ->
      hash = md5(inputString).toString()
      parts = []
      parts.push(hash[0...8])
      parts.push(hash[8...12])
      parts.push('3' + hash[13...16])
      y = (parseInt(hash[16...18], 16) & 0x3f | 0x80).toString(16)
      parts.push(y + hash[18...20])
      parts.push(hash[20...32])
      parts.join('-')

    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @fakeFile =
        name: 'FOO bar "baz".pdf' # filename with spaces and quotes
        size: 1000
        lastModifiedDate:
          toString: sinon.stub().returns('last-modified-date')
        slice: sinon.stub().returns('a file blob')

      @mostRecentContentDisposition = =>
        request = @sandbox.server.requests[@sandbox.server.requests.length - 1]
        request.requestHeaders['Content-Disposition']

    afterEach ->
      @sandbox.restore()

    # TODO: test stop, abort, etc.

    describe 'starting an upload with a unicode filename', ->
      beforeEach ->
        @fakeStartUploadWithFilename = (filename) =>
          # If the filename isn't an HTTP "token", we UTF-8-escape it
          @fakeFile.name = filename
          upload = new Upload(@fakeFile, '/upload/')
          upload.start()
          @sandbox.server.requests[@sandbox.server.requests.length - 1].respond([ 404, {}, '' ]) # not found, go ahead and upload

      it 'starts the upload, and properly unicode-escapes the filename', ->
        @fakeStartUploadWithFilename('元気なですか？.pdf') # filename with unicode

        expect(upload.state).to.eq(3)
        expect(@sandbox.server.requests[1].method).to.eq('POST')
        expect(@mostRecentContentDisposition()).to.eq("attachment; filename*=UTF-8''%E5%85%83%E6%B0%97%E3%81%AA%E3%81%A7%E3%81%99%E3%81%8B%EF%BC%9F.pdf")

      it 'escapes an even slightly not-HTTP-friendly filename', ->
        @fakeStartUploadWithFilename('file,name.txt')
        expect(@mostRecentContentDisposition()).to.eq("attachment; filename*=UTF-8''file%2Cname.txt")

      it 'escapes letters encodeURIComponent does not', ->
        @fakeStartUploadWithFilename("file'name.txt")
        expect(@mostRecentContentDisposition()).to.eq("attachment; filename*=UTF-8''file%27name.txt")

      it 'escapes the asterix', ->
        @fakeStartUploadWithFilename("file*name.txt")
        expect(@mostRecentContentDisposition()).to.eq("attachment; filename*=UTF-8''file%2Aname.txt")

      it 'keeps capitals', ->
        # https://www.pivotaltracker.com/story/show/71501834
        @fakeStartUploadWithFilename("FILE*name.txt")
        expect(@mostRecentContentDisposition()).to.eq("attachment; filename*=UTF-8''FILE%2Aname.txt")

      it 'does not escape the pipe, caret or backtick', ->
        @fakeStartUploadWithFilename("file*|^`name.txt") # trigger encoding
        expect(@mostRecentContentDisposition()).to.eq("attachment; filename*=UTF-8''file%2A|^`name.txt")

      it 'simply quotes complex, no-escaping-necessary characters', ->
        @fakeStartUploadWithFilename("file|^`name.txt")
        expect(@mostRecentContentDisposition()).to.eq('attachment; filename="file|^`name.txt"')

    describe 'starting an upload', ->
      beforeEach ->
        upload = new Upload(@fakeFile, '/upload/')
        upload.start()

      it 'moves into the starting state', ->
        expect(upload.state).to.eq(2)

      it 'sets the url from the stub that was passed in', ->
        expect(@sandbox.server.requests[0].url).to.contain('/upload/')

      it 'computes a correct guid for the file', ->
        expect(@sandbox.server.requests[0].url).to.contain(makeUUID('FOO bar "baz".pdf::last-modified-date::1000'))

      it 'attempts to find the file before uploading', ->
        expect(@sandbox.server.requests[0].method).to.eq('HEAD')

      describe 'when the file is not present on the server yet', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(404, {}, '') # not found, go ahead and upload

        it 'starts the upload', ->
          expect(upload.state).to.eq(3)
          expect(@sandbox.server.requests[1].method).to.eq('POST')

        it 'correctly specifies the content-range', ->
          expect(@sandbox.server.requests[1].requestHeaders['Content-Range']).to.eq('bytes 0-999/1000')

      describe 'when the server has 0 bytes of the file uploaded', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(204, { 'Content-Type': 'application/json' }, '') # no content-range header

        it 'should start the upload', ->
          expect(upload.state).to.eq(3)
          expect(@sandbox.server.requests[1].method).to.eq('POST')

        it 'should specify the correct content-range', ->
          expect(@sandbox.server.requests[1].requestHeaders['Content-Range']).to.eq('bytes 0-999/1000')

      describe 'when part of the file has been uploaded already', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(204, { 'Content-Type': 'application/json', 'Content-Range': 'bytes 0-499/1000' }, '')

        it 'should start the upload', ->
          expect(upload.state).to.eq(3)
          expect(@sandbox.server.requests[1].method).to.eq('POST')

        it 'correctly specifies the content-range', ->
          expect(@sandbox.server.requests[1].requestHeaders['Content-Range']).to.eq('bytes 500-999/1000')

    describe 'with a zero-length file', ->
      beforeEach ->
        @fakeFile.size = 0
        upload = new Upload(@fakeFile, '/upload/')
        upload.start()

      describe 'when the server does not have the file', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(404, {}, '') # not found, go ahead and upload
          # Now the client starts uploading

        it 'has no content-range', ->
          expect(@sandbox.server.requests[1].requestHeaders['Content-Range']).to.be.undefined

      describe 'when server has the file', ->
        beforeEach ->
          @sandbox.server.requests[0].respond(204, {}, '')

        it 'moves to the done state', -> expect(upload.state).to.eq(4)
        it 'does not request any more', -> expect(@sandbox.server.requests[1]).to.be.undefined

    describe 'with a webkitRelativePath', ->
      beforeEach ->
        @fakeFile.webkitRelativePath = 'foo/bar.txt'
        upload = new Upload(@fakeFile, '/upload/')
        upload.start()

      it 'computes a guid based on the relative path', ->
        expect(@sandbox.server.requests[0].url).to.contain(makeUUID('foo/bar.txt::last-modified-date::1000'))

      it 'sends the correct filename', ->
        @sandbox.server.requests[@sandbox.server.requests.length - 1].respond([ 404, {}, '' ]) # not found, go ahead and upload
        expect(@mostRecentContentDisposition()).to.eq("attachment; filename*=UTF-8''foo%2Fbar.txt")
