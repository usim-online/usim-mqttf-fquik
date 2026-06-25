import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'Fquik',
  description: 'Multi-device encrypted messaging sync app powered by MQTTF',

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' }]
  ],

  themeConfig: {
    logo: '/logo.svg',

    nav: [
      { text: 'Guide / 指南', link: '/guide/getting-started' },
      { text: 'Architecture / 架构', link: '/guide/architecture' },
      { text: 'Security / 安全', link: '/guide/security' },
      {
        text: 'GitHub',
        link: 'https://github.com/usim-online/usim-mqttf-fquik'
      }
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Getting Started / 快速开始',
          items: [
            { text: 'Introduction / 简介', link: '/guide/getting-started' },
            { text: 'Build / 构建', link: '/guide/build' },
            { text: 'Running / 运行', link: '/guide/running' },
            { text: 'Configuration / 配置', link: '/guide/configuration' }
          ]
        },
        {
          text: 'Deep Dive / 深入了解',
          items: [
            { text: 'Architecture / 架构', link: '/guide/architecture' },
            { text: 'Security / 安全', link: '/guide/security' },
            { text: 'Message Flow / 消息流', link: '/guide/message-flow' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/usim-online/usim-mqttf-fquik' }
    ],

    footer: {
      message: 'Released under the GPL-3.0 License.',
      copyright: 'Copyright © 2026 USIM Online'
    },

    search: {
      provider: 'local'
    }
  }
})
