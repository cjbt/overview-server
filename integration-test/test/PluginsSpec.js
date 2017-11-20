'use strict'

const asUserWithDocumentSet = require('../support/asUserWithDocumentSet')

describe('Plugins', function() {
  asUserWithDocumentSet('Metadata/basic.csv', function() {
    before(async function() {
      this.browser.loadShortcuts('documentSet')
      this.documentSet = this.browser.shortcuts.documentSet
    })

    it('should pass server, apiToken and documentSetId in the plugin query string', async function() {
      const server = await this.documentSet.createViewAndServer('show-query-string')

      try {
        await this.browser.switchToFrame('view-app-iframe')
        // Wait for load. This plugin is loaded when the <pre> is non-empty
        await this.browser.assertExists({ xpath: '//pre[text() and string-length()>0]', wait: true })
        const text = await this.browser.getText({ css: 'pre' })
        expect(text).to.match(/^\?server=http%3A%2F%2Flocalhost%3A9000&documentSetId=\d+&apiToken=[a-z0-9]+$/)
        await this.browser.switchToFrame(null)
      } finally {
        await server.close()
      }

      await this.documentSet.destroyView('show-query-string')
    })

    describe('with a plugin that calls setRightPane', async function() {
      before(async function() {
        this.server = await this.documentSet.createViewAndServer('right-pane')
      })

      after(async function() {
        await this.server.close()
        await this.documentSet.destroyView('right-pane')
      })

      it('should create a right pane', async function() {
        await this.browser.assertNotExists({ id: 'tree-app-vertical-split-2' }) // it's invisible

        // wait for load
        await this.browser.switchToFrame('view-app-iframe')
        await this.browser.assertExists({ css: 'body.loaded', wait: 'slow' })
        await this.browser.click({ button: 'Set Right Pane' })
        await this.browser.switchToFrame(null)

        await this.browser.assertExists({ id: 'tree-app-vertical-split-2', wait: true }) // wait for animation
        await this.browser.click({ css: '#tree-app-vertical-split-2 button' })
        await browser.sleep(1000) // for animation
        await this.browser.assertExists({ id: 'view-app-right-pane-iframe' })
        await this.browser.switchToFrame('view-app-right-pane-iframe')
        const url = await this.browser.execute(function() { return window.location.href })
        expect(url).to.eq('http://localhost:3333/show?placement=right-pane')
        await this.browser.switchToFrame(null)

        // Move back to left
        await this.browser.click({ css: '#tree-app-vertical-split button' })
        await browser.sleep(1000) // for animation
      })
    })

    describe('with a plugin that calls setModalDialog', async function() {
      before(async function() {
        this.server = await this.documentSet.createViewAndServer('modal-dialog')
      })

      after(async function() {
        await this.server.close()
        await this.documentSet.destroyView('modal-dialog')
      })

      it('should create and close a modal dialog', async function() {
        const b = this.browser

        await b.assertNotExists({ id: 'view-app-modal-dialog' })

        // wait for load
        await b.switchToFrame('view-app-iframe')
        await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
        await b.click({ button: 'Set Modal Dialog' })
        await b.switchToFrame(null)

        await b.assertExists({ id: 'view-app-modal-dialog', wait: true })

        await b.switchToFrame('view-app-modal-dialog-iframe')
        await b.click({ button: 'Set Modal Dialog to Null', wait: 'pageLoad' })
        await b.switchToFrame(null)

        await b.assertNotExists({ id: 'view-app-modal-dialog' })
      })

      it('should send messages from one plugin to another', async function() {
        const b = this.browser

        // wait for load
        await b.switchToFrame('view-app-iframe')
        await b.assertExists({ css: 'body.loaded', wait: 'pageLoad' })
        await b.click({ button: 'Set Modal Dialog' })
        await b.switchToFrame(null)

        await b.assertExists({ id: 'view-app-modal-dialog', wait: true })

        await b.switchToFrame('view-app-modal-dialog-iframe')
        await b.click({ button: 'Send Message', wait: 'pageLoad' })
        await b.click({ button: 'Set Modal Dialog to Null' })
        await b.switchToFrame(null)

        await b.switchToFrame('view-app-iframe')
        await b.assertExists({ tag: 'pre', contains: '{"This is":"a message"}', wait: true })
        await b.switchToFrame(null)

        await b.assertNotExists({ id: 'view-app-modal-dialog' })
      })
    })
  })
})