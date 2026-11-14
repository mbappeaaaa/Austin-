import express from 'express'
import http from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const app = express()
const root = path.dirname(fileURLToPath(import.meta.url))
const backendTarget = new URL(process.env.BACKEND_URL || 'http://127.0.0.1:7778')
const port = Number(process.env.PORT || 3000)

app.use('/api', (req, res) => {
  const targetPath = req.originalUrl.replace(/^\/api(?=\/|$)/, '') || '/'
  const headers = { ...req.headers, host: backendTarget.host }
  delete headers.connection
  const proxyRequest = http.request({
    protocol: backendTarget.protocol,
    hostname: backendTarget.hostname,
    port: backendTarget.port,
    method: req.method,
    path: targetPath,
    headers,
  }, proxyResponse => {
    res.statusCode = proxyResponse.statusCode || 502
    Object.entries(proxyResponse.headers).forEach(([name, value]) => {
      if (value !== undefined) res.setHeader(name, value)
    })
    proxyResponse.pipe(res)
  })
  proxyRequest.setTimeout(5000, () => proxyRequest.destroy(new Error('Backend request timed out')))
  proxyRequest.on('error', () => {
    if (res.headersSent) return res.end()
    res.status(502).json({ status: '1', message: '后端服务未启动或无法连接' })
  })
  req.pipe(proxyRequest)
})

app.use(express.static(path.join(root, 'dist')))
app.get('/login.html', (_req, res) => res.redirect('/#/login'))
app.get(/.*/, (_req, res) => res.sendFile(path.join(root, 'dist', 'index.html')))

http.createServer(app).listen(port, () => {
  console.log(`Austin Notify Vue console listening on http://localhost:${port}`)
})
