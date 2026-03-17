const apiProxyTarget = process.env.SUPERSONIC_API_PROXY_TARGET || 'http://127.0.0.1:9080';

export default {
  dev: {
    '/api/': {
      target: apiProxyTarget,
      changeOrigin: true,
    },
    '/aibi/api/': {
      target: apiProxyTarget,
      changeOrigin: true,
    },
  },
};
