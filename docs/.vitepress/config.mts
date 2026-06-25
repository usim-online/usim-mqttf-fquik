import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'Fquik',
  description: 'Multi-device encrypted messaging sync app powered by MQTTF',

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' }]
  ],

  locales: {
    en: {
      label: 'English',
      lang: 'en',
      link: '/en/',
      themeConfig: {
        nav: [
          { text: 'Guide', link: '/en/guide/getting-started' },
          { text: 'Architecture', link: '/en/guide/architecture' },
          { text: 'Security', link: '/en/guide/security' },
          { text: 'GitHub', link: 'https://github.com/usim-online/usim-mqttf-fquik' }
        ],
        sidebar: {
          '/en/guide/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'Introduction', link: '/en/guide/getting-started' },
                { text: 'Build', link: '/en/guide/build' },
                { text: 'Running', link: '/en/guide/running' },
                { text: 'Configuration', link: '/en/guide/configuration' }
              ]
            },
            {
              text: 'Deep Dive',
              items: [
                { text: 'Architecture', link: '/en/guide/architecture' },
                { text: 'Security', link: '/en/guide/security' },
                { text: 'Message Flow', link: '/en/guide/message-flow' }
              ]
            }
          ]
        },
        footer: {
          message: 'Released under the GPL-3.0 License.',
          copyright: 'Copyright © 2026 USIM Online'
        },
        outline: { label: 'On This Page' },
        docFooter: { prev: 'Previous', next: 'Next' },
        lastUpdated: { text: 'Updated at' }
      }
    },
    zh: {
      label: '简体中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          { text: '指南', link: '/zh/guide/getting-started' },
          { text: '架构', link: '/zh/guide/architecture' },
          { text: '安全', link: '/zh/guide/security' },
          { text: 'GitHub', link: 'https://github.com/usim-online/usim-mqttf-fquik' }
        ],
        sidebar: {
          '/zh/guide/': [
            {
              text: '快速开始',
              items: [
                { text: '简介', link: '/zh/guide/getting-started' },
                { text: '构建', link: '/zh/guide/build' },
                { text: '运行', link: '/zh/guide/running' },
                { text: '配置', link: '/zh/guide/configuration' }
              ]
            },
            {
              text: '深入了解',
              items: [
                { text: '架构', link: '/zh/guide/architecture' },
                { text: '安全', link: '/zh/guide/security' },
                { text: '消息流', link: '/zh/guide/message-flow' }
              ]
            }
          ]
        },
        footer: {
          message: '基于 GPL-3.0 许可发布。',
          copyright: 'Copyright © 2026 USIM Online'
        },
        outline: { label: '本页目录' },
        docFooter: { prev: '上一页', next: '下一页' },
        lastUpdated: { text: '最后更新于' }
      }
    }
  },

  themeConfig: {
    logo: '/logo.svg',
    socialLinks: [
      { icon: 'github', link: 'https://github.com/usim-online/usim-mqttf-fquik' }
    ],
    search: {
      provider: 'local'
    }
  }
})
