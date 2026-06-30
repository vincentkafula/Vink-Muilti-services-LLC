import { defineConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'


function figmaAssetResolver() {
  return {
    name: 'figma-asset-resolver',
    resolveId(id) {
      if (id.startsWith('figma:asset/')) {
        const filename = id.replace('figma:asset/', '')
        return path.resolve(__dirname, 'src/assets', filename)
      }
    },
  }
}

export default defineConfig({
  plugins: [
    figmaAssetResolver(),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  assetsInclude: ['**/*.svg', '**/*.csv', '**/*.pdf'],
  build: {
    // Raise warning threshold — we know the app is large
    chunkSizeWarningLimit: 1000,
    // Inline only tiny assets (<4KB) as base64; larger ones stay as separate files
    assetsInlineLimit: 4096,
    // Enable minification
    minify: 'esbuild',
    // Generate source maps only for errors (smaller output)
    sourcemap: false,
    // Improve CSS chunking
    cssCodeSplit: true,
  },
  // Speed up dev server
  optimizeDeps: {
    include: [
      'react',
      'react-dom',
      'lucide-react',
      'recharts',
      'sonner',
    ],
  },
})
