import { existsSync } from 'node:fs';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';

const entrypoint = resolve('.output/server/index.mjs');

if (!existsSync(entrypoint)) {
  console.error('Production output is missing. Run "pnpm build" before "pnpm start".');
  process.exitCode = 1;
} else {
  const child = spawn(process.execPath, [entrypoint], {
    stdio: 'inherit',
    env: {
      ...process.env,
      NODE_ENV: 'production',
      HOST: process.env.HOST ?? '127.0.0.1',
      PORT: process.env.PORT ?? '3000',
      NITRO_HOST: process.env.NITRO_HOST ?? process.env.HOST ?? '127.0.0.1',
      NITRO_PORT: process.env.NITRO_PORT ?? process.env.PORT ?? '3000',
    },
    windowsHide: false,
  });

  const forwardSignal = (signal) => child.kill(signal);
  process.on('SIGINT', () => forwardSignal('SIGINT'));
  process.on('SIGTERM', () => forwardSignal('SIGTERM'));
  child.on('exit', (code, signal) => {
    process.exitCode = signal ? 1 : (code ?? 1);
  });
}
