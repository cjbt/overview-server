'use strict'

const asUser = require('../support/asUser')

class PdfNotesShortcuts {
  constructor(browser) {
    this.browser = browser
  }

  async createAnnotation() {
    const b = this.browser

    await b.inFrame('document-contents', async () => {
      await b.assertExists('#viewer .textLayer', { wait: 'pageLoad' }) // debugging: Jenkins is failing to find '#viewer .textLayer div'
      await b.assertExists('#viewer .textLayer div', { wait: 'pageLoad' })
      await b.waitUntilBlockReturnsTrue('notes code is loaded', 'pageLoad', function() {
        return document.querySelector('.noteLayer') !== null
      });

      // Often, even after the notes code is loaded, clicking button#addNote
      // doesn't accomplish anything. [adam, 2017-12-08] I suspect that's
      // because the _toolbar_ code isn't loaded yet, but I'm not going to delve
      // into pdfjs to be sure. So let's just assume it's on its way; there are
      // no HTTP requests remaining.
      await b.sleep(1000)

      await b.click('button#addNote')
      await b.assertExists({ css: '#viewerContainer.addingNote', wait: 'fast' }) // wait for it to listen to mouse events

      // [adamhooper, 2018-01-02] TODO does this sleep() call help? We were getting
      // errors with the `#viewer .noteLayer section` selector earlier: sometimes it
      // wasn't appearing. If this line makes those errors go away, that means there's
      // a race and we're failing to wait for something -- but I don't know what.
      await b.sleep(1000)

      const el = (await b.find({ css: '#viewer .textLayer div' })).driverElement
      await b.driver.actions()
        .mouseDown(el)
        .mouseMove({ x: 200, y: 100 })
        .mouseUp()
        .perform()

      await b.assertExists('#viewer .noteLayer section', { wait: true })
      await b.assertExists('.editNoteTool', { wait: true })
      await b.sendKeys('Hello, world!', '.editNoteTool textarea')
      await b.click('.editNoteTool button.editNoteSave')
      await b.assertExists('.editNoteTool button.editNoteSave[disabled]', { wait: true })
      await b.click('.editNoteTool button.editNoteClose')
    })
  }
}

// In order to let users analyze document text
// We need to let them highlight and take notes
describe('PdfNotes', function() {
  asUser.usingTemporaryUser(function() {
    beforeEach(async function() {
      const b = this.browser
      const s = this.browser.shortcuts
      b.loadShortcuts('documentSet')
      b.loadShortcuts('documentSets')
      b.loadShortcuts('importFiles')
      b.pdf = new PdfNotesShortcuts(b)

      await s.importFiles.open()
      await s.importFiles.addFiles([ 'PdfNotes/doc1.pdf', 'PdfNotes/doc2.pdf' ])
      await s.importFiles.finish({ name: 'annotations' })
      await s.documentSet.waitUntilStable()

      await b.shortcuts.documentSet.openDocumentFromList('doc1.pdf')
      await b.find('iframe#document-contents', { wait: 'fast' }) // wait for PDF to start loading
    })

    it('should let the user create an annotation', async function() {
      const b = this.browser

      await b.pdf.createAnnotation() // No error? Then it worked
    })

    it('should save and load annotations on the server', async function() {
      const b = this.browser

      await b.pdf.createAnnotation()

      // Reload the page
      await b.shortcuts.documentSets.open('annotations')
      await b.shortcuts.documentSet.openDocumentFromList('doc1.pdf')

      await b.find('iframe#document-contents', { wait: 'fast' })
      await b.inFrame('document-contents', async () => {
        await b.assertExists({ css: '#viewer .noteLayer section' }, { wait: 'pageLoad' })

        await b.click('#viewer .noteLayer section')
        let text = await b.getAttribute({ css: '.editNoteTool textarea', wait: true }, 'value')
        expect(text).to.eq('Hello, world!')
      })
    })

    it('should search for annotations', async function() {
      const b = this.browser

      await b.pdf.createAnnotation()
      await b.shortcuts.documentSet.goBackToDocumentList()

      await b.sendKeys('notes:Hello, world!', '#document-list-params .search input[name=query]')
      await b.click('#document-list-params .search button')

      const text = await(b.getText({ css: '#document-list ul.documents li', wait: 'pageLoad' }))
      expect(text).to.match(/doc1\.pdf/)
    })

    it('should delete annotations', async function() {
      const b = this.browser

      await b.pdf.createAnnotation()

      await b.inFrame('document-contents', async () => {
        await b.click('#viewer .noteLayer section')
        await b.click({ css: '.editNoteTool button.editNoteDelete', wait: true })
        await b.waitUntilFunctionReturnsTrue('.editNoteTool disappears', true, function() {
          return b.find('.editNoteTool', { throwOnNull: false }).then(x => x === null)
        })
        await b.assertNotExists('#viewer .noteLayer section')
      })
    })

    // We haven't tested deleting them from the server.
  })
})