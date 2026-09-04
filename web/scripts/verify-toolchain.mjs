import process from 'node:process';

const [major, minor, patch] = process.versions.node.split('.').map(Number);
if (major !== 24 || minor < 20 || (minor === 20 && patch < 0)) {
  console.error(`Node.js 24.20.0 or newer in the Node.js 24 LTS line is required; found ${process.versions.node}.`);
  process.exit(1);
}

const userAgent = process.env.npm_config_user_agent ?? '';
if (userAgent && !userAgent.startsWith('pnpm/')) {
  console.error('This project must be installed with pnpm (packageManager: pnpm@10.12.1).');
  process.exit(1);
}
