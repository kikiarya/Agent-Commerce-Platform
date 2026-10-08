require('dotenv').config();

const path = require('path');
const express = require('express');
const cors = require('cors');
const { getDb, DB_PATH } = require('./db');
const { connectRedis, isRedisReady, closeRedis } = require('./redis');
const { startOrderWorker, startTimeoutScanner, stopOrderWorker } = require('./services/orderWorker');
const { warmAllActiveDeals } = require('./services/seckillService');
const { resolveLlmConfig, listProviders } = require('./services/llmConfig');

const authRouter = require('./routes/auth');
const phonesRouter = require('./routes/phones');
const productsRouter = require('./routes/products');
const ordersRouter = require('./routes/orders');
const checkoutRouter = require('./routes/checkout');
const seckillRouter = require('./routes/seckill');
const adminRouter = require('./routes/admin');
const chatRouter = require('./routes/chat');
const trade = require('./services/tradeCoreClient');
const { ensureUserMapSchema } = require('./services/userMap');

const PORT = Number(process.env.PORT) || 3000;
const app = express();

app.use(cors());
app.use(express.json({ limit: '1mb' }));

getDb(); // Initialize schema only. Demo imports require the explicit seed command.
ensureUserMapSchema(getDb());

app.get('/api/health', (_req, res) => {
  const db = getDb();
  const phones = db.prepare('SELECT COUNT(*) AS c FROM phones').get().c;
  const users = db.prepare('SELECT COUNT(*) AS c FROM users').get().c;
  const llm = resolveLlmConfig();
  res.json({
    status: 'ok',
    service: 'AgentCommerceStorefront',
    legacyName: 'OldPhoneStore',
    database: path.resolve(DB_PATH),
    redis: isRedisReady(),
    catalog_items: phones,
    phones,
    users,
    trade_core: {
      enabled: trade.useTradeCore(),
      base: trade.BASE
    },
    llm: {
      enabled: llm.enabled,
      provider: llm.provider,
      label: llm.label,
      model: llm.model,
      providers: listProviders()
    },
    features: [
      'jwt-auth',
      'redis-cache',
      'flash-seckill',
      'multi-category-catalog',
      'trade-core-checkout-bff',
      'ai-customer-service'
    ]
  });
});

app.use('/api/auth', authRouter);
app.use('/api/phones', phonesRouter);
app.use('/api/products', productsRouter);
app.use('/api/orders', ordersRouter);
app.use('/api/checkout', checkoutRouter);
app.use('/api/flash', seckillRouter);
app.use('/api/admin', adminRouter);
app.use('/api/chat', chatRouter);

app.use(express.static(path.join(__dirname, '..', 'public')));

app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api/')) return next();
  if (req.path.startsWith('/admin')) {
    return res.sendFile(path.join(__dirname, '..', 'public', 'admin.html'));
  }
  res.sendFile(path.join(__dirname, '..', 'public', 'index.html'));
});

app.use((err, _req, res, _next) => {
  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    return res.status(400).json({ error: 'Invalid JSON body' });
  }
  console.error(err);
  res.status(500).json({ error: 'Internal server error' });
});

async function boot() {
  await connectRedis();
  if (isRedisReady()) {
    await warmAllActiveDeals();
  }
  await startOrderWorker();
  startTimeoutScanner();

  const server = app.listen(PORT, () => {
    console.log(`OldPhoneStore running at http://localhost:${PORT}`);
    console.log(`SQLite: ${path.resolve(DB_PATH)}`);
    console.log(`Redis: ${isRedisReady() ? 'ready' : 'degraded (SQLite fallbacks active)'}`);
    console.log(`Admin UI: http://localhost:${PORT}/admin`);
  });

  const shutdown = async () => {
    stopOrderWorker();
    server.close();
    await closeRedis();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

boot().catch((err) => {
  console.error('Failed to boot', err);
  process.exit(1);
});
