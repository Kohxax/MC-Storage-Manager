import process from 'node:process';

const expectedMajor = 24;
const nodeMajor = Number.parseInt(process.versions.node.split('.')[0] ?? '0', 10);

if (nodeMajor !== expectedMajor) {
  console.error(`Node.js ${expectedMajor}.x is required (Nuxt 4 LTS baseline); found ${process.versions.node}.`);
  process.exitCode = 1;
} else {
  console.log(`MC Storage Manager web dependencies are ready on Node.js ${process.versions.node}.`);
}
