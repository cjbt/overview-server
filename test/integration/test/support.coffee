# This file ought to be in support/testSuite.coffee, but Mocha shuns it there
child_process = require('child_process')
chromedriver = require('chromedriver')
net = require('net')

# Tries to connect() to a given port on localhost. Calls done() (and drops the
# connection) when the connect() succeeds. Retries otherwise.
waitForConnect = (port, done) ->
  retry = -> waitForConnect(port, done)

  socket = new net.Socket()
  socket.on('error', -> setTimeout(retry, 50))
  socket.on('connect', -> socket.end('Hello there\n'); done())
  socket.connect(port)
  undefined

# Runs child process for selenium-server-standalone
#
# Calls the callback with the child process once Selenium starts listening
# on port 4444.
startSelenium = (cb) ->
  child = child_process.spawn(
    chromedriver.path,
    [
      '--port=4444',
      #'--verbose',
    ],
  )

  child.stdout.pipe(process.stdout)
  child.stderr.pipe(process.stderr)

  done = -> process.nextTick -> cb(child)

  waitForConnect(4444, done)

before (done) ->
  if 'SAUCE_USERNAME' of process.env
    process.nextTick(done)
  else
    startSelenium (selenium) ->
      process.on('exit', -> selenium.kill())
      process.nextTick(done)
