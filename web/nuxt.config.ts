export default defineNuxtConfig({
  devtools: { enabled: false },
  modules: ['@nuxtjs/tailwindcss'],
  css: ['~/assets/css/main.css'],
  runtimeConfig: {
    databaseUrl: process.env.DATABASE_URL ?? './data/storage.db',
    public: {
      appName: 'MC Storage Manager',
    },
  },
  devServer: {
    host: '127.0.0.1',
    port: 3000,
  },
  nitro: {
    preset: 'node-server',
    externals: {
      external: ['better-sqlite3'],
    },
  },
  typescript: {
    strict: true,
    typeCheck: false,
  },
});
