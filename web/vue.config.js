module.exports = {
  runtimeCompiler: true,
  devServer: {
    host: '0.0.0.0',
    port: 10000,
    https: false,
    open: true,
    proxy: 'http://localhost:8080/'
  },
  configureWebpack: {
    optimization: {
      splitChunks: {
        maxSize: 244 * 1024
      }
    }
  }
}
