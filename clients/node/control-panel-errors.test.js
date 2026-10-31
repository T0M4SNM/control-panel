'use strict'

// Tests del cliente con el runner de Node (node --test), contra un servidor HTTP local.
const { test, beforeEach, after } = require('node:test')
const assert = require('node:assert/strict')
const http = require('node:http')
const panel = require('./control-panel-errors')

const received = []
let status = 202

const server = http.createServer((req, res) => {
  let body = ''
  req.on('data', (chunk) => (body += chunk))
  req.on('end', () => {
    received.push({ url: req.url, auth: req.headers.authorization, body: JSON.parse(body) })
    res.writeHead(status).end('{}')
  })
})

const ready = new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
const endpoint = () => `http://127.0.0.1:${server.address().port}/`

beforeEach(async () => {
  await ready
  received.length = 0
  status = 202
  panel._reset()
  panel.init({ endpoint: endpoint(), key: 'cpk_test', environment: 'test', release: '1.0.0', captureUncaught: false })
})

after(() => server.close())

test('envía el error con tipo, pila, contexto y la clave', async () => {
  panel.captureException(new TypeError('Cannot read id'), { url: '/api/stock/7', method: 'GET', userId: 42 })
  await panel.flush()

  assert.equal(received.length, 1)
  const { url, auth, body } = received[0]
  assert.equal(url, '/api/ingest/errors')
  assert.equal(auth, 'Bearer cpk_test')
  const [error] = body.errors
  assert.equal(error.type, 'TypeError')
  assert.equal(error.message, 'Cannot read id')
  assert.match(error.stack, /control-panel-errors\.test\.js/)
  assert.equal(error.environment, 'test')
  assert.equal(error.release, '1.0.0')
  assert.deepEqual(error.context, { url: '/api/stock/7', method: 'GET', userId: '42' })
})

test('agrupa varios errores en un solo envío', async () => {
  panel.captureException(new Error('uno'))
  panel.captureException('dos')
  await panel.flush()

  assert.equal(received.length, 1)
  assert.deepEqual(received[0].body.errors.map((e) => e.message), ['uno', 'dos'])
})

test('el middleware de Express envía URL y método y pasa el error', async () => {
  const handler = panel.expressErrorHandler()
  let passed
  handler(new Error('fallo en ruta'), { originalUrl: '/pedidos/9', method: 'POST' }, {}, (err) => (passed = err))
  await panel.flush()

  assert.equal(passed.message, 'fallo en ruta')
  assert.deepEqual(received[0].body.errors[0].context, { url: '/pedidos/9', method: 'POST' })
})

test('si el panel falla, no lanza nada', async () => {
  status = 500
  panel.captureException(new Error('x'))
  await assert.doesNotReject(panel.flush())
})

test('sin init no hace nada', async () => {
  panel._reset()
  panel.captureException(new Error('x'))
  await panel.flush()
  assert.equal(received.length, 0)
})
