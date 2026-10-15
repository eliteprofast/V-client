'use strict';
const { buildConfig } = require('./config');
const { createApp } = require('./app');

const config = buildConfig();
const app = createApp(config);

const server = app.listen(config.port, () => {
  console.log(`[V-Client] API + website listening on http://localhost:${config.port}`);
  console.log(`[V-Client] Admin panel: http://localhost:${config.port}/admin/`);
  console.log(`[V-Client] Data directory: ${config.dataDir}`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    console.log(`[V-Client] ${signal} received, shutting down`);
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 5000).unref();
  });
}
